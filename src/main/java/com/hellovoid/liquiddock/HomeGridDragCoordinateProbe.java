package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Read-only probe for the portrait 10-row drag barrier.
 *
 * Decompiled launcher evidence shows CellLayout.onDragOver delegates the candidate search to
 * CellLayout.findNearestVacantArea(IIIIZZ), which in turn delegates to
 * GridOccupancyController.findNearestVacantArea.  The controller enumerates mTotalCells and asks
 * its LayoutDropRule whether each candidate is legal.  This probe records those inputs/state
 * without replacing the result or mutating occupancy.
 */
final class HomeGridDragCoordinateProbe {
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";

    private static boolean installed;
    private static int lastInputY = Integer.MIN_VALUE;
    private static int lastResultY = Integer.MIN_VALUE;
    private static int lastRotation = Integer.MIN_VALUE;

    private HomeGridDragCoordinateProbe() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) {
            return;
        }
        try {
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            Method nearest = HookUtil.findMethodExact(cellLayout, "findNearestVacantArea",
                    new Class<?>[]{int.class, int.class, int.class, int.class,
                            boolean.class, boolean.class});
            Api101Bridge.module().hook(nearest)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            observe(chain.getThisObject(), chain.getArgs().toArray(new Object[0]),
                                    result);
                        } catch (Throwable error) {
                            MainHook.log("[DC][GRID10][DRAGPROBE] observe failed: " + error);
                        }
                        return result;
                    });
            installed = true;
            MainHook.log("[DC][GRID10][DRAGPROBE] nearest-vacant probe installed");
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10][DRAGPROBE] install failed: " + error);
        }
    }

    private static void observe(Object layout, Object[] args, Object result) {
        if (!(layout instanceof View) || args.length < 4
                || !(args[0] instanceof Integer) || !(args[1] instanceof Integer)
                || !(args[2] instanceof Integer) || !(args[3] instanceof Integer)) {
            return;
        }
        View view = (View) layout;
        if (view.getResources().getConfiguration().orientation
                != android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            return;
        }

        int inputX = (Integer) args[0];
        int inputY = (Integer) args[1];
        int spanX = (Integer) args[2];
        int spanY = (Integer) args[3];
        int[] cell = result instanceof int[] ? (int[]) result : null;
        int resultX = cell != null && cell.length > 0 ? cell[0] : -1;
        int resultY = cell != null && cell.length > 1 ? cell[1] : -1;
        int rotation = view.getDisplay() == null ? -1 : view.getDisplay().getRotation();

        boolean significantInput = lastInputY == Integer.MIN_VALUE
                || Math.abs(inputY - lastInputY) >= 48;
        boolean resultChanged = resultY != lastResultY;
        boolean rotationChanged = rotation != lastRotation;
        if (!significantInput && !resultChanged && !rotationChanged) return;
        lastInputY = inputY;
        lastResultY = resultY;
        lastRotation = rotation;

        Object occupancy = HookUtil.getField(layout, "mGridOccupancyController");
        int hCells = HookUtil.getIntField(occupancy, "mHCells");
        int vCells = HookUtil.getIntField(occupancy, "mVCells");
        int totalCells = HookUtil.getIntField(occupancy, "mTotalCells");
        Object dropRule = HookUtil.getField(occupancy, "mLayoutDropRule");

        int cellTop = HookUtil.getIntField(layout, "mCellPaddingTop");
        int cellHeight = HookUtil.getIntField(layout, "mCellHeight");
        int heightGap = HookUtil.getIntField(layout, "mHeightGap");

        String rows789 = rows789(occupancy, dropRule, resultX, spanX, spanY, hCells, vCells);
        String ruleName = dropRule == null ? "null" : dropRule.getClass().getSimpleName();
        MainHook.log("[DC][GRID10][DRAGPROBE] in=" + inputX + "," + inputY
                + " span=" + spanX + "x" + spanY
                + " out=" + resultX + "," + resultY
                + " grid=" + hCells + "x" + vCells
                + " total=" + totalCells
                + " rotation=" + rotation
                + " geom=top" + cellTop + "/cell" + cellHeight + "/gap" + heightGap
                + " rule=" + ruleName
                + " rows789=" + rows789);
    }

    private static String rows789(Object occupancy, Object dropRule, int resultX,
                                  int spanX, int spanY, int hCells, int vCells) {
        StringBuilder out = new StringBuilder();
        int x = resultX >= 0 ? Math.min(resultX, Math.max(0, hCells - spanX)) : 0;
        for (int row = 7; row <= 9; row++) {
            if (out.length() > 0) out.append(';');
            if (row + spanY > vCells) {
                out.append(row).append(":OOB");
                continue;
            }
            Object legalValue = dropRule == null ? null
                    : HookUtil.invoke(dropRule, "isLegalXY", x, row, spanX, spanY);
            Object occupiedValue = HookUtil.invoke(
                    occupancy, "isCellOccupied", x, row, spanX, spanY);
            out.append(row)
                    .append(":L").append(Boolean.TRUE.equals(legalValue) ? '1' : '0')
                    .append("/O").append(Boolean.TRUE.equals(occupiedValue) ? '1' : '0');
        }
        return out.toString();
    }
}
