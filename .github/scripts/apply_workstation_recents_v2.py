from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_count(path: str, old: str, new: str, expected: int) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} matches, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# 1) Runtime configuration: independent landscape/portrait All Apps offsets.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    """        final float dockWidthOffset, gridHorizontalOffset;\n        final float allAppsHorizontalOffset, allAppsVerticalOffset;\n        final float iconTopOffset, iconBottomOffset;\n""",
    """        final float dockWidthOffset, gridHorizontalOffset;\n        final float allAppsLandscapeHorizontalOffset, allAppsLandscapeVerticalOffset;\n        final float allAppsPortraitHorizontalOffset, allAppsPortraitVerticalOffset;\n        final float iconTopOffset, iconBottomOffset;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    """            dockWidthOffset = c.f(\"workstation_dock_width_offset\", 0);\n            gridHorizontalOffset = c.f(\"workstation_grid_horizontal_offset\", 0);\n            allAppsHorizontalOffset = c.f(\"workstation_all_apps_horizontal_offset\", 0);\n            allAppsVerticalOffset = c.f(\"workstation_all_apps_vertical_offset\", 0);\n            iconTopOffset = c.f(\"workstation_dock_icon_top_offset\", 0);\n""",
    """            dockWidthOffset = c.f(\"workstation_dock_width_offset\", 0);\n            gridHorizontalOffset = c.f(\"workstation_grid_horizontal_offset\", 0);\n            // Preserve existing workstation All Apps tuning as the fallback for both\n            // orientations; new installs can tune landscape and portrait independently.\n            float legacyAllAppsX = c.f(\"workstation_all_apps_horizontal_offset\", 0);\n            float legacyAllAppsY = c.f(\"workstation_all_apps_vertical_offset\", 0);\n            allAppsLandscapeHorizontalOffset = c.f(\n                    \"workstation_all_apps_landscape_horizontal_offset\", legacyAllAppsX);\n            allAppsLandscapeVerticalOffset = c.f(\n                    \"workstation_all_apps_landscape_vertical_offset\", legacyAllAppsY);\n            allAppsPortraitHorizontalOffset = c.f(\n                    \"workstation_all_apps_portrait_horizontal_offset\", legacyAllAppsX);\n            allAppsPortraitVerticalOffset = c.f(\n                    \"workstation_all_apps_portrait_vertical_offset\", legacyAllAppsY);\n            iconTopOffset = c.f(\"workstation_dock_icon_top_offset\", 0);\n""",
)

# 2) Settings UI: four orientation-specific controls.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    """    \"workstation_grid_horizontal_offset\" -> \"单独调整工作台 8 列图标区域的左右距离，不继承普通桌面偏移\"\n    \"workstation_dock_icon_top_offset\" -> \"调整工作台 Dock 图标与容器顶部之间的距离\"\n""",
    """    \"workstation_grid_horizontal_offset\" -> \"单独调整工作台 8 列图标区域的左右距离，不继承普通桌面偏移\"\n    \"workstation_all_apps_landscape_horizontal_offset\" -> \"仅调整工作台所有应用横屏图标区的水平位置\"\n    \"workstation_all_apps_landscape_vertical_offset\" -> \"仅调整工作台所有应用横屏图标区的垂直位置\"\n    \"workstation_all_apps_portrait_horizontal_offset\" -> \"仅调整工作台所有应用竖屏图标区的水平位置\"\n    \"workstation_all_apps_portrait_vertical_offset\" -> \"仅调整工作台所有应用竖屏图标区的垂直位置\"\n    \"workstation_dock_icon_top_offset\" -> \"调整工作台 Dock 图标与容器顶部之间的距离\"\n""",
)
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    """private val workstationSpecs = listOf(\n    IntSpec(\"workstation_dock_width_offset\", \"工作台 Dock 长度偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_grid_horizontal_offset\", \"工作台桌面水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_horizontal_offset\", \"工作台所有应用水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_vertical_offset\", \"工作台所有应用垂直偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_dock_icon_top_offset\", \"工作台图标上间距\", 0, -48, 48, \"dp\"),\n    IntSpec(\"workstation_dock_icon_bottom_offset\", \"工作台图标下间距\", 0, -48, 48, \"dp\"),\n)\n""",
    """private val workstationSpecs = listOf(\n    IntSpec(\"workstation_dock_width_offset\", \"工作台 Dock 长度偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_grid_horizontal_offset\", \"工作台桌面水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_landscape_horizontal_offset\", \"所有应用 · 横屏水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_landscape_vertical_offset\", \"所有应用 · 横屏垂直偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_portrait_horizontal_offset\", \"所有应用 · 竖屏水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_portrait_vertical_offset\", \"所有应用 · 竖屏垂直偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_dock_icon_top_offset\", \"工作台图标上间距\", 0, -48, 48, \"dp\"),\n    IntSpec(\"workstation_dock_icon_bottom_offset\", \"工作台图标下间距\", 0, -48, 48, \"dp\"),\n)\n""",
)

