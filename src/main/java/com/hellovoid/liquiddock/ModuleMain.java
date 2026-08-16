package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.LegacyConfigMigration;

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
        String packageName = param.getPackageName();
        if (FreeformLeashProtocol.SYSTEM_UI_PACKAGE.equals(packageName)) {
            try {
                SystemUiFreeformLeashProvider.install(param.getClassLoader());
            } catch (Throwable error) {
                // SystemUI stability is more important than freeform exclusion. Never let
                // a LiquidDock capability failure escape into the host process.
                Api101Bridge.log("[DC] SystemUI freeform leash provider unavailable", error);
            }
            try {
                SystemUiHomeOwnershipShadow.install(param.getClassLoader());
            } catch (Throwable error) {
                // Diagnostic-only capability. It must never affect the production freeform bridge.
                Api101Bridge.log("[DC-SHADOW] HOME ownership shadow install unavailable", error);
            }
            return;
        }
        if (!FreeformLeashProtocol.LAUNCHER_PACKAGE.equals(packageName)) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            FreeformCaptureLeashHook.install();
            new MainHook().install(classLoader);
            WorkstationWallpaperOnlyHook.install(classLoader);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
