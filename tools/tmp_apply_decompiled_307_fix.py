from pathlib import Path

ROOT = Path('.')

def patch(path, old, new):
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one match, got {count}: {old[:80]!r}')
    p.write_text(text.replace(old, new, 1))

# 1) Device testing disproved system DragAndDrop mask-name exclusion. Keep only real,
# launcher-owned drag surfaces for ordinary DragController drags.
path = 'src/main/java/com/hellovoid/liquiddock/CaptureExclusionNames.java'
patch(path,
'''    private static final String[] MIUIX_307_DRAG_ICON_LAYERS = {
            "MaskSnapshotLayer_dragIcon", "MaskDark_dragIcon", "MaskIcon_dragIcon"
    };
''', '')
patch(path,
'''            if (Miuix307DragCaptureHook.isDragActive()) {
                for (String name : MIUIX_307_DRAG_ICON_LAYERS) add(names, name);
            }
''', '')

# 2) DragController.mDragViews is DragView[] on the decompiled 307 build. System Dock drag
# uses View.startDragAndDrop + IMiuiDragListener, so freeze the last clean backdrop around it.
path = 'src/main/java/com/hellovoid/liquiddock/Miuix307DragCaptureHook.java'
patch(path,
'''import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
''',
'''import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
''')
patch(path,
'''    /** Package-private read-only state for the mode-1 exclusion policy. */
    static boolean isDragActive() {
        return dragActive;
    }

''', '')
patch(path,
'''            HookUtil.hookMethod(dragController, "endDrag", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onEndDrag();
                        return result;
                    });
            MainHook.log(TAG + " drag-only capture hook installed startOverloads=" + startHooks);
''',
'''            HookUtil.hookMethod(dragController, "endDrag", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onEndDrag();
                        return result;
                    });
            installSystemDockDragHooks(classLoader);
            MainHook.log(TAG + " drag-only capture hook installed startOverloads=" + startHooks);
''')
patch(path,
'''    private static void onStartDrag(Object dragController, String signature) {
''',
'''    /**
     * 307 Dock system drag is not the Launcher DragView surface. Decompiled
     * HotSeatsListContent.startDragInDockForSystem() calls View.startDragAndDrop(), and the
     * resulting mask/leash surfaces are owned by MIUI WMS/Shell. Freeze capture before that
     * call can create those surfaces; IMiuiDragListener/onEnd and resetDraggingView are
     * idempotent resume boundaries.
     */
    private static void installSystemDockDragHooks(ClassLoader classLoader) {
        try {
            Class<?> content = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeatsListContent", false, classLoader);
            HookUtil.hookMethod(content, "startDragInDockForSystem", new Class<?>[0], chain -> {
                setSystemDockDragActive(true);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } catch (Throwable error) {
                    setSystemDockDragActive(false);
                    throw error;
                }
            });
            HookUtil.hookMethod(content, "resetDraggingView", new Class<?>[0], chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                setSystemDockDragActive(false);
                return result;
            });

            Class<?> listenerInterface = Class.forName(
                    "android.view.IMiuiDragListener", false, classLoader);
            int listenerHooks = 0;
            for (int i = 1; i <= 16; i++) {
                try {
                    Class<?> candidate = Class.forName(
                            "com.miui.home.launcher.hotseats.HotSeatsListContent$" + i,
                            false, classLoader);
                    if (!listenerInterface.isAssignableFrom(candidate)) continue;
                    HookUtil.hookMethod(candidate, "onStart", new Class<?>[0], chain -> {
                        setSystemDockDragActive(true);
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
                    HookUtil.hookMethod(candidate, "onEnd", new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        setSystemDockDragActive(false);
                        return result;
                    });
                    listenerHooks++;
                } catch (ClassNotFoundException ignored) {
                }
            }
            MainHook.log(TAG + " system Dock drag freeze hooks installed listeners=" + listenerHooks);
        } catch (Throwable error) {
            MainHook.log(TAG + " system Dock drag freeze hook unavailable: " + error);
        }
    }

    private static void setSystemDockDragActive(boolean active) {
        DockLiquidGlassView glass = currentGlass();
        if (glass != null) glass.setSystemDockDragActive(active);
        MainHook.log(TAG + (active
                ? " system Dock drag start -> capture frozen"
                : " system Dock drag end -> capture resumed"));
    }

    private static void onStartDrag(Object dragController, String signature) {
''')
patch(path,
'''            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (!(views instanceof List) || ((List<?>) views).isEmpty()) return null;
            Object dragView = ((List<?>) views).get(0);
            if (!(dragView instanceof View)) return null;
''',
'''            Object views = HookUtil.getField(dragObject, "mDragViews");
            Object dragView;
            if (views instanceof List) {
                if (((List<?>) views).isEmpty()) return null;
                dragView = ((List<?>) views).get(0);
            } else if (views != null && views.getClass().isArray()) {
                if (Array.getLength(views) == 0) return null;
                dragView = Array.get(views, 0);
            } else {
                return null;
            }
            if (!(dragView instanceof View)) return null;
''')

