package com.weicheng.soundhaptic.audio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.PowerManager;

/** Reports interactive display transitions on the audio thread. */
final class ScreenStateMonitor implements AutoCloseable {
    private static final long SCREEN_OFF_GRACE_MS = 800L;

    private final Context context;
    private final Handler handler;
    private final Runnable onScreenOn;
    private final Runnable onScreenOff;
    private BroadcastReceiver receiver;
    private boolean registered;

    ScreenStateMonitor(Context context, Handler handler,
            Runnable onScreenOn, Runnable onScreenOff) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.onScreenOn = onScreenOn;
        this.onScreenOff = onScreenOff;
    }

    void register() {
        if (registered) {
            return;
        }
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    handler.post(onScreenOn);
                } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    handler.postDelayed(() -> {
                        if (!isInteractive()) {
                            onScreenOff.run();
                        }
                    }, SCREEN_OFF_GRACE_MS);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(receiver, filter, null, handler,
                Context.RECEIVER_NOT_EXPORTED);
        registered = true;
    }

    boolean isInteractive() {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        return powerManager == null || powerManager.isInteractive();
    }

    @Override
    public void close() {
        if (!registered || receiver == null) {
            return;
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // The system may already have detached it during process shutdown.
        }
        registered = false;
        receiver = null;
    }
}
