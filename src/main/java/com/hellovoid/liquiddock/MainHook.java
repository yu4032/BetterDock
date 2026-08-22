package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;


/** Core Launcher hooks for LiquidDock — zero-copy glass, stroke, dock geometry, workstation. */
public class MainHook {

    private static WeakReference<View> shadowViewRef = new WeakReference<>(null);
    private static WeakReference<View> oldBgRef = new WeakReference<>(null);
    private static WeakReference<View> nativeShadowTargetRef = new WeakReference<>(null);

    private static View oldBg() { return oldBgRef.get(); }
    private static void setOldBg(View view) { oldBgRef = new WeakReference<>(view); }
    private static View nativeShadowTarget() { return nativeShadowTargetRef.get(); }
    private static void setNativeShadowTarget(View view) {
        nativeShadowTargetRef = new WeakReference<>(view);
    }
    private static int lastShadowW;
    private static int bgW, bgH, shadowPad;
    private static float bgR = 30f;
    private static float strokeR = 30f;
    private static volatile boolean workstationMode;
    private static volatile boolean workstationModeHookConfirmed;
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
        if (dockCustomization) {
            installNativeDockShadowSuppression(classLoader);
            installDockShadowSetupHook(classLoader);
        }
        if (config.glass.enabled) {
            if (Miuix307MaterialPipeline.install(classLoader, config)) {
                log("[DC] MiuiX 307 zero-copy material active");
                return;
            }
            log("[DC] MiuiX 307 zero-copy material unavailable; liquid glass disabled");
        }
        if (!dockCustomization) {
            log("[DC] Dock customization disabled; no legacy glass fallback");
            return;
        }

        // ── Dock customization path; liquid glass is zero-copy-only above ──
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
        ClassLoader cl = classLoader;

