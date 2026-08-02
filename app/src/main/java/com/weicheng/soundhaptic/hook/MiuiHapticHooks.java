package com.weicheng.soundhaptic.hook;

import android.content.Context;
import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;
import com.weicheng.soundhaptic.audio.HapticEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Hooks MIUI's five explicit capability gates and four semantic terminal overloads. */
public final class MiuiHapticHooks {
    private static final String TAG = PulsePlayer.TAG;
    private static final String HAPTIC_UTIL = "miui.util.HapticFeedbackUtil";

    private final XposedInterface xposed;
    private final HapticRuntime runtime;
    private boolean installed;

    public MiuiHapticHooks(XposedInterface xposed, HapticRuntime runtime) {
        this.xposed = xposed;
        this.runtime = runtime;
    }

    public void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        int capabilityCount = 0;
        int terminalCount = 0;
        int warmupCount = 0;
        try {
            Class<?> utility = Class.forName(HAPTIC_UTIL, false, classLoader);
            for (Constructor<?> constructor : utility.getDeclaredConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 0 || !Context.class.isAssignableFrom(params[0])) {
                    continue;
                }
                xposed.hook(constructor)
                        .setId("soundhaptic_miui_warmup_" + params.length)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object receiver = chain.getThisObject();
                            ReflectionAccess.setBoolean(
                                    receiver, "mIsSupportLinearMotorVibrate", true);
                            ReflectionAccess.setBoolean(
                                    receiver, "mIsSupportZLinearMotorVibrate", true);
                            runtime.player((Context) chain.getArg(0)).warmUpModuleFeedback();
                            return result;
                        });
                warmupCount++;
            }
            for (Method method : utility.getDeclaredMethods()) {
                String name = method.getName();
                if (isCapabilityGate(method)) {
                    xposed.hook(method)
                            .setId("soundhaptic_miui_support_" + name + "_"
                                    + method.getParameterCount())
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> true);
                    capabilityCount++;
                } else if (isTerminalDispatch(method)) {
                    xposed.hook(method)
                            .setId("soundhaptic_miui_terminal_" + name + "_"
                                    + method.getParameterCount())
                            .setPriority(XposedInterface.PRIORITY_HIGHEST)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                HapticEffect effect =
                                        MiuiEffectMapper.fromArguments(chain.getArgs());
                                if (effect == null) {
                                    return chain.proceed();
                                }
                                Context context = ReflectionAccess.findContext(
                                        chain.getThisObject(), chain.getArgs());
                                runtime.play(context, effect, "MIUI terminal " + name);
                                xposed.log(Log.INFO, TAG, "terminal MIUI method=" + name
                                        + " semantic=" + chain.getArg(1)
                                        + " effect=" + effect.name());
                                return true;
                            });
                    terminalCount++;
                }
            }
        } catch (ClassNotFoundException ignored) {
            xposed.log(Log.INFO, TAG, "MIUI HapticFeedbackUtil not present in this process");
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "Unable to install MIUI terminal haptic hooks", error);
        }

        installed = terminalCount > 0;
        xposed.log(installed ? Log.INFO : Log.ERROR, TAG,
                "MIUI root hooks installed=" + installed + " support=" + capabilityCount
                        + " terminal=" + terminalCount + " warmup=" + warmupCount);
    }

    private static boolean isCapabilityGate(Method method) {
        if (method.getReturnType() != boolean.class) {
            return false;
        }
        String name = method.getName();
        if (method.getParameterCount() == 0) {
            return name.equals("isSupportLinearMotorVibrate")
                    || name.equals("isSupportZLinearMotorVibrate")
                    || name.equals("isSupportHapticVersion2");
        }
        return method.getParameterCount() == 1
                && method.getParameterTypes()[0] == int.class
                && (name.equals("isSupportLinearMotorVibrate")
                || name.equals("isSupportExtHapticFeedback"));
    }

    /** Exact overloads that dispatch IDs or keys to MIUI VibrateUtils. */
    private static boolean isTerminalDispatch(Method method) {
        if (method.getReturnType() != boolean.class) {
            return false;
        }
        String name = method.getName();
        Class<?>[] params = method.getParameterTypes();
        if (params.length < 3
                || !"android.os.VibrationAttributes".equals(params[0].getName())) {
            return false;
        }
        if (name.equals("performExtHapticFeedback")) {
            return params.length == 3 && params[1] == int.class && params[2] == boolean.class
                    || params.length == 4 && params[1] == int.class
                    && params[2] == double.class && params[3] == String.class;
        }
        if (!name.equals("performHapticFeedback") || params.length != 4) {
            return false;
        }
        return params[1] == int.class && params[2] == double.class
                && params[3] == String.class
                || params[1] == String.class && params[2] == boolean.class
                && params[3] == int.class;
    }
}
