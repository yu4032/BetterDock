from pathlib import Path
import re


def replace_once(path, old, new, label):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match in {path}, found {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


def write(path, text):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")

# ── Pure capture scene/source policy ──────────────────────────────────
write("src/main/java/com/hellovoid/liquiddock/CaptureScene.java", """package com.hellovoid.liquiddock;

enum CaptureScene { HOME, APP, RECENTS, ALL_APPS }
""")

write("src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java", """package com.hellovoid.liquiddock;

/** Chooses a backdrop source without ever full-display-capturing launcher-owned scenes. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY, LOCAL_LAYER }

    private CaptureSourcePolicy() {}

    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        // Recents and All Apps are rendered by stock Launcher-owned windows/surfaces.
        // Capture that root directly; if it is not available, fail closed to wallpaper.
        return localLayerAvailable ? Source.LOCAL_LAYER : Source.WALLPAPER;
    }
}
""")

write("src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java", """package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;

    CaptureScene desired() { return desired; }
    long revision() { return revision; }
    boolean workstationSuspended() { return workstationSuspended; }
    boolean allAppsActive() { return allAppsActive; }
    boolean matches(CaptureScene scene, long expectedRevision) {
        return desired == scene && revision == expectedRevision;
    }

    void prearmRecents(long nowNanos) {
        gestureTarget = CaptureScene.RECENTS;
        gestureTargetUntilNanos = nowNanos + 700_000_000L;
        setDesired(CaptureScene.RECENTS);
    }

    void setGestureTarget(String target, long nowNanos) {
        gestureTarget = "HOME".equals(target) ? CaptureScene.HOME
                : "RECENTS".equals(target) ? CaptureScene.RECENTS : CaptureScene.APP;
        gestureTargetUntilNanos = nowNanos + 1_500_000_000L;
        setDesired(gestureTarget);
    }

    boolean gestureTargetExpired(long nowNanos) {
        return gestureTarget != null && nowNanos >= gestureTargetUntilNanos;
    }

    void clearGestureTarget() { gestureTarget = null; }

    /** Launcher focus loss proves a pending HOME target is stale: an app has actually
     * taken the foreground. Do not clear APP/RECENTS because those targets are still
     * useful before lifecycle/focus catches up. */
    boolean clearGestureTargetIfHome() {
        if (gestureTarget != CaptureScene.HOME) return false;
        gestureTarget = null;
        gestureTargetUntilNanos = 0L;
        return true;
    }

    /** Stock laptop All Apps lives in a focusable LauncherOverlayWindow.  It can make the
     * main Launcher window lose focus without an external app taking the foreground. */
    void setAllAppsActive(boolean active) {
        if (allAppsActive == active) return;
        allAppsActive = active;
        revision++;
        if (active) {
            desired = CaptureScene.ALL_APPS;
        } else if (desired == CaptureScene.ALL_APPS) {
            // The owning DockLiquidGlassView immediately refreshes against real launcher
            // lifecycle/overview state. APP is only a neutral interim value here.
            desired = CaptureScene.APP;
        }
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (recentsVisible) return CaptureScene.RECENTS;
        if (allAppsActive) return CaptureScene.ALL_APPS;
        if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
        return CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean lifecycleKnown, boolean launcherResumed) {
        CaptureScene next = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
        return setDesired(next);
    }

    void setWorkstationSuspended(boolean enabled, long nowNanos,
                                     boolean recentsVisible, boolean lifecycleKnown,
                                     boolean launcherResumed) {
        workstationSuspended = enabled;
        gestureTarget = null;
        revision++;
        desired = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
    }

    private boolean setDesired(CaptureScene next) {
        if (desired == next) return false;
        desired = next;
        revision++;
        return true;
    }
}
""")

# ── Workstation All Apps: independent top/bottom absolute spacing ─────
write("src/main/java/com/hellovoid/liquiddock/WorkstationGridMarginPolicy.java", """package com.hellovoid.liquiddock;

/** Pure workstation All Apps absolute spacing policy. */
final class WorkstationGridMarginPolicy {
    private WorkstationGridMarginPolicy() {}

    /** Returns {left, right, top, bottom}. Native asymmetric margins are intentionally ignored. */
    static int[] apply(int baseLeft, int baseRight, int baseTop, int baseBottom,
                       int horizontalSpacing, int topSpacing, int bottomSpacing) {
        int horizontal = Math.max(0, horizontalSpacing);
        int top = Math.max(0, topSpacing);
        int bottom = Math.max(0, bottomSpacing);
        return new int[]{horizontal, horizontal, top, bottom};
    }
}
""")

schema = "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"
replace_once(schema,
"""        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_VERTICAL_OFFSET = dp(
                \"workstation_all_apps_landscape_vertical_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET = dp(
                \"workstation_all_apps_portrait_horizontal_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_VERTICAL_OFFSET = dp(
                \"workstation_all_apps_portrait_vertical_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
""",
"""        // Merged vertical keys are retained for old configs/JSON only; current UI writes
        // independent top/bottom spacing keys below.
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_VERTICAL_OFFSET = dp(
                \"workstation_all_apps_landscape_vertical_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_TOP_SPACING = dp(
                \"workstation_all_apps_landscape_top_spacing\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_LANDSCAPE_BOTTOM_SPACING = dp(
                \"workstation_all_apps_landscape_bottom_spacing\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET = dp(
                \"workstation_all_apps_portrait_horizontal_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_VERTICAL_OFFSET = dp(
                \"workstation_all_apps_portrait_vertical_offset\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_TOP_SPACING = dp(
                \"workstation_all_apps_portrait_top_spacing\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> ALL_APPS_PORTRAIT_BOTTOM_SPACING = dp(
                \"workstation_all_apps_portrait_bottom_spacing\", 0, 0, 0, 0, 240,
                ConfigKey.ExportMode.ALWAYS);
""", "schema workstation spacing keys")
replace_once(schema,
"""                Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET,
""",
"""                Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET,
                Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING,
                Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING,
                Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET,
                Workstation.ALL_APPS_PORTRAIT_TOP_SPACING,
                Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING,
""", "schema all key list")

cfg = "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"
replace_once(cfg,
"""        final float dockWidthOffset, gridHorizontalOffset;
        final float allAppsLandscapeHorizontalOffset, allAppsLandscapeVerticalOffset;
        final float allAppsPortraitHorizontalOffset, allAppsPortraitVerticalOffset;
        final float iconTopOffset, iconBottomOffset;
""",
"""        final float dockWidthOffset, gridHorizontalOffset;
        final float allAppsLandscapeHorizontalOffset;
        final float allAppsLandscapeTopSpacing, allAppsLandscapeBottomSpacing;
        final float allAppsPortraitHorizontalOffset;
        final float allAppsPortraitTopSpacing, allAppsPortraitBottomSpacing;
        final float iconTopOffset, iconBottomOffset;
""", "workstation fields")
replace_once(cfg,
"""            // Preserve existing workstation All Apps tuning as the fallback for both
            // orientations; new installs can tune landscape and portrait independently.
            float legacyAllAppsX = c.f(\"workstation_all_apps_horizontal_offset\", 0);
            float legacyAllAppsY = c.f(\"workstation_all_apps_vertical_offset\", 0);
            allAppsLandscapeHorizontalOffset = c.f(
                    \"workstation_all_apps_landscape_horizontal_offset\", legacyAllAppsX);
            allAppsLandscapeVerticalOffset = c.f(
                    \"workstation_all_apps_landscape_vertical_offset\", legacyAllAppsY);
            allAppsPortraitHorizontalOffset = c.f(
                    \"workstation_all_apps_portrait_horizontal_offset\", legacyAllAppsX);
            allAppsPortraitVerticalOffset = c.f(
                    \"workstation_all_apps_portrait_vertical_offset\", legacyAllAppsY);
""",
"""            // Compatibility chain: oldest global vertical -> old per-orientation merged
            // vertical -> new independent top/bottom. Existing users keep their layout until
            // they move either new edge control.
            float legacyAllAppsX = c.f(ConfigSchema.Workstation.LEGACY_ALL_APPS_HORIZONTAL_OFFSET.name(), 0);
            float legacyAllAppsY = c.f(ConfigSchema.Workstation.LEGACY_ALL_APPS_VERTICAL_OFFSET.name(), 0);
            float mergedLandscapeY = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET.name(), legacyAllAppsY);
            float mergedPortraitY = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET.name(), legacyAllAppsY);
            allAppsLandscapeHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET.name(), legacyAllAppsX);
            allAppsLandscapeTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING.name(), mergedLandscapeY);
            allAppsLandscapeBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING.name(), mergedLandscapeY);
            allAppsPortraitHorizontalOffset = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET.name(), legacyAllAppsX);
            allAppsPortraitTopSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING.name(), mergedPortraitY);
            allAppsPortraitBottomSpacing = c.f(
                    ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING.name(), mergedPortraitY);
""", "workstation config fallback")

home = "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"
replace_once(home,
"""    private static int workstationAllAppsLandscapeHorizontalOffset;
    private static int workstationAllAppsLandscapeVerticalOffset;
    private static int workstationAllAppsPortraitHorizontalOffset;
    private static int workstationAllAppsPortraitVerticalOffset;
""",
"""    private static int workstationAllAppsLandscapeHorizontalOffset;
    private static int workstationAllAppsLandscapeTopSpacing;
    private static int workstationAllAppsLandscapeBottomSpacing;
    private static int workstationAllAppsPortraitHorizontalOffset;
    private static int workstationAllAppsPortraitTopSpacing;
    private static int workstationAllAppsPortraitBottomSpacing;
""", "HomeGrid fields")
replace_once(home,
"""    static void setWorkstationAllAppsOffsets(int landscapeHorizontal, int landscapeVertical,
                                                    int portraitHorizontal, int portraitVertical) {
        workstationAllAppsLandscapeHorizontalOffset = landscapeHorizontal;
        workstationAllAppsLandscapeVerticalOffset = landscapeVertical;
        workstationAllAppsPortraitHorizontalOffset = portraitHorizontal;
        workstationAllAppsPortraitVerticalOffset = portraitVertical;
    }
""",
"""    static void setWorkstationAllAppsOffsets(int landscapeHorizontal,
                                                    int landscapeTop, int landscapeBottom,
                                                    int portraitHorizontal,
                                                    int portraitTop, int portraitBottom) {
        workstationAllAppsLandscapeHorizontalOffset = landscapeHorizontal;
        workstationAllAppsLandscapeTopSpacing = landscapeTop;
        workstationAllAppsLandscapeBottomSpacing = landscapeBottom;
        workstationAllAppsPortraitHorizontalOffset = portraitHorizontal;
        workstationAllAppsPortraitTopSpacing = portraitTop;
        workstationAllAppsPortraitBottomSpacing = portraitBottom;
    }
""", "HomeGrid setter")
replace_once(home,
"""                int horizontalMargin = portrait
                        ? workstationAllAppsPortraitHorizontalOffset
                        : workstationAllAppsLandscapeHorizontalOffset;
                int verticalMargin = portrait
                        ? workstationAllAppsPortraitVerticalOffset
                        : workstationAllAppsLandscapeVerticalOffset;
                int[] margins = WorkstationGridMarginPolicy.apply(
                        baseLeft, baseRight, baseTop, baseBottom,
                        horizontalMargin, verticalMargin);
""",
"""                int horizontalMargin = portrait
                        ? workstationAllAppsPortraitHorizontalOffset
                        : workstationAllAppsLandscapeHorizontalOffset;
                int topMargin = portrait
                        ? workstationAllAppsPortraitTopSpacing
                        : workstationAllAppsLandscapeTopSpacing;
                int bottomMargin = portrait
                        ? workstationAllAppsPortraitBottomSpacing
                        : workstationAllAppsLandscapeBottomSpacing;
                int[] margins = WorkstationGridMarginPolicy.apply(
                        baseLeft, baseRight, baseTop, baseBottom,
                        horizontalMargin, topMargin, bottomMargin);
""", "HomeGrid policy call")

main = "src/main/java/com/hellovoid/liquiddock/MainHook.java"
replace_once(main,
"""        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeVerticalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitVerticalOffset * workstationAllAppsScale));
""",
"""        HomeGridHook.setWorkstationAllAppsOffsets(
                Math.round(config.workstation.allAppsLandscapeHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsLandscapeBottomSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitHorizontalOffset * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitTopSpacing * workstationAllAppsScale),
                Math.round(config.workstation.allAppsPortraitBottomSpacing * workstationAllAppsScale));
""", "MainHook workstation spacing")

compose = "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"
replace_once(compose,
"""    \"workstation_all_apps_landscape_horizontal_offset\" -> \"直接设置工作台所有应用横屏图标区左右间距；不叠加系统默认位置\"
    \"workstation_all_apps_landscape_vertical_offset\" -> \"直接设置工作台所有应用横屏图标区上下间距；不叠加系统默认位置\"
    \"workstation_all_apps_portrait_horizontal_offset\" -> \"直接设置工作台所有应用竖屏图标区左右间距；不叠加系统默认位置\"
    \"workstation_all_apps_portrait_vertical_offset\" -> \"直接设置工作台所有应用竖屏图标区上下间距；不叠加系统默认位置\"
""",
"""    \"workstation_all_apps_landscape_horizontal_offset\" -> \"直接设置工作台所有应用横屏图标区左右间距；不叠加系统默认位置\"
    \"workstation_all_apps_landscape_top_spacing\" -> \"直接设置工作台所有应用横屏图标区上间距；不叠加系统默认位置\"
    \"workstation_all_apps_landscape_bottom_spacing\" -> \"直接设置工作台所有应用横屏图标区下间距；不叠加系统默认位置\"
    \"workstation_all_apps_portrait_horizontal_offset\" -> \"直接设置工作台所有应用竖屏图标区左右间距；不叠加系统默认位置\"
    \"workstation_all_apps_portrait_top_spacing\" -> \"直接设置工作台所有应用竖屏图标区上间距；不叠加系统默认位置\"
    \"workstation_all_apps_portrait_bottom_spacing\" -> \"直接设置工作台所有应用竖屏图标区下间距；不叠加系统默认位置\"
""", "Compose summaries")
replace_once(compose,
"""    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, \"所有应用 · 横屏水平间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_VERTICAL_OFFSET, \"所有应用 · 横屏垂直间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, \"所有应用 · 竖屏水平间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_VERTICAL_OFFSET, \"所有应用 · 竖屏垂直间距\"),
""",
"""    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, \"所有应用 · 横屏水平间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING, \"所有应用 · 横屏上间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING, \"所有应用 · 横屏下间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, \"所有应用 · 竖屏水平间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING, \"所有应用 · 竖屏上间距\"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING, \"所有应用 · 竖屏下间距\"),
""", "Compose specs")

# ── Stock Launcher All Apps state hooks ──────────────────────────────
replace_once(main,
"""        hookOverviewStateEvent(cl, \"EnterOverviewStateEvent\", true);
        hookOverviewStateEvent(cl, \"ExitOverviewStateEvent\", false);

        // Workstation Recents is entered from the dedicated Dock button. The system DEX
""",
"""        hookOverviewStateEvent(cl, \"EnterOverviewStateEvent\", true);
        hookOverviewStateEvent(cl, \"ExitOverviewStateEvent\", false);
        installAllAppsCaptureHooks(cl);

        // Workstation Recents is entered from the dedicated Dock button. The system DEX
""", "install all apps hooks")

replace_once(main,
"""                        launcherLifecycleKnown = true;
                        launcherResumed = hasFocus;
                        log(\"[DC] liquid focus: \" + hasFocus);
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass != null) {
""",
"""                        DockLiquidGlassView glass = liquidGlassView;
                        // Stock laptop All Apps opens a focusable LauncherOverlayWindow named
                        // \"Laptop overlay\". That focus transfer is still Launcher-owned and
                        // must not be classified as an external APP scene.
                        if (glass != null && glass.isAllAppsActive()) {
                            log(\"[DC] liquid focus ignored while stock All Apps overlay owns focus: \" + hasFocus);
                            return r;
                        }
                        launcherLifecycleKnown = true;
                        launcherResumed = hasFocus;
                        log(\"[DC] liquid focus: \" + hasFocus);
                        if (glass != null) {
""", "focus all apps guard")

marker = """    private static void hookDockGestureTarget(ClassLoader cl, String eventName, String target) {
"""
helper = """    private static void installAllAppsCaptureHooks(ClassLoader cl) {
        // Stock laptop/workstation All Apps lives in LauncherOverlayWindow(\"Laptop overlay\")
        // and calls enableFocus(true). Mark the launcher-owned scene BEFORE the original call
        // so nested onWindowFocusChanged(false) cannot be mistaken for an external app.
        try {
            Class<?> laptop = Class.forName(
                    \"com.miui.home.launcher.laptop.AllAppsController\", false, cl);
            HookUtil.hookMethod(laptop, \"showAllApps\", new Class<?>[]{boolean.class},
                    chain -> {
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass != null) glass.setAllAppsActive(
                                true, resolveLaptopAllAppsCaptureRoot(chain.getThisObject()));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = liquidGlassView;
                        if (glass != null) glass.setAllAppsActive(
                                true, resolveLaptopAllAppsCaptureRoot(chain.getThisObject()));
                        return result;
                    });
            HookUtil.hookMethod(laptop, \"closeAllApps\", new Class<?>[]{boolean.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = liquidGlassView;
                        if (glass != null) glass.setAllAppsActive(false, null);
                        return result;
                    });
            log(\"[DC] stock laptop All Apps capture state hooked\");
        } catch (Throwable e) {
            log(\"[DC] stock laptop All Apps capture hook unavailable: \" + e);
        }

        // Normal All Apps stays in the Launcher main window. Its transition controller gives
        // us the target LauncherState early enough to prevent a first-frame display capture.
        try {
            Class<?> transition = Class.forName(
                    \"com.miui.home.launcher.allapps.AllAppsTransitionController\", false, cl);
            Class<?> launcherState = Class.forName(
                    \"com.miui.home.launcher.LauncherState\", false, cl);
            HookUtil.hookMethod(transition, \"setState\", new Class<?>[]{launcherState},
                    chain -> {
                        boolean entering = isStockAllAppsState(chain.getArgs().get(0));
                        DockLiquidGlassView glass = liquidGlassView;
                        if (entering && glass != null) glass.setAllAppsActive(
                                true, resolveNormalAllAppsCaptureRoot(chain.getThisObject()));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = liquidGlassView;
                        if (glass != null) glass.setAllAppsActive(entering,
                                entering ? resolveNormalAllAppsCaptureRoot(chain.getThisObject()) : null);
                        return result;
                    });
            Class<?> builder = Class.forName(
                    \"com.miui.home.launcher.anim.AnimatorSetBuilder\", false, cl);
            Class<?> animationConfig = Class.forName(
                    \"com.miui.home.launcher.LauncherStateManager$AnimationConfig\", false, cl);
            HookUtil.hookMethod(transition, \"setStateWithAnimation\",
                    new Class<?>[]{launcherState, launcherState, builder, animationConfig},
                    chain -> {
                        // Official DEX: the second LauncherState is the destination whose
                        // getAllAppsVerticalProgress() drives this animation.
                        boolean entering = isStockAllAppsState(chain.getArgs().get(1));
                        DockLiquidGlassView glass = liquidGlassView;
                        if (entering && glass != null) glass.setAllAppsActive(
                                true, resolveNormalAllAppsCaptureRoot(chain.getThisObject()));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        glass = liquidGlassView;
                        if (glass != null && !entering) glass.setAllAppsActive(false, null);
                        return result;
                    });
            log(\"[DC] stock normal All Apps capture state hooked\");
        } catch (Throwable e) {
            log(\"[DC] stock normal All Apps capture hook unavailable: \" + e);
        }
    }

    private static boolean isStockAllAppsState(Object state) {
        return state != null && \"com.miui.home.launcher.uioverrides.AllAppsState\"
                .equals(state.getClass().getName());
    }

    private static View resolveLaptopAllAppsCaptureRoot(Object controller) {
        try {
            Object dragLayer = HookUtil.invoke(controller, \"getDragLayer\");
            if (dragLayer instanceof View) return (View) dragLayer;
        } catch (Throwable ignored) {}
        try {
            Object dragLayer = HookUtil.getField(controller, \"mDragLayer\");
            if (dragLayer instanceof View) return (View) dragLayer;
        } catch (Throwable ignored) {}
        return null;
    }

    private static View resolveNormalAllAppsCaptureRoot(Object controller) {
        try {
            Object appsView = HookUtil.getField(controller, \"mAppsView\");
            if (appsView instanceof View) return (View) appsView;
        } catch (Throwable ignored) {}
        return null;
    }

""" + marker
replace_once(main, marker, helper, "All Apps helper insertion")

# ── Local launcher-owned Surface capture ─────────────────────────────
glass = "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"
replace_once(glass,
"""    private View recentsView;
""",
"""    private View recentsView;
    // Normal All Apps uses the Launcher main root; laptop All Apps uses its separate
    // focusable \"Laptop overlay\" root. Both are local to com.miui.home and exclude the
    // Floating Dock Surface by construction when captured with captureLayers().
    private View allAppsCaptureRoot;
""", "glass all apps root field")

replace_once(glass,
"""    void setRecentsView(View view) {
        recentsView = view;
    }

    /** Exact Overview lifecycle supplied by launcher Enter/ExitOverviewStateEvent hooks.
""",
"""    void setRecentsView(View view) {
        recentsView = view;
    }

    boolean isAllAppsActive() {
        return sceneState.allAppsActive();
    }

    void setAllAppsActive(boolean active, View captureRoot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAllAppsActive(active, captureRoot));
            return;
        }
        boolean rootChanged = active && captureRoot != null && allAppsCaptureRoot != captureRoot;
        if (active && captureRoot != null) allAppsCaptureRoot = captureRoot;
        if (!active) allAppsCaptureRoot = null;
        boolean stateChanged = sceneState.allAppsActive() != active;
        sceneState.setAllAppsActive(active);
        if (!stateChanged && !rootChanged) return;
        observationValid = false;
        lastCaptureStartNanos = 0L;
        updateDesiredScene();
        requestStateCapture(active ? \"all-apps-enter\" : \"all-apps-exit\");
    }

    /** Exact Overview lifecycle supplied by launcher Enter/ExitOverviewStateEvent hooks.
""", "glass All Apps methods")

insert_before = """    private boolean hasValidDockWindowSurface() {
"""
local_helpers = """    private android.view.SurfaceControl resolveLauncherOwnedCaptureSurface(CaptureScene scene) {
        View source = null;
        if (scene == CaptureScene.RECENTS) {
            source = recentsView != null ? recentsView : workspace;
        } else if (scene == CaptureScene.ALL_APPS) {
            source = allAppsCaptureRoot != null ? allAppsCaptureRoot : workspace;
        }
        return resolveViewRootSurfaceControl(source);
    }

    private android.view.SurfaceControl resolveViewRootSurfaceControl(View source) {
        if (source == null) return null;
        try {
            View rootView = source.getRootView();
            java.lang.reflect.Method getVri = View.class.getDeclaredMethod(\"getViewRootImpl\");
            getVri.setAccessible(true);
            Object vri = getVri.invoke(rootView);
            if (vri == null) return null;
            java.lang.reflect.Method getSc = vri.getClass().getDeclaredMethod(\"getSurfaceControl\");
            getSc.setAccessible(true);
            Object value = getSc.invoke(vri);
            if (!(value instanceof android.view.SurfaceControl)) return null;
            android.view.SurfaceControl sc = (android.view.SurfaceControl) value;
            try {
                if (!sc.isValid()) return null;
            } catch (Throwable ignored) {}
            return sc;
        } catch (Throwable e) {
            logW(\"launcher-owned root SurfaceControl unavailable scene=\" + sceneState.desired()
                    + \" error=\" + e);
            return null;
        }
    }

""" + insert_before
replace_once(glass, insert_before, local_helpers, "glass local surface helpers")

replace_once(glass,
"""        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        capturing = true;
""",
"""        final CaptureScene requestScene = sceneState.desired();
        final long requestSceneRevision = sceneState.revision();
        final android.view.SurfaceControl localCaptureSurface = useFullscreen
                ? resolveLauncherOwnedCaptureSurface(requestScene) : null;
        final CaptureSourcePolicy.Source requestedSource;
        if (!useFullscreen || (workstationMode && requestScene == CaptureScene.APP)) {
            requestedSource = CaptureSourcePolicy.Source.WALLPAPER;
        } else {
            requestedSource = CaptureSourcePolicy.sourceFor(
                    requestScene, localCaptureSurface != null);
        }
        capturing = true;
""", "capture source decision")

replace_once(glass,
"""        // APP/RECENTS mode-1 needs the Dock exclusion.  Cache the SurfaceControl across
        // frames and re-resolve only after a window/rotation/error invalidates it.
        boolean needsDockExclude = useFullscreen
                && requestScene != CaptureScene.HOME && !workstationMode;
""",
"""        // Only a genuine external APP uses full-display mode-1. Launcher-owned Recents
        // and All Apps capture their own root layer and never depend on Dock exclusion.
        boolean needsDockExclude = useFullscreen
                && requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                && !workstationMode;
""", "dock exclude source scope")
replace_once(glass,
"""        logI((useFullscreen ? \"fullscreen\" : \"captureMode(2)\") + \" attempt display=\" + request.displayId
                + \" strip=\" + request.stripRect + \" tile=\" + request.tileRect
                + \" scale=\" + captureScale + \" exclude=\" + (dockWindowSurface != null));
""",
"""        logI(\"capture source=\" + requestedSource + \" attempt display=\" + request.displayId
                + \" strip=\" + request.stripRect + \" tile=\" + request.tileRect
                + \" scale=\" + captureScale + \" exclude=\" + (dockWindowSurface != null)
                + \" scene=\" + requestScene);
""", "capture log")

old_async = """                if (useFullscreen) {
                    android.view.SurfaceControl[] excludes = null;
                    if (dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    // Async path: submit and return; the result arrives on the SF callback
                    // thread, so this worker thread is free to service the next request
                    // immediately (no blocking wait inside getBuffer()).
                    final CaptureRequest req = request;
                    // Home screen: wallpaper-only capture (mode 2, fast,
                    // inherently icon/dock-free).  APP and RECENTS use
                    // full-display capture with Dock + drag layers excluded
                    // (mode 1) for real-time content.
                    // Workstation mode: all scenes use wallpaper-only (mode 2).
                    // The stock Dock has its own snapshot pipeline; LiquidDock
                    // must not enter mode-1 and risk sampling its own overlay.
                    boolean wallpaperMode = requestScene == CaptureScene.HOME
                            || workstationMode;
                    String[] excludeNames = null;
                    if (!wallpaperMode) {
                        excludeNames = dockWindowLayerName != null
                                ? new String[]{dockWindowLayerName, dragLayerName}
                                : (dragLayerName != null ? new String[]{dragLayerName} : null);
                    }
                    // Wallpaper is static: if a valid strip cache exists (rotation barrier
                    // passed: current orientation produced a real installed frame, same
                    // wallpaper, strip covers the request), serve the crop from cache
                    // and skip the SF capture entirely.
                    if (wallpaperMode && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
                        return;
                    }
                    logI(\"capture mode=\" + (wallpaperMode ? 2 : 1)
                            + \" names=\" + java.util.Arrays.toString(
                                    wallpaperMode ? new String[]{\"Wallpaper BBQ wrapper\"} : excludeNames)
                            + \" crop=\" + req.stripRect + \" scale=\" + captureScale
                            + \" scene=\" + requestScene + \" revision=\" + requestSceneRevision);
                    // Wallpaper mode still passes the Dock exclusion: during Dock expand/
                    // collapse the SF layer tree shifts and the wallpaper include can pick
                    // up the Dock's content; excluding the Dock layer is a belt-and-braces
                    // guard in both modes.
                    final LiveScreenCapture.CaptureCallback captureCb = new LiveScreenCapture.CaptureCallback() {
                        @Override public void onResult(Bitmap bmp) {
                            handleCaptureResult(bmp, req, generation, attempt,
                                    requestScene, requestSceneRevision);
                        }
                        @Override public void onError(Throwable error) {
                            mainHandler.post(() -> {
                                if (generation != captureGeneration
                                        || activeCaptureAttempt != attempt) return;
                                // Only the current attempt may drop the SF client:
                                // a stale zombie worker's late error must not clear
                                // the live client the new worker is using.
                                liveCapture = null;
                                invalidateDockWindowSurfaceCache();
                                retireCaptureAttempt(attempt);
                                Log.e(TAG, \"async fullscreen capture failed\", error);
                                if (sourceDirty) requestStateCapture();
                            });
                        }
                    };
                    client.captureScreenAsync(req.stripRect, captureScale, req.displayId,
                            wallpaperMode ? null : excludes, excludeNames,
                            wallpaperMode ? 2 : 1,
                            captureCb);
                    return; // async path owns completion via handleCaptureResult
                } else {
"""
new_async = """                if (useFullscreen) {
                    android.view.SurfaceControl[] excludes = null;
                    if (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
                    final CaptureRequest req = request;
                    final LiveScreenCapture captureClient = client;
                    final LiveScreenCapture.CaptureCallback captureCb = new LiveScreenCapture.CaptureCallback() {
                        @Override public void onResult(Bitmap bmp) {
                            handleCaptureResult(bmp, req, generation, attempt,
                                    requestScene, requestSceneRevision);
                        }
                        @Override public void onError(Throwable error) {
                            mainHandler.post(() -> {
                                if (generation != captureGeneration
                                        || activeCaptureAttempt != attempt) return;
                                liveCapture = null;
                                invalidateDockWindowSurfaceCache();
                                retireCaptureAttempt(attempt);
                                Log.e(TAG, \"async capture failed source=\" + requestedSource, error);
                                if (sourceDirty) requestStateCapture();
                            });
                        }
                    };

                    CaptureSourcePolicy.Source actualSource = requestedSource;
                    if (actualSource == CaptureSourcePolicy.Source.LOCAL_LAYER
                            && localCaptureSurface != null) {
                        // Recents/All Apps are Launcher-owned. Capture their ViewRoot layer
                        // directly so the separate Floating Dock Surface cannot appear in the
                        // input at all. If the hidden LayerCapture API rejects this build,
                        // fall back to wallpaper — never to full-display capture.
                        LiveScreenCapture.CaptureCallback localCb = new LiveScreenCapture.CaptureCallback() {
                            @Override public void onResult(Bitmap bmp) { captureCb.onResult(bmp); }
                            @Override public void onError(Throwable error) {
                                logW(\"local launcher-layer capture failed; wallpaper fallback: \" + error);
                                captureClient.captureScreenAsync(req.stripRect, captureScale,
                                        req.displayId, null, null, 2, captureCb);
                            }
                        };
                        if (captureClient.captureLayerAsync(req.stripRect, captureScale,
                                localCaptureSurface, localCb)) {
                            logI(\"capture local launcher layer scene=\" + requestScene);
                            return;
                        }
                        logW(\"local launcher-layer API unavailable; wallpaper fallback scene=\"
                                + requestScene);
                        actualSource = CaptureSourcePolicy.Source.WALLPAPER;
                    }

                    boolean wallpaperMode = actualSource == CaptureSourcePolicy.Source.WALLPAPER;
                    String[] excludeNames = null;
                    if (actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY) {
                        excludeNames = dockWindowLayerName != null
                                ? new String[]{dockWindowLayerName, dragLayerName}
                                : (dragLayerName != null ? new String[]{dragLayerName} : null);
                    }
                    if (wallpaperMode && tryServeWallpaperFromCache(
                            req, requestScene, requestSceneRevision, attempt)) {
                        return;
                    }
                    logI(\"capture source=\" + actualSource
                            + \" names=\" + java.util.Arrays.toString(
                                    wallpaperMode ? new String[]{\"Wallpaper BBQ wrapper\"} : excludeNames)
                            + \" crop=\" + req.stripRect + \" scale=\" + captureScale
                            + \" scene=\" + requestScene + \" revision=\" + requestSceneRevision);
                    captureClient.captureScreenAsync(req.stripRect, captureScale, req.displayId,
                            wallpaperMode ? null : excludes, excludeNames,
                            wallpaperMode ? 2 : 1, captureCb);
                    return;
                } else {
"""
replace_once(glass, old_async, new_async, "capture async source dispatch")

# ── LayerCaptureArgs bridge (local launcher roots only) ───────────────
live = "src/main/java/com/hellovoid/liquiddock/LiveScreenCapture.java"
replace_once(live,
"""    private final Constructor<?> asyncListenerConstructor;

    private final Method launcherCaptureWallpaperBitmap;
""",
"""    private final Constructor<?> asyncListenerConstructor;
    private final Constructor<?> layerCaptureBuilderConstructor;
    private final Method layerSetSourceCrop;
    private final Method layerSetFrameScale;
    private final Method layerBuild;
    private final Method captureLayers;

    private final Method launcherCaptureWallpaperBitmap;
""", "layer capture fields")
replace_once(live,
"""        capture.setAccessible(true);
        captureDisplay = capture;

        Method launcherCapture = null;
""",
"""        capture.setAccessible(true);
        captureDisplay = capture;

        Constructor<?> layerCtor = null;
        Method layerCrop = null, layerScale = null, layerBuildMethod = null, layerCapture = null;
        try {
            Class<?> layerArgsClass = Class.forName(
                    \"android.window.ScreenCapture$LayerCaptureArgs\");
            Class<?> layerBuilderClass = Class.forName(
                    \"android.window.ScreenCapture$LayerCaptureArgs$Builder\");
            layerCtor = layerBuilderClass.getDeclaredConstructor(android.view.SurfaceControl.class);
            layerCtor.setAccessible(true);
            layerCrop = layerBuilderClass.getMethod(\"setSourceCrop\", Rect.class);
            layerScale = layerBuilderClass.getMethod(\"setFrameScale\", float.class, float.class);
            layerBuildMethod = layerBuilderClass.getMethod(\"build\");
            layerCapture = screenCaptureClass.getMethod(
                    \"captureLayers\", layerArgsClass, listenerClass);
            layerCapture.setAccessible(true);
        } catch (Throwable error) {
            Log.w(TAG, \"ScreenCapture.captureLayers unavailable; launcher scenes use wallpaper fallback\", error);
        }
        layerCaptureBuilderConstructor = layerCtor;
        layerSetSourceCrop = layerCrop;
        layerSetFrameScale = layerScale;
        layerBuild = layerBuildMethod;
        captureLayers = layerCapture;

        Method launcherCapture = null;
""", "layer capture constructor reflection")

marker2 = """    /** Wallpaper-selector semantics (vendor captureMode 2), with compositor-side crop/scale added. */
"""
layer_method = """    /** Capture one local Launcher-owned ViewRoot layer; Floating Dock is a separate Surface. */
    boolean captureLayerAsync(Rect sourceCrop, float scale,
                              android.view.SurfaceControl layer, CaptureCallback callback) {
        if (layer == null || callback == null || asyncListenerConstructor == null
                || layerCaptureBuilderConstructor == null || captureLayers == null) return false;
        try {
            Object builder = layerCaptureBuilderConstructor.newInstance(layer);
            layerSetSourceCrop.invoke(builder, new Rect(sourceCrop));
            layerSetFrameScale.invoke(builder, scale, scale);
            Object args = layerBuild.invoke(builder);
            Object listener = asyncListenerConstructor.newInstance(
                    (java.util.function.ObjIntConsumer<Object>) (buffer, status) -> {
                        Object hardwareBuffer = null;
                        try {
                            if (buffer == null) {
                                callback.onError(new RuntimeException(
                                        \"layer capture: null buffer status=\" + status));
                                return;
                            }
                            Object bitmap = asBitmap.invoke(buffer);
                            if (bitmap instanceof Bitmap) callback.onResult((Bitmap) bitmap);
                            else callback.onError(new RuntimeException(
                                    \"layer capture: asBitmap returned non-Bitmap\"));
                            try { hardwareBuffer = getHardwareBuffer.invoke(buffer); }
                            catch (Throwable ignored) {}
                        } catch (Throwable error) {
                            callback.onError(error);
                        } finally {
                            closeHardwareBuffer(hardwareBuffer);
                        }
                    });
            captureLayers.invoke(null, args, listener);
            logI(\"async launcher-layer capture submitted crop=\" + sourceCrop + \" scale=\" + scale);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, \"launcher-layer capture submit failed\", error);
            return false;
        }
    }

""" + marker2
replace_once(live, marker2, layer_method, "layer capture method")

print("stock-launcher All Apps spacing/capture patch applied")
