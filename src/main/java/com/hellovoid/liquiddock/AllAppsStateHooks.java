package com.hellovoid.liquiddock;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Stock HyperOS All Apps / Drawer state integration, isolated from the composition root. */
final class AllAppsStateHooks {
    private final Supplier<DockLiquidGlassView> glassProvider;
    private final Consumer<String> logger;
    private boolean drawerStatusHooksInstalled;

    AllAppsStateHooks(Supplier<DockLiquidGlassView> glassProvider, Consumer<String> logger) {
        this.glassProvider = glassProvider;
        this.logger = logger;
    }

    void install(ClassLoader cl) {
        installDrawerStatusHooks(cl);
        installAllAppsCaptureHooks(cl);
    }

    private void installAllAppsCaptureHooks(ClassLoader cl) {
        // Laptop All Apps owns focus via LauncherOverlayWindow. Prearm before the original
        // show call so its focus transfer cannot be mistaken for an external APP.
        try {
            Class<?> laptop = Class.forName(
                    "com.miui.home.launcher.laptop.AllAppsController", false, cl);
            HookUtil.hookMethod(laptop, "showAllApps", new Class<?>[]{boolean.class},
                    chain -> {
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setAllAppsActive(true);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = glassProvider.get();
                        if (glass != null) glass.setAllAppsActive(true);
                        return result;
                    });
            HookUtil.hookMethod(laptop, "closeAllApps", new Class<?>[]{boolean.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setAllAppsActive(false);
                        return result;
                    });
            logger.accept("[DC] stock laptop All Apps state hooked");
        } catch (Throwable e) {
            logger.accept("[DC] stock laptop All Apps hook unavailable: " + e);
        }

        // Normal All Apps transition methods are early prearm/fallback boundaries. When
        // DrawerStatusService is available it owns the final close state.
        try {
            Class<?> transition = Class.forName(
                    "com.miui.home.launcher.allapps.AllAppsTransitionController", false, cl);
            Class<?> launcherState = Class.forName(
                    "com.miui.home.launcher.LauncherState", false, cl);
            HookUtil.hookMethod(transition, "setState", new Class<?>[]{launcherState},
                    chain -> {
                        boolean entering = isStockAllAppsState(chain.getArgs().get(0));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (entering && glass != null) glass.setAllAppsActive(true);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = glassProvider.get();
                        if (glass != null && (entering || !drawerStatusHooksInstalled))
                            glass.setAllAppsActive(entering);
                        return result;
                    });
            Class<?> builder = Class.forName(
                    "com.miui.home.launcher.anim.AnimatorSetBuilder", false, cl);
            Class<?> animationConfig = Class.forName(
                    "com.miui.home.launcher.LauncherStateManager$AnimationConfig", false, cl);
            HookUtil.hookMethod(transition, "setStateWithAnimation",
                    new Class<?>[]{launcherState, launcherState, builder, animationConfig},
                    chain -> {
                        boolean entering = isStockAllAppsState(chain.getArgs().get(1));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (entering && glass != null) glass.setAllAppsActive(true);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = glassProvider.get();
                        if (glass != null && !entering && !drawerStatusHooksInstalled)
                            glass.setAllAppsActive(false);
                        return result;
                    });
            logger.accept("[DC] stock normal All Apps transition fallback hooked");
        } catch (Throwable e) {
            logger.accept("[DC] stock normal All Apps transition hook unavailable: " + e);
        }
    }

    private static boolean isStockAllAppsState(Object state) {
        return state != null && "com.miui.home.launcher.uioverrides.AllAppsState"
                .equals(state.getClass().getName());
    }

    private void installDrawerStatusHooks(ClassLoader cl) {
        boolean installed = false;
        try {
            Class<?> drawer = Class.forName(
                    "com.miui.home.launcher.dock.v3.dependencies.DrawerStatusServiceImpl",
                    false, cl);
            HookUtil.hookMethod(drawer, "dispatchDrawerOpen", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setAllAppsActive(true);
                        return result;
                    });
            HookUtil.hookMethod(drawer, "dispatchDrawerClose", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.setAllAppsActive(false);
                        return result;
                    });
            HookUtil.hookMethod(drawer, "dispatchDrawerProgress", new Class<?>[]{float.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (glass != null) glass.requestCapture("drawer-progress");
                        return result;
                    });
            installed = true;
            logger.accept("[DC] stock DrawerStatusService state hooked");
        } catch (Throwable e) {
            logger.accept("[DC] stock DrawerStatusService hook unavailable: " + e);
        }
        drawerStatusHooksInstalled = installed;
    }
}
