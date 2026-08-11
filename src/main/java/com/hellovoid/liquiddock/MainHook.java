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
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static View overlay, shadowView, oldBg, nativeShadowTarget;
    private static DockLiquidGlassView liquidGlassView;
    private static volatile boolean launcherResumed;
    private static volatile boolean launcherLifecycleKnown;
    private static volatile boolean systemUiPanelExpanded;
    private static int bgW, bgH, shadowPad;
    private static int strokeBaseR = 255, strokeBaseG = 255, strokeBaseB = 255, strokeBaseAlpha = 255;
    private static float bgR = 30f;
    private static float strokeR = 30f;
    private static volatile boolean workstationMode;
    private static final java.util.Map<Long, HomeItemPosition> normalLayoutBackup =
            new java.util.HashMap<>();
    private static final java.util.WeakHashMap<View, android.animation.ValueAnimator>
            dockResizeAnimators = new java.util.WeakHashMap<>();

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

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;

        installWorkstationModeGuard(lpparam.classLoader);
        LiquidDockConfig config = LiquidDockConfig.load();
        if (!config.enabled) {
            XposedBridge.log("[DC] LiquidDock master switch disabled");
            return;
        }
        RecentsHapticHook.install(lpparam.classLoader, () -> {
            DockLiquidGlassView glass = liquidGlassView;
            if (glass != null) glass.onRecentsHapticTrigger();
        });
        installWorkstationDockHooks(lpparam.classLoader, config.workstation);
        if (!config.dock.resizeAnimation)
            installDockResizeAnimationBypass(lpparam.classLoader,
                    config.dock.smoothResizeAnimation);
        if (workstationMode) {
            XposedBridge.log("[DC] workstation active; using isolated workstation parameters");
        }
        LiquidDockConfig.Grid grid = config.grid;
        boolean grid8x4 = grid.enabled;
        boolean dp = grid.dp;
        boolean offsets = grid.offsets;
        float gridScale = dp
            ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int landXBase = dp ? 57 : 160;
        int landYBase = dp ? 28 : 80;
        int portXBase = dp ? 28 : 80;
        int portYBase = dp ? 57 : 160;
        float landHorizontal = grid.landscapeHorizontal;
        float landTopDistance = grid.landscapeTop;
        float landBottomDistance = grid.landscapeBottom;
        float portraitHorizontal = grid.portraitHorizontal;
        float portraitTopDistance = grid.portraitTop;
        float portraitBottomDistance = grid.portraitBottom;
        float landLeft = offsets ? landHorizontal : landXBase + landHorizontal;
        float landRight = offsets ? landHorizontal : landXBase + landHorizontal;
        float landTop = offsets ? landTopDistance : landYBase + landTopDistance;
        float landBottom = offsets ? landBottomDistance : landYBase + landBottomDistance;
        float portLeft = offsets ? portraitHorizontal : portXBase + portraitHorizontal;
        float portRight = offsets ? portraitHorizontal : portXBase + portraitHorizontal;
        float portTop = offsets ? portraitTopDistance : portYBase + portraitTopDistance;
        float portBottom = offsets ? portraitBottomDistance : portYBase + portraitBottomDistance;
        float landGap = grid.landscapeRowGap;
        float portGap = grid.portraitRowGap;
        if (!offsets) {
            landLeft -= landXBase; landRight -= landXBase;
            landTop -= landYBase; landBottom -= landYBase;
            portLeft -= portXBase; portRight -= portXBase;
            portTop -= portYBase; portBottom -= portYBase;
            landGap -= dp ? 1 : 3; portGap -= dp ? 1 : 3;
        }
        HomeGridHook.install(lpparam.classLoader, grid8x4,
            Math.round(landLeft * gridScale), Math.round(landRight * gridScale),
            Math.round(landTop * gridScale), Math.round(landBottom * gridScale),
            Math.round(portLeft * gridScale), Math.round(portRight * gridScale),
            Math.round(portTop * gridScale), Math.round(portBottom * gridScale),
            Math.round(landGap * gridScale), Math.round(portGap * gridScale),
            Math.round(grid.landscapeIndicatorY * gridScale),
            Math.round(grid.portraitIndicatorY * gridScale));
        HomeGridHook.setWorkstationHorizontalOffset(Math.round(
                config.workstation.gridHorizontalOffset * gridScale));
        boolean dockCustomization = config.dock.enabled;
        boolean liquidGlass = config.glass.enabled;
        if (!dockCustomization && !liquidGlass) {
            XposedBridge.log("[DC] Dock customization and liquid glass both disabled");
            return;
        }
        if (!dockCustomization) {
            XposedBridge.log("[DC] Dock customization disabled (liquid glass only)");
            // Liquid glass runs standalone: install its capture lifecycle hooks and the
            // setupViews initializer, then skip all non-glass dock modification hooks below.
            installLiquidGlassCaptureHooks(lpparam.classLoader);
            // The stroke is independent of dock customization — keep it alive on the
            // native background even when dock geometry customization is off.
            final BetterDockConfig.Dock strokeCfg = config.dock;
            try {
                XposedHelpers.findAndHookMethod("com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    lpparam.classLoader, "setBackgroundRadius", float.class,
                    new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (workstationMode) return;
                        strokeR = Math.max(0f, (Float) p.args[0]);
                    }});
            } catch (Throwable e) {
                XposedBridge.log("[DC] stroke corner hook failed: " + e);
            }
            try {
                XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "setupViews",
                    new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (workstationMode) return;
                        try {
                            Object hs = XposedHelpers.getObjectField(param.thisObject, "mHotSeats");
                            if (hs == null) return;
                            View oldBg = (View) XposedHelpers.getObjectField(hs, "mBlurBackground2");
                            if (oldBg == null) return;
                            ViewGroup parent = (ViewGroup) oldBg.getParent();
                            if (parent == null) return;
                            int gv = ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity;
                            View workspace = null;
                            try {
                                Object candidate = XposedHelpers.getObjectField(param.thisObject, "mWorkspace");
                                if (candidate instanceof View) workspace = (View) candidate;
                            } catch (Throwable ignored) {}
                            if (liquidGlassView != null && liquidGlassView.getParent() != null) return;
                            // Stroke overlay independent of dock customization.
                            if (overlay == null || overlay.getParent() == null) {
                                float ds = oldBg.getResources().getDisplayMetrics().density;
                                int sqW = Math.max(1, Math.round(strokeCfg.squircleStrokeWidth * ds));
                                int sqOff = Math.round(strokeCfg.squircleStrokeOffset * ds);
                                int sw = Math.max(1, Math.round(strokeCfg.strokeWidth * ds));
                                int stdSw = Math.max(1, Math.round(strokeCfg.standardStrokeWidth * ds));
                                overlay = makeOverlay(oldBg, strokeCfg.strokeEnabled, strokeCfg.squircle,
                                        sqOff, sqW, strokeCfg.squircleCp, strokeCfg.fillDiff, sw, stdSw,
                                        strokeCfg.strokeShadow,
                                        Math.max(1, Math.round(strokeCfg.strokeShadowRadius * ds)),
                                        strokeCfg.strokeShadowAlpha);
                                overlay.setId(View.generateViewId());
                                parent.addView(overlay, new FrameLayout.LayoutParams(-1, -1, gv));
                            }
                            // The Dock window's view tree can be rebuilt (dock hide/show,
                            // scene switches); if the previous glass view was destroyed with
                            // its parent, recreate it so the glass does not silently revert
                            // to the default background.
                            if (liquidGlassView != null) {
                                XposedBridge.log("[DC] re-creating glass view (previous detached)");
                            }
                            liquidGlassView = LiquidGlassFactory.create(oldBg, workspace,
                                    config.glass, false, 0.58f);
                            liquidGlassView.setId(View.generateViewId());
                            seedLauncherLifecycleState(param.thisObject);
                            liquidGlassView.setLauncherState(launcherLifecycleKnown, launcherResumed);
                            liquidGlassView.setSystemUiPanelExpanded(systemUiPanelExpanded);
                            bindRecentsView(liquidGlassView, param.thisObject);
                            bindDockDragController(liquidGlassView, lpparam.classLoader);
                            installDockTouchListener(liquidGlassView, oldBg.getRootView());
                            liquidGlassView.post(() -> installDockTouchListener(
                                    liquidGlassView, oldBg.getRootView()));
                            try {
                                View launcherDecor = ((android.app.Activity) param.thisObject)
                                        .getWindow().getDecorView();
                                installDockAreaTouchDetector(liquidGlassView, launcherDecor);
                                liquidGlassView.post(() -> installDockAreaTouchDetector(
                                        liquidGlassView, ((android.app.Activity) param.thisObject)
                                                .getWindow().getDecorView()));
                            } catch (Throwable ignored) {}
                            int bgIndex = parent.indexOfChild(oldBg);
                            parent.addView(liquidGlassView, Math.max(0, bgIndex),
                                new FrameLayout.LayoutParams(1, 1, gv));
                            // Standalone mode has no overlay to drive syncAll; size the glass
                            // view from the dock background immediately so it is not 1x1.
                            syncAll(oldBg);
                            liquidGlassView.post(() -> syncAll(oldBg));
                        } catch (Throwable e) { XposedBridge.log("[DC] liquid-only init err: " + e); }
                    }});
                // The launcher calls setBackgroundWidth/Height/Radius when it lays out the
                // dock background; hook them (sync-only, no offset modification) so the
                // standalone glass view tracks the real default background size.
                Class<?> hsc2 = XposedHelpers.findClass(
                        "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                        lpparam.classLoader);
                XposedHelpers.findAndHookMethod(hsc2, "setBackgroundWidth", int.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
                XposedHelpers.findAndHookMethod(hsc2, "setBackgroundHeight", int.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
                XposedHelpers.findAndHookMethod(hsc2, "setBackgroundRadius", float.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
            } catch (Throwable e) { XposedBridge.log("[DC] liquid-only hooks err: " + e); }
            return;
        }
        LiquidDockConfig.Dock dock = config.dock;
        XposedBridge.log("[DC] init: bl=" + dock.blurRadius + " sq=" + dock.squircle);
        boolean sq = dock.squircle, fd = dock.fillDiff;
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
        ClassLoader cl = lpparam.classLoader;
        // Install the lightweight lifecycle/wallpaper observers unconditionally once Dock
        // customization is active.  The previous conditional install could leave a glass View
        // permanently gated if the process-start config and setupViews config differed.
        installLiquidGlassCaptureHooks(cl);

        try {
            String hsc = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

            if (bottomOffset != 0) { try {
                Class<?> deviceConfig = XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", cl);
                XposedHelpers.findAndHookMethod(deviceConfig, "getHotSeatsMarginBottom",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (workstationMode) return;
                            p.setResult((Integer) p.getResult() + bottomOffset);
                        }
                    });
            } catch (Throwable e) { XposedBridge.log("[DC] bottom offset hook unavailable: " + e); } }

            if (spacing != 0) { try {
                Class<?> recyclerView = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView", cl);
                Class<?> recyclerState = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView$State", cl);
                Class<?> layoutManager = XposedHelpers.findClass(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager", cl);
                XposedHelpers.findAndHookMethod(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    cl, "getItemOffsets", Rect.class, View.class, recyclerView, recyclerState,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (workstationMode) return;
                            Rect out = (Rect) p.args[0];
                            out.left += spacing;
                            out.right += spacing;
                        }
                    });
                XposedHelpers.findAndHookMethod(layoutManager, "updateBackgroundView",
                    FrameLayout.class, int.class, int.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (workstationMode) return;
                            int itemCount = (Integer) XposedHelpers.callMethod(p.thisObject, "getItemCount");
                            if (itemCount > 0)
                                p.args[1] = (Integer) p.args[1] + spacing * 2 * itemCount;
                        }
                    });
            } catch (Throwable e) { XposedBridge.log("[DC] spacing hook unavailable: " + e); } }

            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundWidth", int.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (!workstationMode && wo != 0) p.args[0] = (int) p.args[0] + wo; }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundHeight", int.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (!workstationMode && ho != 0) p.args[0] = (int) p.args[0] + ho; }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundRadius", float.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (workstationMode) return;
                        float systemRadius = (Float) p.args[0];
                        strokeR = Math.max(0f, systemRadius + co);
                        p.args[0] = Math.max(0f, systemRadius + blurCo);
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject);
                        if (sq) { View v = (View) p.thisObject; float r = (Float) XposedHelpers.getObjectField(v, "mCornerRadius");
                            if (r > 0) v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                @Override public void getOutline(View vv, android.graphics.Outline o) { o.setPath(squirclePath(new RectF(0, 0, v.getWidth(), v.getHeight()), r)); }}); } }});

            try { Class<?> bu = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", cl);
                XposedHelpers.findAndHookMethod(bu, "setBackgroundBlur", View.class, int.class, float[].class, int[][].class,
                    new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (!workstationMode && br != 100) p.args[1] = br; }});
            } catch (Throwable ignored) {}
            try {
                XposedHelpers.findAndHookMethod("com.miui.home.launcher.hotseats.HotSeats", cl,
                    "getMingouStaticDockBlurShadowTarget", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Object target = p.getResult();
                            if (target instanceof View) nativeShadowTarget = (View) target;
                        }
                    });
                Class<?> ms = XposedHelpers.findClass("com.miui.home.launcher.common.MiShadowUtils", cl);
                XposedHelpers.findAndHookMethod(ms, "applyViewShadow", View.class, int.class,
                    float.class, float.class, float.class, float.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (workstationMode) return;
                            if (p.args[0] != nativeShadowTarget) return;
                            p.args[1] = Color.TRANSPARENT;
                            p.args[2] = 0f;
                            p.args[3] = 0f;
                            p.args[4] = 0f;
                        }
                    });
            } catch (Throwable e) {
                XposedBridge.log("[DC] native Dock shadow hook unavailable: " + e);
            }

            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher", cl, "setupViews",
                new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) {
                    try { LiquidDockConfig current = LiquidDockConfig.load();
                        LiquidDockConfig.Dock c2 = current.dock;
                        Object hs = XposedHelpers.getObjectField(param.thisObject, "mHotSeats"); if (hs == null) return;
                        if (!workstationMode) try {
                            Object target = XposedHelpers.callMethod(hs, "getMingouStaticDockBlurShadowTarget");
                            if (target instanceof View) {
                                nativeShadowTarget = (View) target;
                                Class<?> ms = XposedHelpers.findClass(
                                    "com.miui.home.launcher.common.MiShadowUtils", cl);
                                XposedHelpers.callStaticMethod(ms, "applyViewShadow",
                                    nativeShadowTarget, Color.TRANSPARENT, 0f, 0f, 0f, 1f);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] native Dock shadow clear failed: " + e);
                        }
                        oldBg = (View) XposedHelpers.getObjectField(hs, "mBlurBackground2"); if (oldBg == null) return;
                        ViewGroup parent = (ViewGroup) oldBg.getParent(); if (parent == null) return;
                        int gv = ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity;
                        boolean strokeEnabled = c2.strokeEnabled, sq2 = c2.squircle, fd2 = c2.fillDiff;
                        strokeBaseR = c2.strokeR;
                        strokeBaseG = c2.strokeG;
                        strokeBaseB = c2.strokeB;
                        strokeBaseAlpha = c2.strokeAlpha;
                        float dockScale2 = c2.dimensionsDp
                                ? oldBg.getResources().getDisplayMetrics().density : 1f;
                        int sqW = Math.max(1, Math.round(c2.squircleStrokeWidth * dockScale2));
                        int sqOff = Math.round(c2.squircleStrokeOffset * dockScale2);
                        float sqCp = c2.squircleCp;
                        int sw = Math.max(1, Math.round(c2.strokeWidth * dockScale2));
                        int stdSw = Math.max(1, Math.round(c2.standardStrokeWidth * dockScale2));
                        boolean shadow = c2.strokeShadow;
                        int shadowRadius = Math.max(1, Math.round(c2.strokeShadowRadius * dockScale2));
                        int shadowAlpha = c2.strokeShadowAlpha;
                        boolean dockShadow = c2.shadowEnabled;
                        boolean liquidGlass = current.glass.enabled;
                        int dockShadowRadius = Math.max(1, Math.round(c2.shadowRadius * dockScale2));
                        int dockShadowSize = Math.max(1, Math.round(c2.shadowSize * dockScale2));
                        int dockShadowAlpha = c2.shadowAlpha;
                        int dockShadowY = Math.round(c2.shadowY * dockScale2);
                        if (overlay != null && overlay.getParent() != null) return;
                        if (overlay != null) {
                            XposedBridge.log("[DC] re-creating dock overlay (previous detached)");
                        }
                        if (liquidGlassView != null && liquidGlassView.getParent() != null) return;
                        if (liquidGlassView != null) {
                            XposedBridge.log("[DC] re-creating glass view (previous detached)");
                        }
                        if (liquidGlass) {
                            View workspace = null;
                            try {
                                Object candidate = XposedHelpers.getObjectField(param.thisObject, "mWorkspace");
                                if (candidate instanceof View) workspace = (View) candidate;
                            } catch (Throwable ignored) {}
                            liquidGlassView = LiquidGlassFactory.create(oldBg, workspace,
                                    current.glass, sq2, sqCp);
                            liquidGlassView.setId(View.generateViewId());
                            seedLauncherLifecycleState(param.thisObject);
                            liquidGlassView.setLauncherState(launcherLifecycleKnown, launcherResumed);
                            liquidGlassView.setSystemUiPanelExpanded(systemUiPanelExpanded);
                            bindRecentsView(liquidGlassView, param.thisObject);
                            bindDockDragController(liquidGlassView, cl);
                            installDockTouchListener(liquidGlassView, oldBg.getRootView());
                            liquidGlassView.post(() -> installDockTouchListener(
                                    liquidGlassView, oldBg.getRootView()));
                            try {
                                View launcherDecor = ((android.app.Activity) param.thisObject)
                                        .getWindow().getDecorView();
                                installDockAreaTouchDetector(liquidGlassView, launcherDecor);
                                liquidGlassView.post(() -> installDockAreaTouchDetector(
                                        liquidGlassView, ((android.app.Activity) param.thisObject)
                                                .getWindow().getDecorView()));
                            } catch (Throwable ignored) {}
                            int bgIndex = parent.indexOfChild(oldBg);
                            parent.addView(liquidGlassView, Math.max(0, bgIndex),
                                new FrameLayout.LayoutParams(1, 1, gv));
                        }
                        if (workstationMode) {
                            if (liquidGlassView != null) {
                                liquidGlassView.setWorkstationMode(true);
                                syncAll(oldBg);
                            }
                            return;
                        }
                        if (dockShadow) {
                            shadowView = makeDockShadow(sq2, sqOff, sqCp, dockShadowRadius, dockShadowSize,
                                dockShadowAlpha, dockShadowY);
                            shadowView.setId(View.generateViewId());
                            int bgIndex = parent.indexOfChild(oldBg);
                            parent.addView(shadowView, Math.max(0, bgIndex),
                                new FrameLayout.LayoutParams(1, 1));
                            ViewGroup unclipped = parent;
                            for (int level = 0; level < 4 && unclipped != null; level++) {
                                unclipped.setClipChildren(false);
                                unclipped.setClipToPadding(false);
                                android.view.ViewParent next = unclipped.getParent();
                                unclipped = next instanceof ViewGroup ? (ViewGroup) next : null;
                            }
                        }
                        overlay = makeOverlay(oldBg, strokeEnabled, sq2, sqOff, sqW, sqCp, fd2, sw, stdSw,
                            shadow, shadowRadius, shadowAlpha);
                        overlay.setId(View.generateViewId()); parent.addView(overlay, new FrameLayout.LayoutParams(-1, -1, gv));
                        syncAll(oldBg);
                    } catch (Throwable e) { XposedBridge.log("[DC] err: " + e); }
                }});
        } catch (Throwable e) { XposedBridge.log("[DC] init err: " + e); }
    }

    private static void seedLauncherLifecycleState(Object launcher) {
        if (launcher == null) return;
        try {
            Object paused = XposedHelpers.callMethod(launcher, "isPause");
            Object visible = XposedHelpers.callMethod(launcher, "isVisible");
            Object focused = XposedHelpers.callMethod(launcher, "isWindowFocus");
            if (paused instanceof Boolean && !((Boolean) paused)) {
                // A positive "not paused" answer is useful.  A paused value during setupViews is
                // ambiguous because setupViews normally runs from onCreate before the first
                // onResume; keep UNKNOWN in that case so window focus can bootstrap capture.
                launcherLifecycleKnown = true;
                launcherResumed = true;
            }
            XposedBridge.log("[DC] liquid lifecycle seed: known=" + launcherLifecycleKnown
                + " resumed=" + launcherResumed + " paused=" + paused
                + " visible=" + visible + " focus=" + focused);
        } catch (Throwable e) {
            // UNKNOWN is intentional: the View's actual window visibility/focus will bootstrap
            // capture until an explicit onResume/onPause callback is observed.
            XposedBridge.log("[DC] liquid lifecycle seed unavailable; using window gate: " + e);
        }
    }

    private static void installLiquidGlassCaptureHooks(ClassLoader cl) {
        Class<?> launcherClass;
        try {
            launcherClass = XposedHelpers.findClass("com.miui.home.launcher.Launcher", cl);
        } catch (Throwable e) {
            XposedBridge.log("[DC] Launcher class unavailable for liquid capture lifecycle: " + e);
            return;
        }

        // HyperOS Launcher already mirrors SystemUI's notification/control-center expansion
        // into DeviceConfig.  This is more precise than treating every focus loss as SystemUI:
        // the floating Dock also legitimately loses focus while it is shown above an app.
        try {
            Class<?> deviceConfig = XposedHelpers.findClass(
                    "com.miui.home.launcher.DeviceConfig", cl);
            XposedHelpers.findAndHookMethod(deviceConfig, "setControlPanelExpanded",
                    boolean.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            boolean expanded = Boolean.TRUE.equals(p.args[0]);
                            systemUiPanelExpanded = expanded;
                            DockLiquidGlassView glass = liquidGlassView;
                            if (glass != null) glass.setSystemUiPanelExpanded(expanded);
                            XposedBridge.log("[DC] liquid SystemUI panel expanded=" + expanded);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log("[DC] SystemUI panel capture gate unavailable: " + e);
        }

        XC_MethodHook focusHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                boolean hasFocus = Boolean.TRUE.equals(p.args[0]);
                launcherLifecycleKnown = true;
                launcherResumed = hasFocus; // window focus is the reliable home-screen signal
                XposedBridge.log("[DC] liquid focus: " + hasFocus);
                DockLiquidGlassView glass = liquidGlassView;
                if (glass != null) {
                    glass.setLauncherState(true, hasFocus);
                    if (!hasFocus) {
                        // An app came to the front: resolve its SF layer name so mode-1
                        // captures can include exactly that layer.
                        glass.refreshForegroundAppLayer();
                    }
                    // Focus returning to the launcher = the way-home transition starts
                    // (Dock collapses with the icon fly-in animation).  Record the
                    // timestamp (render logs correlate with the animation window) and
                    // arm the capture-skip grace so the collapse is never painted into
                    // the backdrop.
                    if (hasFocus) glass.onLauncherFocused();
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod(launcherClass, "onWindowFocusChanged",
                    boolean.class, focusHook);
        } catch (Throwable e) {
            XposedBridge.log("[DC] onWindowFocusChanged hook failed: " + e);
        }

        // Dock v3 resolves the final gesture target before Launcher focus/lifecycle catches up.
        // These events are emitted again when an animation is interrupted, so they provide the
        // correct source switch for HOME, APP and RECENTS without timing guesses.
        hookDockGestureTarget(cl, "GestureToHome", "HOME");
        hookDockGestureTarget(cl, "GestureToApp", "APP");
        hookDockGestureTarget(cl, "GestureToRecent", "RECENTS");

        try {
            XposedHelpers.findAndHookMethod(launcherClass, "onConfigurationChanged",
                    Configuration.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            DockLiquidGlassView glass = liquidGlassView;
                            if (glass == null) return;
                            glass.requestCapture("launcher-configuration-changed");
                            glass.postDelayed(() -> glass.requestCapture(
                                    "launcher-configuration-settled"), 220L);
                        }
                    });
        } catch (Throwable e) {
            XposedBridge.log("[DC] liquid configuration hook unavailable: " + e);
        }

        XC_MethodHook resumeHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                // onResume is NOT authoritative: pulling the Dock out over an app can
                // trigger launcher onResume while the launcher window is NOT focused.
                // Window focus (onWindowFocusChanged) decides launcherResumed.
                XposedBridge.log("[DC] liquid lifecycle: onResume (focus decides)");
            }
        };
        XC_MethodHook pauseHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                // onPause is likewise overridden by the window-focus signal.
                XposedBridge.log("[DC] liquid lifecycle: onPause (focus decides)");
            }
        };

        XC_MethodHook startHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                // onStart fires for BOTH returning home AND pulling the Dock out over an
                // app (MIUI restores the launcher activity during the gesture), so it can
                // NOT decide capture mode.  Window visibility (onWindowVisibilityChanged)
                // is the discriminating signal: returning home makes the launcher WINDOW
                // visible at the start of the animation; a Dock pull leaves it GONE.
                XposedBridge.log("[DC] liquid lifecycle: onStart (visibility decides)");
            }
        };
        XC_MethodHook stopHook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                XposedBridge.log("[DC] liquid lifecycle: onStop (visibility decides)");
            }
        };

        XC_MethodHook visibilityHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!launcherClass.isInstance(p.thisObject)) return;
                boolean visible = (((Integer) p.args[0]) & View.VISIBLE) != 0;
                XposedBridge.log("[DC] liquid window visibility: " + p.args[0]);
                DockLiquidGlassView glass = liquidGlassView;
                // Visibility changes during both Dock pull-up and return-home animations;
                // they are not a HOME/APP signal. Window focus owns that decision.
                if (glass != null) glass.requestCapture(
                        visible ? "launcher-window-visible" : "launcher-window-hidden");
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onWindowVisibilityChanged",
                    int.class, visibilityHook);
        } catch (Throwable e) {
            XposedBridge.log("[DC] onWindowVisibilityChanged hook failed: " + e);
        }

        boolean directLifecycleHooked = false;
        try {
            XposedHelpers.findAndHookMethod(launcherClass, "onResume", resumeHook);
            XposedHelpers.findAndHookMethod(launcherClass, "onPause", pauseHook);
            XposedHelpers.findAndHookMethod(launcherClass, "onStart", startHook);
            XposedHelpers.findAndHookMethod(launcherClass, "onStop", stopHook);
            directLifecycleHooked = true;
        } catch (Throwable directError) {
            XposedBridge.log("[DC] Launcher lifecycle direct hook unavailable: " + directError);
        }

        if (!directLifecycleHooked) {
            // Fallback for builds where Launcher inherits the lifecycle methods without
            // declaring them. This hook is installed only in the com.miui.home process.
            try {
                XposedHelpers.findAndHookMethod(Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!launcherClass.isInstance(p.thisObject)) return;
                            launcherLifecycleKnown = true;
                            launcherResumed = true;
                            XposedBridge.log("[DC] liquid lifecycle fallback: onResume");
                            DockLiquidGlassView glass = liquidGlassView;
                            if (glass != null) glass.setLauncherState(true, true);
                        }
                    });
                XposedHelpers.findAndHookMethod(Activity.class, "onPause",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (!launcherClass.isInstance(p.thisObject)) return;
                            launcherLifecycleKnown = true;
                            launcherResumed = false;
                            XposedBridge.log("[DC] liquid lifecycle fallback: onPause");
                            DockLiquidGlassView glass = liquidGlassView;
                            if (glass != null) glass.setLauncherState(true, false);
                        }
                    });
            } catch (Throwable fallbackError) {
                XposedBridge.log("[DC] Launcher lifecycle fallback hook unavailable: " + fallbackError);
            }
        }

        try {
            XposedHelpers.findAndHookMethod(WallpaperManager.class, "setWallpaperOffsets",
                IBinder.class, float.class, float.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass == null) return;
                        glass.onWallpaperOffsetChanged((Float) p.args[1], (Float) p.args[2]);
                    }
                });
        } catch (Throwable e) {
            XposedBridge.log("[DC] Wallpaper normalized-offset hook unavailable: " + e);
        }
        try {
            XposedHelpers.findAndHookMethod(WallpaperManager.class, "setDisplayOffset",
                IBinder.class, int.class, int.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass == null) return;
                        glass.onWallpaperDisplayOffsetChanged((Integer) p.args[1], (Integer) p.args[2]);
                    }
                });
        } catch (Throwable e) {
            // Hidden/SystemApi on AOSP, but some Launcher builds use it directly.
            XposedBridge.log("[DC] Wallpaper raw-offset hook unavailable: " + e);
        }
        try {
            XposedHelpers.findAndHookMethod(WallpaperManager.class, "setWallpaperZoomOut",
                IBinder.class, float.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass == null) return;
                        glass.onWallpaperZoomChanged((Float) p.args[1]);
                    }
                });
        } catch (Throwable e) {
            XposedBridge.log("[DC] Wallpaper zoom hook unavailable: " + e);
        }
    }

    private static void hookDockGestureTarget(ClassLoader cl, String eventName, String target) {
        try {
            Class<?> eventClass = XposedHelpers.findClass(
                    "com.miui.home.launcher.dock.v3." + eventName, cl);
            XposedBridge.hookAllConstructors(eventClass, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    DockLiquidGlassView glass = liquidGlassView;
                    if (glass != null) glass.setGestureCaptureTarget(target);
                    XposedBridge.log("[DC] liquid gesture target=" + target);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("[DC] " + eventName + " capture hook unavailable: " + e);
        }
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
                float left = shadowPad;
                float top = shadowPad;
                RectF bounds;
                float corner;
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

    private static View makeOverlay(View bg, boolean strokeEnabled, boolean sq, int sqOff, int sqW, float sqCp,
                                    boolean fd, int sw, int stdSw, boolean shadow,
                                    int shadowRadius, int shadowAlpha) {
        return new View(bg.getContext()) {
            @Override protected void onDraw(Canvas c) {
                if (!strokeEnabled || getWidth() < 1 || getHeight() < 1) return;
                // Size from the overlay's own layout (match_parent on the dock background)
                // so the stroke also works when dock customization is disabled and the
                // syncAll-driven static bgW/bgH are never updated.
                float w = getWidth(), h = getHeight(), r = Math.max(0, sq ? strokeR + sqOff : strokeR - 1f);
                if (sq) {
                    if (shadow) drawSqShadow(c, w, h, r, sqOff, sqCp, shadowRadius, shadowAlpha);
                    drawSq(c, w, h, r, sqOff, sqW, sqCp); return;
                }
                if (shadow) drawRoundShadow(c, w, h, r, shadowRadius, shadowAlpha);
                if (fd) c.drawPath(roundRectRing(w, h, r, sw), noc(150));
                else {
                    Paint stroke = noc(150); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(stdSw);
                    c.drawRoundRect(1, 1, w - 1, h - 1, r, r, stroke);
                }
            }
            @Override protected void onDetachedFromWindow() {
                boolean ownsGlobalState = overlay == this;
                DockLiquidGlassView glass = liquidGlassView;
                if (glass != null) glass.setLauncherResumed(false);
                liquidGlassView = null;
                if (ownsGlobalState) overlay = null;
                if (oldBg == bg) oldBg = null;
                super.onDetachedFromWindow();
            }
        };
    }

    private static Path roundRectRing(float w, float h, float r, float inset) {
        Path outer = new Path(); outer.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        Path inner = new Path();
        float ir = Math.max(0, r - inset);
        inner.addRoundRect(new RectF(inset, inset, w - inset, h - inset), ir, ir, Path.Direction.CW);
        outer.op(inner, Path.Op.DIFFERENCE);
        return outer;
    }

    private static void drawRoundShadow(Canvas c, float w, float h, float r, int radius, int alpha) {
        int steps = Math.max(1, Math.min(radius, 40));
        for (int i = steps; i >= 1; i--) {
            float outerInset = i - 1f, innerInset = i;
            Path band = new Path();
            float outerR = Math.max(0, r - outerInset), innerR = Math.max(0, r - innerInset);
            band.addRoundRect(new RectF(outerInset, outerInset, w - outerInset, h - outerInset),
                outerR, outerR, Path.Direction.CW);
            Path inner = new Path();
            inner.addRoundRect(new RectF(innerInset, innerInset, w - innerInset, h - innerInset),
                innerR, innerR, Path.Direction.CW);
            band.op(inner, Path.Op.DIFFERENCE);
            float strength = 1f - (i - 1f) / steps;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.argb(Math.round(alpha * strength * strength), 0, 0, 0));
            c.drawPath(band, paint);
        }
    }

    private static void drawSqShadow(Canvas c, float w, float h, float r, int sqOff,
                                     float sqCp, int radius, int alpha) {
        int steps = Math.max(1, Math.min(radius, 40));
        for (int i = steps; i >= 1; i--) {
            float oi = i - 1f, ii = i;
            Path band = squirclePath(new RectF(-sqOff + oi, -sqOff + oi,
                w + sqOff - oi, h + sqOff - oi), Math.max(0, r - oi), sqCp);
            Path inner = squirclePath(new RectF(-sqOff + ii, -sqOff + ii,
                w + sqOff - ii, h + sqOff - ii), Math.max(0, r - ii), sqCp);
            band.op(inner, Path.Op.DIFFERENCE);
            float strength = 1f - (i - 1f) / steps;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.argb(Math.round(alpha * strength * strength), 0, 0, 0));
            c.drawPath(band, paint);
        }
    }

    private static void drawSq(Canvas c, float w, float h, float r, int sqOff, int sqW, float sqCp) {
        Path outer = squirclePath(new RectF(-sqOff, -sqOff, w + sqOff, h + sqOff), r, sqCp);
        Path inner = squirclePath(new RectF(-sqOff + sqW, -sqOff + sqW, w + sqOff - sqW, h + sqOff - sqW),
            Math.max(0, r - sqW * .5f), .65f);
        outer.op(inner, Path.Op.DIFFERENCE);
        c.drawPath(outer, noc(200));
    }

    private static Paint noc(int a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int alpha = Math.round(a * clamp(strokeBaseAlpha, 0, 255) / 255f);
        p.setColor(Color.argb(alpha, Math.max(0, Math.min(255, strokeBaseR)),
            Math.max(0, Math.min(255, strokeBaseG)), Math.max(0, Math.min(255, strokeBaseB))));
        return p;
    }
    private static void clear(Paint p) { p.setColor(0); p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); }
    private static void syncShadowGeometry() {
        View shadow = shadowView, stroke = overlay;
        if (shadow == null || stroke == null || bgW <= 0 || bgH <= 0) return;
        ViewGroup.LayoutParams lp = shadow.getLayoutParams();
        if (lp != null) {
            lp.width = bgW + shadowPad * 2;
            lp.height = bgH + shadowPad * 2;
            shadow.setLayoutParams(lp);
        }
        shadow.setX(stroke.getX() - shadowPad);
        shadow.setY(stroke.getY() - shadowPad);
        shadow.invalidate();
    }

    /** Bind the launcher's recents view to the glass so its motion (multitasking cards)
     *  keeps captures alive even when the Dock is static. */
    private static void bindRecentsView(DockLiquidGlassView glass, Object launcher) {
        try {
            // getRecentsView() pulls in a phone-only class (NewHomeView) on HyperOS Pad and
            // throws NoClassDefFoundError; read the overview panel field directly instead.
            Object panel = XposedHelpers.getObjectField(launcher, "mOverviewPanel");
            if (panel instanceof View) glass.setRecentsView((View) panel);
        } catch (Throwable e) {
            XposedBridge.log("[DC] recents bind failed: " + e);
        }
    }

    /** Watch touches on the DOCK window root: any touch on the Dock area triggers a glass
     *  refresh (tap, hover before an up-swipe, drag).  Listener never consumes events. */
    private static void installDockTouchListener(DockLiquidGlassView glass, View dockRoot) {
        try {
            if (dockRoot == null || dockRoot.getWidth() <= 0 || dockRoot.getHeight() <= 0) return;
            dockRoot.setOnTouchListener((View v, android.view.MotionEvent ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                    case android.view.MotionEvent.ACTION_MOVE:
                        glass.onDockTouchEvent();
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        glass.onDockGestureMotion(ev.getActionMasked(), ev.getRawY());
                        break;
                    default:
                        break;
                }
                return false; // never consume; the Dock's own handlers stay untouched
            });
        } catch (Throwable e) {
            XposedBridge.log("[DC] dock touch listener failed: " + e);
        }
    }

    /** Coordinate-based detector on the LAUNCHER window root: any touch point that lands
     *  inside (or near) the Dock area counts as Dock interaction and triggers a glass
     *  refresh.  Unlike the Dock-window listener, this also catches touches the system
     *  dispatches elsewhere during an icon drag (drag surface), as long as the finger
     *  stays near the Dock.  Never consumes events. */
    private static void installDockAreaTouchDetector(DockLiquidGlassView glass, View launcherRoot) {
        try {
            if (launcherRoot == null || launcherRoot.getWidth() <= 0
                    || launcherRoot.getHeight() <= 0) return;
            launcherRoot.setOnTouchListener((View v, android.view.MotionEvent ev) -> {
                switch (ev.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
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
                    default:
                        break;
                }
                return false; // never consume
            });
        } catch (Throwable e) {
            XposedBridge.log("[DC] dock area touch detector failed: " + e);
        }
    }

    /** Hook the launcher's drag controller: while a Dock icon drag is in flight the glass
     *  keeps capturing (so the background follows the rearrangement) and the drag surface
     *  layer name is resolved and excluded from captures (so the floating icon never
     *  freezes into the glass background). */
    private static void bindDockDragController(DockLiquidGlassView glass, ClassLoader cl) {
        try {
            Class<?> dc = XposedHelpers.findClass("com.miui.home.launcher.DragController", cl);
            XposedHelpers.findAndHookMethod(dc, "startDrag",
                android.graphics.drawable.Drawable.class, boolean.class,
                XposedHelpers.findClass("com.miui.home.launcher.ItemInfo", cl),
                int.class, int.class, float.class,
                XposedHelpers.findClass("com.miui.home.launcher.DragSource", cl),
                int.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        String dragName = resolveDragSurfaceLayerName(p.thisObject);
                        glass.setDockDragging(true, dragName);
                    }
                });
            XposedHelpers.findAndHookMethod(dc, "endDrag", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    glass.setDockDragging(false, null);
                }
            });
            XposedBridge.log("[DC] dock drag controller hooked");
        } catch (Throwable e) {
            XposedBridge.log("[DC] drag controller hook failed: " + e);
        }
    }

    /** Extract the SF layer name of the drag surface ("drag surface#NNNN") from the
     *  DragController's DragObject.mDragViews[0] view's SurfaceControl. */
    private static String resolveDragSurfaceLayerName(Object dragController) {
        try {
            Object dragObject = XposedHelpers.getObjectField(dragController, "mDragObject");
            if (dragObject == null) return null;
            Object views = XposedHelpers.getObjectField(dragObject, "mDragViews");
            if (!(views instanceof java.util.List) || ((java.util.List<?>) views).isEmpty()) {
                return null;
            }
            Object dragView = ((java.util.List<?>) views).get(0);
            if (dragView instanceof View) {
                java.lang.reflect.Method getSc = View.class.getDeclaredMethod("getSurfaceControl");
                getSc.setAccessible(true);
                Object sc = getSc.invoke(dragView);
                if (sc != null) {
                    String s = sc.toString(); // Surface(name=drag surface#16904)/@0x...
                    int i = s.indexOf("name=");
                    int j = s.indexOf(')', i);
                    if (i >= 0 && j > i) {
                        String name = s.substring(i + 5, j);
                        XposedBridge.log("[DC] drag surface layer: " + name);
                        return name;
                    }
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("[DC] drag surface resolve failed: " + e);
        }
        return null;
    }

    private static void syncAll(View bg) { if (bg == null) return;
        if (workstationMode && liquidGlassView == null) return;
        if (overlay == null && liquidGlassView == null && shadowView == null) return;
        try { bgW = XposedHelpers.getIntField(bg, "mWidth"); bgH = XposedHelpers.getIntField(bg, "mHeight");
            Object r = XposedHelpers.getObjectField(bg, "mCornerRadius"); if (r instanceof Float) bgR = (Float) r;
            if (bgW <= 0) return;
            if (overlay != null) {
                if (workstationMode) overlay.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = overlay.getLayoutParams();
                if (lp != null) { lp.width = bgW; lp.height = bgH; overlay.setLayoutParams(lp); }
                overlay.invalidate();
            }
            if (liquidGlassView != null) {
                ViewGroup.LayoutParams glassLp = liquidGlassView.getLayoutParams();
                if (glassLp != null) {
                    // Match the stroke overlay exactly (bgW/bgH already include the
                    // updateBackgroundView spacing/offset adjustments).
                    glassLp.width = bgW; glassLp.height = bgH;
                    liquidGlassView.setLayoutParams(glassLp);
                }
                liquidGlassView.setGlassRadius(bgR);
                liquidGlassView.invalidate();
            }
            if (shadowView != null) {
                if (workstationMode) {
                    shadowView.setVisibility(View.GONE);
                    return;
                }
                syncShadowGeometry();
                overlay.post(MainHook::syncShadowGeometry);
            }
        } catch (Throwable ignored) {} }

    static boolean isWorkstationMode() { return workstationMode; }

    private static void installDockResizeAnimationBypass(ClassLoader classLoader,
                                                          boolean smoothAnimation) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    classLoader, "updateBackgroundSize", int.class, int.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                param.setObjectExtra("bd_old_w", XposedHelpers.getIntField(param.thisObject, "mWidth"));
                                param.setObjectExtra("bd_old_h", XposedHelpers.getIntField(param.thisObject, "mHeight"));
                                param.setObjectExtra("bd_old_r", XposedHelpers.getObjectField(param.thisObject, "mCornerRadius"));
                            } catch (Throwable ignored) {}
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object set = XposedHelpers.getObjectField(
                                        param.thisObject, "animatorSet");
                                if (set instanceof android.animation.Animator)
                                    ((android.animation.Animator) set).end();
                                Object radius = XposedHelpers.getObjectField(
                                        param.thisObject, "mViewRadiusAnimator");
                                if (radius instanceof android.animation.Animator)
                                    ((android.animation.Animator) radius).end();
                            } catch (Throwable ignored) {}
                            View view = (View) param.thisObject;
                            if (smoothAnimation) animateDockGeometryFromPrevious(view,
                                    param.getObjectExtra("bd_old_w"),
                                    param.getObjectExtra("bd_old_h"),
                                    param.getObjectExtra("bd_old_r"));
                            else syncAll(view);
                        }
                    });
            XposedBridge.log("[DC] Dock resize animation disabled");
        } catch (Throwable e) {
            XposedBridge.log("[DC] Dock resize animation bypass unavailable: " + e);
        }
    }

    private static void animateDockGeometryFromPrevious(View view, Object oldWObject,
                                                         Object oldHObject, Object oldRObject) {
        try {
            int targetW = XposedHelpers.getIntField(view, "mWidth");
            int targetH = XposedHelpers.getIntField(view, "mHeight");
            float targetR = ((Number) XposedHelpers.getObjectField(view, "mCornerRadius")).floatValue();
            int startW = oldWObject instanceof Number ? ((Number) oldWObject).intValue() : targetW;
            int startH = oldHObject instanceof Number ? ((Number) oldHObject).intValue() : targetH;
            float startR = oldRObject instanceof Number ? ((Number) oldRObject).floatValue() : targetR;
            synchronized (dockResizeAnimators) {
                android.animation.ValueAnimator previous = dockResizeAnimators.remove(view);
                if (previous != null) {
                    startW = XposedHelpers.getIntField(view, "mWidth");
                    startH = XposedHelpers.getIntField(view, "mHeight");
                    startR = ((Number) XposedHelpers.getObjectField(view, "mCornerRadius")).floatValue();
                    previous.cancel();
                }
                if (startW == targetW && startH == targetH && Math.abs(startR - targetR) < .01f) {
                    syncAll(view);
                    return;
                }
                final int fromW = startW, fromH = startH;
                final float fromR = startR;
                XposedHelpers.setIntField(view, "mWidth", fromW);
                XposedHelpers.setIntField(view, "mHeight", fromH);
                XposedHelpers.setObjectField(view, "mCornerRadius", fromR);
                android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(180L);
                animator.setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f));
                animator.addUpdateListener(a -> {
                    float t = (Float) a.getAnimatedValue();
                    XposedHelpers.setIntField(view, "mWidth", Math.round(fromW + (targetW - fromW) * t));
                    XposedHelpers.setIntField(view, "mHeight", Math.round(fromH + (targetH - fromH) * t));
                    XposedHelpers.setObjectField(view, "mCornerRadius", fromR + (targetR - fromR) * t);
                    try { XposedHelpers.callMethod(view, "triggerMeasure"); } catch (Throwable ignored) {}
                    view.requestLayout();
                    syncAll(view);
                });
                animator.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        synchronized (dockResizeAnimators) { dockResizeAnimators.remove(view); }
                    }
                });
                dockResizeAnimators.put(view, animator);
                animator.start();
            }
        } catch (Throwable e) {
            syncAll(view);
            XposedBridge.log("[DC] smooth Dock resize failed: " + e);
        }
    }

    private static void installWorkstationDockHooks(ClassLoader classLoader,
                                                    LiquidDockConfig.Workstation config) {
        if (!config.dockEnabled) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int widthOffset = Math.round(config.dockWidthOffset * scale);
        int iconTopOffset = Math.round(config.iconTopOffset * scale);
        int iconBottomOffset = Math.round(config.iconBottomOffset * scale);
        try {
            XposedHelpers.findAndHookMethod(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    classLoader, "setBackgroundWidth", int.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (workstationMode && widthOffset != 0)
                                param.args[0] = (Integer) param.args[0] + widthOffset;
                        }
                    });
            XposedBridge.log("[DC] workstation Dock width hook offset=" + widthOffset);
        } catch (Throwable e) {
            XposedBridge.log("[DC] workstation Dock hook unavailable: " + e);
        }
        try {
            Class<?> recyclerView = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView", classLoader);
            Class<?> recyclerState = XposedHelpers.findClass(
                    "androidx.recyclerview.widget.RecyclerView$State", classLoader);
            XposedHelpers.findAndHookMethod(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    classLoader, "getItemOffsets", Rect.class, View.class,
                    recyclerView, recyclerState, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!workstationMode) return;
                            Rect out = (Rect) param.args[0];
                            out.top += iconTopOffset;
                            out.bottom += iconBottomOffset;
                        }
                    });
            XposedBridge.log("[DC] workstation Dock icon vertical offsets top="
                    + iconTopOffset + " bottom=" + iconBottomOffset);
        } catch (Throwable e) {
            XposedBridge.log("[DC] workstation Dock icon offset hook unavailable: " + e);
        }
    }

    private static void installWorkstationModeGuard(ClassLoader classLoader) {
        boolean detected = false;
        // Current HyperOS build: this is the actual active LauncherMode, not merely a
        // preference. LaptopStateManager receives the transition before the hierarchy is
        // rebuilt, which lets every LiquidDock hook stand down during that rebuild.
        try {
            Class<?> modeController = XposedHelpers.findClass(
                    "com.miui.home.launcher.allapps.LauncherModeController", classLoader);
            workstationMode = (Boolean) XposedHelpers.callStaticMethod(
                    modeController, "isLaptopMode");
            Class<?> stateManager = XposedHelpers.findClass(
                    "com.miui.home.launcher.laptop.LaptopStateManager", classLoader);
            XposedHelpers.findAndHookMethod(stateManager, "onLaptopModeChanged",
                    boolean.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            boolean entering = (Boolean) param.args[0];
                            if (entering) backupNormalHomeLayout();
                            // On exit, re-enable 8x4 before Launcher reloads the normal DB.
                            setWorkstationMode(entering);
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!((Boolean) param.args[0])) scheduleNormalLayoutRestore();
                            HomeGridHook.scheduleAllPageRefresh();
                        }
                    });
            detected = true;
            XposedBridge.log("[DC] workstation guard uses LauncherModeController; active="
                    + workstationMode);
        } catch (Throwable currentApiError) {
            XposedBridge.log("[DC] current workstation API unavailable: " + currentApiError);
        }
        // Older Mingou builds used a DeviceConfig preference directly.
        if (!detected) try {
            Class<?> deviceConfig = XposedHelpers.findClass(
                    "com.miui.home.launcher.DeviceConfig", classLoader);
            workstationMode = (Boolean) XposedHelpers.callStaticMethod(
                    deviceConfig, "isMingouLaptopPcModeEnabled");
            XposedHelpers.findAndHookMethod(deviceConfig,
                    "setMingouLaptopPcModeEnabled", boolean.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            setWorkstationMode((Boolean) param.args[0]);
                        }
                    });
            detected = true;
            XposedBridge.log("[DC] workstation guard uses legacy DeviceConfig; active="
                    + workstationMode);
        } catch (Throwable legacyApiError) {
            XposedBridge.log("[DC] legacy workstation API unavailable: " + legacyApiError);
        }
        if (!detected) {
            // Keep normal mode usable, but report loudly instead of the old silent fallback.
            workstationMode = false;
            XposedBridge.log("[DC] ERROR: no supported workstation state API found");
        }
    }

    private static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        HomeGridHook.setWorkstationMode(enabled);
        XposedBridge.log("[DC] Mingou workstation mode changed=" + enabled);
        if (!enabled) {
            if (oldBg != null) oldBg.post(() -> {
                if (liquidGlassView != null) liquidGlassView.setWorkstationMode(false);
                if (overlay != null) overlay.setVisibility(View.VISIBLE);
                if (shadowView != null) shadowView.setVisibility(View.VISIBLE);
                syncAll(oldBg);
            });
            return;
        }
        // Restore the native Dock immediately. Modified views remain hidden until the
        // launcher rebuilds its normal-mode hierarchy, avoiding workstation rendering bugs.
        if (oldBg != null) oldBg.post(() -> {
            if (oldBg != null) oldBg.setAlpha(1f);
            if (overlay != null) overlay.setVisibility(View.GONE);
            if (shadowView != null) shadowView.setVisibility(View.GONE);
            if (liquidGlassView != null) {
                liquidGlassView.setVisibility(View.VISIBLE);
                liquidGlassView.setWorkstationMode(true);
            }
        });
    }

    private static void backupNormalHomeLayout() {
        normalLayoutBackup.clear();
        View root = oldBg == null ? null : oldBg.getRootView();
        if (root != null) collectHomeItemPositions(root, false);
        XposedBridge.log("[DC] normal 8x4 layout backup items=" + normalLayoutBackup.size());
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
            long id = XposedHelpers.getLongField(tag, "id");
            if (id >= 0) {
                if (!restore) {
                    normalLayoutBackup.put(id, new HomeItemPosition(
                            XposedHelpers.getLongField(tag, "screenId"),
                            XposedHelpers.getIntField(tag, "cellX"),
                            XposedHelpers.getIntField(tag, "cellY"),
                            XposedHelpers.getIntField(tag, "spanX"),
                            XposedHelpers.getIntField(tag, "spanY")));
                } else {
                    HomeItemPosition saved = normalLayoutBackup.get(id);
                    if (saved != null) {
                        XposedHelpers.setLongField(tag, "screenId", saved.screenId);
                        XposedHelpers.setIntField(tag, "cellX", saved.cellX);
                        XposedHelpers.setIntField(tag, "cellY", saved.cellY);
                        XposedHelpers.setIntField(tag, "spanX", saved.spanX);
                        XposedHelpers.setIntField(tag, "spanY", saved.spanY);
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
        XposedBridge.log("[DC] normal 8x4 layout restored from backup items="
                + normalLayoutBackup.size());
    }

    private static final class HomeItemPosition {
        final long screenId;
        final int cellX, cellY, spanX, spanY;
        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.spanX = spanX;
            this.spanY = spanY;
        }
    }

}
