package com.hellovoid.liquiddock;

import androidx.annotation.NonNull;

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
            ClassLoader classLoader = param.getClassLoader();
            ConfigReader configReader = ConfigReader.load();
            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);

            // This branch is a dedicated 10x6 experiment. Existing users still control whether
            // custom grid hooks run through the established home_grid_8x4 master switch, but an
            // absent profile key selects 10x6 here instead of changing the production UI/schema.
            String selectedProfileValue = configReader.has(GridProfileConfig.PROFILE_KEY)
                    ? configReader.s(GridProfileConfig.PROFILE_KEY,
                            GridProfileConfig.DEFAULT_PROFILE)
                    : "10x6";
            HomeGridProfile selectedProfile = HomeGridProfile.fromPersisted(
                    GridProfileConfig.normalizeProfile(selectedProfileValue));
            boolean customGridEnabled = runtimeConfig.enabled && runtimeConfig.grid.enabled;

            new MainHook().install(classLoader);
            HomeGridProfileOverlayHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridHorizontalCenteringHook.install(classLoader,
                    customGridEnabled, selectedProfile);
            HomeGridVerticalBoundsHook.install(classLoader,
                    customGridEnabled, selectedProfile, runtimeConfig.grid);
            HomeGridRotationBridge.install(classLoader,
                    customGridEnabled, selectedProfile);
            WorkspaceDropRuleHook.install(classLoader, customGridEnabled);
            if (customGridEnabled) {
                Api101Bridge.log("[DC] home grid profile=" + selectedProfile.persistedValue());
            }
        } catch (Throwable error) {
            Api101Bridge.log("[DC] API101 package init failed", error);
        }
    }
}
