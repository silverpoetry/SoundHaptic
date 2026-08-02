package com.weicheng.soundhaptic.audio;

/** Stable semantic effects used by Android, MIUI and the speaker renderer. */
public enum HapticEffect {
    TEXTURE_TICK("texture_tick", pulse(0, 4, 13, HapticShape.DAMPED_TWO_CYCLE)),
    LOW_TICK("low_tick", pulse(0, 5, 16, HapticShape.DAMPED_TWO_CYCLE)),
    TICK("tick", pulse(0, 5, 21, HapticShape.LRA_ACTIVE_BRAKE)),
    KEYBOARD_RELEASE("keyboard_release", pulse(0, 4, 16, HapticShape.DAMPED_TWO_CYCLE)),
    KEYBOARD_PRESS("keyboard_press", pulse(0, 7, 25, HapticShape.LRA_ACTIVE_BRAKE)),
    CLICK("click", pulse(0, 10, 32, HapticShape.LRA_ACTIVE_BRAKE)),
    HEAVY_CLICK("heavy_click", pulse(0, 14, 46, HapticShape.LRA_ACTIVE_BRAKE)),
    DOUBLE_CLICK("double_click",
            pulse(0, 8, 31, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(42, 10, 38, HapticShape.LRA_ACTIVE_BRAKE)),
    TOGGLE_ON("toggle_on", pulse(0, 7, 27, HapticShape.LRA_ACTIVE_BRAKE)),
    TOGGLE_OFF("toggle_off", pulse(0, 5, 14, HapticShape.DAMPED_TWO_CYCLE)),
    ASSISTANT("assistant", pulse(0, 11, 37, HapticShape.LRA_ACTIVE_BRAKE)),
    SCREEN_LOCK("screen_lock_209",
            pulse(0, 10, 34, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(46, 10, 27, HapticShape.LRA_ACTIVE_BRAKE)),
    NOTIFICATION_CLEANUP("notification_cleanup_212",
            pulse(0, 8, 39, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(80, 9, 35, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(169, 10, 32, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(268, 12, 28, HapticShape.DAMPED_TWO_CYCLE)),
    PROCESS_CLEANUP("process_cleanup_213",
            pulse(0, 10, 38, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(82, 10, 34, HapticShape.LRA_ACTIVE_BRAKE),
            pulse(169, 11, 30, HapticShape.DAMPED_TWO_CYCLE),
            pulse(274, 12, 26, HapticShape.DAMPED_TWO_CYCLE));

    public final String label;
    public final HapticPulse[] pulses;

    HapticEffect(String label, HapticPulse... pulses) {
        this.label = label;
        this.pulses = pulses;
    }

    private static HapticPulse pulse(int delayMs, int durationMs, int strengthPercent,
            HapticShape shape) {
        return new HapticPulse(delayMs, durationMs, strengthPercent, shape);
    }
}
