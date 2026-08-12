from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_count(path: str, old: str, new: str, expected: int) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{path}: expected {expected} matches, found {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


# 1) Runtime workstation config: keep normal desktop X separate from laptop All Apps X/Y.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    """        final boolean dockEnabled, dimensionsDp;\n        final float dockWidthOffset, gridHorizontalOffset, iconTopOffset, iconBottomOffset;\n""",
    """        final boolean dockEnabled, dimensionsDp;\n        final float dockWidthOffset, gridHorizontalOffset;\n        final float allAppsHorizontalOffset, allAppsVerticalOffset;\n        final float iconTopOffset, iconBottomOffset;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    """            dockWidthOffset = c.f(\"workstation_dock_width_offset\", 0);\n            gridHorizontalOffset = c.f(\"workstation_grid_horizontal_offset\", 0);\n            iconTopOffset = c.f(\"workstation_dock_icon_top_offset\", 0);\n""",
    """            dockWidthOffset = c.f(\"workstation_dock_width_offset\", 0);\n            gridHorizontalOffset = c.f(\"workstation_grid_horizontal_offset\", 0);\n            allAppsHorizontalOffset = c.f(\"workstation_all_apps_horizontal_offset\", 0);\n            allAppsVerticalOffset = c.f(\"workstation_all_apps_vertical_offset\", 0);\n            iconTopOffset = c.f(\"workstation_dock_icon_top_offset\", 0);\n""",
)

# 2) Settings UI: independent All Apps offsets; rename the existing desktop offset for clarity.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    """private val workstationSpecs = listOf(\n    IntSpec(\"workstation_dock_width_offset\", \"工作台 Dock 长度偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_grid_horizontal_offset\", \"工作台布局水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_dock_icon_top_offset\", \"工作台图标上间距\", 0, -48, 48, \"dp\"),\n    IntSpec(\"workstation_dock_icon_bottom_offset\", \"工作台图标下间距\", 0, -48, 48, \"dp\"),\n)\n""",
    """private val workstationSpecs = listOf(\n    IntSpec(\"workstation_dock_width_offset\", \"工作台 Dock 长度偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_grid_horizontal_offset\", \"工作台桌面水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_horizontal_offset\", \"工作台所有应用水平偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_all_apps_vertical_offset\", \"工作台所有应用垂直偏移\", 0, -240, 240, \"dp\"),\n    IntSpec(\"workstation_dock_icon_top_offset\", \"工作台图标上间距\", 0, -48, 48, \"dp\"),\n    IntSpec(\"workstation_dock_icon_bottom_offset\", \"工作台图标下间距\", 0, -48, 48, \"dp\"),\n)\n""",
)

# 3) Import/export the new workstation All Apps offsets.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java",
    """        j.put(\"workstation_grid_horizontal_offset\", readDpPreference(sp,\n                \"workstation_grid_horizontal_offset\"));\n        j.put(\"workstation_dock_icon_top_offset\", readDpPreference(sp,\n""",
    """        j.put(\"workstation_grid_horizontal_offset\", readDpPreference(sp,\n                \"workstation_grid_horizontal_offset\"));\n        j.put(\"workstation_all_apps_horizontal_offset\", readDpPreference(sp,\n                \"workstation_all_apps_horizontal_offset\"));\n        j.put(\"workstation_all_apps_vertical_offset\", readDpPreference(sp,\n                \"workstation_all_apps_vertical_offset\"));\n        j.put(\"workstation_dock_icon_top_offset\", readDpPreference(sp,\n""",
)
replace_count(
    "src/main/java/com/hellovoid/liquiddock/SettingsActivity.java",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
    """            \"workstation_dock_width_offset\", \"workstation_grid_horizontal_offset\",\n            \"workstation_all_apps_horizontal_offset\", \"workstation_all_apps_vertical_offset\",\n            \"workstation_dock_icon_top_offset\", \"workstation_dock_icon_bottom_offset\",\n""",
    2,
)

