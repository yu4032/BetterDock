package com.hellovoid.liquiddock;

/**
 * Removes MIUI's stale stock-grid swap-placement pattern while preserving rotation-safe
 * placement for widgets that MIUI's native transform treats as fixed SPECIAL_WIDGET slots.
 *
 * <p>GridOccupancyController remains the owner of bounds, occupied cells and vacancy search.
 * LayoutDropRuleForSwapPlaces also carries stock-grid pattern restrictions that become invalid
 * once Workspace uses a non-stock profile, so ordinary items still bypass that pattern. For the
 * 10x6 profile, however, arbitrary 4x2 origins are not representable by the native special-widget
 * rotation path; those placements are vetoed here before they can enter Workspace state.</p>
 */
final class WorkspaceDropRuleHook {
    private static final String TAG = "[DC][GRID]";
    private static boolean installed;

    private WorkspaceDropRuleHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled, HomeGridProfile profile) {
        if (!customGridEnabled || installed) return;
        try {
            Class<?> rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces",
                    false, classLoader);
            HookUtil.hookMethod(rule, "isLegalXY",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    chain -> {
                        int cellX = (Integer) chain.getArg(0);
                        int cellY = (Integer) chain.getArg(1);
                        int spanX = (Integer) chain.getArg(2);
                        int spanY = (Integer) chain.getArg(3);

                        // Bounds/occupancy are checked by GridOccupancyController separately.
                        // This hook removes only the stale stock-grid pattern, with one explicit
                        // safety gate for 10x6 4x2 widgets whose rotation uses fixed native slots.
                        return WorkspaceDropPolicy.isPlacementAllowed(
                                profile, cellX, cellY, spanX, spanY);
                    });
            installed = true;
            MainHook.log(TAG + " custom-grid swap placement rule installed profile="
                    + profile.persistedValue());
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid swap placement rule unavailable: " + error);
        }
    }
}
