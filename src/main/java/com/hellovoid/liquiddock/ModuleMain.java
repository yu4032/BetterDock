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
                SystemUiHomeOwnershipSource.install(classLoader);
            } catch (Throwable error) {
                // HOME ownership fails closed independently from freeform exclusion.
                Api101Bridge.log("[DC] SystemUI HOME ownership source unavailable", error);
            }
            try {
                SystemUiFreeformLeashProvider.install(classLoader);
            } catch (Throwable error) {
                // SystemUI stability is more important than freeform exclusion. Never let
                // a LiquidDock capability failure escape into the host process.
                Api101Bridge.log("[DC] SystemUI freeform leash provider unavailable", error);
            }
            return;
        }
        if (!FreeformLeashProtocol.LAUNCHER_PACKAGE.equals(packageName)) return;
        try {
            LegacyConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            FreeformCaptureLeashHook.install();
            new MainHook().install(classLoader);
            // MainHook/307 may have installed the historical DeviceConfig margin delta.
            // Neutralize that compatibility path, then own the visible HotSeats Y directly.
            DockBottomGeometryHook.install(classLoader);
            // 10x6 intentionally overlays only profile-sized hook points after the stable
            // 8x4 core has installed its generic CellLayout/widget/margin behavior.
            HomeGridProfileOverlayHook.install(classLoader);
            // Final normal-Workspace geometry runs after profile counts are resolved. It
            // separates X/Y sizing without taking ownership of workstation or Laptop All Apps.
            HomeGridGeometryIndependenceHook.install(classLoader);
            // MainHook's legacy whole-Dock shadow lives in the full Dock-customization path.
            // Install the complementary owner after MainHook so Shadow stays functional when
            // Dock size/blur customization is disabled, without duplicating the normal path.
            DockShadowIndependenceHook.install(classLoader);
            Miuix307CaptureOwnershipHook.install(classLoader);
            WorkstationWallpaperOnlyHook.install(classLoader);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
