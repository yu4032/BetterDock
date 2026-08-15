package com.hellovoid.liquiddock;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Stock HyperOS All Apps / Drawer state integration, isolated from the composition root. */
final class AllAppsStateHooks {
    private final Supplier<DockLiquidGlassView> glassProvider;
    private final Consumer<String> logger;
    private boolean drawerStatusHooksInstalled;
    private boolean laptopHooksInstalled;

    AllAppsStateHooks(Supplier<DockLiquidGlassView> glassProvider, Consumer<String> logger) {
        this.glassProvider = glassProvider;
        this.logger = logger;
    }

    void install(ClassLoader cl) {
        installDrawerStatusHooks(cl);
        installAllAppsCaptureHooks(cl);
    }

    private void installAllAppsCaptureHooks(ClassLoader cl) {
        // Laptop All Apps changes focus before its open state is visible elsewhere.  Both
        // show+close hooks must exist before either callback may write persistent state.
        boolean laptopPairReady = false;
        try {
            Class<?> laptop = Class.forName(
                    "com.miui.home.launcher.laptop.AllAppsController", false, cl);
            HookUtil.hookMethod(laptop, "showAllApps", new Class<?>[]{boolean.class}, chain -> {
                DockLiquidGlassView glass = glassProvider.get();
                if (glass != null) {
                    if (laptopHooksInstalled) glass.setAllAppsActive(true);
                    else glass.prearmAllAppsCapture("laptop-partial-show");
                }
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                glass = glassProvider.get();
                if (glass != null && laptopHooksInstalled) glass.setAllAppsActive(true);
                return result;
            });
            HookUtil.hookMethod(laptop, "closeAllApps", new Class<?>[]{boolean.class}, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DockLiquidGlassView glass = glassProvider.get();
                if (glass != null) {
                    if (laptopHooksInstalled) glass.setAllAppsActive(false);
                    else glass.requestCapture("laptop-partial-close");
                }
                return result;
            });
            laptopPairReady = true;
        } catch (Throwable e) {
            logger.accept("[DC] stock laptop All Apps hook unavailable/partial: " + e);
        }
        laptopHooksInstalled = laptopPairReady;
        logger.accept("[DC] stock laptop All Apps authority=" + laptopHooksInstalled);

        // Normal transitions are only bounded hints when DrawerStatusService owns final state.
        try {
            Class<?> transition = Class.forName(
                    "com.miui.home.launcher.allapps.AllAppsTransitionController", false, cl);
            Class<?> launcherState = Class.forName(
                    "com.miui.home.launcher.LauncherState", false, cl);
            HookUtil.hookMethod(transition, "setState", new Class<?>[]{launcherState}, chain -> {
                boolean entering = isStockAllAppsState(chain.getArgs().get(0));
                DockLiquidGlassView glass = glassProvider.get();
                if (entering && glass != null) glass.prearmAllAppsCapture("normal-setState");
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                glass = glassProvider.get();
                if (glass != null && !drawerStatusHooksInstalled) glass.setAllAppsActive(entering);
                return result;
            });
            Class<?> builder = Class.forName(
                    "com.miui.home.launcher.anim.AnimatorSetBuilder", false, cl);
            Class<?> animationConfig = Class.forName(
                    "com.miui.home.launcher.LauncherStateManager$AnimationConfig", false, cl);
            HookUtil.hookMethod(transition, "setStateWithAnimation",
                    new Class<?>[]{launcherState, launcherState, builder, animationConfig}, chain -> {
                        boolean entering = isStockAllAppsState(chain.getArgs().get(1));
                        DockLiquidGlassView glass = glassProvider.get();
                        if (entering && glass != null) {
                            glass.prearmAllAppsCapture("normal-setStateWithAnimation");
                        }
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = glassProvider.get();
                        if (glass != null && !drawerStatusHooksInstalled) glass.setAllAppsActive(entering);
                        return result;
                    });
            logger.accept("[DC] stock normal All Apps transition prearm hooked");
        } catch (Throwable e) {
            logger.accept("[DC] stock normal All Apps transition hook unavailable: " + e);
        }
    }

    private static boolean isStockAllAppsState(Object state) {
        return state != null && "com.miui.home.launcher.uioverrides.AllAppsState"
                .equals(state.getClass().getName());
    }

    private void installDrawerStatusHooks(ClassLoader cl) {
        boolean pairReady = false;
        Class<?> drawer = null;
        try {
            drawer = Class.forName(
                    "com.miui.home.launcher.dock.v3.dependencies.DrawerStatusServiceImpl",
                    false, cl);
            HookUtil.hookMethod(drawer, "dispatchDrawerOpen", new Class<?>[0], chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DockLiquidGlassView glass = glassProvider.get();
                if (glass != null) {
                    if (drawerStatusHooksInstalled) glass.setAllAppsActive(true);
                    else glass.prearmAllAppsCapture("drawer-partial-open");
                }
                return result;
            });
            HookUtil.hookMethod(drawer, "dispatchDrawerClose", new Class<?>[0], chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                DockLiquidGlassView glass = glassProvider.get();
                if (glass != null) {
                    if (drawerStatusHooksInstalled) glass.setAllAppsActive(false);
                    else glass.requestCapture("drawer-partial-close");
                }
                return result;
            });
            pairReady = true;
        } catch (Throwable e) {
            logger.accept("[DC] stock DrawerStatusService authority unavailable/partial: " + e);
        }
        drawerStatusHooksInstalled = pairReady;

        // Progress is cadence-only and is not part of the authority pair.
        if (drawer != null) {
            try {
                HookUtil.hookMethod(drawer, "dispatchDrawerProgress", new Class<?>[]{float.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    DockLiquidGlassView glass = glassProvider.get();
                    if (glass != null) glass.requestCapture("drawer-progress");
                    return result;
                });
            } catch (Throwable e) {
                logger.accept("[DC] stock DrawerStatusService progress hook unavailable: " + e);
            }
        }
        logger.accept("[DC] stock DrawerStatusService authority=" + drawerStatusHooksInstalled);
    }
}
