package com.weicheng.soundhaptic.hook;

import android.content.Context;
import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;
import com.weicheng.soundhaptic.audio.HapticEffect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.libxposed.api.XposedInterface;

/** The system_server boundary: virtual capability plus semantic/raw final vibration dispatch. */
public final class SystemVibratorHooks {
    private static final String TAG = PulsePlayer.TAG;
    private static final String SERVICE = "com.android.server.vibrator.VibratorManagerService";
    private static final Pattern HAPTIC_REASON =
            Pattern.compile("performHapticFeedback(?:ForInputDevice)?\\(constant=(-?\\d+)");

    private final XposedInterface xposed;
    private final HapticRuntime runtime;
    private final Object vibratorInfoLock = new Object();
    private final ThreadLocal<Integer> semanticDepth = ThreadLocal.withInitial(() -> 0);

    private volatile Object virtualVibratorInfo;
    private boolean installed;

    public SystemVibratorHooks(XposedInterface xposed, HapticRuntime runtime) {
        this.xposed = xposed;
        this.runtime = runtime;
    }

    public void install(ClassLoader classLoader) {
        if (installed) {
            return;
        }
        Class<?> service;
        try {
            service = Class.forName(SERVICE, false, classLoader);
        } catch (ClassNotFoundException error) {
            xposed.log(Log.ERROR, TAG, "VibratorManagerService class not found");
            return;
        }

        int constructors = hookConstructors(service);
        int capabilities = hookCapabilities(service);
        int semantic = hookSemanticDispatch(service);
        int vibration = hookRawDispatch(service);
        int cancel = hookCancellation(service);
        installed = vibration > 0;
        xposed.log(installed ? Log.INFO : Log.ERROR, TAG,
                "hooks installed=" + installed + " constructors=" + constructors
                        + " capabilities=" + capabilities + " semantic=" + semantic
                        + " vibration=" + vibration + " cancel=" + cancel
                        + " class=" + service.getName());
    }