        try {
            String hsc = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

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
        } catch (Throwable e) { log("[DC] init err: " + e); }
    }

    // ── helpers ──────────────────────────────────────────────────────

    /**
     * Suppress MIUI's own static Dock shadow before either the legacy or 307 zero-copy path can
     * return. LiquidDock's independent whole-Dock shadow is the sole visual shadow owner.
     */
    private static void installNativeDockShadowSuppression(ClassLoader cl) {
        try {
            HookUtil.hookMethod(cl,
                    "com.miui.home.launcher.hotseats.HotSeats",
                    "getMingouStaticDockBlurShadowTarget",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (r instanceof View) setNativeShadowTarget((View) r);
                        return r;
                    });
            Class<?> ms = Class.forName("com.miui.home.launcher.common.MiShadowUtils", false, cl);
            HookUtil.hookMethod(ms, "applyViewShadow",
                    new Class<?>[]{View.class, int.class, float.class, float.class, float.class,
                            float.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (workstationMode) return chain.proceed(args);
                        if (args[0] != nativeShadowTarget()) return chain.proceed(args);
                        args[1] = Color.TRANSPARENT;
                        args[2] = 0f;
                        args[3] = 0f;
                        args[4] = 0f;
                        return chain.proceed(args);
                    });
        } catch (Throwable e) {
            log("[DC] native Dock shadow hook unavailable: " + e);
        }
    }

    /** Install the independent whole-Dock shadow before the MiuiX 307 early return. */
    private static void installDockShadowSetupHook(ClassLoader cl) {
        try {
            HookUtil.hookMethod(cl, "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            LiquidDockConfig current = LiquidDockConfig.load();
                            LiquidDockConfig.Dock dock = current.dock;
                            Object hs = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hs == null) return r;
                            if (!workstationMode) clearNativeDockShadow(hs);
                            View background = resolveActiveDockBackground(hs);
                            if (background == null) return r;
                            syncDockShadow(background, dock);
                        } catch (Throwable e) {
                            log("[DC] Dock shadow setup failed: " + e);
                        }
                        return r;
                    });
        } catch (Throwable e) {
            log("[DC] Dock shadow setup hook unavailable: " + e);
        }
    }

    private static void clearNativeDockShadow(Object hotSeats) {
        try {
            Object target = HookUtil.invoke(hotSeats, "getMingouStaticDockBlurShadowTarget");
            if (target instanceof View) {
                View nativeTarget = (View) target;
                setNativeShadowTarget(nativeTarget);
                HookUtil.invokeStatic("com.miui.home.launcher.common.MiShadowUtils",
                        "applyViewShadow", nativeTarget, Color.TRANSPARENT, 0f, 0f, 0f, 1f);
            }
        } catch (Throwable e) {
            log("[DC] native Dock shadow clear failed: " + e);
        }
    }

    /** Prefer the active theme-aware background and keep BlurBackground2 as compatibility fallback. */
    private static View resolveActiveDockBackground(Object hotSeats) {
        if (hotSeats == null) return null;
        try {
            Object active = HookUtil.invoke(hotSeats, "getHotSeatsBackground");
            if (active instanceof View) return (View) active;
        } catch (Throwable ignored) {}
        try {
            Object compat = HookUtil.getField(hotSeats, "mBlurBackground2");
            return compat instanceof View ? (View) compat : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Keep one independent shadow bound to the active vendor material. This is also called by the
     * zero-copy geometry path when HyperOS replaces or resizes the material without setupViews.
     */
    static void syncDockShadow(View dockBg, LiquidDockConfig.Dock dock) {
        if (dockBg == null || dock == null || !dock.enabled) return;
        setOldBg(dockBg);

        float nativeRadius = MiuixGlassHook.readNativeOpticsRadius(dockBg);
        strokeR = DockStrokeRenderer.resolveConfiguredRadius(dockBg, dock, nativeRadius);

        View currentShadow = shadowViewRef.get();
        if (workstationMode) {
            dockBg.setAlpha(0f);
            if (currentShadow != null) currentShadow.setVisibility(View.GONE);
            return;
        }
        if (!dock.shadowEnabled) {
            if (currentShadow != null) currentShadow.setVisibility(View.GONE);
            return;
        }

        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        if (parent == null) return;
        if (currentShadow == null || currentShadow.getParent() != parent) {
            if (currentShadow != null && currentShadow.getParent() instanceof ViewGroup) {
                ((ViewGroup) currentShadow.getParent()).removeView(currentShadow);
            }
            float scale = dock.dimensionsDp
                    ? dockBg.getResources().getDisplayMetrics().density : 1f;
            int sqOff = Math.round(dock.squircleStrokeOffset * scale);
            int shadowRadius = Math.max(1, Math.round(dock.shadowRadius * scale));
            int shadowSize = Math.max(1, Math.round(dock.shadowSize * scale));
            int shadowY = Math.round(dock.shadowY * scale);
            View shadow = makeDockShadow(dockBg, dock.squircle, sqOff, dock.squircleCp,
                    shadowRadius, shadowSize, dock.shadowAlpha, shadowY);
            shadow.setId(View.generateViewId());
            int bgIndex = parent.indexOfChild(dockBg);
            parent.addView(shadow, Math.max(0, bgIndex), new FrameLayout.LayoutParams(1, 1));
            shadowViewRef = new WeakReference<>(shadow);
            currentShadow = shadow;

            ViewGroup unclipped = parent;
            for (int level = 0; level < 4 && unclipped != null; level++) {
                unclipped.setClipChildren(false);
                unclipped.setClipToPadding(false);
                android.view.ViewParent next = unclipped.getParent();
                unclipped = next instanceof ViewGroup ? (ViewGroup) next : null;
            }
        }
        currentShadow.setVisibility(View.VISIBLE);
        syncAll(dockBg);
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
        boolean detected = false;
        try {
            Class<?> mc = Class.forName("com.miui.home.launcher.allapps.LauncherModeController", false, cl);
            Object laptopResult = HookUtil.invokeStatic("com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
            if (laptopResult instanceof Boolean) {
                workstationMode = (Boolean) laptopResult;
            } else {
                Object dcResult = HookUtil.invokeStatic(
                        "com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
                workstationMode = dcResult instanceof Boolean && (Boolean) dcResult;
            }
            Class<?> sm = Class.forName("com.miui.home.launcher.laptop.LaptopStateManager", false, cl);
            HookUtil.hookMethod(sm, "onLaptopModeChanged", new Class<?>[]{boolean.class},
                    chain -> {
                        boolean entering = (Boolean) chain.getArgs().get(0);
                        workstationModeHookConfirmed = true;
                        if (entering) backupNormalHomeLayout();
                        setWorkstationMode(entering);
                        Object r = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!entering) scheduleNormalLayoutRestore();
                        HomeGridHook.scheduleAllPageRefresh();
                        return r;
                    });
            detected = true;
            log("[DC] workstation guard uses LauncherModeController; active=" + workstationMode);
            // Deferred re-check: isLaptopMode() may return null at early startup;
            // re-query after the Launcher has finished initializing its mode state.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (workstationModeHookConfirmed) return; // hook already confirmed the state
                try {
                    Object recheck = HookUtil.invokeStatic(
                            "com.miui.home.launcher.allapps.LauncherModeController", "isLaptopMode");
                    boolean actual = recheck instanceof Boolean && (Boolean) recheck;
                    if (recheck == null) {
                        try {
                            Object dcResult = HookUtil.invokeStatic(
                                    "com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
                            actual = dcResult instanceof Boolean && (Boolean) dcResult;
                        } catch (Throwable ignored) {}
                    }
                    if (actual != workstationMode) setWorkstationMode(actual);
                } catch (Throwable ignored) {}
            }, 2000L);
        } catch (Throwable currentApiError) {
            log("[DC] current workstation API unavailable: " + currentApiError);
        }
        if (!detected) try {
            Class<?> dc = Class.forName("com.miui.home.launcher.DeviceConfig", false, cl);
            workstationMode = (Boolean) HookUtil.invokeStatic("com.miui.home.launcher.DeviceConfig", "isMingouLaptopPcModeEnabled");
            HookUtil.hookMethod(dc, "setMingouLaptopPcModeEnabled", new Class<?>[]{boolean.class},
                    chain -> {
                        setWorkstationMode((Boolean) chain.getArgs().get(0));
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            detected = true;
            log("[DC] workstation guard uses legacy DeviceConfig; active=" + workstationMode);
        } catch (Throwable legacyApiError) {
            log("[DC] legacy workstation API unavailable: " + legacyApiError);
        }
        if (!detected) {
            workstationMode = false;
            log("[DC] ERROR: no supported workstation state API found");
        }
    }

    private static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        HomeGridHook.setWorkstationMode(enabled);
        WorkstationDockGeometryHook.onWorkstationModeChanged(enabled);
        log("[DC] Mingou workstation mode changed=" + enabled);
        View dockBg = oldBg();
        if (!enabled) {
            if (dockBg != null) dockBg.post(() -> {
                dockBg.setAlpha(1f);
                View currentShadow = shadowViewRef.get();
                if (currentShadow != null) currentShadow.setVisibility(View.VISIBLE);
                syncAll(dockBg);
            });
            return;
        }
        if (dockBg != null) dockBg.post(() -> {
            // The workstation Dock background is rendered by its independent laptop
            // DockContainerView. Suppress every normal-mode background layer here.
            dockBg.setAlpha(0f);
            View currentShadow = shadowViewRef.get();
            if (currentShadow != null) currentShadow.setVisibility(View.GONE);
        });
    }

    private static void backupNormalHomeLayout() {
        normalLayoutBackup.clear();
        View dockBg = oldBg();
        View root = dockBg == null ? null : dockBg.getRootView();
        if (root != null) collectHomeItemPositions(root, false);
        log("[DC] normal 8x4 layout backup items=" + normalLayoutBackup.size());
    }

    private static void scheduleNormalLayoutRestore() {
        View dockBg = oldBg();
        View root = dockBg == null ? null : dockBg.getRootView();
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

    private static View makeDockShadow(View dockBg, boolean sq, int sqOff, float sqCp,
                                       int radius, int size, int alpha, int offsetY) {
        final int maxDistance = Math.max(1, size);
        final int blurRadius = Math.min(Math.max(1, radius), maxDistance);
        final int spread = Math.max(0, maxDistance - blurRadius);
        shadowPad = Math.max(4, maxDistance + Math.abs(offsetY) + 4);
        View view = new View(dockBg.getContext()) {
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
                if (shadowViewRef.get() == this) shadowViewRef = new WeakReference<>(null);
                super.onDetachedFromWindow();
            }
        };
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        return view;
    }

    private static void syncShadowGeometry() {
        View shadow = shadowViewRef.get(), dockBg = oldBg();
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
        View shadowView = shadowViewRef.get();
        if (bg == null || shadowView == null) return;
        boolean anim = animating(bg);
        try {
            int width = bg.getWidth();
            int height = bg.getHeight();
            try {
                int fieldWidth = HookUtil.getIntField(bg, "mWidth");
                if (fieldWidth > 0) width = fieldWidth;
            } catch (Throwable ignored) {}
            try {
                int fieldHeight = HookUtil.getIntField(bg, "mHeight");
                if (fieldHeight > 0) height = fieldHeight;
            } catch (Throwable ignored) {}
            bgW = width;
            bgH = height;
            try {
                Object r = HookUtil.getField(bg, "mCornerRadius");
                if (r instanceof Number) bgR = ((Number) r).floatValue();
            } catch (Throwable ignored) {}
            if (bgW <= 0 || bgH <= 0 || workstationMode) {
                shadowView.setVisibility(View.GONE);
                return;
            }
            shadowView.setVisibility(View.VISIBLE);
            if (!anim) {
                boolean sizeChanged = bgW != lastShadowW;
                lastShadowW = bgW;
                syncShadowGeometry();
                if (sizeChanged) shadowView.post(MainHook::syncShadowGeometry);
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
