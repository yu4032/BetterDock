package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Read-only probe for the portrait 10-row drag barrier.
 *
 * Launcher DEX inspection establishes this chain:
 * CellScreen.translateTouch(DragObject) -> translateTouchY(DragObject, scaleY, pivotRatio)
 * -> CellLayout.onDragOver -> CellLayout.findNearestVacantArea(IIIIZZ)
 * -> GridOccupancyController.findNearestVacantArea.
 *
 * translateTouchY derives the first/last row bounds with CellLayout.setupLayoutParam(),
 * DeviceConfig.getCellCountY(), and DeviceConfig.getCellHeight(), then inverse-maps the drag
 * center through the CellLayout scale/pivot transform.  This probe records every boundary input
 * needed to distinguish a touch-translation wall from a vacancy/legality wall.  It never replaces
 * a launcher return value and never mutates DragObject, CellLayout, or occupancy state.
 */
final class HomeGridDragCoordinateProbe {
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String CELL_SCREEN = "com.miui.home.launcher.CellScreen";
    private static final String DRAG_OBJECT = "com.miui.home.launcher.DragObject";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";

    private static final ThreadLocal<TraceState> TRACE =
            ThreadLocal.withInitial(TraceState::new);

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
            Class<?> dragObject = Class.forName(DRAG_OBJECT, false, classLoader);
            Class<?> cellScreen = Class.forName(CELL_SCREEN, false, classLoader);
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);

            installTouchProbe(cellScreen, dragObject, deviceConfig);
            installNearestProbe(cellLayout);

            installed = true;
            MainHook.log("[DC][GRID10][DRAGPROBE] touch-to-nearest probe installed");
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10][DRAGPROBE] install failed: " + error);
        }
    }

    private static void installTouchProbe(Class<?> cellScreen, Class<?> dragObject,
                                          Class<?> deviceConfig) throws NoSuchMethodException {
        Method translateTouch = HookUtil.findMethodExact(
                cellScreen, "translateTouch", new Class<?>[]{dragObject});
        Api101Bridge.module().hook(translateTouch)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object drag = chain.getArg(0);
                    TraceState trace = TRACE.get();
                    trace.sequence++;
                    trace.rawY = safeIntField(drag, "y");
                    trace.translatedY = trace.rawY;
                    trace.translateInputY = trace.rawY;
                    trace.translateYResult = Float.NaN;
                    trace.deviceCountY = -1;
                    trace.deviceCellHeight = -1;
                    trace.scaleY = Float.NaN;
                    trace.pivotRatio = Float.NaN;
                    try {
                        return chain.proceed();
                    } finally {
                        trace.translatedY = safeIntField(drag, "y");
                    }
                });

        Method translateTouchY = HookUtil.findMethodExact(
                cellScreen, "translateTouchY",
                new Class<?>[]{dragObject, float.class, float.class});
        Api101Bridge.module().hook(translateTouchY)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    TraceState trace = TRACE.get();
                    Object drag = chain.getArg(0);
                    trace.translateInputY = safeIntField(drag, "y");
                    trace.scaleY = floatArg(chain.getArg(1));
                    trace.pivotRatio = floatArg(chain.getArg(2));
                    trace.deviceCountY = intValue(
                            HookUtil.invokeStatic(deviceConfig, "getCellCountY"), -1);
                    trace.deviceCellHeight = intValue(
                            HookUtil.invokeStatic(deviceConfig, "getCellHeight"), -1);
                    Object result = chain.proceed();
                    if (result instanceof Float) {
                        trace.translateYResult = (Float) result;
                    }
                    return result;
                });
    }

    private static void installNearestProbe(Class<?> cellLayout) throws NoSuchMethodException {
        Method nearest = HookUtil.findMethodExact(cellLayout, "findNearestVacantArea",
                new Class<?>[]{int.class, int.class, int.class, int.class,
                        boolean.class, boolean.class});
        Api101Bridge.module().hook(nearest)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        observeNearest(chain.getThisObject(),
                                chain.getArgs().toArray(new Object[0]), result);
                    } catch (Throwable error) {
                        MainHook.log("[DC][GRID10][DRAGPROBE] observe failed: " + error);
                    }
                    return result;
                });
    }

    private static void observeNearest(Object layout, Object[] args, Object result) {
        if (!(layout instanceof View) || args.length < 4
                || !(args[0] instanceof Integer) || !(args[1] instanceof Integer)
                || !(args[2] instanceof Integer) || !(args[3] instanceof Integer)) {
            return;
        }
        View view = (View) layout;
        if (view.getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_PORTRAIT) {
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

        TraceState trace = TRACE.get();
        String rows789 = rows789(occupancy, dropRule, resultX, spanX, spanY, hCells, vCells);
        String ruleName = dropRule == null ? "null" : dropRule.getClass().getSimpleName();
        MainHook.log("[DC][GRID10][DRAGPROBE] seq=" + trace.sequence
                + " rawY=" + trace.rawY
                + " translatedY=" + trace.translatedY
                + " translateInputY=" + trace.translateInputY
                + " translateResultY=" + trace.translateYResult
                + " dcCountY=" + trace.deviceCountY
                + " dcCellHeight=" + trace.deviceCellHeight
                + " scaleY=" + trace.scaleY
                + " pivotRatio=" + trace.pivotRatio
                + " nearestIn=" + inputX + "," + inputY
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

    private static int safeIntField(Object target, String name) {
        if (target == null) return Integer.MIN_VALUE;
        try {
            return HookUtil.getIntField(target, name);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static float floatArg(Object value) {
        return value instanceof Float ? (Float) value : Float.NaN;
    }

    private static final class TraceState {
        int sequence;
        int rawY = Integer.MIN_VALUE;
        int translatedY = Integer.MIN_VALUE;
        int translateInputY = Integer.MIN_VALUE;
        float translateYResult = Float.NaN;
        int deviceCountY = -1;
        int deviceCellHeight = -1;
        float scaleY = Float.NaN;
        float pivotRatio = Float.NaN;
    }
}