# 3) Freeze/resume API in the renderer. cancelPendingCaptureWork() invalidates any in-flight
# callback but deliberately keeps the currently installed bitmap, which is exactly the desired
# clean-frame freeze semantics.
path = 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java'
patch(path,
'''    private volatile boolean dockDragging = false;
    private volatile String dragLayerName = null;
''',
'''    private volatile boolean dockDragging = false;
    // MIUI system Dock drag uses WMS/Shell-owned startDragAndDrop surfaces. Unlike ordinary
    // Launcher DragView motion, those surfaces cannot be excluded using a Launcher-owned
    // SurfaceControl, so keep the last clean backdrop installed until the system drag ends.
    private volatile boolean systemDockDragActive = false;
    private volatile String dragLayerName = null;
''')
patch(path,
'''    /** Dock icon drag state (MainHook hooks DragController.startDrag/endDrag).  While
     *  dragging, the glass keeps capturing continuously so the background follows the icon
     *  rearrangement; the drag surface layer is excluded from captures. */
    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {
''',
'''    /** Freeze capture while MIUI's system DragAndDrop owns the moving Dock icon. */
    void setSystemDockDragActive(boolean active) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSystemDockDragActive(active));
            return;
        }
        if (systemDockDragActive == active) return;
        systemDockDragActive = active;
        if (active) {
            logI("Liquid capture frozen: system Dock drag");
            mainHandler.removeCallbacks(cancelGrace);
            cancelPendingCaptureWork();
            invalidate();
            return;
        }
        logI("Liquid capture resumed: system Dock drag ended");
        resetCaptureCircuit("system-dock-drag-end");
        beginObservationBurst();
        observationValid = false;
        lastCaptureStartNanos = 0L;
        requestStateCapture("system-dock-drag-end");
    }

    /** Dock icon drag state (MainHook hooks DragController.startDrag/endDrag).  While
     *  dragging, the glass keeps capturing continuously so the background follows the icon
     *  rearrangement; the drag surface layer is excluded from captures. */
    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {
''')
patch(path,
'''        if (systemUiPanelExpanded) return false;
        // Screen-off/doze is a hard stop. Unlike Dock visibility, Recents does NOT bypass this.
''',
'''        if (systemUiPanelExpanded) return false;
        // The system Dock drag is rendered by WMS/Shell-owned surfaces created by
        // startDragAndDrop(). Freeze the last clean frame before the ordinary dockDragging /
        // Recents visibility exception below can keep sampling those surfaces.
        if (systemDockDragActive) return false;
        // Screen-off/doze is a hard stop. Unlike Dock visibility, Recents does NOT bypass this.
''')

