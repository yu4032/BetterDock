package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

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
            new MainHook().install(param.getClassLoader());
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
