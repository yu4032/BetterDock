from pathlib import Path

ROOT = Path('.')


def patch(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# 1) Themed HotSeats background: preserve vendor blur/shadow/outline ownership.
# ---------------------------------------------------------------------------
path = 'src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java'

patch(path,
'''import java.lang.reflect.Field;\nimport java.lang.reflect.Method;\n''',
'''import java.lang.reflect.Field;\n''')

patch(path,
'''    private static boolean nativeBlurRadiusFailureLogged;\n    private static ClassLoader launcherClassLoader;\n''',
'''    private static boolean nativeBlurRadiusFailureLogged;\n''')

patch(path,
'''        launcherClassLoader = cl;\n        boolean nativeMaterial = isNativeMaterialBackground(dockBg);\n''',
'''        boolean nativeMaterial = isNativeMaterialBackground(dockBg);\n        boolean nativeVisualOwner = isNativeVisualOwner(dockBg);\n''')

patch(path,
'''        float density = dockBg.getResources().getDisplayMetrics().density;\n        int blurPx = Math.round(config.glass.blur * density);\n        if (nativeMaterial) {\n            nativeBlurRadiusPx = blurPx;\n            boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);\n            MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);\n            if (!passOk) {\n                // Native MiuiX normally owns compositor pass-window blur. Content blur is only\n                // its compatibility fallback; BlurBackground2 deliberately never enters here.\n                boolean contentOk = MiBlurBridge.applyContentBlur(\n                        dockBg, blurPx, config.glass.captureScale);\n                MainHook.log(TAG + " fallback to content blur ok=" + contentOk);\n            }\n        } else {\n            nativeBlurRadiusPx = -1;\n            clearCompatBackgroundBlur(dockBg, cl);\n        }\n''',
'''        float density = dockBg.getResources().getDisplayMetrics().density;\n        int blurPx = Math.round(config.glass.blur * density);\n        nativeBlurRadiusPx = nativeVisualOwner ? blurPx : -1;\n        if (nativeMaterial) {\n            boolean passOk = MiBlurBridge.applyPassWindowBlur(dockBg, blurPx);\n            MainHook.log(TAG + " passWindowBlur radius=" + blurPx + " ok=" + passOk);\n            if (!passOk) {\n                // The true MiuiX material normally owns compositor pass-window blur.\n                boolean contentOk = MiBlurBridge.applyContentBlur(\n                        dockBg, blurPx, config.glass.captureScale);\n                MainHook.log(TAG + " fallback to content blur ok=" + contentOk);\n            }\n        } else if (nativeVisualOwner) {\n            // BlurBackground2.addBlur() already enables the vendor pass-window blur and also\n            // owns MiShadow / outline state. Do not clear or re-enable that stack here; the\n            // pre-draw preserver below only clamps its existing blur radius.\n            MainHook.log(TAG + " compat BlurBackground2 keeps vendor visual owner radius="\n                    + blurPx);\n        }\n''')

patch(path,
'''        if (nativeMaterial) enforcePrismalOpticalOnly(glass);\n''',
'''        if (nativeVisualOwner) enforcePrismalOpticalOnly(glass);\n''')

patch(path,
'''        backgroundRef = dockBg;\n        glassRef = glass;\n        hostRef = host;\n        installNativeBackgroundPreserver(dockBg, glass, nativeMaterial);\n''',
'''        backgroundRef = dockBg;\n        glassRef = glass;\n        hostRef = host;\n        if (nativeVisualOwner) enforceNativeBlurRadius(dockBg);\n        installNativeBackgroundPreserver(dockBg, glass, nativeVisualOwner);\n''')

patch(path,
'''        MainHook.log(TAG + " Prismal glass installed above "\n                + (nativeMaterial ? "MiuiX native material" : "BlurBackground2 compatibility")\n                + " background with live ownership");\n''',
'''        MainHook.log(TAG + " Prismal optical layer installed above "\n                + (nativeMaterial ? "MiuiX native material" : "BlurBackground2 vendor visual")\n                + " background with live ownership");\n''')

patch(path,
'''        if (isNativeMaterialBackground(dockBg)) {\n            float density = dockBg.getResources().getDisplayMetrics().density;\n            nativeBlurRadiusPx = Math.round(config.glass.blur * density);\n            enforceNativeBlurRadius(dockBg);\n        } else {\n            nativeBlurRadiusPx = -1;\n            clearCompatBackgroundBlur(dockBg, launcherClassLoader);\n        }\n''',
'''        if (isNativeVisualOwner(dockBg)) {\n            float density = dockBg.getResources().getDisplayMetrics().density;\n            nativeBlurRadiusPx = Math.round(config.glass.blur * density);\n            enforceNativeBlurRadius(dockBg);\n        } else {\n            nativeBlurRadiusPx = -1;\n        }\n''')

patch(path,
'''    private static boolean isNativeMaterialBackground(View dockBg) {\n        return dockBg != null && NATIVE_BACKGROUND_CLASS.equals(dockBg.getClass().getName());\n    }\n\n    /** Clear the legacy blur that BlurBackground2.addBlur() reapplies on attach/radius changes. */\n    private static void clearCompatBackgroundBlur(View dockBg, ClassLoader classLoader) {\n        if (dockBg == null || isNativeMaterialBackground(dockBg)\n                || !COMPAT_BACKGROUND_CLASS.equals(dockBg.getClass().getName())) return;\n        try {\n            ClassLoader cl = classLoader != null ? classLoader : dockBg.getClass().getClassLoader();\n            Class<?> utilities = Class.forName(\n                    "com.miui.home.launcher.common.BlurUtilities", false, cl);\n            Method clearAllBlur = utilities.getDeclaredMethod("clearAllBlur", View.class);\n            clearAllBlur.setAccessible(true);\n            clearAllBlur.invoke(null, dockBg);\n            MainHook.log(TAG + " compat BlurBackground2 native blur cleared; Prismal owns blur");\n        } catch (Throwable error) {\n            MainHook.log(TAG + " compat BlurBackground2 native blur clear failed: " + error);\n        }\n    }\n''',
'''    private static boolean isNativeMaterialBackground(View dockBg) {\n        return dockBg != null && NATIVE_BACKGROUND_CLASS.equals(dockBg.getClass().getName());\n    }\n\n    /** Both 307 implementations own the native blur/shadow/outline visual stack. */\n    private static boolean isNativeVisualOwner(View dockBg) {\n        if (dockBg == null) return false;\n        String name = dockBg.getClass().getName();\n        return NATIVE_BACKGROUND_CLASS.equals(name) || COMPAT_BACKGROUND_CLASS.equals(name);\n    }\n''')

patch(path,
'''    private static void installNativeBackgroundPreserver(\n            View dockBg, DockLiquidGlassView glass, boolean nativeMaterial) {\n''',
'''    private static void installNativeBackgroundPreserver(\n            View dockBg, DockLiquidGlassView glass, boolean nativeVisualOwner) {\n''')

patch(path,
'''            // Keep the geometry/background View itself alive. Only the true MiuiX material\n            // owns native blur; BlurBackground2 has already had its legacy blur cleared and\n            // Prismal must retain the user's configured blur radius.\n            if (nativeMaterial) {\n                enforcePrismalOpticalOnly(glass);\n                enforceNativeBlurRadius(dockBg);\n            }\n''',
'''            // Keep the vendor geometry/background/shadow stack alive underneath Prismal.\n            // Both supported 307 backgrounds own native blur; Prismal only refracts/highlights.\n            if (nativeVisualOwner) {\n                enforcePrismalOpticalOnly(glass);\n                enforceNativeBlurRadius(dockBg);\n            }\n''')

patch(path,
'''            if (nativeMaterial) {\n                enforcePrismalOpticalOnly(glass);\n                enforceNativeBlurRadius(dockBg);\n            }\n''',
'''            if (nativeVisualOwner) {\n                enforcePrismalOpticalOnly(glass);\n                enforceNativeBlurRadius(dockBg);\n            }\n''')


# ---------------------------------------------------------------------------
# 2) Ordinary DragController fallback: freeze when no real drag Surface is excludable.
# ---------------------------------------------------------------------------
path = 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java'

patch(path,
'''    private volatile boolean dockDragging = false;\n    // MIUI system Dock drag uses WMS/Shell-owned startDragAndDrop surfaces. Unlike ordinary\n''',
'''    private volatile boolean dockDragging = false;\n    // Ordinary 307 DragController has no reflectable View.getSurfaceControl(). If no valid\n    // drag Surface can be excluded, freeze the last clean backdrop instead of sampling the\n    // moving icon into the glass. A later valid Surface automatically resumes live capture.\n    private volatile boolean dockDragCaptureFrozen = false;\n    // MIUI system Dock drag uses WMS/Shell-owned startDragAndDrop surfaces. Unlike ordinary\n''')

patch(path,
'''    void setDockDragging(boolean dragging, String dragSurfaceLayerName,\n                         android.view.SurfaceControl dragSurface) {\n        dockDragging = dragging;\n        dragLayerName = dragging ? dragSurfaceLayerName : null;\n        dragSurfaceControl = dragging && isValidCaptureSurface(dragSurface) ? dragSurface : null;\n        if (dragging) {\n            resetCaptureCircuit("drag-start");\n            beginObservationBurst();\n            observationValid = false;\n            requestStateCapture("drag-start");\n        }\n    }\n''',
'''    void setDockDragging(boolean dragging, String dragSurfaceLayerName,\n                         android.view.SurfaceControl dragSurface) {\n        boolean wasFrozen = dockDragCaptureFrozen;\n        boolean hasExcludableSurface = dragging && isValidCaptureSurface(dragSurface);\n        dockDragging = dragging;\n        dragLayerName = dragging ? dragSurfaceLayerName : null;\n        dragSurfaceControl = hasExcludableSurface ? dragSurface : null;\n        dockDragCaptureFrozen = dragging && !hasExcludableSurface;\n\n        if (dockDragCaptureFrozen) {\n            if (!wasFrozen) {\n                logI("Liquid capture frozen: Dock drag has no excludable Surface");\n                mainHandler.removeCallbacks(cancelGrace);\n                cancelPendingCaptureWork();\n                invalidate();\n            }\n            return;\n        }\n\n        if (dragging) {\n            if (wasFrozen) logI("Liquid capture resumed: Dock drag Surface became excludable");\n            resetCaptureCircuit("drag-start");\n            beginObservationBurst();\n            observationValid = false;\n            lastCaptureStartNanos = 0L;\n            requestStateCapture("drag-start");\n            return;\n        }\n\n        if (wasFrozen) {\n            logI("Liquid capture resumed: Dock drag ended");\n            resetCaptureCircuit("dock-drag-end");\n            beginObservationBurst();\n            observationValid = false;\n            lastCaptureStartNanos = 0L;\n            requestStateCapture("dock-drag-end");\n        }\n    }\n''')

patch(path,
'''        if (systemDockDragActive) return false;\n        // Screen-off/doze is a hard stop. Unlike Dock visibility, Recents does NOT bypass this.\n''',
'''        if (systemDockDragActive) return false;\n        // Ordinary DragController is safe to capture only when its moving Surface can be\n        // excluded. On this 307 build View.getSurfaceControl() is absent, so freeze instead.\n        if (dockDragCaptureFrozen) return false;\n        // Screen-off/doze is a hard stop. Unlike Dock visibility, Recents does NOT bypass this.\n''')

print('exact production patch applied')
