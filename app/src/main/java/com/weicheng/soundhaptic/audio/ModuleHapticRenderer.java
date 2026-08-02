package com.weicheng.soundhaptic.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.weicheng.soundhaptic.HapticSettings;

/** Owns live module settings, semantic patterns, keep-alive, and low-latency system playback. */
public final class ModuleHapticRenderer {
    private static final String TAG = "SoundHaptic";
    private static final int KEEP_ALIVE_DURATION_MS = 40;
    private static final int MODULE_BUFFER_DURATION_MS = 20;

    private final Context context;
    private final Handler audioHandler;
    private final PreviewPulseRenderer fallbackRenderer;
    private final PersistentHapticStream systemStream;
    private final PreparedTrackPool preparedTracks;
    private final ScreenStateMonitor screenStateMonitor;
    private final SharedPreferences settingsPreferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener;
    private int modulePulseSequence;
    private int patternGeneration;
    private volatile HapticSettings.Snapshot settings = HapticSettings.defaults();
    private volatile boolean closed;

    public ModuleHapticRenderer(Context context, Handler audioHandler,
            AudioTrackFactory trackFactory, PreviewPulseRenderer fallbackRenderer,
            SharedPreferences settingsPreferences) {
        this.context = context.getApplicationContext();
        this.audioHandler = audioHandler;
        this.fallbackRenderer = fallbackRenderer;
        this.settingsPreferences = settingsPreferences;
        preparedTracks = new PreparedTrackPool(trackFactory);
        systemStream = "android".equals(this.context.getPackageName())
                ? new PersistentHapticStream(trackFactory) : null;
        screenStateMonitor = new ScreenStateMonitor(this.context, audioHandler,
                this::startKeepAlive, this::stopKeepAlive);
        settings = HapticSettings.read(settingsPreferences);
        settingsListener = (preferences, key) -> {
            settings = HapticSettings.read(preferences);
            Log.d(TAG, "remote setting changed key=" + key);
            audioHandler.post(this::applySettingsOnAudioThread);
        };
        if (settingsPreferences != null) {
            settingsPreferences.registerOnSharedPreferenceChangeListener(settingsListener);
        }
    }

    public void warmUp() {
        if (closed) {
            return;
        }
        audioHandler.post(() -> {
            if (systemStream == null) {
                ensurePreparedTracks();
            }
            screenStateMonitor.register();
            if (screenStateMonitor.isInteractive()) {
                startKeepAlive();
            }
        });
    }

    public void play(HapticEffect effect, int repeatCount, int repeatSpacingMs) {
        HapticSettings.Snapshot current = settings;
        if (closed || !current.enabled || current.volumePercent == 0) {
            return;
        }
        HapticEffect safeEffect = effect == null ? HapticEffect.CLICK : effect;
        int safeCount = clamp(repeatCount, 1, 12);
        int safeSpacing = clamp(repeatSpacingMs, 24, 500);
        audioHandler.post(() -> schedulePattern(safeEffect, safeCount, safeSpacing));
    }

    public void cancelPending() {
        if (!closed) {
            audioHandler.post(() -> {
                patternGeneration++;
                if (systemStream != null) {
                    systemStream.clear();
                }
            });
        }
    }

    /** Must run on the shared audio thread. */
    public void stop() {
        patternGeneration++;
        preparedTracks.stop();
    }

    public void beginClose() {
        closed = true;
        if (settingsPreferences != null) {
            settingsPreferences.unregisterOnSharedPreferenceChangeListener(settingsListener);
        }
    }

    /** Must run on the shared audio thread after {@link #beginClose()}. */
    public void closeOnAudioThread() {
        patternGeneration++;
        screenStateMonitor.close();
        stopKeepAlive();
        preparedTracks.close();
        if (systemStream != null) {
            systemStream.close();
        }
    }

    private void schedulePattern(HapticEffect effect, int repeatCount, int repeatSpacingMs) {
        // Incidental haptics must not cancel a semantic multi-pulse pattern. Only an explicit
        // vibrator cancellation advances this generation.
        int generation = patternGeneration;
        for (int repeat = 0; repeat < repeatCount; repeat++) {
            for (HapticPulse pulse : effect.pulses) {
                int delay = repeat * repeatSpacingMs + pulse.delayMs;
                Runnable playback = () -> {
                    if (!closed && generation == patternGeneration) {
                        playPreparedPulse(pulse, effect.label);
                    }
                };
                if (delay == 0) {
                    playback.run();
                } else {
                    audioHandler.postDelayed(playback, delay);
                }
            }
        }
    }

    private void ensurePreparedTracks() {
        if (closed) {
            return;
        }
        HapticSettings.Snapshot current = settings;
        preparedTracks.prepare(createModuleBuffer(HapticEffect.CLICK.pulses[0], current),
                current.forceSpeaker, current.trackGain());
    }

