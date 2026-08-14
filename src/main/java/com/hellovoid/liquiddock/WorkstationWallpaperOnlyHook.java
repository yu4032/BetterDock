package com.hellovoid.liquiddock;

import android.view.View;

/**
 * Keeps HyperOS workstation/laptop Dock backgrounds on the launcher's native
 * wallpaper-snapshot path.
 *
 * The stock workstation implementation already captures the wallpaper bitmap,
 * crops it to the Dock and blurs that crop. Transitions to All Apps / Recents
 * can switch the Dock back to the live-blur path, which may feed the Floating
 * Dock itself back into the blur. This hook pins those transitions to snapshot
 * mode and prevents LiquidDock's normal mode-1 Recents capture from being
 * activated in workstation mode.
 */
final class WorkstationWallpaperOnlyHook {
    private static final String HOTSEATS =
            "com.miui.home.launcher.hotseats.HotSeats";
    private static final String ALL_APPS_CONTROLLER =
            "com.miui.home.launcher.laptop.AllAppsController";
    private static final String LAUNCHER =
            "com.miui.home.launcher.Launcher";

    private WorkstationWallpaperOnlyHook() {}

    static void install(ClassLoader classLoader) {
        installLiquidRecentsBlock();
        installNativeSnapshotLock(classLoader);
        installAllAppsSnapshotTriggers(classLoader);
        installRecentsSnapshotTrigger(classLoader);
    }

    /**
     * MainHook still receives Launcher.showOrHideRecent() for compatibility with
     * normal mode. In workstation mode do not let that callback unsuspend the
     * LiquidDock glass and enter mode-1 full-display capture.
     */
    private static void installLiquidRecentsBlock() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class,
                    "onWorkstationRecentsButton", new Class<?>[0], chain -> {
                        if (MainHook.isWorkstationMode()) {
                            MainHook.log("[DC] workstation Recents: Liquid mode-1 blocked; "
                                    + "using wallpaper snapshot");
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation Liquid Recents block unavailable: " + error);
        }
    }

    /** Pin the stock Mingou workstation Dock to its static wallpaper snapshot. */
    private static void installNativeSnapshotLock(ClassLoader classLoader) {
        try {
            Class<?> hotSeats = Class.forName(HOTSEATS, false, classLoader);
            boolean anyInstalled = false;

            try {
                HookUtil.hookMethod(hotSeats, "setMingouStaticDockLiveBlurVisible",
                        new Class<?>[]{boolean.class}, chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (MainHook.isWorkstationMode() && Boolean.TRUE.equals(args[0])) {
                                args[0] = false;
                                MainHook.log("[DC] workstation live Dock blur blocked");
                            }
                            return chain.proceed(args);
                        });
                anyInstalled = true;
            } catch (Throwable t) {
                MainHook.log("[DC] workstation live-blur lock unavailable: " + t.getMessage());
            }

            try {
                HookUtil.hookMethod(hotSeats, "setMingouStaticDockSnapshotMode",
                        new Class<?>[]{boolean.class}, chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (MainHook.isWorkstationMode()) args[0] = true;
                            return chain.proceed(args);
                        });
                anyInstalled = true;
            } catch (Throwable t) {
                MainHook.log("[DC] workstation snapshot-mode lock unavailable: " + t.getMessage());
            }

            if (anyInstalled) {
                MainHook.log("[DC] workstation native wallpaper-snapshot lock installed");
            }
        } catch (Throwable error) {
            MainHook.log("[DC] workstation native snapshot lock class unavailable: " + error.getMessage());
        }
    }

    /** Refresh the wallpaper-only snapshot at both All Apps transition boundaries. */
    private static void installAllAppsSnapshotTriggers(ClassLoader classLoader) {
        try {
            Class<?> controller = Class.forName(ALL_APPS_CONTROLLER, false, classLoader);
            hookAllAppsTransition(controller, "showAllApps");
            hookAllAppsTransition(controller, "showWindow");
            MainHook.log("[DC] workstation All Apps wallpaper triggers installed");
        } catch (Throwable error) {
            MainHook.log("[DC] workstation All Apps snapshot triggers unavailable: " + error);
        }
    }

    private static void hookAllAppsTransition(Class<?> controller, String methodName) {
        try {
            HookUtil.hookMethod(controller, methodName, new Class<?>[]{boolean.class}, chain -> {
                Object launcher = HookUtil.invoke(chain.getThisObject(), "getLauncher");
                if (MainHook.isWorkstationMode())
                    forceWallpaperSnapshot(launcher, methodName + "-before");
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) {
                    forceWallpaperSnapshot(launcher, methodName + "-after");
                    postSnapshotRefresh(launcher, methodName + "-settled");
                }
                return result;
            });
        } catch (Throwable error) {
            MainHook.log("[DC] workstation " + methodName + " snapshot hook unavailable: " + error);
        }
    }

    /** Recents also stays on the same wallpaper-only native snapshot. */
    private static void installRecentsSnapshotTrigger(ClassLoader classLoader) {
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            HookUtil.hookMethod(launcher, "showOrHideRecent", new Class<?>[0], chain -> {
                Object thisLauncher = chain.getThisObject();
                if (MainHook.isWorkstationMode())
                    forceWallpaperSnapshot(thisLauncher, "recents-before");
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) {
                    forceWallpaperSnapshot(thisLauncher, "recents-after");
                    postSnapshotRefresh(thisLauncher, "recents-settled");
                }
                return result;
            });
            MainHook.log("[DC] workstation Recents wallpaper trigger installed");
        } catch (Throwable error) {
            MainHook.log("[DC] workstation Recents snapshot trigger unavailable: " + error);
        }
    }

    private static void postSnapshotRefresh(Object launcher, String reason) {
        if (!(launcher instanceof View)) {
            Object hotSeats = HookUtil.invoke(launcher, "getHotSeats");
            if (hotSeats instanceof View) {
                ((View) hotSeats).postDelayed(
                        () -> forceWallpaperSnapshot(launcher, reason), 180L);
            }
            return;
        }
        ((View) launcher).postDelayed(() -> forceWallpaperSnapshot(launcher, reason), 180L);
    }

    /**
     * Calls only the stock workstation snapshot API. Internally HyperOS obtains the
     * wallpaper bitmap directly, crops it to the Dock and blurs that crop, so no Dock
     * SurfaceFlinger layer can be sampled into the result.
     */
    private static void forceWallpaperSnapshot(Object launcher, String reason) {
        if (!MainHook.isWorkstationMode() || launcher == null) return;
        try {
            Object hotSeats = HookUtil.invoke(launcher, "getHotSeats");
            if (hotSeats == null) return;
            HookUtil.invoke(hotSeats, "requestMingouStaticDockBlurSnapshotIfNeeded", false);
            HookUtil.invoke(hotSeats, "showMingouStaticDockBlurOverlayIfPossible");
            HookUtil.invoke(hotSeats, "setMingouStaticDockSnapshotMode", true);
            HookUtil.invoke(hotSeats, "setMingouStaticDockLiveBlurVisible", false);
            MainHook.log("[DC] workstation wallpaper snapshot forced reason=" + reason);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation wallpaper snapshot force FAILED: " + error);
        }
    }
}
