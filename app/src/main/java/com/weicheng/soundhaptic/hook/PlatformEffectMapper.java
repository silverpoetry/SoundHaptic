package com.weicheng.soundhaptic.hook;

import com.weicheng.soundhaptic.audio.HapticEffect;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;

/** Maps Android haptic constants and hidden VibrationEffect segment graphs to semantic profiles. */
final class PlatformEffectMapper {
    private PlatformEffectMapper() { }

    static HapticEffect fromHapticConstant(int constant) {
        if ((constant & 0xF0000000) == 0x10000000) {
            return MiuiEffectMapper.fromSemanticId(constant & 0xFF);
        }
        return switch (constant) {
            case -1 -> null; // NO_HAPTICS
            case 4, 9, 27 -> HapticEffect.TEXTURE_TICK;
            case 6, 8, 11, 13, 18, 23, 26 -> HapticEffect.TICK;
            case 7 -> HapticEffect.KEYBOARD_RELEASE;
            case 3 -> HapticEffect.KEYBOARD_PRESS;
            case 1, 5, 12, 15, 16, 19, 20, 10004 -> HapticEffect.CLICK;
            case 0, 14, 25, 10001, 10003 -> HapticEffect.HEAVY_CLICK;
            case 17, 10005 -> HapticEffect.DOUBLE_CLICK;
            case 21 -> HapticEffect.TOGGLE_ON;
            case 22 -> HapticEffect.TOGGLE_OFF;
            case 24 -> HapticEffect.LOW_TICK;
            case 10002 -> HapticEffect.ASSISTANT;
            default -> HapticEffect.CLICK;
        };
    }

    static HapticEffect fromDirectVibration(Object vibration, long durationMs) {
        HapticEffect structured = inspect(vibration,
                Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        if (structured != null) {
            return structured;
        }
        String description = String.valueOf(vibration).toUpperCase(Locale.US);
        if (description.contains("DOUBLE_CLICK")) {
            return HapticEffect.DOUBLE_CLICK;
        }
        if (description.contains("HEAVY_CLICK") || description.contains("THUD")) {
            return HapticEffect.HEAVY_CLICK;
        }
        if (description.contains("TEXTURE_TICK") || description.contains("LOW_TICK")) {
            return HapticEffect.TEXTURE_TICK;
        }
        if (description.contains("TICK")) {
            return HapticEffect.TICK;
        }
        if (description.contains("CLICK") || description.contains("POP")) {
            return HapticEffect.CLICK;
        }
        if (durationMs >= 18) {
            return HapticEffect.HEAVY_CLICK;
        }
        if (durationMs >= 0 && durationMs <= 6) {
            return HapticEffect.TICK;
        }
        return HapticEffect.CLICK;
    }

    /** Reads hidden segment graphs without linking hidden framework APIs. */
    private static HapticEffect inspect(Object value, Set<Object> visited, int depth) {
        if (value == null || depth > 8 || visited.contains(value)) {
            return null;
        }
        Class<?> type = value.getClass();
        if (isLeaf(type)) {
            return null;
        }
        visited.add(value);

        String className = type.getName();
        if (className.endsWith("PrebakedSegment")) {
            Number id = ReflectionAccess.number(value, "getEffectId");
            return id == null ? null : fromPredefinedId(id.intValue());
        }
        if (className.endsWith("PrimitiveSegment")) {
            Number id = ReflectionAccess.number(value, "getPrimitiveId");
            return id == null ? null : fromPrimitiveId(id.intValue());
        }
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                HapticEffect effect = inspect(
                        Array.get(value, index), visited, depth + 1);
                if (effect != null) {
                    return effect;
                }
            }
            return null;
        }
        if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                HapticEffect effect = inspect(item, visited, depth + 1);
                if (effect != null) {
                    return effect;
                }
            }
            return null;
        }
        if (className.equals("android.util.SparseArray")) {
            Number size = ReflectionAccess.number(value, "size");
            if (size != null) {
                for (int index = 0; index < size.intValue(); index++) {
                    HapticEffect effect = inspect(
                            ReflectionAccess.invoke(value, "valueAt", index), visited, depth + 1);
                    if (effect != null) {
                        return effect;
                    }
                }
            }
            return null;
        }
        if (className.startsWith("android.os.CombinedVibration")
                || className.startsWith("android.os.VibrationEffect")) {
            for (String getter : new String[]{"getEffect", "getEffects", "getSegments"}) {
                HapticEffect effect = inspect(
                        ReflectionAccess.invoke(value, getter), visited, depth + 1);
                if (effect != null) {
                    return effect;
                }
            }
        }
        return null;
    }

    private static boolean isLeaf(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type)
                || CharSequence.class.isAssignableFrom(type)
                || Boolean.class == type || Character.class == type || Class.class == type;
    }

    private static HapticEffect fromPredefinedId(int id) {
        if ((id & 0xF0000000) == 0x10000000) {
            return MiuiEffectMapper.fromSemanticId(id & 0xFF);
        }
        return switch (id) {
            case 0, 4 -> HapticEffect.CLICK;
            case 1 -> HapticEffect.DOUBLE_CLICK;
            case 2 -> HapticEffect.TICK;
            case 3, 5 -> HapticEffect.HEAVY_CLICK;
            case 21 -> HapticEffect.TEXTURE_TICK;
            default -> null;
        };
    }

    private static HapticEffect fromPrimitiveId(int id) {
        return switch (id) {
            case 1 -> HapticEffect.CLICK;
            case 2, 5 -> HapticEffect.HEAVY_CLICK;
            case 7 -> HapticEffect.TICK;
            case 8 -> HapticEffect.LOW_TICK;
            case 3, 4, 6 -> HapticEffect.ASSISTANT;
            default -> null;
        };
    }
}
