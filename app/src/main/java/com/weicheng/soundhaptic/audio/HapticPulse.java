package com.weicheng.soundhaptic.audio;

/** One delayed burst inside a semantic effect. */
public final class HapticPulse {
    public final int delayMs;
    public final int durationMs;
    public final int strengthPercent;
    public final HapticShape shape;

    public HapticPulse(int delayMs, int durationMs, int strengthPercent, HapticShape shape) {
        this.delayMs = delayMs;
        this.durationMs = durationMs;
        this.strengthPercent = strengthPercent;
        this.shape = shape;
    }
}
