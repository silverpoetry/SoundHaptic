package com.weicheng.soundhaptic;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.weicheng.soundhaptic.audio.AudioTrackFactory;
import com.weicheng.soundhaptic.audio.HapticEffect;
import com.weicheng.soundhaptic.audio.HapticShape;
import com.weicheng.soundhaptic.audio.ModuleHapticRenderer;
import com.weicheng.soundhaptic.audio.PreviewPulseRenderer;

/**
 * Thread-safe audio facade used by the companion UI and hooked processes.
 * Rendering details live in dedicated preview and module renderers.
 */
public final class PulsePlayer implements AutoCloseable {
    public static final String TAG = "SoundHaptic";

    private final HandlerThread audioThread;
    private final Handler audioHandler;
    private final PreviewPulseRenderer previewRenderer;
    private final ModuleHapticRenderer moduleRenderer;
    private volatile boolean closed;

    public PulsePlayer(Context context) {
        this(context, null);
    }

    public PulsePlayer(Context context, SharedPreferences settingsPreferences) {
        Context appContext = context.getApplicationContext();
        audioThread = new HandlerThread("SoundHapticAudio", Process.THREAD_PRIORITY_URGENT_AUDIO);
        audioThread.start();
        audioHandler = new Handler(audioThread.getLooper());
        AudioTrackFactory trackFactory = new AudioTrackFactory(appContext);
        previewRenderer = new PreviewPulseRenderer(audioHandler, trackFactory);
        moduleRenderer = new ModuleHapticRenderer(appContext, audioHandler, trackFactory,
                previewRenderer, settingsPreferences);
    }

    public void play(double frequencyHz, int durationMs, int strengthPercent, HapticShape shape,
            boolean forceSpeaker) {
        play(frequencyHz, durationMs, strengthPercent, shape, forceSpeaker, 100);
    }

    public void play(double frequencyHz, int durationMs, int strengthPercent, HapticShape shape,
            boolean forceSpeaker, int volumePercent) {
        if (closed) {
            return;
        }
        double safeFrequency = clamp(frequencyHz, 40.0, 400.0);
        int safeDuration = (int) clamp(durationMs, 4, 1000);
        int safeStrength = (int) clamp(strengthPercent, 1, 100);
        float safeTrackGain = (float) clamp(volumePercent / 100.0, 0.0, 1.0);
        audioHandler.post(() -> previewRenderer.play(
                safeFrequency, safeDuration, safeStrength,
                shape, forceSpeaker, safeTrackGain));
    }

    /** Prepares module playback while the system is starting to avoid first-use latency. */
    public void warmUpModuleFeedback() {
        if (!closed) {
            moduleRenderer.warmUp();
        }
    }

    public void playModuleFeedback(HapticEffect effect) {
        playModuleFeedback(effect, 1, 60);
    }

    public void playModuleFeedback(HapticEffect effect, int repeatCount, int repeatSpacingMs) {
        if (!closed) {
            moduleRenderer.play(effect, repeatCount, repeatSpacingMs);
        }
    }

    public void stopAll() {
        if (!closed) {
            audioHandler.post(() -> {
                moduleRenderer.stop();
                previewRenderer.stop();
            });
        }
    }

    /** Cancels future pulses without swallowing a short pulse already accepted for playback. */
    public void cancelPendingModuleFeedback() {
        if (!closed) {
            moduleRenderer.cancelPending();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        moduleRenderer.beginClose();
        audioHandler.post(() -> {
            previewRenderer.stop();
            moduleRenderer.closeOnAudioThread();
            audioThread.quitSafely();
        });
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