    private int hookConstructors(Class<?> service) {
        int count = 0;
        for (Constructor<?> constructor : service.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 0 || !Context.class.isAssignableFrom(params[0])) {
                continue;
            }
            xposed.hook(constructor)
                    .setId("soundhaptic_service_constructor")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        runtime.player((Context) chain.getArg(0)).warmUpModuleFeedback();
                        return result;
                    });
            count++;
        }
        return count;
    }

    private int hookCapabilities(Class<?> service) {
        int count = 0;
        for (Method method : service.getDeclaredMethods()) {
            if (method.getName().equals("getVibratorIds")
                    && method.getParameterCount() == 0
                    && method.getReturnType() == int[].class) {
                xposed.hook(method)
                        .setId("soundhaptic_virtual_vibrator_ids")
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            return result instanceof int[] && ((int[]) result).length > 0
                                    ? result : new int[]{0};
                        });
                count++;
            } else if (method.getName().equals("getVibratorInfo")
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == int.class
                    && "android.os.VibratorInfo".equals(method.getReturnType().getName())) {
                xposed.hook(method)
                        .setId("soundhaptic_virtual_vibrator_info")
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (result != null || ((Integer) chain.getArg(0)) != 0) {
                                return result;
                            }
                            return virtualVibratorInfo(method.getReturnType());
                        });
                count++;
            }
        }
        return count;
    }

    private Object virtualVibratorInfo(Class<?> infoClass) throws ReflectiveOperationException {
        Object current = virtualVibratorInfo;
        if (current != null) {
            return current;
        }
        synchronized (vibratorInfoLock) {
            current = virtualVibratorInfo;
            if (current == null) {
                Class<?> builderClass = Class.forName(
                        "android.os.VibratorInfo$Builder", false, infoClass.getClassLoader());
                Object builder = builderClass.getConstructor(int.class).newInstance(0);
                builderClass.getMethod("setSupportedEffects", int[].class)
                        .invoke(builder, (Object) new int[]{0, 1, 2, 3, 4, 5, 21});
                builderClass.getMethod("setCapabilities", long.class)
                        .invoke(builder, 4L | 32L);
                Method supportedPrimitive = builderClass.getMethod(
                        "setSupportedPrimitive", int.class, int.class);
                for (int primitiveId = 1; primitiveId <= 8; primitiveId++) {
                    supportedPrimitive.invoke(builder, primitiveId, 10);
                }
                builderClass.getMethod("setPrimitiveDelayMax", int.class).invoke(builder, 1000);
                builderClass.getMethod("setCompositionSizeMax", int.class).invoke(builder, 256);
                current = builderClass.getMethod("build").invoke(builder);
                virtualVibratorInfo = current;
                xposed.log(Log.INFO, TAG, "virtual VibratorInfo created for id=0");
            }
            return current;
        }
    }

    private int hookSemanticDispatch(Class<?> service) {
        int count = 0;
        for (Method method : service.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!method.getName().equals("performHapticFeedbackWithEffect")
                    || params.length < 7 || params[3] != int.class) {
                continue;
            }
            xposed.hook(method)
                    .setId("soundhaptic_platform_semantic_final")
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        int constant = (Integer) chain.getArg(3);
                        try {
                            Context context = ReflectionAccess.serviceContext(chain.getThisObject());
                            HapticEffect effect =
                                    PlatformEffectMapper.fromHapticConstant(constant);
                            if (context != null && effect != null) {
                                runtime.player(context).playModuleFeedback(effect);
                                xposed.log(Log.INFO, TAG,
                                        "terminal platform constant=" + constant
                                                + " effect=" + effect.name()
                                                + " package=" + chain.getArg(2));
                            }
                        } catch (Throwable error) {
                            xposed.log(Log.ERROR, TAG,
                                    "platform semantic conversion failed", error);
                        }

                        int previous = semanticDepth.get();
                        semanticDepth.set(previous + 1);
                        try {
                            return chain.proceed();
                        } finally {
                            semanticDepth.set(previous);
                        }
                    });
            count++;
        }
        return count;
    }

    private int hookRawDispatch(Class<?> service) {
        int count = 0;
        for (Method method : service.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!method.getName().equals("vibrateInternal") || params.length < 6
                    || !"android.os.CombinedVibration".equals(params[3].getName())) {
                continue;
            }
            xposed.hook(method)
                    .setId("soundhaptic_vibrate_internal")
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        if (semanticDepth.get() == 0) {
                            try {
                                Object reason = chain.getArg(5);
                                playRawRequest(
                                        ReflectionAccess.serviceContext(chain.getThisObject()),
                                        chain.getArg(3),
                                        reason instanceof String ? (String) reason : null);
                            } catch (Throwable error) {
                                xposed.log(Log.ERROR, TAG, "vibration conversion failed", error);
                            }
                        }
                        return chain.proceed();
                    });
            count++;
        }
        return count;
    }

    private int hookCancellation(Class<?> service) {
        int count = 0;
        for (Method method : service.getDeclaredMethods()) {
            if (!method.getName().equals("cancelVibrate")) {
                continue;
            }
            xposed.hook(method)
                    .setId("soundhaptic_cancel_" + method.getParameterCount())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        runtime.cancelPendingFeedback();
                        return chain.proceed();
                    });
            count++;
        }
        return count;
    }

    private void playRawRequest(Context context, Object vibration, String reason) {
        if (context == null) {
            xposed.log(Log.WARN, TAG, "vibration ignored: system context unavailable");
            return;
        }
        long duration = ReflectionAccess.duration(vibration);
        Integer constant = parseHapticConstant(reason);
        HapticEffect effect = constant == null
                ? PlatformEffectMapper.fromDirectVibration(vibration, duration)
                : PlatformEffectMapper.fromHapticConstant(constant);
        if (effect == null) {
            Log.d(TAG, "ignored no-haptics request reason=" + reason);
            return;
        }
        runtime.player(context).playModuleFeedback(effect);
        xposed.log(Log.INFO, TAG, "terminal vibration duration=" + duration
                + " constant=" + constant + " effect=" + effect.name()
                + " reason=" + reason);
    }

    private static Integer parseHapticConstant(String reason) {
        if (reason == null) {
            return null;
        }
        Matcher matcher = HAPTIC_REASON.matcher(reason);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
