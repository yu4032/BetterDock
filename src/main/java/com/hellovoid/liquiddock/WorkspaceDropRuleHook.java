package com.hellovoid.liquiddock;

/**
 * Removes MIUI's stock swap-placement pattern rule only while LiquidDock's optional extended
 * Workspace grid is enabled.
 *
 * GridOccupancyController still owns bounds, occupied cells and vacancy search. The vendor
 * LayoutDropRuleForSwapPlaces adds a separate stock-grid legality filter, such as even-column
 * 1x1 placement and fixed 4-span anchors. Once Workspace uses a non-stock profile, that pattern
 * rule can become an invisible barrier even though the active cell counts, coordinate arrays and
 * occupancy matrix are already correct. This hook bypasses only that stale pattern filter; it
 * does not replace the occupancy matrix, bounds checking, vacancy search, or rotation transform.
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
                        // This callback removes only the stock-grid swap-placement pattern.
                        return true;
                    });
            installed = true;
            MainHook.log(TAG + " custom-grid swap placement rule bypass installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid swap placement rule unavailable: " + error);
        }
    }
}
