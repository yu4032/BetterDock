package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.GridProfileConfig;
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
            ConfigMigration.migrateAtProcessStart();
            ClassLoader classLoader = param.getClassLoader();
            ConfigReader configReader = ConfigReader.load();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);
            HomeGridProfile selectedProfile = HomeGridProfile.fromPersisted(
                    GridProfileConfig.normalizeProfile(configReader.s(
                            GridProfileConfig.PROFILE_KEY, GridProfileConfig.DEFAULT_PROFILE)));
            boolean customGridEnabled = runtimeConfig.enabled && runtimeConfig.grid.enabled;

            new MainHook().install(classLoader);
            MiuixFolderGlassHook.install(classLoader, runtimeConfig);
            DockBottomGeometryHook.install(classLoader);
            HomeGridProfileOverlayHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridOrientationMemoryHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridMutationCaptureHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridDeviceConfigCountHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridHorizontalCenteringHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridVerticalBoundsHook.install(classLoader,
                    customGridEnabled, selectedProfile, runtimeConfig.grid);
            WorkspaceDropRuleHook.install(classLoader, customGridEnabled);
            HomeGridDragBoundsHook.install(classLoader,
                    customGridEnabled, selectedProfile);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
