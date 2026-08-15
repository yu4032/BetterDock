package com.hellovoid.liquiddock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Core Launcher hooks for LiquidDock — glass, stroke, dock geometry, workstation. */
public class MainHook {

    private static View shadowView, oldBg, nativeShadowTarget;
    private static int lastShadowW;
    private static DockLiquidGlassView liquidGlassView;
    private static DockLiquidGlassHostView liquidGlassHostView;
    private static LauncherSceneController launcherSceneController;
    private static int bgW, bgH, shadowPad;
    private static float bgR = 30f;
    private static float strokeR = 30f;
    private static volatile boolean workstationMode;
    private static boolean dockDragHooksInstalled;
    private static volatile boolean dockDragHooksReady;
    private static final java.util.Map<Long, HomeItemPosition> normalLayoutBackup =
            new java.util.HashMap<>();
    private static final java.util.WeakHashMap<View, android.animation.ValueAnimator>
            dockResizeAnimators = new java.util.WeakHashMap<>();

    // ── entry point ──────────────────────────────────────────────────

    public void install(ClassLoader classLoader) {
        installWorkstationModeGuard(classLoader);
        LiquidDockConfig config = LiquidDockConfig.load();
        WidgetGridSizing.setWidgetAdaptationEnabled(
                WidgetGridSizing.shouldAdaptWidgets(config.grid.enabled, config.grid.widgetAdaptation));
        debugLogging = config.debugLog;
        log("[DC] LiquidDock " + (debugLogging ? "debug logging ON" : "loaded"));
        if (!config.enabled) {
            log("[DC] LiquidDock master switch disabled");
            return;
        }
        DockStrokeRenderer.installNativeHook(classLoader, config.dock);
        RecentsHapticHook.install(classLoader, () -> {
            DockLiquidGlassView glass = liquidGlassView;
            // Laptop/workstation Recents has a dedicated button; generic gesture/haptic
            // pre-arm must never switch its Dock to live capture.
            if (glass != null && !workstationMode) glass.onRecentsHapticTrigger();
        });
        installWorkstationDockHooks(classLoader, config.workstation);
        WorkstationDockGeometryHook.install(classLoader, config.workstation);
        if (!config.dock.resizeAnimation)
            installDockResizeAnimationBypass(classLoader, config.dock.smoothResizeAnimation);
        if (workstationMode)
            log("[DC] workstation active; using isolated workstation parameters");

        LiquidDockConfig.Grid grid = config.grid;
        boolean grid8x4 = grid.enabled, dp = grid.dp, offsets = grid.offsets;
        float gridScale = dp ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int landXBase = dp ? 57 : 160, landYBase = dp ? 28 : 80;
        int portXBase = dp ? 28 : 80, portYBase = dp ? 57 : 160;
        float landLeft = offsets ? grid.landscapeHorizontal : landXBase + grid.landscapeHorizontal;
        float landRight = offsets ? grid.landscapeHorizontal : landXBase + grid.landscapeHorizontal;
        float landTop = offsets ? grid.landscapeTop : landYBase + grid.landscapeTop;
        float landBottom = offsets ? grid.landscapeBottom : landYBase + grid.landscapeBottom;
        float portLeft = offsets ? grid.portraitHorizontal : portXBase + grid.portraitHorizontal;
        float portRight = offsets ? grid.portraitHorizontal : portXBase + grid.portraitHorizontal;
        float portTop = offsets ? grid.portraitTop : portYBase + grid.portraitTop;
        float portBottom = offsets ? grid.portraitBottom : portYBase + grid.portraitBottom;
        float landGap = grid.landscapeRowGap, portGap = grid.portraitRowGap;
        if (!offsets) {
            landLeft -= landXBase; landRight -= landXBase;
            landTop -= landYBase; landBottom -= landYBase;
            portLeft -= portXBase; portRight -= portXBase;
            portTop -= portYBase; portBottom -= portYBase;
            landGap -= dp ? 1 : 3; portGap -= dp ? 1 : 3;
        }
        DockDividerHook.install(classLoader);
        HomeGridHook.install(classLoader, grid8x4,
            Math.round(landLeft * gridScale), Math.round(landRight * gridScale),
            Math.round(landTop * gridScale), Math.round(landBottom * gridScale),
            Math.round(portLeft * gridScale), Math.round(portRight * gridScale),
            Math.round(portTop * gridScale), Math.round(portBottom * gridScale),
            Math.round(landGap * gridScale), Math.round(portGap * gridScale),
            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));
        HomeGridHook.setWorkstationHorizontalOffset(Math.round(
                config.workstation.gridHorizontalOffset * gridScale));
        // All Apps controls are absolute edge spacing in dp. They must not inherit the
        // ordinary grid_margins_dp unit switch, otherwise the same spacing setting changes
        // meaning when the normal desktop grid unit mode changes.
        float workstationAllAppsScale = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeBottomSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitBottomSpacing * workstationAllAppsScale));

        boolean dockCustomization = config.dock.enabled;
        boolean liquidGlass = config.glass.enabled;
        if (!dockCustomization && !liquidGlass) {
            log("[DC] Dock customization and liquid glass both disabled");
            return;
        }

        // ── liquid-glass-only path (no dock geometry customization) ──
        if (!dockCustomization) {
            log("[DC] Dock customization disabled (liquid glass only)");
            installLiquidGlassCaptureHooks(classLoader);
            final LiquidDockConfig.Dock strokeCfg = config.dock;
            try {
                HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (workstationMode) return result;
                        try {
                            Object hs = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hs == null) return result;
                            View vBg = (View) HookUtil.getField(hs, "mBlurBackground2");
                            if (vBg == null) return result;
                            ViewGroup parent = (ViewGroup) vBg.getParent();
                            if (parent == null) return result;
                            int gv = ((FrameLayout.LayoutParams) vBg.getLayoutParams()).gravity;
                            View workspace = null;
                            try {
                                Object w = HookUtil.getField(chain.getThisObject(), "mWorkspace");
                                if (w instanceof View) workspace = (View) w;
                            } catch (Throwable ignored) {}
                            if (liquidGlassHostView != null && liquidGlassHostView.getParent() != null)
                                return result;
                            if (liquidGlassView != null)
                                log("[DC] re-creating glass view (previous detached)");
                            int bgIndex = parent.indexOfChild(vBg);
                            liquidGlassView = installLiquidGlassLayer(parent, Math.max(0, bgIndex), gv,
                                    vBg, workspace, config, false, 0.58f);
                            syncLauncherSceneState(chain.getThisObject(), liquidGlassView);
                            installDockTouchListener(liquidGlassView, vBg.getRootView());
                            liquidGlassView.post(() -> installDockTouchListener(liquidGlassView, vBg.getRootView()));
                            try {
                                View decor = ((android.app.Activity) chain.getThisObject()).getWindow().getDecorView();
                                installDockAreaTouchDetector(liquidGlassView, decor);
                                liquidGlassView.post(() -> installDockAreaTouchDetector(liquidGlassView,
                                        ((android.app.Activity) chain.getThisObject()).getWindow().getDecorView()));
                            } catch (Throwable ignored) {}
                            syncAll(vBg);
                            liquidGlassView.post(() -> syncAll(vBg));
                        } catch (Throwable e) { log("[DC] liquid-only init err: " + e); }
                        return result;
                    });
                // Sync-only hooks for glass tracking
                Class<?> hsc2 = Class.forName(
                        "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                        false, classLoader);
                HookUtil.hookMethod(hsc2, "setBackgroundWidth", new Class<?>[]{int.class},
                        chain -> { Object r = chain.proceed(chain.getArgs().toArray(new Object[0])); syncAll((View) chain.getThisObject()); return r; });
                HookUtil.hookMethod(hsc2, "setBackgroundHeight", new Class<?>[]{int.class},
                        chain -> { Object r = chain.proceed(chain.getArgs().toArray(new Object[0])); syncAll((View) chain.getThisObject()); return r; });
                HookUtil.hookMethod(hsc2, "setBackgroundRadius", new Class<?>[]{float.class},
                        chain -> { Object r = chain.proceed(chain.getArgs().toArray(new Object[0])); syncAll((View) chain.getThisObject()); return r; });
            } catch (Throwable e) { log("[DC] liquid-only hooks err: " + e); }
            return;
        }

        // ── full dock-customization + liquid-glass path ──
        LiquidDockConfig.Dock dock = config.dock;
        log("[DC] init: bl=" + dock.blurRadius + " sq=" + dock.squircle);
        boolean sq = dock.squircle;
        float dockScale = dock.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int wo = Math.round(dock.widthOffset * dockScale);
        int ho = Math.round(dock.heightOffset * dockScale);
        int br = dock.blurRadius;
        float cornerScale = dock.cornersDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int co = Math.round(dock.cornerOffset * cornerScale);
        int blurCo = Math.round(dock.blurCornerOffset * cornerScale);
        int spacing = Math.round(dock.spacing * dockScale);
        int bottomOffset = Math.round(dock.bottomOffset * dockScale);
        ClassLoader cl = classLoader;
        installLiquidGlassCaptureHooks(cl);

        try {
            String hsc = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

            // bottom offset
            if (bottomOffset != 0) {
                try {
                    Class<?> deviceConfig = Class.forName("com.miui.home.launcher.DeviceConfig", false, cl);
                    HookUtil.hookMethod(deviceConfig, "getHotSeatsMarginBottom", new Class<?>[0],
                            chain -> {
                                Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                                if (workstationMode) return r;
                                return (Integer) r + bottomOffset;
                            });
                } catch (Throwable e) { log("[DC] bottom offset hook unavailable: " + e); }
            }

            // spacing
            if (spacing != 0) {
                try {
                    Class<?> recyclerView = Class.forName("androidx.recyclerview.widget.RecyclerView", false, cl);
                    Class<?> recyclerState = Class.forName("androidx.recyclerview.widget.RecyclerView$State", false, cl);
                    HookUtil.hookMethod(cl,
                        "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                        "getItemOffsets",
                        chain -> {
                            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            if (workstationMode) return r;
                            Rect out = (Rect) chain.getArgs().get(0);
                            out.left += spacing;
                            out.right += spacing;
                            return r;
                        }, Rect.class, View.class, recyclerView, recyclerState);

                    Class<?> layoutManager = Class.forName(
                            "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager", false, cl);
                    HookUtil.hookMethod(layoutManager, "updateBackgroundView",
                            new Class<?>[]{FrameLayout.class, int.class, int.class, float.class},
                            chain -> {
                                if (workstationMode) return chain.proceed(chain.getArgs().toArray(new Object[0]));
                                int itemCount = (Integer) HookUtil.invoke(chain.getThisObject(), "getItemCount");
                                if (itemCount > 0) {
                                    Object[] args = chain.getArgs().toArray(new Object[0]);
                                    args[1] = (Integer) args[1] + spacing * 2 * itemCount;
                                    return chain.proceed(args);
                                }
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            });
                } catch (Throwable e) { log("[DC] spacing hook unavailable: " + e); }
            }

            // setBackgroundWidth: before (width offset) + after (syncAll)
            HookUtil.hookMethod(cl, hsc, "setBackgroundWidth",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!workstationMode && wo != 0) args[0] = (int) args[0] + wo;
                        Object r = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return r;
                    }, int.class);

            // setBackgroundHeight: before (height offset) + after (syncAll)
            HookUtil.hookMethod(cl, hsc, "setBackgroundHeight",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!workstationMode && ho != 0) args[0] = (int) args[0] + ho;
                        Object r = chain.proceed(args);
                        syncAll((View) chain.getThisObject());
                        return r;
                    }, int.class);

            // setBackgroundRadius: complex before + after with squircle
            HookUtil.hookMethod(cl, hsc, "setBackgroundRadius",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!workstationMode) {
                            View v = (View) chain.getThisObject();
                            float systemRadius = (Float) args[0];
                            if (!animating(v)) strokeR = Math.max(0f, systemRadius + co);
                            args[0] = Math.max(0f, systemRadius + blurCo);
                        }
                        Object r = chain.proceed(args);
                        View v = (View) chain.getThisObject();
                        syncAll(v);
                        if (sq) {
                            if (animating(v)) return r;
                            float radius = (Float) HookUtil.getField(v, "mCornerRadius");
                            if (radius > 0) v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                @Override public void getOutline(View vv, android.graphics.Outline o) {
                                    o.setPath(squirclePath(new RectF(0, 0, v.getWidth(), v.getHeight()), radius));
                                }});
                        }
                        return r;
                    }, float.class);

            // BlurUtilities
            try {
                Class<?> bu = Class.forName("com.miui.home.launcher.common.BlurUtilities", false, cl);
                HookUtil.hookMethod(bu, "setBackgroundBlur",
                        new Class<?>[]{View.class, int.class, float[].class, int[][].class},
                        chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (!workstationMode && br != 100) args[1] = br;
                            return chain.proceed(args);
                        });
            } catch (Throwable ignored) {}

            // native shadow
            try {
                HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeats", "getMingouStaticDockBlurShadowTarget",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (r instanceof View) nativeShadowTarget = (View) r;
                        return r;
                    });
                Class<?> ms = Class.forName("com.miui.home.launcher.common.MiShadowUtils", false, cl);
                HookUtil.hookMethod(ms, "applyViewShadow",
                        new Class<?>[]{View.class, int.class, float.class, float.class, float.class, float.class},
                        chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            if (workstationMode) return chain.proceed(args);
                            if (args[0] != nativeShadowTarget) return chain.proceed(args);
                            args[1] = Color.TRANSPARENT;
                            args[2] = 0f;
                            args[3] = 0f;
                            args[4] = 0f;
                            return chain.proceed(args);
                        });
            } catch (Throwable e) { log("[DC] native Dock shadow hook unavailable: " + e); }

            // setupViews: glass + independent Dock shadow init
            HookUtil.hookMethod(cl, "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            LiquidDockConfig current = LiquidDockConfig.load();
                            LiquidDockConfig.Dock c2 = current.dock;
                            Object hs = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hs == null) return r;
                            if (!workstationMode) try {
                                Object target = HookUtil.invoke(hs, "getMingouStaticDockBlurShadowTarget");
                                if (target instanceof View) {
                                    nativeShadowTarget = (View) target;
                                    HookUtil.invokeStatic("com.miui.home.launcher.common.MiShadowUtils",
                                            "applyViewShadow", nativeShadowTarget, Color.TRANSPARENT, 0f, 0f, 0f, 1f);
                                }
                            } catch (Throwable e) { log("[DC] native Dock shadow clear failed: " + e); }
                            oldBg = (View) HookUtil.getField(hs, "mBlurBackground2");
                            if (oldBg == null) return r;
                            ViewGroup parent = (ViewGroup) oldBg.getParent();
                            if (parent == null) return r;
                            int gv = ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity;
                            float ds2 = c2.dimensionsDp ? oldBg.getResources().getDisplayMetrics().density : 1f;
                            int sqOff = Math.round(c2.squircleStrokeOffset * ds2);
                            float sqCp = c2.squircleCp;
                            boolean dockShadow = c2.shadowEnabled;
                            boolean liquid = current.glass.enabled;
                            int dsR = Math.max(1, Math.round(c2.shadowRadius * ds2));
                            int dsS = Math.max(1, Math.round(c2.shadowSize * ds2));
                            int dsA = c2.shadowAlpha;
                            int dsY = Math.round(c2.shadowY * ds2);
                            if (liquidGlassHostView != null && liquidGlassHostView.getParent() != null) return r;
                            if (liquidGlassView != null) log("[DC] re-creating glass view (previous detached)");
                            if (liquid) {
                                View workspace = null;
                                try {
                                    Object w = HookUtil.getField(chain.getThisObject(), "mWorkspace");
                                    if (w instanceof View) workspace = (View) w;
                                } catch (Throwable ignored) {}
                                int bgIdx = parent.indexOfChild(oldBg);
                                liquidGlassView = installLiquidGlassLayer(parent, Math.max(0, bgIdx), gv,
                                        oldBg, workspace, current, c2.squircle, sqCp);
                                syncLauncherSceneState(chain.getThisObject(), liquidGlassView);
                                installDockTouchListener(liquidGlassView, oldBg.getRootView());
                                liquidGlassView.post(() -> installDockTouchListener(liquidGlassView, oldBg.getRootView()));
                                try {
                                    View decor = ((android.app.Activity) chain.getThisObject()).getWindow().getDecorView();
                                    installDockAreaTouchDetector(liquidGlassView, decor);
                                    liquidGlassView.post(() -> installDockAreaTouchDetector(liquidGlassView,
                                            ((android.app.Activity) chain.getThisObject()).getWindow().getDecorView()));
                                } catch (Throwable ignored) {}
                            }
                            if (workstationMode) {
                                // Laptop/workstation Dock has its own DockContainerView
                                // background. Never leave the normal HotSeats background or
                                // LiquidDock glass visible underneath it.
                                oldBg.setAlpha(0f);
                                if (liquidGlassView != null)
                                    liquidGlassView.setWorkstationMode(true);
                                if (liquidGlassHostView != null)
                                    liquidGlassHostView.setVisibility(View.GONE);
                                return r;
                            }
                            if (dockShadow) {
                                shadowView = makeDockShadow(c2.squircle, sqOff, sqCp, dsR, dsS, dsA, dsY);
                                shadowView.setId(View.generateViewId());
                                int bgIdx = parent.indexOfChild(oldBg);
                                parent.addView(shadowView, Math.max(0, bgIdx), new FrameLayout.LayoutParams(1, 1));
                                ViewGroup unc = parent;
                                for (int lvl = 0; lvl < 4 && unc != null; lvl++) {
                                    unc.setClipChildren(false); unc.setClipToPadding(false);
                                    android.view.ViewParent nxt = unc.getParent();
                                    unc = nxt instanceof ViewGroup ? (ViewGroup) nxt : null;
                                }
                            }
                            syncAll(oldBg);
                        } catch (Throwable e) { log("[DC] err: " + e); }
                        return r;
                    });
        } catch (Throwable e) { log("[DC] init err: " + e); }
    }

    private static DockLiquidGlassView installLiquidGlassLayer(
            ViewGroup parent, int insertIndex, int gravity,
            View background, View workspace, LiquidDockConfig config,
            boolean squircle, float squircleCp) {
        DockLiquidGlassView glass = LiquidGlassFactory.create(background, workspace,
                config.glass, config.dock, squircle, squircleCp);
        glass.setId(View.generateViewId());

        DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setLayers(glass);

        float radius = bgR;
        try {
            Object value = HookUtil.getField(background, "mCornerRadius");
            if (value instanceof Float) radius = (Float) value;
        } catch (Throwable ignored) {}
        host.setGeometry(radius, squircle, squircleCp);
        host.reloadOverlay(config.dock, config.glass);

        parent.addView(host, insertIndex,
                new FrameLayout.LayoutParams(1, 1, gravity));
        liquidGlassHostView = host;
        liquidGlassView = glass;
        return glass;
    }

    // ── lifecycle / capture hooks ────────────────────────────────────

    private static LauncherSceneController launcherSceneController() {
        LauncherSceneController controller = launcherSceneController;
        if (controller == null) {
            controller = new LauncherSceneController(
                    () -> liquidGlassView, () -> workstationMode, MainHook::log);
            launcherSceneController = controller;
        }
        return controller;
    }

    private static void syncLauncherSceneState(Object launcher, DockLiquidGlassView glass) {
        if (glass == null) return;
        LauncherSceneController controller = launcherSceneController();
        controller.seed(launcher);
        glass.setLauncherState(controller.lifecycleKnown(), controller.launcherResumed());
        glass.setSystemUiPanelExpanded(controller.systemUiPanelExpanded());
        controller.bindRecentsView(glass, launcher);
    }


    private static void installLiquidGlassCaptureHooks(ClassLoader cl) {
        Class<?> launcherClass;
        try {
            launcherClass = Class.forName("com.miui.home.launcher.Launcher", false, cl);
        } catch (Throwable e) {
            log("[DC] Launcher class unavailable for liquid capture lifecycle: " + e);
            return;
        }

        LauncherSceneController sceneController = launcherSceneController();

        sceneController.installFocusAndPanelHooks(cl, launcherClass);

        sceneController.installStateHooks(cl, launcherClass);

        // Configuration changes (rotation)
        try {
            HookUtil.hookMethod(launcherClass, "onConfigurationChanged", new Class<?>[]{Configuration.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass != null) {
                            glass.requestCapture("launcher-configuration-changed");
                            glass.beginRotationStabilize();
                            glass.postDelayed(() -> glass.requestCapture("launcher-configuration-settled"), 220L);
                        }
                        return r;
                    });
        } catch (Throwable e) { log("[DC] liquid configuration hook unavailable: " + e); }

        // Window visibility (capture trigger, not HOME/APP signal)
        XposedInterface.Hooker visibilityHook = chain -> {
            if (!launcherClass.isInstance(chain.getThisObject()))
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
            boolean visible = (Integer) chain.getArgs().get(0) == View.VISIBLE;
            log("[DC] liquid window visibility: " + chain.getArgs().get(0));
            DockLiquidGlassView glass = liquidGlassView;
            if (glass != null)
                glass.requestCapture(visible ? "launcher-window-visible" : "launcher-window-hidden");
            return r;
        };
        try {
            HookUtil.hookMethod(Activity.class, "onWindowVisibilityChanged", new Class<?>[]{int.class}, visibilityHook);
        } catch (Throwable e) { log("[DC] onWindowVisibilityChanged hook failed: " + e); }

        sceneController.installLifecycleHooks(launcherClass);

        // Wallpaper offsets / zoom → notify glass
        try {
            HookUtil.hookMethod(WallpaperManager.class, "setWallpaperOffsets",
                    new Class<?>[]{IBinder.class, float.class, float.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView g = liquidGlassView;
                        if (g != null) g.onWallpaperOffsetChanged(
                                (Float) chain.getArgs().get(1), (Float) chain.getArgs().get(2));
                        return r;
                    });
        } catch (Throwable e) { log("[DC] Wallpaper normalized-offset hook unavailable: " + e); }

        try {
            HookUtil.hookMethod(WallpaperManager.class, "setDisplayOffset",
                    new Class<?>[]{IBinder.class, int.class, int.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView g = liquidGlassView;
                        if (g != null) g.onWallpaperDisplayOffsetChanged(
                                (Integer) chain.getArgs().get(1), (Integer) chain.getArgs().get(2));
                        return r;
                    });
        } catch (Throwable e) { log("[DC] Wallpaper raw-offset hook unavailable: " + e); }

        try {
            HookUtil.hookMethod(WallpaperManager.class, "setWallpaperZoomOut",
                    new Class<?>[]{IBinder.class, float.class},
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView g = liquidGlassView;
                        if (g != null) g.onWallpaperZoomChanged((Float) chain.getArgs().get(1));
                        return r;
                    });
        } catch (Throwable e) { log("[DC] Wallpaper zoom hook unavailable: " + e); }
        installDockDragHooks(cl);
    }

    // ── helpers ──────────────────────────────────────────────────────

    /** Install DragController hooks once. Persistent drag state requires a complete pair. */
    private static void installDockDragHooks(ClassLoader cl) {
        if (dockDragHooksInstalled) return;
        boolean startHooked = false;
        boolean endHooked = false;
        try {
            Class<?> dc = Class.forName("com.miui.home.launcher.DragController", false, cl);
            try {
                HookUtil.hookMethod(dc, "startDrag",
                        new Class<?>[]{
                            android.graphics.drawable.Drawable.class, boolean.class,
                            Class.forName("com.miui.home.launcher.ItemInfo", false, cl),
                            int.class, int.class, float.class,
                            Class.forName("com.miui.home.launcher.DragSource", false, cl),
                            int.class
                        },
                        chain -> {
                            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            DockLiquidGlassView g = liquidGlassView;
                            if (g != null) {
                                if (dockDragHooksReady) {
                                    g.setDockDragging(true,
                                            resolveDragSurfaceLayerName(chain.getThisObject()));
                                } else {
                                    g.requestCapture("drag-start-partial-hook");
                                }
                            }
                            return r;
                        });
                startHooked = true;
            } catch (Throwable e) {
                log("[DC] drag start hook unavailable: " + e);
            }
            try {
                HookUtil.hookMethod(dc, "endDrag", new Class<?>[0],
                        chain -> {
                            Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            DockLiquidGlassView g = liquidGlassView;
                            if (g != null) {
                                if (dockDragHooksReady) g.setDockDragging(false, null);
                                else g.requestCapture("drag-end-partial-hook");
                            }
                            return r;
                        });
                endHooked = true;
            } catch (Throwable e) {
                log("[DC] drag end hook unavailable: " + e);
            }
        } catch (Throwable e) {
            log("[DC] drag controller unavailable: " + e);
        }
        dockDragHooksReady = startHooked && endHooked;
        dockDragHooksInstalled = true;
        log("[DC] dock drag authority ready=" + dockDragHooksReady
                + " start=" + startHooked + " end=" + endHooked);
    }

    private static String resolveDragSurfaceLayerName(Object dragController) {
        try {
            Object dragObject = HookUtil.getField(dragController, "mDragObject");
            if (dragObject == null) return null;
            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (!(views instanceof java.util.List) || ((java.util.List<?>) views).isEmpty()) return null;
            Object dragView = ((java.util.List<?>) views).get(0);
            if (dragView instanceof View) {
                Method getSc = View.class.getDeclaredMethod("getSurfaceControl");
                getSc.setAccessible(true);
                Object sc = getSc.invoke(dragView);
                if (sc != null) {
                    String s = sc.toString();
                    int i = s.indexOf("name="), j = s.indexOf(')', i);
                    if (i >= 0 && j > i) {
                        String name = s.substring(i + 5, j);
                        log("[DC] drag surface layer: " + name);
                        return name;
                    }
                }
            }
        } catch (Throwable e) { log("[DC] drag surface resolve failed: " + e); }
        return null;
    }

    private static boolean isExternalAppForeground(DockLiquidGlassView glass) {
        LauncherSceneController controller = launcherSceneController;
        return controller != null && glass != null
                && controller.isExternalAppForeground(glass.getContext());
    }

    private static void installDockTouchListener(DockLiquidGlassView glass, View dockRoot) {
        try {
            if (dockRoot == null || dockRoot.getWidth() <= 0 || dockRoot.getHeight() <= 0) return;
            dockRoot.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        glass.onDockTouchEvent();
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY(),
                                isExternalAppForeground(glass));
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        glass.onDockTouchEvent();
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        break;
                }
                return false;
            });
        } catch (Throwable e) { log("[DC] dock touch listener failed: " + e); }
    }

    private static void installDockAreaTouchDetector(DockLiquidGlassView glass, View launcherRoot) {
        try {
            if (launcherRoot == null || launcherRoot.getWidth() <= 0 || launcherRoot.getHeight() <= 0) return;
            launcherRoot.setOnTouchListener((v, ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        if (glass.isTouchInDockArea(ev.getRawX(), ev.getRawY())) {
                            glass.onDockTouchEvent();
                            glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY(),
                                    isExternalAppForeground(glass));
                        }
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        if (glass.isTouchInDockArea(ev.getRawX(), ev.getRawY())) {
                            glass.onDockTouchEvent();
                            glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        }
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        break;
                }
                return false;
            });
        } catch (Throwable e) { log("[DC] dock area touch detector failed: " + e); }
    }

    private static void installDockResizeAnimationBypass(ClassLoader cl, boolean smoothAnimation) {
        try {
            HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    "updateBackgroundSize",
                    chain -> {
                        int oldW = 0, oldH = 0; float oldR = 0f;
                        try {
                            oldW = HookUtil.getIntField(chain.getThisObject(), "mWidth");
                            oldH = HookUtil.getIntField(chain.getThisObject(), "mHeight");
                            Object r = HookUtil.getField(chain.getThisObject(), "mCornerRadius");
                            if (r instanceof Float) oldR = (Float) r;
                        } catch (Throwable ignored) {}
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object r = chain.proceed(args);
                        try {
                            Object set = HookUtil.getField(chain.getThisObject(), "animatorSet");
                            if (set instanceof android.animation.Animator)
                                ((android.animation.Animator) set).end();
                            Object radius = HookUtil.getField(chain.getThisObject(), "mViewRadiusAnimator");
                            if (radius instanceof android.animation.Animator)
                                ((android.animation.Animator) radius).end();
                        } catch (Throwable ignored) {}
                        View view = (View) chain.getThisObject();
                        if (smoothAnimation)
                            animateDockGeometryFromPrevious(view, oldW, oldH, oldR);
                        else syncAll(view);
                        return r;
                    }, int.class, int.class, float.class);
            log("[DC] Dock resize animation disabled");
        } catch (Throwable e) { log("[DC] Dock resize animation bypass unavailable: " + e); }
    }

    private static void animateDockGeometryFromPrevious(View view, int startW, int startH, float startR) {
        try {
            int targetW = HookUtil.getIntField(view, "mWidth");
            int targetH = HookUtil.getIntField(view, "mHeight");
            float targetR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
            synchronized (dockResizeAnimators) {
                android.animation.ValueAnimator previous = dockResizeAnimators.remove(view);
                if (previous != null) {
                    startW = HookUtil.getIntField(view, "mWidth");
                    startH = HookUtil.getIntField(view, "mHeight");
                    startR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
                    previous.cancel();
                }
                if (startW == targetW && startH == targetH && Math.abs(startR - targetR) < .01f) {
                    syncAll(view); return;
                }
                final int fromW = startW, fromH = startH;
                final float fromR = startR;
                HookUtil.setIntField(view, "mWidth", fromW);
                HookUtil.setIntField(view, "mHeight", fromH);
                HookUtil.setField(view, "mCornerRadius", fromR);
                android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
                a.setDuration(180L);
                a.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f));
                a.addUpdateListener(anim -> {
                    float t = (Float) anim.getAnimatedValue();
                    HookUtil.setIntField(view, "mWidth", Math.round(fromW + (targetW - fromW) * t));
                    HookUtil.setIntField(view, "mHeight", Math.round(fromH + (targetH - fromH) * t));
                    HookUtil.setField(view, "mCornerRadius", fromR + (targetR - fromR) * t);
                    try { HookUtil.invoke(view, "triggerMeasure"); } catch (Throwable ignored) {}
                    view.requestLayout();
                    syncAll(view);
                });
                a.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        synchronized (dockResizeAnimators) { dockResizeAnimators.remove(view); }
                    }
                });
                dockResizeAnimators.put(view, a);
                a.start();
            }
        } catch (Throwable e) {
            syncAll(view);
            log("[DC] smooth Dock resize failed: " + e);
        }
    }

    private static void installWorkstationDockHooks(ClassLoader cl, LiquidDockConfig.Workstation config) {
        if (!config.dockEnabled) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int iconTopOffset = Math.round(config.iconTopOffset * scale);
        int iconBottomOffset = Math.round(config.iconBottomOffset * scale);
        try {
            Class<?> recyclerView = Class.forName("androidx.recyclerview.widget.RecyclerView", false, cl);
            Class<?> recyclerState = Class.forName("androidx.recyclerview.widget.RecyclerView$State", false, cl);
            HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    "getItemOffsets",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (workstationMode) {
                            Rect out = (Rect) chain.getArgs().get(0);
                            out.top += iconTopOffset;
                            out.bottom += iconBottomOffset;
                        }
                        return r;
                    }, Rect.class, View.class, recyclerView, recyclerState);
            log("[DC] workstation Dock icon vertical offsets top=" + iconTopOffset + " bottom=" + iconBottomOffset);
        } catch (Throwable e) { log("[DC] workstation Dock icon offset hook unavailable: " + e); }
    }

    private static void installWorkstationModeGuard(ClassLoader cl) {
        try {
            Class.forName("com.miui.home.launcher.allapps.LauncherModeController", false, cl);
            workstationMode = (Boolean) HookUtil.invokeStatic(
                    "com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
            Class<?> sm = Class.forName(
                    "com.miui.home.launcher.laptop.LaptopStateManager", false, cl);
            HookUtil.hookMethod(sm, "onLaptopModeChanged", new Class<?>[]{boolean.class},
                    chain -> {
                        boolean entering = (Boolean) chain.getArgs().get(0);
                        if (entering) backupNormalHomeLayout();
                        setWorkstationMode(entering);
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!entering) scheduleNormalLayoutRestore();
                        HomeGridHook.scheduleAllPageRefresh();
                        return r;
                    });
            log("[DC] workstation guard uses LauncherModeController; active=" + workstationMode);
        } catch (Throwable currentApiError) {
            workstationMode = false;
            log("[DC] workstation current API unavailable; mode disabled: " + currentApiError);
        }
    }

    private static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        HomeGridHook.setWorkstationMode(enabled);
        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);
        log("[DC] workstation mode changed=" + enabled);
        if (!enabled) {
            if (oldBg != null) oldBg.post(() -> {
                if (liquidGlassView != null) liquidGlassView.setWorkstationMode(false);
                else oldBg.setAlpha(1f);
                if (shadowView != null) shadowView.setVisibility(View.VISIBLE);
                syncAll(oldBg);
            });
            return;
        }
        if (oldBg != null) oldBg.post(() -> {
            // The workstation Dock background is rendered by its independent laptop
            // DockContainerView. Suppress every normal-mode background layer here.
            oldBg.setAlpha(0f);
            if (shadowView != null) shadowView.setVisibility(View.GONE);
            if (liquidGlassView != null) liquidGlassView.setWorkstationMode(true);
        });
    }

    private static void backupNormalHomeLayout() {
        normalLayoutBackup.clear();
        View root = oldBg == null ? null : oldBg.getRootView();
        if (root != null) collectHomeItemPositions(root, false);
        log("[DC] normal 8x4 layout backup items=" + normalLayoutBackup.size());
    }

    private static void scheduleNormalLayoutRestore() {
        View root = oldBg == null ? null : oldBg.getRootView();
        if (root == null || normalLayoutBackup.isEmpty()) return;
        root.post(() -> restoreNormalHomeLayout(root));
        root.postDelayed(() -> restoreNormalHomeLayout(root), 250L);
        root.postDelayed(() -> restoreNormalHomeLayout(root), 700L);
    }

    private static void collectHomeItemPositions(View view, boolean restore) {
        Object tag = view.getTag();
        if (tag != null) try {
            long id = HookUtil.getLongField(tag, "id");
            if (id >= 0) {
                if (!restore) {
                    normalLayoutBackup.put(id, new HomeItemPosition(
                            HookUtil.getLongField(tag, "screenId"),
                            HookUtil.getIntField(tag, "cellX"), HookUtil.getIntField(tag, "cellY"),
                            HookUtil.getIntField(tag, "spanX"), HookUtil.getIntField(tag, "spanY")));
                } else {
                    HomeItemPosition saved = normalLayoutBackup.get(id);
                    if (saved != null) {
                        HookUtil.setLongField(tag, "screenId", saved.screenId);
                        HookUtil.setIntField(tag, "cellX", saved.cellX);
                        HookUtil.setIntField(tag, "cellY", saved.cellY);
                        HookUtil.setIntField(tag, "spanX", saved.spanX);
                        HookUtil.setIntField(tag, "spanY", saved.spanY);
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectHomeItemPositions(group.getChildAt(i), restore);
        }
    }

    private static void restoreNormalHomeLayout(View root) {
        if (workstationMode) return;
        collectHomeItemPositions(root, true);
        root.requestLayout();
        root.invalidate();
        log("[DC] normal 8x4 layout restored from backup items=" + normalLayoutBackup.size());
    }

    // ── drawing / sync ───────────────────────────────────────────────

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static Path squirclePath(RectF r, float rad) { return squirclePath(r, rad, 0.65f); }
    private static Path squirclePath(RectF r, float rad, float cp) {
        Path p = new Path(); if (rad <= 1) { p.addRect(r, Path.Direction.CW); return p; }
        float a = rad, c = a * cp, l = r.left, t = r.top, ri = r.right, b = r.bottom;
        p.moveTo(l, t + a); p.cubicTo(l, t + a - c, l + a - c, t, l + a, t); p.lineTo(ri - a, t);
        p.cubicTo(ri - a + c, t, ri, t + a - c, ri, t + a); p.lineTo(ri, b - a);
        p.cubicTo(ri, b - a + c, ri - a + c, b, ri - a, b); p.lineTo(l + a, b);
        p.cubicTo(l + a - c, b, l, b - a + c, l, b - a); p.close(); return p;
    }

    private static View makeDockShadow(boolean sq, int sqOff, float sqCp,
                                       int radius, int size, int alpha, int offsetY) {
        final int maxDistance = Math.max(1, size);
        final int blurRadius = Math.min(Math.max(1, radius), maxDistance);
        final int spread = Math.max(0, maxDistance - blurRadius);
        shadowPad = Math.max(4, maxDistance + Math.abs(offsetY) + 4);
        View view = new View(oldBg.getContext()) {
            @Override protected void onDraw(Canvas canvas) {
                if (bgW <= 0 || bgH <= 0) return;
                float left = shadowPad, top = shadowPad;
                RectF bounds; float corner;
                if (sq) {
                    bounds = new RectF(left - sqOff - spread, top - sqOff - spread,
                        left + bgW + sqOff + spread, top + bgH + sqOff + spread);
                    corner = Math.max(0, strokeR + sqOff + spread);
                } else {
                    bounds = new RectF(left + 1f - spread, top + 1f - spread,
                        left + bgW - 1f + spread, top + bgH - 1f + spread);
                    corner = Math.max(0, strokeR - 1f + spread);
                }
                Path shape = sq ? squirclePath(bounds, corner, sqCp) : new Path();
                if (!sq) shape.addRoundRect(bounds, corner, corner, Path.Direction.CW);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.argb(1, 0, 0, 0));
                paint.setShadowLayer(blurRadius, 0, offsetY, Color.argb(alpha, 0, 0, 0));
                canvas.drawPath(shape, paint);
            }
            @Override protected void onDetachedFromWindow() {
                if (shadowView == this) shadowView = null;
                super.onDetachedFromWindow();
            }
        };
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        return view;
    }

    private static void syncShadowGeometry() {
        View shadow = shadowView, dockBg = oldBg;
        if (shadow == null || dockBg == null || bgW <= 0 || bgH <= 0) return;
        ViewGroup.LayoutParams lp = shadow.getLayoutParams();
        if (lp != null) {
            lp.width = bgW + shadowPad * 2; lp.height = bgH + shadowPad * 2;
            shadow.setLayoutParams(lp);
        }
        shadow.setX(dockBg.getX() - shadowPad);
        shadow.setY(dockBg.getY() - shadowPad);
        shadow.invalidate();
    }

    private static void syncAll(View bg) {
        if (bg == null) return;
        if (workstationMode && liquidGlassView == null) return;
        if (liquidGlassHostView == null && liquidGlassView == null && shadowView == null) return;
        boolean anim = animating(bg);
        try {
            bgW = HookUtil.getIntField(bg, "mWidth"); bgH = HookUtil.getIntField(bg, "mHeight");
            Object r = HookUtil.getField(bg, "mCornerRadius"); if (r instanceof Float) bgR = (Float) r;
            if (bgW <= 0) return;
            if (liquidGlassHostView != null) {
                liquidGlassHostView.setVisibility(workstationMode ? View.GONE : View.VISIBLE);
                ViewGroup.LayoutParams hlp = liquidGlassHostView.getLayoutParams();
                if (hlp != null && (hlp.width != bgW || hlp.height != bgH)) {
                    hlp.width = bgW;
                    hlp.height = bgH;
                    liquidGlassHostView.setLayoutParams(hlp);
                }
                liquidGlassHostView.setTranslationX(bg.getTranslationX());
                liquidGlassHostView.setTranslationY(bg.getTranslationY());
                liquidGlassHostView.setRadius(bgR);
                liquidGlassHostView.invalidate();
            } else if (liquidGlassView != null) {
                // Compatibility fallback for a stale pre-host instance during Launcher setup.
                ViewGroup.LayoutParams glp = liquidGlassView.getLayoutParams();
                if (glp != null && (glp.width != bgW || glp.height != bgH)) {
                    glp.width = bgW;
                    glp.height = bgH;
                    liquidGlassView.setLayoutParams(glp);
                }
                liquidGlassView.setGlassRadius(bgR);
                liquidGlassView.invalidate();
            }
            if (shadowView != null) {
                if (workstationMode) { shadowView.setVisibility(View.GONE); return; }
                if (!anim) {
                    boolean sizeChanged = bgW != lastShadowW;
                    lastShadowW = bgW;
                    // Position can change without a width change (startup/translation/layout).
                    // Always align X/Y once the Dock settles; only size changes need a posted
                    // second pass after LayoutParams are applied.
                    syncShadowGeometry();
                    if (sizeChanged) shadowView.post(MainHook::syncShadowGeometry);
                }
            }
        } catch (Throwable ignored) {}
    }

    static boolean isWorkstationMode() { return workstationMode; }

    // ── logging ──────────────────────────────────────────────────────

    static boolean debugLogging;

    static void log(String s) { if (!debugLogging) return; Api101Bridge.log(s); fileLog(s); }

    private static void fileLog(String s) {
        try {
            String line = new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)
                .format(new java.util.Date()) + " " + s + "\n";
            java.io.File dir = new java.io.File("/sdcard/Download");
            if (!dir.canWrite()) dir = new java.io.File("/data/local/tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(dir, "liquiddock.log"), true);
            out.write(line.getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {}
    }

    private static boolean animating(View v) {
        try { return Boolean.TRUE.equals(HookUtil.invoke(v, "isAnimating")); }
        catch (Throwable e) { return false; }
    }

    // ── data ─────────────────────────────────────────────────────────

    private static final class HomeItemPosition {
        final long screenId;
        final int cellX, cellY, spanX, spanY;
        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId; this.cellX = cellX; this.cellY = cellY;
            this.spanX = spanX; this.spanY = spanY;
        }
    }
}
