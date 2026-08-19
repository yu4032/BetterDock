package com.hellovoid.liquiddock;

/**
 * Removes MIUI's stock swap-placement pattern rule only while LiquidDock's custom 8x4 / 4x8
 * workspace grid is enabled.
 *
 * GridOccupancyController still owns bounds, occupied cells and vacancy search. The vendor
 * LayoutDropRuleForSwapPlaces adds a separate stock-grid legality filter (for example even-column
 * 1x1 placement and fixed 4-span anchors). On the transposed portrait custom grid that stale rule
 * becomes the invisible barrier even though mVCells, mYs and the occupancy matrix are already 8
 * rows. This is the same narrow rule that the original BetterDock free-placement implementation
 * bypassed; it does not replace the occupancy matrix or transform algorithm.
 */
final class WorkspaceDropRuleHook {
    private static final String TAG = "[DC][GRID]";
    private static boolean installed;

    private WorkspaceDropRuleHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled) {
        if (!customGridEnabled || installed) return;
        try {
            Class<?> rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces",
                    false, classLoader);
            HookUtil.hookMethod(rule, "isLegalXY",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    chain -> {
                        // Bounds/occupancy are checked by GridOccupancyController separately.
                        // This callback removes only the stock 6-column swap-placement pattern.
                        return true;
                    });
            installed = true;
            MainHook.log(TAG + " custom-grid swap placement rule bypass installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid swap placement rule unavailable: " + error);
        }
    }
}
