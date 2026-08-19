package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.LegacyConfigMigration;

import io.github.libxposed.api.XposedModule;

/** libxposed API 101 entry point. Launcher is the sole injected process; SystemUI stays untouched. */
public final class ModuleMain extends XposedModule {
    private static final String LAUNCHER_PACKAGE = "com.miui.home";

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        Api101Bridge.init(this);
        Api101Bridge.log("[DC] API101 module loaded process=" + param.getProcessName()
                + " framework=" + getFrameworkName() + " api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!LAUNCHER_PACKAGE.equals(param.getPackageName())) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.load();

            new MainHook().install(classLoader);
            WorkspaceDropRuleHook.install(classLoader,
                    runtimeConfig.enabled && runtimeConfig.grid.enabled,
                    runtimeConfig.grid.profile);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
