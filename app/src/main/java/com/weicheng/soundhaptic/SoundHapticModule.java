package com.weicheng.soundhaptic;

import android.annotation.SuppressLint;
import android.util.Log;

import com.weicheng.soundhaptic.hook.CapabilityHooks;
import com.weicheng.soundhaptic.hook.HapticRuntime;
import com.weicheng.soundhaptic.hook.MiuiHapticHooks;
import com.weicheng.soundhaptic.hook.ProcessScope;
import com.weicheng.soundhaptic.hook.SystemVibratorHooks;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** API 102 composition root. All implementation lives in focused hook components. */
public final class SoundHapticModule extends XposedModule {
    private final HapticRuntime runtime = new HapticRuntime(this);
    private final CapabilityHooks capabilities = new CapabilityHooks(this);
    private final MiuiHapticHooks miuiHooks = new MiuiHapticHooks(this, runtime);
    private final SystemVibratorHooks systemHooks = new SystemVibratorHooks(this, runtime);

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, PulsePlayer.TAG, "loaded: process=" + param.getProcessName()
                + " systemServer=" + param.isSystemServer() + " api=" + getApiVersion());
        if (!param.isSystemServer() && !ProcessScope.supports(param.getProcessName())) {
            detach();
            return;
        }
        if (!param.isSystemServer()) {
            capabilities.installPlatformProperties();
        }
    }

    @Override
    public void onSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        systemHooks.install(param.getClassLoader());
    }

    @Override
    @SuppressLint("NewApi")
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();
        if (!ProcessScope.supports(packageName)) {
            return;
        }
        ClassLoader classLoader = param.getDefaultClassLoader();
        capabilities.installFrameworkClient(classLoader);
        // Install MIUI's generic capability/dispatch boundary before any package-specific
        // facade can initialize and cache a negative capability result.
        miuiHooks.install(classLoader);
        if (ProcessScope.MIUI_HOME.equals(packageName)) {
            capabilities.installMiuiHome(classLoader);
        }
    }
}
