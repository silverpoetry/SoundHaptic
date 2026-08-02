package com.weicheng.soundhaptic;

import android.content.SharedPreferences;

import com.weicheng.soundhaptic.audio.HapticShape;

/** Shared API102 remote-preference contract used by the companion UI and hooked processes. */
public final class HapticSettings {
    public static final String GROUP = "haptic_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_VOLUME = "volume_percent";
    private static final String KEY_FREQUENCY = "frequency_hz";
    private static final String KEY_DURATION = "reference_duration_ms";
    private static final String KEY_STRENGTH = "reference_strength_percent";
    private static final String KEY_SHAPE = "shape_ordinal";
    private static final String KEY_FORCE_SPEAKER = "force_speaker";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_VOLUME = 31;
    public static final int DEFAULT_FREQUENCY = 252;
    public static final int DEFAULT_DURATION = 16;
    public static final int DEFAULT_STRENGTH = 38;
    public static final HapticShape DEFAULT_SHAPE = HapticShape.ERM_START_STOP;
    public static final boolean DEFAULT_FORCE_SPEAKER = true;

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;
    public static final int MIN_FREQUENCY = 120;
    public static final int MAX_FREQUENCY = 350;
    public static final int MIN_DURATION = 4;
    public static final int MAX_DURATION = 16;
    public static final int MIN_STRENGTH = 10;
    public static final int MAX_STRENGTH = 60;

    private HapticSettings() { }

    public static Snapshot defaults() {
        return new Snapshot(DEFAULT_ENABLED, DEFAULT_VOLUME, DEFAULT_FREQUENCY,
                DEFAULT_DURATION, DEFAULT_STRENGTH, DEFAULT_SHAPE, DEFAULT_FORCE_SPEAKER);
    }

    public static Snapshot read(SharedPreferences preferences) {
        if (preferences == null) {
            return defaults();
        }
        HapticShape[] shapes = HapticShape.values();
        int shapeIndex = boundedOrDefault(
                preferences.getInt(KEY_SHAPE, DEFAULT_SHAPE.ordinal()),
                DEFAULT_SHAPE.ordinal(), 0, shapes.length - 1);
        return new Snapshot(
                preferences.getBoolean(KEY_ENABLED, DEFAULT_ENABLED),
                boundedOrDefault(preferences.getInt(KEY_VOLUME, DEFAULT_VOLUME),
                        DEFAULT_VOLUME, MIN_VOLUME, MAX_VOLUME),
                boundedOrDefault(preferences.getInt(KEY_FREQUENCY, DEFAULT_FREQUENCY),
                        DEFAULT_FREQUENCY, MIN_FREQUENCY, MAX_FREQUENCY),
                boundedOrDefault(preferences.getInt(KEY_DURATION, DEFAULT_DURATION),
                        DEFAULT_DURATION, MIN_DURATION, MAX_DURATION),
                boundedOrDefault(preferences.getInt(KEY_STRENGTH, DEFAULT_STRENGTH),
                        DEFAULT_STRENGTH, MIN_STRENGTH, MAX_STRENGTH),
                shapes[shapeIndex],
                preferences.getBoolean(KEY_FORCE_SPEAKER, DEFAULT_FORCE_SPEAKER));
    }

    /**
     * Persists one complete settings snapshot as a synchronous remote-preference transaction.
     * API102 forwards the transaction to the module service, so returning only after commit keeps
     * the UI's "applied" state aligned with hooked-process change notifications.
     */
    public static boolean write(SharedPreferences preferences, Snapshot value) {
        return preferences.edit()
                .putBoolean(KEY_ENABLED, value.enabled)
                .putInt(KEY_VOLUME, value.volumePercent)
                .putInt(KEY_FREQUENCY, value.frequencyHz)
                .putInt(KEY_DURATION, value.referenceDurationMs)
                .putInt(KEY_STRENGTH, value.referenceStrengthPercent)
                .putInt(KEY_SHAPE, value.shape.ordinal())
                .putBoolean(KEY_FORCE_SPEAKER, value.forceSpeaker)
                .commit();
    }

    public static final class Snapshot {
        public final boolean enabled;
        public final int volumePercent;
        public final int frequencyHz;
        public final int referenceDurationMs;
        public final int referenceStrengthPercent;
        public final HapticShape shape;
        public final boolean forceSpeaker;

        public Snapshot(boolean enabled, int volumePercent, int frequencyHz,
                int referenceDurationMs, int referenceStrengthPercent,
                HapticShape shape, boolean forceSpeaker) {
            this.enabled = enabled;
            this.volumePercent = clamp(volumePercent, MIN_VOLUME, MAX_VOLUME);
            this.frequencyHz = clamp(frequencyHz, MIN_FREQUENCY, MAX_FREQUENCY);
            this.referenceDurationMs = clamp(referenceDurationMs,
                    MIN_DURATION, MAX_DURATION);
            this.referenceStrengthPercent = clamp(referenceStrengthPercent,
                    MIN_STRENGTH, MAX_STRENGTH);
            this.shape = shape == null ? DEFAULT_SHAPE : shape;
            this.forceSpeaker = forceSpeaker;
        }

        public float trackGain() {
            return volumePercent / 100.0f;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int boundedOrDefault(int value, int fallback, int min, int max) {
        return value < min || value > max ? fallback : value;
    }
}