# 3) Settings import/export: export new keys; retain legacy import compatibility.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java",
    """        j.put(\"workstation_all_apps_horizontal_offset\", readDpPreference(sp,\n                \"workstation_all_apps_horizontal_offset\"));\n        j.put(\"workstation_all_apps_vertical_offset\", readDpPreference(sp,\n                \"workstation_all_apps_vertical_offset\"));\n""",
    """        j.put(\"workstation_all_apps_landscape_horizontal_offset\", readDpPreference(sp,\n                \"workstation_all_apps_landscape_horizontal_offset\"));\n        j.put(\"workstation_all_apps_landscape_vertical_offset\", readDpPreference(sp,\n                \"workstation_all_apps_landscape_vertical_offset\"));\n        j.put(\"workstation_all_apps_portrait_horizontal_offset\", readDpPreference(sp,\n                \"workstation_all_apps_portrait_horizontal_offset\"));\n        j.put(\"workstation_all_apps_portrait_vertical_offset\", readDpPreference(sp,\n                \"workstation_all_apps_portrait_vertical_offset\"));\n""",
)
# The first dpKeys array is export normalization; use only the new keys there.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_all_apps_horizontal_offset\", \"workstation_all_apps_vertical_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_all_apps_landscape_horizontal_offset\",\n            \"workstation_all_apps_landscape_vertical_offset\",\n            \"workstation_all_apps_portrait_horizontal_offset\",\n            \"workstation_all_apps_portrait_vertical_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
)
# The second dpKeys array is import; include old keys so older exports remain readable.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_all_apps_horizontal_offset\", \"workstation_all_apps_vertical_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_all_apps_landscape_horizontal_offset\",\n            \"workstation_all_apps_landscape_vertical_offset\",\n            \"workstation_all_apps_portrait_horizontal_offset\",\n            \"workstation_all_apps_portrait_vertical_offset\",\n            \"workstation_all_apps_horizontal_offset\", \"workstation_all_apps_vertical_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
)

# 4) HomeGridHook: choose All Apps offsets by actual orientation.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """    private static int workstationHorizontalOffset;\n    private static int workstationAllAppsHorizontalOffset;\n    private static int workstationAllAppsVerticalOffset;\n""",
    """    private static int workstationHorizontalOffset;\n    private static int workstationAllAppsLandscapeHorizontalOffset;\n    private static int workstationAllAppsLandscapeVerticalOffset;\n    private static int workstationAllAppsPortraitHorizontalOffset;\n    private static int workstationAllAppsPortraitVerticalOffset;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """    static void setWorkstationAllAppsOffsets(int horizontal, int vertical) {\n        workstationAllAppsHorizontalOffset = horizontal;\n        workstationAllAppsVerticalOffset = vertical;\n    }\n""",
    """    static void setWorkstationAllAppsOffsets(int landscapeHorizontal, int landscapeVertical,\n                                                    int portraitHorizontal, int portraitVertical) {\n        workstationAllAppsLandscapeHorizontalOffset = landscapeHorizontal;\n        workstationAllAppsLandscapeVerticalOffset = landscapeVertical;\n        workstationAllAppsPortraitHorizontalOffset = portraitHorizontal;\n        workstationAllAppsPortraitVerticalOffset = portraitVertical;\n    }\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """            int workstationX = workstationAllApps\n                    ? workstationAllAppsHorizontalOffset : workstationHorizontalOffset;\n            int workstationY = workstationAllApps ? workstationAllAppsVerticalOffset : 0;\n""",
    """            int workstationX = workstationAllApps\n                    ? (portrait ? workstationAllAppsPortraitHorizontalOffset\n                            : workstationAllAppsLandscapeHorizontalOffset)\n                    : workstationHorizontalOffset;\n            int workstationY = workstationAllApps\n                    ? (portrait ? workstationAllAppsPortraitVerticalOffset\n                            : workstationAllAppsLandscapeVerticalOffset)\n                    : 0;\n""",
)

