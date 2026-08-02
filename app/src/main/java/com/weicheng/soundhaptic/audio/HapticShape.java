package com.weicheng.soundhaptic.audio;

/** User-selectable PCM envelope. */
public enum HapticShape {
    LRA_TWO_CYCLE("LRA 方波", 10, "短促、干净"),
    LRA_ACTIVE_BRAKE("LRA 反相制动", 10, "收尾更硬"),
    DAMPED_TWO_CYCLE("阻尼脉冲", 10, "衰减更自然"),
    BIPHASIC_BRAKE("双相脉冲", 7, "最短促的反馈"),
    ERM_START_STOP("ERM 启停", 70, "适合较长反馈"),
    SINE_REFERENCE("正弦波", 10, "对照波形");

    public final String label;
    public final int recommendedDurationMs;
    public final String description;

    HapticShape(String label, int recommendedDurationMs, String description) {
        this.label = label;
        this.recommendedDurationMs = recommendedDurationMs;
        this.description = description;
    }
}
