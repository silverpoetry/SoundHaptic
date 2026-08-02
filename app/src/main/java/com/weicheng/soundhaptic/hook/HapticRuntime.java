package com.weicheng.soundhaptic.hook;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.weicheng.soundhaptic.HapticSettings;
import com.weicheng.soundhaptic.PulsePlayer;
import com.weicheng.soundhaptic.audio.HapticEffect;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Owns the one audio engine used by a hooked process and filters duplicate terminal emissions. */
public final class HapticRuntime {
    private static final String TAG = PulsePlayer.TAG;
    private static final long DUPLICATE_WINDOW_MS = 32L;

    private final XposedInterface xposed;
    private final Object playerLock = new Object();
    private final Object feedbackLock = new Object();

    private volatile PulsePlayer player;
    private volatile SharedPreferences preferences;
    private long lastFeedbackAtMs = Long.MIN_VALUE;
    private HapticEffect lastEffect;

    public HapticRuntime(XposedInterface xposed) {
        this.xposed = xposed;
    }

    public PulsePlayer player(Context context) {
        PulsePlayer current = player;
        if (current != null) {
            return current;
        }
        synchronized (playerLock) {
            current = player;
            if (current == null) {
                current = new PulsePlayer(context, preferences());
                player = current;
                xposed.log(Log.INFO, TAG, "audio engine initialized");
            }
            return current;
        }
    }

    public void play(Context context, HapticEffect effect, String source) {
        Context playbackContext = context != null ? context : currentApplicationContext();
        if (playbackContext == null) {
            Log.w(TAG, "haptic skipped without process context source=" + source);
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();
        synchronized (feedbackLock) {
            long elapsed = now - lastFeedbackAtMs;
            boolean sameEffect = effect == lastEffect;
            boolean trailingGeneric = !isMultiPulse(effect);
            if (elapsed >= 0 && elapsed < DUPLICATE_WINDOW_MS
                    && (sameEffect || trailingGeneric)) {
                Log.d(TAG, "UI haptic duplicate suppressed source=" + source
                        + " effect=" + effect.name() + " elapsed=" + elapsed + "ms");
                return;
            }
            lastFeedbackAtMs = now;
            lastEffect = effect;
        }

        PulsePlayer current = player(playbackContext);
        current.playModuleFeedback(effect);
        Log.d(TAG, "local haptic source=" + source + " effect=" + effect.name());
    }

    public void cancelPendingFeedback() {
        PulsePlayer current = player;
        if (current != null) {
            current.cancelPendingModuleFeedback();
        }
    }

    private SharedPreferences preferences() {
        SharedPreferences current = preferences;
        if (current != null) {
            return current;
        }
        try {
            current = xposed.getRemotePreferences(HapticSettings.GROUP);
            preferences = current;
            return current;
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG,
                    "Unable to open remote haptic settings; using defaults", error);
            return null;
        }
    }

    private static boolean isMultiPulse(HapticEffect effect) {
        return effect == HapticEffect.SCREEN_LOCK
                || effect == HapticEffect.NOTIFICATION_CLEANUP
                || effect == HapticEffect.PROCESS_CLEANUP;
    }

    private static Context currentApplicationContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object application = method.invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
