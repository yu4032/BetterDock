package com.hellovoid.liquiddock;

/**
 * Replaces MIUI's stock swap-placement pattern rule only while LiquidDock's custom workspace grid
 * is enabled. Native occupancy still owns collision resolution; this hook keeps free placement for
 * ordinary spans while restoring the 2x2 macroblock invariant required by MIUI's grid transform.
 */
final class WorkspaceDropRuleHook {
    private static final String TAG = "[DC][GRID]";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static boolean installed;

    private WorkspaceDropRuleHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (!customGridEnabled || selectedProfile == null || installed) return;
        try {
            Class<?> rule = Class.forName(
                    "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces",
                    false, classLoader);
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
            HookUtil.hookMethod(rule, "isLegalXY",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    chain -> {
                        if (MainHook.isWorkstationMode()) return chain.proceed();

                        Object xValue = chain.getArg(0);
                        Object yValue = chain.getArg(1);
                        Object spanXValue = chain.getArg(2);
                        Object spanYValue = chain.getArg(3);
                        if (!(xValue instanceof Integer) || !(yValue instanceof Integer)
                                || !(spanXValue instanceof Integer)
                                || !(spanYValue instanceof Integer)) {
                            return chain.proceed();
                        }

                        int cellX = (Integer) xValue;
                        int cellY = (Integer) yValue;
                        int spanX = (Integer) spanXValue;
                        int spanY = (Integer) spanYValue;
                        Object columnsValue = HookUtil.invokeStatic(
                                deviceConfig, "getCellCountX");
                        Object rowsValue = HookUtil.invokeStatic(
                                deviceConfig, "getCellCountY");
                        if (columnsValue instanceof Integer && rowsValue instanceof Integer) {
                            int columns = (Integer) columnsValue;
                            int rows = (Integer) rowsValue;
                            if (selectedProfile.matchesCounts(columns, rows)) {
                                return HomeGridDropLegalityPolicy.isLegal(
                                        selectedProfile, columns, rows,
                                        cellX, cellY, spanX, spanY);
                            }
                        }

                        // During a transient count mismatch, preserve the only transform-critical
                        // invariant locally. GridOccupancyController still owns the real bounds.
                        if (spanX == 2 && spanY == 2) {
                            return cellX >= 0 && cellY >= 0
                                    && (cellX & 1) == 0 && (cellY & 1) == 0;
                        }
                        return true;
                    });
            installed = true;
            MainHook.log(TAG + " selective custom-grid drop rule installed profile="
                    + selectedProfile.persistedValue());
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid swap placement rule unavailable: " + error);
        }
    }
}
