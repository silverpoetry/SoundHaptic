package com.weicheng.soundhaptic.hook;

import com.weicheng.soundhaptic.audio.HapticEffect;

import java.util.Locale;

/** Converts MIUI keys and Xiaomi extended IDs into stable semantic feedback profiles. */
final class MiuiEffectMapper {
    private MiuiEffectMapper() { }

    static HapticEffect fromArguments(Iterable<?> args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                HapticEffect effect = fromKey((String) arg);
                if (effect != null) {
                    return effect;
                }
            }
        }
        for (Object arg : args) {
            if (arg instanceof Integer) {
                return fromExtendedId((Integer) arg);
            }
        }
        return null;
    }

    private static HapticEffect fromKey(String key) {
        String value = key.toLowerCase(Locale.US).replace('.', '_');
        if (containsAny(value, "tap_light", "flick_light", "mesh_light", "popup_light",
                "button_small", "gear_light")) {
            return HapticEffect.TICK;
        }
        if (containsAny(value, "tap_normal", "flick", "mesh_normal", "popup_normal",
                "button_middle", "trigger_drawer")) {
            return HapticEffect.CLICK;
        }
        if (containsAny(value, "long_press", "longpress", "mesh_heavy", "pickup", "hold",
                "button_large", "gear_heavy", "alert")) {
            return HapticEffect.HEAVY_CLICK;
        }
        if (containsAny(value, "keyboard", "virtual_key_down", "virtual_key_tap")) {
            return HapticEffect.KEYBOARD_PRESS;
        }
        if (value.contains("virtual_key_up")) {
            return HapticEffect.KEYBOARD_RELEASE;
        }
        if (containsAny(value, "switch", "zaxis")) {
            return HapticEffect.TOGGLE_ON;
        }
        if (containsAny(value, "scroll_edge", "boundary")) {
            return HapticEffect.LOW_TICK;
        }
        return null;
    }

    static HapticEffect fromSemanticId(int id) {
        return switch (id) {
            case 1, 6, 9, 13, 19, 20 -> HapticEffect.TICK;
            case 0, 2, 5, 8, 12, 18 -> HapticEffect.CLICK;
            case 4, 7, 10, 14, 17, 21, 23 -> HapticEffect.HEAVY_CLICK;
            case 3, 24 -> HapticEffect.TOGGLE_ON;
            case 11, 15, 16 -> HapticEffect.LOW_TICK;
            case 22 -> HapticEffect.KEYBOARD_PRESS;
            default -> HapticEffect.CLICK;
        };
    }

    private static HapticEffect fromExtendedId(int rawId) {
        int id = (rawId & 0xF0000000) == 0x10000000 ? rawId & 0xFF : rawId;
        return switch (id) {
            case 162 -> HapticEffect.TICK;
            case 163, 174, 201, 202, 203 -> HapticEffect.LOW_TICK;
            case 209 -> HapticEffect.SCREEN_LOCK;
            case 212 -> HapticEffect.NOTIFICATION_CLEANUP;
            case 213 -> HapticEffect.PROCESS_CLEANUP;
            case 205, 214, 216 -> HapticEffect.HEAVY_CLICK;
            case 206, 187 -> HapticEffect.TOGGLE_ON;
            case 207, 208 -> HapticEffect.TOGGLE_OFF;
            case 176, 180, 210, 211, 215 -> HapticEffect.ASSISTANT;
            case 178, 181, 182, 186, 189, 190, 204 ->
                    HapticEffect.TEXTURE_TICK;
            case 179 -> HapticEffect.KEYBOARD_PRESS;
            case 173, 175, 177, 183, 184, 185, 188, 191, 217 ->
                    HapticEffect.CLICK;
            default -> id >= 0 && id <= 24
                    ? fromSemanticId(id) : HapticEffect.CLICK;
        };
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