    private void startKeepAlive() {
        HapticSettings.Snapshot current = settings;
        if (!current.enabled || current.volumePercent == 0) {
            return;
        }
        if (systemStream != null) {
            systemStream.start(current.trackGain(), current.forceSpeaker);
            return;
        }
        if (closed) {
            return;
        }
        short[] silence = new short[
                WaveformSynthesizer.SAMPLE_RATE * KEEP_ALIVE_DURATION_MS / 1000];
        preparedTracks.startKeepAlive(silence, current.forceSpeaker, current.trackGain());
    }

    private void stopKeepAlive() {
        if (systemStream != null) {
            systemStream.stop();
        } else {
            preparedTracks.stopKeepAlive();
        }
    }

    private void playPreparedPulse(HapticPulse pulse, String effectLabel) {
        HapticSettings.Snapshot current = settings;
        if (!current.enabled || current.volumePercent == 0) {
            return;
        }
        double frequencyHz = current.frequencyHz;
        int durationMs = resolveDuration(pulse, current);
        int strengthPercent = resolveStrength(pulse, current);
        HapticShape shape = current.shape;
        if (systemStream != null) {
            systemStream.start(current.trackGain(), current.forceSpeaker);
            systemStream.enqueue(WaveformSynthesizer.synthesize(
                    frequencyHz, durationMs, strengthPercent / 100.0, shape));
            int sequence = ++modulePulseSequence;
            Log.d(TAG, "system stream pulse seq=" + sequence + " effect=" + effectLabel
                    + " frequency=" + frequencyHz + "Hz duration=" + durationMs
                    + "ms strength=" + strengthPercent + "% shape=" + shape
                    + " volume=" + current.volumePercent + "%");
            return;
        }

        ensurePreparedTracks();
        int sequence = ++modulePulseSequence;
        try {
            int trackIndex = preparedTracks.play(createModuleBuffer(pulse, current),
                    current.forceSpeaker, current.trackGain());
            if (trackIndex < 0) {
                fallbackRenderer.play(frequencyHz, durationMs, strengthPercent, shape,
                        current.forceSpeaker, current.trackGain());
                return;
            }
            Log.d(TAG, "module pulse seq=" + sequence + " effect=" + effectLabel
                    + " frequency=" + frequencyHz + "Hz duration=" + durationMs
                    + "ms strength=" + strengthPercent + "% shape=" + shape
                    + " volume=" + current.volumePercent + "% track=" + trackIndex);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to play prepared module pulse seq=" + sequence, error);
            fallbackRenderer.play(frequencyHz, durationMs, strengthPercent, shape,
                    current.forceSpeaker, current.trackGain());
        }
    }

    private short[] createModuleBuffer(HapticPulse pulse, HapticSettings.Snapshot current) {
        short[] active = WaveformSynthesizer.synthesize(
                current.frequencyHz, resolveDuration(pulse, current),
                resolveStrength(pulse, current) / 100.0, current.shape);
        short[] buffer = new short[
                WaveformSynthesizer.SAMPLE_RATE * MODULE_BUFFER_DURATION_MS / 1000];
        System.arraycopy(active, 0, buffer, 0, Math.min(active.length, buffer.length));
        return buffer;
    }

    private void applySettingsOnAudioThread() {
        HapticSettings.Snapshot current = settings;
        patternGeneration++;
        if (systemStream != null) {
            systemStream.clear();
        }
        if (!current.enabled || current.volumePercent == 0) {
            stopKeepAlive();
            return;
        }
        if (systemStream != null) {
            systemStream.apply(current.trackGain(), current.forceSpeaker);
        }
        preparedTracks.apply(current.trackGain(), current.forceSpeaker);
        if (screenStateMonitor.isInteractive()) {
            startKeepAlive();
        }
        Log.i(TAG, "settings applied enabled=" + current.enabled
                + " volume=" + current.volumePercent + "% frequency="
                + current.frequencyHz + "Hz duration=" + current.referenceDurationMs
                + "ms strength=" + current.referenceStrengthPercent + "% shape="
                + current.shape + " speaker=" + current.forceSpeaker);
    }

    private static int resolveDuration(HapticPulse pulse, HapticSettings.Snapshot current) {
        return clamp((int) Math.round(pulse.durationMs
                        * current.referenceDurationMs
                        / (double) HapticSettings.DEFAULT_DURATION),
                HapticSettings.MIN_DURATION, MODULE_BUFFER_DURATION_MS);
    }

    private static int resolveStrength(HapticPulse pulse, HapticSettings.Snapshot current) {
        return clamp((int) Math.round(pulse.strengthPercent
                        * current.referenceStrengthPercent
                        / (double) HapticSettings.DEFAULT_STRENGTH),
                1, 100);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
