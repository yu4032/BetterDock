package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.libxposed.api.XposedModule;

/** libxposed API 101 entry point. */
public final class ModuleMain extends XposedModule {
    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        Api101Bridge.log("[DC] API101 module loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!"com.miui.home".equals(param.getPackageName())) return;
        try {
            // Install the module-internal HOME capture guard before MainHook registers the
            // Launcher gesture hooks that can trigger the first HOME capture.
            HomeCaptureBarrier.install();

            ClassLoader classLoader = param.getClassLoader();
            new MainHook().handleLoadPackage(new XC_LoadPackage.LoadPackageParam(
                    param.getPackageName(), classLoader));
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