# 5) MainHook: feed four offsets and use Launcher.showOrHideRecent() as the only
# workstation live-capture entry point. DEX route:
# HotSeatsListContentAdapter -> LauncherModeController.isLaptopMode -> showOrHideRecent.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """        RecentsHapticHook.install(classLoader, () -> {\n            DockLiquidGlassView glass = liquidGlassView;\n            if (glass != null) glass.onRecentsHapticTrigger();\n        });\n""",
    """        RecentsHapticHook.install(classLoader, () -> {\n            DockLiquidGlassView glass = liquidGlassView;\n            // Laptop/workstation Recents has a dedicated button; generic gesture/haptic\n            // pre-arm must never switch its Dock to live capture.\n            if (glass != null && !workstationMode) glass.onRecentsHapticTrigger();\n        });\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """        HomeGridHook.setWorkstationAllAppsOffsets(\n                Math.round(config.workstation.allAppsHorizontalOffset * gridScale),\n                Math.round(config.workstation.allAppsVerticalOffset * gridScale));\n""",
    """        HomeGridHook.setWorkstationAllAppsOffsets(\n                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * gridScale),\n                Math.round(config.workstation.allAppsLandscapeVerticalOffset * gridScale),\n                Math.round(config.workstation.allAppsPortraitHorizontalOffset * gridScale),\n                Math.round(config.workstation.allAppsPortraitVerticalOffset * gridScale));\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """        hookDockGestureTarget(cl, \"GestureToHome\", \"HOME\");\n        hookDockGestureTarget(cl, \"GestureToApp\", \"APP\");\n        hookDockGestureTarget(cl, \"GestureToRecent\", \"RECENTS\");\n\n        // Configuration changes (rotation)\n""",
    """        hookDockGestureTarget(cl, \"GestureToHome\", \"HOME\");\n        hookDockGestureTarget(cl, \"GestureToApp\", \"APP\");\n        hookDockGestureTarget(cl, \"GestureToRecent\", \"RECENTS\");\n\n        // Workstation Recents is entered from the dedicated Dock button. The system DEX\n        // routes HotSeatsListContentAdapter's laptop branch to Launcher.showOrHideRecent().\n        // Hook before the original call so the very first transition frame is mode-1 live.\n        try {\n            HookUtil.hookMethod(launcherClass, \"showOrHideRecent\", new Class<?>[0],\n                    chain -> {\n                        DockLiquidGlassView glass = liquidGlassView;\n                        if (workstationMode && glass != null) {\n                            glass.onWorkstationRecentsButton();\n                            log(\"[DC] workstation Recents button boundary\");\n                        }\n                        return chain.proceed(chain.getArgs().toArray(new Object[0]));\n                    });\n        } catch (Throwable e) {\n            log(\"[DC] workstation showOrHideRecent hook unavailable: \" + e);\n        }\n\n        // Configuration changes (rotation)\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """                    DockLiquidGlassView glass = liquidGlassView;\n                    if (glass != null) glass.setGestureCaptureTarget(target);\n                    log(\"[DC] liquid gesture target=\" + target);\n""",
    """                    DockLiquidGlassView glass = liquidGlassView;\n                    if (glass != null && !workstationMode)\n                        glass.setGestureCaptureTarget(target);\n                    if (!workstationMode) log(\"[DC] liquid gesture target=\" + target);\n""",
)

