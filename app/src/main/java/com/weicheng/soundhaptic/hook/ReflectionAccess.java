package com.weicheng.soundhaptic.hook;

import android.content.Context;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small, failure-tolerant reflection boundary for hidden Android and MIUI objects. */
final class ReflectionAccess {
    private ReflectionAccess() { }

    static Context findContext(Object receiver, Iterable<?> args) {
        if (receiver instanceof View) {
            return ((View) receiver).getContext();
        }
        if (receiver instanceof Context) {
            return (Context) receiver;
        }
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof View) {
                    return ((View) arg).getContext();
                }
                if (arg instanceof Context) {
                    return (Context) arg;
                }
            }
        }
        Object value = field(receiver, "mContext");
        return value instanceof Context ? (Context) value : null;
    }

    static Context serviceContext(Object service) {
        Object value = field(service, "mContext");
        return value instanceof Context ? (Context) value : null;
    }

    static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    static boolean setBoolean(Object target, String name, boolean value) {
        if (target == null) {
            return false;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.setBoolean(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    static long duration(Object vibration) {
        Object value = invoke(vibration, "getDuration");
        return value instanceof Long ? (Long) value : -1L;
    }

    static Number number(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Number ? (Number) value : null;
    }

    static Object invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    static Object invoke(Object target, String name, int index) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name, int.class);
                method.setAccessible(true);
                return method.invoke(target, index);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }
}
