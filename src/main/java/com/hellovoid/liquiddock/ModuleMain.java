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
            ClassLoader classLoader = param.getClassLoader();
            try {
                SystemUiTaskExecutorSource.install(classLoader);
            } catch (Throwable error) {
                Api101Bridge.log("[DC] SystemUI task executor source unavailable", error);
            }
            try {
                SystemUiTransitionSource.install(classLoader);
            } catch (Throwable error) {
                // Transition observation is passive/fail-open and must never destabilize SystemUI.
                Api101Bridge.log("[DC] SystemUI transition source unavailable", error);
            }
            try {
                SystemUiHomeOwnershipSource.install(classLoader);
            } catch (Throwable error) {
                // HOME ownership fails closed independently from freeform exclusion.
                Api101Bridge.log("[DC] SystemUI HOME ownership source unavailable", error);
            }
            try {
                SystemUiFreeformLeashProvider.install(classLoader);
            } catch (Throwable error) {
                // Keep the provider available to other non-glass consumers; the retired
                // screen-capture renderer no longer requests its HardwareBuffer snapshots.
                Api101Bridge.log("[DC] SystemUI freeform leash provider unavailable", error);
            }
            return;
        }
        if (!FreeformLeashProtocol.LAUNCHER_PACKAGE.equals(packageName)) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.load();

            // release/1.3.0 has one glass renderer: HyperOS PassBlur -> OES -> Prismal.
            // Do not install the retired Bitmap/screen-capture lifecycle, Recents pre-arm,
            // workstation wallpaper-capture, or capture diagnostic bridges.
            new MainHook().install(classLoader);
            WorkspaceDropRuleHook.install(classLoader,
                    runtimeConfig.enabled && runtimeConfig.grid.enabled);
            Miuix307DropFinishCompatHook.install(classLoader);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
