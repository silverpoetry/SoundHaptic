package com.weicheng.soundhaptic.ui;

import android.os.Handler;
import android.os.Looper;

import com.weicheng.soundhaptic.HapticSettings;
import com.weicheng.soundhaptic.PulsePlayer;
import com.weicheng.soundhaptic.audio.HapticShape;

import java.util.function.Supplier;

/** Owns companion-app preview playback and its cancellable sweep sequence. */
public final class HapticPreviewController implements AutoCloseable {
    private static final int[] SWEEP_FREQUENCIES = {
            180, 200, 220, 235, 250, 265, 280, 300
    };

    private final PulsePlayer player;
    private final Supplier<HapticSettings.Snapshot> settingsSupplier;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;

    public HapticPreviewController(PulsePlayer player,
            Supplier<HapticSettings.Snapshot> settingsSupplier) {
        this.player = player;
        this.settingsSupplier = settingsSupplier;
    }

    public void playCurrent() {
        generation++;
        player.stopAll();
        HapticSettings.Snapshot settings = settingsSupplier.get();
        player.play(settings.frequencyHz, settings.referenceDurationMs,
                settings.referenceStrengthPercent, settings.shape,
                settings.forceSpeaker, settings.volumePercent);
    }

    public void startSweep() {
        player.stopAll();
        int currentGeneration = ++generation;
        HapticSettings.Snapshot settings = settingsSupplier.get();
        int safeStrength = Math.min(settings.referenceStrengthPercent, 45);
        for (int index = 0; index < SWEEP_FREQUENCIES.length; index++) {
            int sweepFrequency = SWEEP_FREQUENCIES[index];
            handler.postDelayed(() -> {
                if (currentGeneration != generation) {
                    return;
                }
                int pulseDuration = clamp(
                        (int) Math.round(2000.0 / sweepFrequency), 8, 15);
                player.play(sweepFrequency, pulseDuration, safeStrength,
                        HapticShape.LRA_TWO_CYCLE, settings.forceSpeaker,
                        settings.volumePercent);
            }, index * 330L);
        }
    }

    public void stop() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        player.stopAll();
    }

    @Override
    public void close() {
        stop();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
