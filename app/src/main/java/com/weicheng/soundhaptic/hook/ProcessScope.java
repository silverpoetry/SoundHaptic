package com.weicheng.soundhaptic.hook;

/** Single source of truth for processes supported by the module. */
public final class ProcessScope {
    public static final String SYSTEM_UI = "com.android.systemui";
    public static final String MIUI_HOME = "com.miui.home";
    public static final String SETTINGS = "com.android.settings";
    public static final String SYSTEM_UI_PLUGIN = "miui.systemui.plugin";

    private ProcessScope() { }

    public static boolean supports(String processName) {
        return matches(processName, SYSTEM_UI)
                || matches(processName, MIUI_HOME)
                || matches(processName, SETTINGS)
                || matches(processName, SYSTEM_UI_PLUGIN);
    }

    private static boolean matches(String processName, String packageName) {
        return packageName.equals(processName)
                || processName != null && processName.startsWith(packageName + ":");
    }
}