# 4) HomeGridHook: DEX-backed All Apps scoping and real translation semantics.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """    private static volatile boolean workstationMode;\n    private static int workstationHorizontalOffset;\n""",
    """    private static volatile boolean workstationMode;\n    private static int workstationHorizontalOffset;\n    private static int workstationAllAppsHorizontalOffset;\n    private static int workstationAllAppsVerticalOffset;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """    static void setWorkstationHorizontalOffset(int offset) {\n        workstationHorizontalOffset = offset;\n    }\n\n""",
    """    static void setWorkstationHorizontalOffset(int offset) {\n        workstationHorizontalOffset = offset;\n    }\n\n    static void setWorkstationAllAppsOffsets(int horizontal, int vertical) {\n        workstationAllAppsHorizontalOffset = horizontal;\n        workstationAllAppsVerticalOffset = vertical;\n    }\n\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """            int baseWidthGap = HookUtil.getIntField(cellLayout, \"mWidthGap\");\n            int baseLeft = configLeft\n                - Math.max(0, countX - 1) * (baseWidthGap / 2);\n            int baseHeightGap = 1;\n            if (baseCell <= 0) return;\n\n            int width = layout.getWidth();\n            int height = layout.getHeight();\n            if (width <= 0 || height <= 0) return;\n\n            // With the 8x4 count hooks MIUI can retain the opposite orientation's\n            // GridConfig after rotation. Use the established Pad defaults as the\n            // orientation-specific baseline; native 6x4 continues using MIUI's\n            // live GridConfig values.\n            if (grid8x4Enabled) {\n                int dockBarHeight = 0;\n                try {\n                    dockBarHeight = Math.max(0, (Integer) HookUtil.invoke(\n                            config, \"getDockBarHeight\"));\n                } catch (Throwable ignored) {}\n                int contentHeight = Math.max(baseCell * countY,\n                        height - Math.min(height, dockBarHeight));\n                baseWidthGap = 0;\n                baseHeightGap = Math.max(1, Math.round(density));\n                baseLeft = Math.max(0, (width - baseCell * countX) / 2);\n                baseTop = Math.max(0, (contentHeight - baseCell * countY\n                    - baseHeightGap * Math.max(0, countY - 1)) / 2);\n            }\n\n            int baseRight = width - (baseLeft + baseCell * countX\n                + baseWidthGap * Math.max(0, countX - 1));\n            int baseBottom = height - (baseTop + baseCell * countY\n                + baseHeightGap * Math.max(0, countY - 1));\n            boolean workstation = workstationMode || MainHook.isWorkstationMode();\n            int left = baseLeft + (workstation ? workstationHorizontalOffset\n                    : (portrait ? portraitLeft : landscapeLeft));\n            int right = baseRight + (workstation ? workstationHorizontalOffset\n                    : (portrait ? portraitRight : landscapeRight));\n            int top = baseTop + (workstation ? 0 : (portrait ? portraitTop : landscapeTop));\n            int bottom = baseBottom + (workstation ? 0 : (portrait ? portraitBottom : landscapeBottom));\n            int rowGap = baseHeightGap + (workstation ? 0\n                    : (portrait ? portraitRowGap : landscapeRowGap));\n""",
    """            int baseWidthGap = HookUtil.getIntField(cellLayout, \"mWidthGap\");\n            int baseLeft = configLeft\n                - Math.max(0, countX - 1) * (baseWidthGap / 2);\n            int baseHeightGap = 1;\n            if (baseCell <= 0) return;\n\n            int width = layout.getWidth();\n            int height = layout.getHeight();\n            if (width <= 0 || height <= 0) return;\n\n            boolean workstation = workstationMode || MainHook.isWorkstationMode();\n            boolean workstationAllApps = workstation && isLaptopAllApps(cellLayout);\n\n            // Laptop All Apps has its own GridType/GridConfig.  The system DEX identifies\n            // it through CellLayout.isInLapTopAllApps() and\n            // GRID_TYPE_IN_ALL_APPS_WORKSPACE. Preserve that dedicated geometry instead\n            // of replacing it with the normal Workspace centering formula. The 8x4 count\n            // still applies; cellSize below shrinks as needed to stay inside this layout.\n            if (workstationAllApps) {\n                baseLeft = Math.max(0, configLeft);\n                baseTop = Math.max(0, baseTop);\n                baseWidthGap = Math.max(0, HookUtil.getIntField(cellLayout, \"mWidthGap\"));\n                baseHeightGap = Math.max(0, HookUtil.getIntField(cellLayout, \"mHeightGap\"));\n            } else if (grid8x4Enabled) {\n                // With the 8x4 count hooks MIUI can retain the opposite orientation's\n                // GridConfig after rotation. Use the established Pad defaults as the\n                // orientation-specific baseline for normal Workspace pages.\n                int dockBarHeight = 0;\n                try {\n                    dockBarHeight = Math.max(0, (Integer) HookUtil.invoke(\n                            config, \"getDockBarHeight\"));\n                } catch (Throwable ignored) {}\n                int contentHeight = Math.max(baseCell * countY,\n                        height - Math.min(height, dockBarHeight));\n                baseWidthGap = 0;\n                baseHeightGap = Math.max(1, Math.round(density));\n                baseLeft = Math.max(0, (width - baseCell * countX) / 2);\n                baseTop = Math.max(0, (contentHeight - baseCell * countY\n                    - baseHeightGap * Math.max(0, countY - 1)) / 2);\n            }\n\n            int baseRight = width - (baseLeft + baseCell * countX\n                + baseWidthGap * Math.max(0, countX - 1));\n            int baseBottom = height - (baseTop + baseCell * countY\n                + baseHeightGap * Math.max(0, countY - 1));\n            if (workstationAllApps) {\n                // The stock All Apps config is sized for its own icon/search/indicator\n                // stack.  With 8 columns the old cell size can make the derived far\n                // margins negative; keep the native near margins and let cellSize shrink.\n                baseRight = Math.max(0, baseRight);\n                baseBottom = Math.max(0, baseBottom);\n            }\n\n            int workstationX = workstationAllApps\n                    ? workstationAllAppsHorizontalOffset : workstationHorizontalOffset;\n            int workstationY = workstationAllApps ? workstationAllAppsVerticalOffset : 0;\n            if (workstation) {\n                // Offsets are translations, not symmetric insets. Clamp them against the\n                // native margins so the adjusted grid can never be pushed off-screen.\n                workstationX = Math.max(-baseLeft, Math.min(baseRight, workstationX));\n                workstationY = Math.max(-baseTop, Math.min(baseBottom, workstationY));\n            }\n            int left = baseLeft + (workstation ? workstationX\n                    : (portrait ? portraitLeft : landscapeLeft));\n            int right = baseRight + (workstation ? -workstationX\n                    : (portrait ? portraitRight : landscapeRight));\n            int top = baseTop + (workstation ? workstationY\n                    : (portrait ? portraitTop : landscapeTop));\n            int bottom = baseBottom + (workstation ? -workstationY\n                    : (portrait ? portraitBottom : landscapeBottom));\n            int rowGap = baseHeightGap + (workstation ? 0\n                    : (portrait ? portraitRowGap : landscapeRowGap));\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java",
    """    private static void installRotationRefresh(ClassLoader classLoader) {\n""",
    """    private static boolean isLaptopAllApps(Object cellLayout) {\n        try {\n            Object result = HookUtil.invoke(cellLayout, \"isInLapTopAllApps\");\n            return Boolean.TRUE.equals(result);\n        } catch (Throwable ignored) {\n            return false;\n        }\n    }\n\n    private static void installRotationRefresh(ClassLoader classLoader) {\n""",
)

# 5) MainHook: pass the dedicated offsets and isolate normal Dock background in workstation.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """        HomeGridHook.setWorkstationHorizontalOffset(Math.round(\n                config.workstation.gridHorizontalOffset * gridScale));\n\n""",
    """        HomeGridHook.setWorkstationHorizontalOffset(Math.round(\n                config.workstation.gridHorizontalOffset * gridScale));\n        HomeGridHook.setWorkstationAllAppsOffsets(\n                Math.round(config.workstation.allAppsHorizontalOffset * gridScale),\n                Math.round(config.workstation.allAppsVerticalOffset * gridScale));\n\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """                            if (workstationMode) {\n                                if (liquidGlassView != null) {\n                                    liquidGlassView.setWorkstationMode(true);\n                                    syncAll(oldBg);\n                                }\n                                return r;\n                            }\n""",
    """                            if (workstationMode) {\n                                // Laptop/workstation Dock has its own DockContainerView\n                                // background. Never leave the normal HotSeats background or\n                                // LiquidDock glass visible underneath it.\n                                oldBg.setAlpha(0f);\n                                if (liquidGlassView != null)\n                                    liquidGlassView.setWorkstationMode(true);\n                                return r;\n                            }\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    """        if (!enabled) {\n            if (oldBg != null) oldBg.post(() -> {\n                if (liquidGlassView != null) liquidGlassView.setWorkstationMode(false);\n                if (overlay != null) overlay.setVisibility(View.VISIBLE);\n                if (shadowView != null) shadowView.setVisibility(View.VISIBLE);\n                syncAll(oldBg);\n            });\n            return;\n        }\n        if (oldBg != null) oldBg.post(() -> {\n            if (oldBg != null) oldBg.setAlpha(1f);\n            if (overlay != null) overlay.setVisibility(View.GONE);\n            if (shadowView != null) shadowView.setVisibility(View.GONE);\n            if (liquidGlassView != null) {\n                liquidGlassView.setVisibility(View.VISIBLE);\n                liquidGlassView.setWorkstationMode(true);\n            }\n        });\n""",
    """        if (!enabled) {\n            if (oldBg != null) oldBg.post(() -> {\n                if (liquidGlassView != null) liquidGlassView.setWorkstationMode(false);\n                else oldBg.setAlpha(1f);\n                if (overlay != null) overlay.setVisibility(View.VISIBLE);\n                if (shadowView != null) shadowView.setVisibility(View.VISIBLE);\n                syncAll(oldBg);\n            });\n            return;\n        }\n        if (oldBg != null) oldBg.post(() -> {\n            // The workstation Dock background is rendered by its independent laptop\n            // DockContainerView. Suppress every normal-mode background layer here.\n            oldBg.setAlpha(0f);\n            if (overlay != null) overlay.setVisibility(View.GONE);\n            if (shadowView != null) shadowView.setVisibility(View.GONE);\n            if (liquidGlassView != null) liquidGlassView.setWorkstationMode(true);\n        });\n""",
)

# 6) Capture scene state: workstation is a capture suspension, not a wallpaper source.
replace_count(
    "src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java",
    "workstationWallpaperOnly",
    "workstationSuspended",
    6,
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java",
    """        if (workstationSuspended) return CaptureScene.HOME;\n        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;\n""",
    """        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java",
    """    void setWorkstationSuspended(boolean enabled, long nowNanos,\n                                     boolean recentsVisible, boolean lifecycleKnown,\n                                     boolean launcherResumed) {\n        workstationSuspended = enabled;\n""",
    """    void setWorkstationSuspended(boolean enabled, long nowNanos,\n                                     boolean recentsVisible, boolean lifecycleKnown,\n                                     boolean launcherResumed) {\n        workstationSuspended = enabled;\n""",
)

# 7) Liquid glass: workstation mode means no normal Dock capture/view at all.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    private boolean isCaptureAllowed() {\n        // DeviceConfig mirrors notification shade and control-center expansion from SystemUI.\n""",
    """    private boolean isCaptureAllowed() {\n        // Workstation/laptop Dock has an independent background. The normal Dock glass\n        // must not capture or render while that container is active.\n        if (sceneState.workstationSuspended()) return false;\n        // DeviceConfig mirrors notification shade and control-center expansion from SystemUI.\n""",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    """    /** Workstation Dock is stationary: capture only the wallpaper layer and reuse the\n     * mode-2 cache. Geometry changes are served by recropping that cached strip. */\n    void setWorkstationMode(boolean enabled) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setWorkstationMode(enabled));\n            return;\n        }\n        if (sceneState.workstationWallpaperOnly() == enabled) return;\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        sceneState.setWorkstationWallpaperOnly(enabled, System.nanoTime(), isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n        if (enabled) {\n            Bitmap old = capture;\n            capture = null;\n            captureShader = null;\n            if (old != null && !old.isRecycled()) old.recycle();\n            if (nativeBackgroundHiddenByGlass) {\n                geometrySource.setAlpha(1f);\n                nativeBackgroundHiddenByGlass = false;\n                clearSystemMaterial();\n            }\n            invalidate();\n        }\n        sourceDirty = true;\n        requestStateCapture(enabled ? \"workstation-wallpaper-once\" : \"workstation-exit\");\n    }\n""",
    """    /** Workstation/laptop mode owns a separate DockContainerView background. Suspend\n     *  LiquidDock completely instead of treating workstation as another wallpaper scene. */\n    void setWorkstationMode(boolean enabled) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setWorkstationMode(enabled));\n            return;\n        }\n        if (sceneState.workstationSuspended() == enabled) return;\n        cancelPendingCaptureWork();\n        captureGeneration++;\n        appVisualSignatureValid = false;\n        dynamicAppActiveUntilNanos = 0L;\n        sceneState.setWorkstationSuspended(enabled, System.nanoTime(), isRecentsVisible(),\n                launcherLifecycleKnown, launcherResumed);\n\n        Bitmap old = capture;\n        capture = null;\n        captureShader = null;\n        if (old != null && !old.isRecycled()) old.recycle();\n        clearSystemMaterial();\n\n        if (enabled) {\n            // Hide both normal-mode layers: the stock HotSeats background and this glass.\n            // The laptop DockContainerView remains visible and renders its own background.\n            geometrySource.setAlpha(0f);\n            nativeBackgroundHiddenByGlass = true;\n            setVisibility(INVISIBLE);\n            sourceDirty = false;\n            invalidate();\n            return;\n        }\n\n        // Return to normal mode safely: show the stock background until the first fresh\n        // LiquidDock frame is installed, then installCapture() will hide it again.\n        setVisibility(VISIBLE);\n        geometrySource.setAlpha(1f);\n        nativeBackgroundHiddenByGlass = false;\n        sourceDirty = true;\n        lastCaptureStartNanos = 0L;\n        requestStateCapture(\"workstation-exit\");\n    }\n""",
)

print("workstation/laptop All Apps patch applied")