# 6) DockLiquidGlassView: workstation stays on its independent static background. Only
# the exact Recents button temporarily unsuspends this glass and forces RECENTS/mode-1.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    private float gestureDownRawY = Float.NaN;\n    private float recentsPrearmDistancePx;\n    private boolean recentsPrearmed;\n    private long lastCaptureStartNanos;\n""",
    """    private float gestureDownRawY = Float.NaN;\n    private float recentsPrearmDistancePx;\n    private boolean recentsPrearmed;\n    // Workstation owns a separate Dock background. It remains static/wallpaper-backed;\n    // this normal LiquidDock view is activated only for the exact Recents button path.\n    private boolean workstationMode;\n    private boolean workstationRecentsActive;\n    private boolean workstationRecentsWasVisible;\n    private long lastCaptureStartNanos;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    void onDockGestureMotion(int action, float rawY) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> onDockGestureMotion(action, rawY));\n            return;\n        }\n        if (action == android.view.MotionEvent.ACTION_DOWN) {\n""",
    """    void onDockGestureMotion(int action, float rawY) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> onDockGestureMotion(action, rawY));\n            return;\n        }\n        // Workstation has no swipe-to-Recents path; only Launcher.showOrHideRecent()\n        // may activate live capture there.\n        if (workstationMode) return;\n        if (action == android.view.MotionEvent.ACTION_DOWN) {\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    void onRecentsHapticTrigger() {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(this::onRecentsHapticTrigger);\n            return;\n        }\n        prearmRecentsCapture(\"recents-prearm-haptic\");\n""",
    """    void onRecentsHapticTrigger() {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(this::onRecentsHapticTrigger);\n            return;\n        }\n        if (workstationMode) return;\n        prearmRecentsCapture(\"recents-prearm-haptic\");\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    void setGestureCaptureTarget(String target) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setGestureCaptureTarget(target));\n            return;\n        }\n        sceneState.setGestureTarget(target, System.nanoTime());\n""",
    """    void setGestureCaptureTarget(String target) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setGestureCaptureTarget(target));\n            return;\n        }\n        if (workstationMode) return;\n        sceneState.setGestureTarget(target, System.nanoTime());\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    private void updateDesiredScene() {\n        CaptureScene prev = sceneState.desired();\n""",
    """    private void updateDesiredScene() {\n        // Visibility only closes a workstation Recents session; it never opens one.\n        // Opening is owned exclusively by the exact showOrHideRecent button hook.\n        if (workstationMode && workstationRecentsActive) {\n            boolean visible = isRecentsVisible();\n            if (visible) {\n                workstationRecentsWasVisible = true;\n            } else if (workstationRecentsWasVisible) {\n                suspendWorkstationGlass(\"workstation-recents-hidden\");\n                return;\n            }\n        }\n        CaptureScene prev = sceneState.desired();\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    /** Workstation/laptop mode owns a separate DockContainerView background. Suspend\n     *  LiquidDock completely instead of treating workstation as another wallpaper scene. */\n    void setWorkstationMode(boolean enabled) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setWorkstationMode(enabled));\n            return;\n        }\n        if (sceneState.workstationSuspended() == enabled) return;\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        sceneState.setWorkstationSuspended(enabled, System.nanoTime(), isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n\n        Bitmap old = capture;\n        capture = null;\n        captureShader = null;\n        if (old != null && !old.isRecycled()) old.recycle();\n        clearSystemMaterial();\n\n        if (enabled) {\n            // Hide both normal-mode layers: the stock HotSeats background and this glass.\n            // The laptop DockContainerView remains visible and renders its own background.\n            geometrySource.setAlpha(0f);\n            nativeBackgroundHiddenByGlass = true;\n            setVisibility(INVISIBLE);\n            sourceDirty = false;\n            invalidate();\n            return;\n        }\n\n        // Return to normal mode safely: show the stock background until the first fresh\n        // LiquidDock frame is installed, then installCapture() will hide it again.\n        setVisibility(VISIBLE);\n        geometrySource.setAlpha(1f);\n        nativeBackgroundHiddenByGlass = false;\n        sourceDirty = true;\n        lastCaptureStartNanos = 0L;\n        requestStateCapture(\"workstation-exit\");\n    }\n""",
    """    /** Workstation/laptop mode owns a separate DockContainerView background. Keep\n     *  normal LiquidDock suspended there; only the exact Recents button temporarily\n     *  activates live mode-1 capture for the multitasking transition/view. */\n    void setWorkstationMode(boolean enabled) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setWorkstationMode(enabled));\n            return;\n        }\n        if (workstationMode == enabled) return;\n        workstationMode = enabled;\n        if (enabled) {\n            suspendWorkstationGlass(\"workstation-enter\");\n            return;\n        }\n\n        workstationRecentsActive = false;\n        workstationRecentsWasVisible = false;\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        sceneState.setWorkstationSuspended(false, System.nanoTime(), isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n        setVisibility(VISIBLE);\n        geometrySource.setAlpha(1f);\n        nativeBackgroundHiddenByGlass = false;\n        sourceDirty = true;\n        observationValid = false;\n        lastCaptureStartNanos = 0L;\n        requestStateCapture(\"workstation-exit\");\n    }\n\n    /** Called before Launcher.showOrHideRecent() only in workstation mode. */\n    void onWorkstationRecentsButton() {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(this::onWorkstationRecentsButton);\n            return;\n        }\n        if (!workstationMode) return;\n        // A second press is the exit toggle. Keep live capture through the closing\n        // animation; recents visibility dropping will suspend the glass afterward.\n        if (workstationRecentsActive) {\n            logI(\"workstation Recents exit requested; waiting for panel hide\");\n            return;\n        }\n\n        workstationRecentsActive = true;\n        workstationRecentsWasVisible = false;\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        long now = System.nanoTime();\n        sceneState.setWorkstationSuspended(false, now, isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n        // Exact button boundary is authoritative long enough for the overview animation\n        // to become visible; once visible, normal Recents visibility owns the scene.\n        sceneState.setGestureTarget(\"RECENTS\", now);\n        setVisibility(VISIBLE);\n        // Never reveal the normal HotSeats background in workstation. The independent\n        // DockContainerView remains underneath; this glass draws only the live Recents frame.\n        geometrySource.setAlpha(0f);\n        nativeBackgroundHiddenByGlass = true;\n        applySelectedBlurBackend();\n        sourceDirty = true;\n        observationValid = false;\n        lastCaptureStartNanos = 0L;\n        requestStateCapture(\"workstation-recents-button\");\n\n        // Failed/blocked transition safety: if the panel never becomes visible, do not\n        // leave the normal glass active over the workstation Dock indefinitely.\n        mainHandler.postDelayed(() -> {\n            if (!workstationMode || !workstationRecentsActive) return;\n            if (isRecentsVisible()) {\n                workstationRecentsWasVisible = true;\n                return;\n            }\n            if (!workstationRecentsWasVisible)\n                suspendWorkstationGlass(\"workstation-recents-timeout\");\n        }, 1800L);\n    }\n\n    private void suspendWorkstationGlass(String reason) {\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        workstationRecentsActive = false;\n        workstationRecentsWasVisible = false;\n        sceneState.setWorkstationSuspended(true, System.nanoTime(), isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n\n        Bitmap old = capture;\n        capture = null;\n        captureShader = null;\n        if (old != null && !old.isRecycled()) old.recycle();\n        clearSystemMaterial();\n        geometrySource.setAlpha(0f);\n        nativeBackgroundHiddenByGlass = true;\n        setVisibility(INVISIBLE);\n        sourceDirty = false;\n        invalidate();\n        logI(\"workstation glass suspended reason=\" + reason);\n    }\n""",
)

print("workstation orientation + exact Recents patch applied")