# 4) BlurBackground2 is NOT the MiuiX native material owner. Decompiled addBlur() calls
# BlurUtilities.setBackgroundBlur/setBackgroundBlurAlpha. Clear only that vendor blur and let
# Prismal retain its configured blur. Preserve the View itself for geometry/stroke ownership.
path = 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'
patch(path,
'''import java.lang.reflect.Field;
''',
'''import java.lang.reflect.Field;
import java.lang.reflect.Method;
''')
patch(path,
'''    private static final float SQUIRCLE_CP = 0.58f;

    private static DockLiquidGlassView glassRef;
''',
'''    private static final float SQUIRCLE_CP = 0.58f;
    private static final String NATIVE_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";
    private static final String COMPAT_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

    private static DockLiquidGlassView glassRef;
''')
patch(path,
'''    private static boolean nativeBlurRadiusFailureLogged;
''',
'''    private static boolean nativeBlurRadiusFailureLogged;
    private static ClassLoader launcherClassLoader;
''')
patch(path,
'''        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        if (parent == null) return false;

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == parent) {
''',
'''        ViewGroup parent = dockBg.getParent() instanceof ViewGroup
                ? (ViewGroup) dockBg.getParent() : null;
        if (parent == null) return false;
        launcherClassLoader = cl;
        boolean nativeMaterial = isNativeMaterialBackground(dockBg);

        if (backgroundRef == dockBg && hostRef != null && hostRef.getParent() == parent) {
''')
patch(path,
'''        float density = dockBg.getResources().getDisplayMetrics().density;
        int blurPx = Math.round(config.glass.blur * density);
        nativeBlurRadiusPx = blurPx;
        boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);
        MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);
        if (!passOk) {
            // Keep every 307 sampling-quality knob tied to the existing GUI value. This is only
            // a compatibility fallback; normal 307 operation uses compositor pass-window blur.
            boolean contentOk = MiBlurBridge.applyContentBlur(
                    dockBg, blurPx, config.glass.captureScale);
            MainHook.log(TAG + " fallback to content blur ok=" + contentOk);
        }
''',
'''        float density = dockBg.getResources().getDisplayMetrics().density;
        int blurPx = Math.round(config.glass.blur * density);
        if (nativeMaterial) {
            nativeBlurRadiusPx = blurPx;
            boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);
            MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);
            if (!passOk) {
                // Native MiuiX normally owns compositor pass-window blur. Content blur is only
                // its compatibility fallback; BlurBackground2 deliberately never enters here.
                boolean contentOk = MiBlurBridge.applyContentBlur(
                        dockBg, blurPx, config.glass.captureScale);
                MainHook.log(TAG + " fallback to content blur ok=" + contentOk);
            }
        } else {
            nativeBlurRadiusPx = -1;
            clearCompatBackgroundBlur(dockBg, cl);
        }
''')
patch(path,
'''        enforcePrismalOpticalOnly(glass);
''',
'''        if (nativeMaterial) enforcePrismalOpticalOnly(glass);
''')
patch(path,
'''        installNativeBackgroundPreserver(dockBg, glass);
''',
'''        installNativeBackgroundPreserver(dockBg, glass, nativeMaterial);
''')
patch(path,
'''        MainHook.log(TAG + " Prismal glass installed above MiuiX background with live ownership");
''',
'''        MainHook.log(TAG + " Prismal glass installed above "
                + (nativeMaterial ? "MiuiX native material" : "BlurBackground2 compatibility")
                + " background with live ownership");
''')
patch(path,
'''        float density = dockBg.getResources().getDisplayMetrics().density;
        nativeBlurRadiusPx = Math.round(config.glass.blur * density);
        enforceNativeBlurRadius(dockBg);

        float radius = readRadius(dockBg);
''',
'''        if (isNativeMaterialBackground(dockBg)) {
            float density = dockBg.getResources().getDisplayMetrics().density;
            nativeBlurRadiusPx = Math.round(config.glass.blur * density);
            enforceNativeBlurRadius(dockBg);
        } else {
            nativeBlurRadiusPx = -1;
            clearCompatBackgroundBlur(dockBg, launcherClassLoader);
        }

        float radius = readRadius(dockBg);
''')
patch(path,
'''    /**
     * MiuiX already owns the actual backdrop blur. Prismal must only refract the raw sampled
''',
'''    private static boolean isNativeMaterialBackground(View dockBg) {
        return dockBg != null && NATIVE_BACKGROUND_CLASS.equals(dockBg.getClass().getName());
    }

    /** Clear the legacy blur that BlurBackground2.addBlur() reapplies on attach/radius changes. */
    private static void clearCompatBackgroundBlur(View dockBg, ClassLoader classLoader) {
        if (dockBg == null || isNativeMaterialBackground(dockBg)
                || !COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return;
        try {
            ClassLoader cl = classLoader != null ? classLoader : dockBg.getClass().getClassLoader();
            Class<?> utilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, cl);
            Method clearAllBlur = utilities.getDeclaredMethod("clearAllBlur", View.class);
            clearAllBlur.setAccessible(true);
            clearAllBlur.invoke(null, dockBg);
            MainHook.log(TAG + " compat BlurBackground2 native blur cleared; Prismal owns blur");
        } catch (Throwable error) {
            MainHook.log(TAG + " compat BlurBackground2 native blur clear failed: " + error);
        }
    }

    /**
     * MiuiX already owns the actual backdrop blur. Prismal must only refract the raw sampled
''')
patch(path,
'''    private static void installNativeBackgroundPreserver(
            View dockBg, DockLiquidGlassView glass) {
''',
'''    private static void installNativeBackgroundPreserver(
            View dockBg, DockLiquidGlassView glass, boolean nativeMaterial) {
''')
patch(path,
'''            // DockLiquidGlassView can hot-reload the persisted legacy blur mode/radius at 1 Hz.
            // Reassert the 307 optical contract and native radius after vendor state updates but
            // before this frame reaches SurfaceFlinger.
            enforcePrismalOpticalOnly(glass);
            enforceNativeBlurRadius(dockBg);
''',
'''            // Keep the geometry/background View itself alive. Only the true MiuiX material
            // owns native blur; BlurBackground2 has already had its legacy blur cleared and
            // Prismal must retain the user's configured blur radius.
            if (nativeMaterial) {
                enforcePrismalOpticalOnly(glass);
                enforceNativeBlurRadius(dockBg);
            }
''')
patch(path,
'''            enforcePrismalOpticalOnly(glass);
            enforceNativeBlurRadius(dockBg);
            try {
''',
'''            if (nativeMaterial) {
                enforcePrismalOpticalOnly(glass);
                enforceNativeBlurRadius(dockBg);
            }
            try {
''')

# 5) The decompiled BlurBackground2 calls addBlur() directly from onAttachedToWindow() and
# setBackgroundRadius(). Hook those exact boundaries in addition to triggerMeasure so each
# vendor reapplication is cleared immediately after the original method returns.
path = 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'
patch(path,
'''    private static void installThemedBackgroundHooks(
            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {
        int hooked = 0;
''',
'''    private static void installThemedBackgroundHooks(
            Class<?> backgroundClass, LiquidDockConfig config, ClassLoader classLoader) {
        // Decompiled BlurBackground2.addBlur() is invoked by both attach and radius updates.
        // Run our sync after those originals so MiuixGlassHook can clear the just-reapplied blur.
        HookUtil.hookMethod(backgroundClass, "onAttachedToWindow", new Class<?>[0], chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            View background = (View) chain.getThisObject();
            ensureGlassBound(background, config, classLoader);
            MiuixGlassHook.syncGeometry(background, config);
            return result;
        });
        HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                new Class<?>[]{float.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    View background = (View) chain.getThisObject();
                    ensureGlassBound(background, config, classLoader);
                    MiuixGlassHook.syncGeometry(background, config);
                    return result;
                });

        int hooked = 0;
''')

print('Applied decompiled 307 drag/theme production patch')
