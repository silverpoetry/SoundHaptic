package com.weicheng.soundhaptic;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Owns the official libxposed service connection used for scope requests and diagnostics. */
public final class SoundHapticApp extends Application
        implements XposedServiceHelper.OnServiceListener {
    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    private static final CopyOnWriteArraySet<ServiceStateListener> LISTENERS =
            new CopyOnWriteArraySet<>();
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        XposedServiceHelper.registerListener(this);
    }

    public static void addServiceStateListener(ServiceStateListener listener) {
        LISTENERS.add(listener);
        listener.onServiceStateChanged(service);
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        LISTENERS.remove(listener);
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        notifyListeners(boundService);
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
        }
        notifyListeners(null);
    }

    private static void notifyListeners(XposedService value) {
        for (ServiceStateListener listener : LISTENERS) {
            listener.onServiceStateChanged(value);
        }
    }
}
