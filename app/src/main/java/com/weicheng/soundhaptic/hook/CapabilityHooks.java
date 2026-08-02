package com.weicheng.soundhaptic.hook;

import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Declares only the actuator capabilities that gate the ROM's normal haptic dispatch. */
public final class CapabilityHooks {
    private static final String TAG = PulsePlayer.TAG;
    private static final String HAPTIC_MOTOR = "sys.haptic.motor";
    private static final String HAPTIC_VERSION = "sys.haptic.version";
    private static final String MOTOR_NOT_SUPPORTED = "ro.odm.mm.vibrator.motor_not_support";

    private final XposedInterface xposed;
    private boolean platformInstalled;
    private boolean clientInstalled;
    private boolean homeInstalled;

    public CapabilityHooks(XposedInterface xposed) {
        this.xposed = xposed;
    }

    /** Runs before Application creation because HyperOS caches these properties in static fields. */
    public void installPlatformProperties() {
        if (platformInstalled) {
            return;
        }
        int count = 0;
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties", false, null);
            for (Method method : properties.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 0 || params[0] != String.class) {
                    continue;
                }
                if (method.getName().equals("get") && method.getReturnType() == String.class
                        && (params.length == 1
                        || params.length == 2 && params[1] == String.class)) {
                    xposed.hook(method)
                            .setId("soundhaptic_platform_property_get_" + params.length)
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> propertyValue(
                                    (String) chain.getArg(0), chain::proceed));
                    count++;
                } else if (method.getName().equals("getBoolean")
                        && method.getReturnType() == boolean.class
                        && params.length == 2 && params[1] == boolean.class) {
                    xposed.hook(method)
                            .setId("soundhaptic_platform_property_get_boolean")
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> MOTOR_NOT_SUPPORTED.equals(chain.getArg(0))
                                    ? false : chain.proceed());
                    count++;
                }
            }
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "Unable to declare platform haptic properties", error);
        }
        platformInstalled = count >= 3;
        xposed.log(platformInstalled ? Log.INFO : Log.ERROR, TAG,
                "platform haptic property capability installed=" + platformInstalled
                        + " methods=" + count);
    }

    /** Makes framework client checks agree with the virtual service actuator. */
    public void installFrameworkClient(ClassLoader classLoader) {
        if (clientInstalled) {
            return;
        }
        int count = 0;
        try {
            Class<?> vibrator = Class.forName("android.os.SystemVibrator", false, classLoader);
            for (Method method : vibrator.getDeclaredMethods()) {
                if (method.getName().equals("hasVibrator")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == boolean.class) {
                    xposed.hook(method)
                            .setId("soundhaptic_client_has_vibrator")
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> true);
                    count++;
                }
            }

            Class<?> manager = Class.forName(
                    "android.os.SystemVibratorManager", false, classLoader);
            for (Method method : manager.getDeclaredMethods()) {
                if (method.getName().equals("getVibratorIds")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == int[].class) {
                    xposed.hook(method)
                            .setId("soundhaptic_client_vibrator_ids")
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                return result instanceof int[] && ((int[]) result).length > 0
                                        ? result : new int[]{0};
                            });
                    count++;
                }
            }
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG,
                    "Unable to install client vibrator capability hooks", error);
        }
        clientInstalled = count > 0;
        xposed.log(clientInstalled ? Log.INFO : Log.ERROR, TAG,
                "client vibrator capability hooks installed=" + clientInstalled
                        + " methods=" + count);
    }

    /** Handles only the launcher's private SystemProperties wrapper. */
    public void installMiuiHome(ClassLoader classLoader) {
        if (homeInstalled) {
            return;
        }
        int propertyHooks = 0;
        try {
            Class<?> properties = Class.forName(
                    "com.miui.launcher.utils.SystemProperties", false, classLoader);
            for (Method method : properties.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!method.getName().equals("get") || method.getReturnType() != String.class
                        || params.length != 2 || params[0] != String.class
                        || params[1] != String.class) {
                    continue;
                }
                xposed.hook(method)
                        .setId("soundhaptic_home_haptic_version_capability")
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> HAPTIC_VERSION.equals(chain.getArg(0))
                                ? "2.0" : chain.proceed());
                propertyHooks++;
            }
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "Unable to declare MiuiHome Haptic V2 capability", error);
        }
        homeInstalled = propertyHooks > 0;
        xposed.log(homeInstalled ? Log.INFO : Log.ERROR, TAG,
                "MiuiHome Haptic V2 capability installed=" + homeInstalled
                        + " propertyHooks=" + propertyHooks);
    }

    private static Object propertyValue(String key, Proceed original) throws Throwable {
        if (HAPTIC_MOTOR.equals(key)) {
            return "linear";
        }
        if (HAPTIC_VERSION.equals(key)) {
            return "2.0";
        }
        if (MOTOR_NOT_SUPPORTED.equals(key)) {
            return "false";
        }
        return original.call();
    }

    @FunctionalInterface
    private interface Proceed {
        Object call() throws Throwable;
    }
}
