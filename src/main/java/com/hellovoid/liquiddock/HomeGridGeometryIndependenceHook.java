package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Final geometry owner for the normal extended Workspace grid.
 *
 * HomeGridHook still owns the established grid lifecycle, rotation refresh, widgets,
 * folders and indicator behavior. This complementary owner runs only at the two layout
 * boundaries where MIUI consumes CellLayout geometry. It replaces the legacy square-cell
 * result with axis-independent geometry, while explicitly leaving workstation and Laptop
 * All Apps on their existing paths.
 */
final class HomeGridGeometryIndependenceHook {
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";

    private HomeGridGeometryIndependenceHook() {}

    static void install(ClassLoader classLoader) {
        if (!homeBoolean("grid8x4Enabled")) return;
        try {
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);

            Method calculate = HookUtil.findMethodExact(
                    cellLayout, "calculateXsAndYs", new Class<?>[0]);
            // Highest wraps the established HomeGridHook callback. Its post section therefore
            // runs after MIUI and after HomeGridHook's legacy post-rebuild, making this the
            // final geometry written by calculateXsAndYs().
            Api101Bridge.module().hook(calculate)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        applyNormalGeometry(chain.getThisObject());
                        return result;
                    });

            Method onLayout = HookUtil.findMethodExact(cellLayout, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            // Lowest pre-processing runs after HomeGridHook's prepareCellLayoutGeometryForLayout
            // but immediately before the real CellLayout.onLayout(), so MIUI consumes the
            // axis-independent geometry instead of the legacy coupled cellSize.
            Api101Bridge.module().hook(onLayout)
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .intercept(chain -> {
                        applyNormalGeometry(chain.getThisObject());
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });

            MainHook.log("[DC] normal Workspace axis-independent geometry owner installed");
        } catch (Throwable error) {
            MainHook.log("[DC] normal Workspace geometry owner unavailable: " + error);
        }
    }

    private static void applyNormalGeometry(Object cellLayout) {
        try {
            if (!homeBoolean("grid8x4Enabled") || MainHook.isWorkstationMode()
                    || isLaptopAllApps(cellLayout)) return;
            if (!(cellLayout instanceof View)) return;
            View layout = (View) cellLayout;
            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0 || !sizeMatchesOrientation(layout, width, height)) {
                return;
            }

            Object config = HookUtil.getField(cellLayout, "mGridConfig");
            if (config == null) return;
            Object countXValue = HookUtil.invoke(config, "getCountX");
            Object countYValue = HookUtil.invoke(config, "getCountY");
            if (!(countXValue instanceof Integer) || !(countYValue instanceof Integer)) return;
            int countX = (Integer) countXValue;
            int countY = (Integer) countYValue;
            if (countX <= 0 || countY <= 0) return;

            Object gridCells = HookUtil.getField(cellLayout, "mGridCell");
            if (gridCells != null) {
                int matrixX = java.lang.reflect.Array.getLength(gridCells);
                int matrixY = matrixX == 0 ? 0
                        : java.lang.reflect.Array.getLength(
                                java.lang.reflect.Array.get(gridCells, 0));
                if (matrixX > 0 && matrixY > 0
                        && (matrixX != countX || matrixY != countY)) {
                    MainHook.log("[DC] final geometry count/matrix mismatch: config="
                            + countX + "x" + countY + " matrix=" + matrixX + "x" + matrixY);
                    countX = matrixX;
                    countY = matrixY;
                }
            }
            if (countX <= 0 || countY <= 0) return;

            Object baseCellValue = HookUtil.invoke(config, "getCellSize");
            if (!(baseCellValue instanceof Integer) || (Integer) baseCellValue <= 0) return;
            int baseCell = (Integer) baseCellValue;
            int dockBarHeight = 0;
            Object dockValue = HookUtil.invoke(config, "getDockBarHeight");
            if (dockValue instanceof Integer) dockBarHeight = Math.max(0, (Integer) dockValue);

            boolean portrait = layout.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
            int baseHeightGap = Math.max(1, Math.round(homeFloat("density")));
            int[] geometry = HomeGridGeometryPolicy.normalWorkspace(
                    width, height, dockBarHeight,
                    countX, countY, baseCell, baseHeightGap,
                    homeInt(portrait ? "portraitLeft" : "landscapeLeft"),
                    homeInt(portrait ? "portraitRight" : "landscapeRight"),
                    homeInt(portrait ? "portraitTop" : "landscapeTop"),
                    homeInt(portrait ? "portraitBottom" : "landscapeBottom"),
                    homeInt(portrait ? "portraitRowGap" : "landscapeRowGap"));

            ensureCoordinateStorage(cellLayout, countX, countY);
            HookUtil.setIntField(cellLayout, "mHCells", countX);
            HookUtil.setIntField(cellLayout, "mVCells", countY);
            HookUtil.setIntField(cellLayout, "mCellPaddingLeft", geometry[0]);
            HookUtil.setIntField(cellLayout, "mCellPaddingTop", geometry[2]);
            HookUtil.setIntField(cellLayout, "mCellWidth", geometry[4]);
            HookUtil.setIntField(cellLayout, "mCellHeight", geometry[5]);
            HookUtil.setIntField(cellLayout, "mWidthGap", geometry[6]);
            HookUtil.setIntField(cellLayout, "mHeightGap", geometry[7]);
            rebuildCoordinates(cellLayout, countX, countY, geometry);
        } catch (Throwable error) {
            MainHook.log("[DC] final normal Workspace geometry failed: " + error);
        }
    }

    private static void ensureCoordinateStorage(Object cellLayout, int countX, int countY) {
        Object xsValue = HookUtil.getField(cellLayout, "mXs");
        Object ysValue = HookUtil.getField(cellLayout, "mYs");
        if (!(xsValue instanceof int[]) || ((int[]) xsValue).length != countX) {
            HookUtil.setField(cellLayout, "mXs", new int[countX]);
        }
        if (!(ysValue instanceof int[]) || ((int[]) ysValue).length != countY) {
            HookUtil.setField(cellLayout, "mYs", new int[countY]);
        }
    }

    private static void rebuildCoordinates(Object cellLayout, int countX, int countY,
                                           int[] geometry) {
        int[] xs = new int[countX];
        int[] ys = new int[countY];
        for (int x = 0; x < countX; x++) {
            xs[x] = geometry[0] + x * (geometry[4] + geometry[6]);
        }
        for (int y = 0; y < countY; y++) {
            ys[y] = geometry[2] + y * (geometry[5] + geometry[7]);
        }
        HookUtil.setField(cellLayout, "mXs", xs);
        HookUtil.setField(cellLayout, "mYs", ys);
    }

    private static boolean sizeMatchesOrientation(View view, int width, int height) {
        int orientation = view.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return height >= width;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return width >= height;
        return true;
    }

    private static boolean isLaptopAllApps(Object cellLayout) {
        String gridType = "";
        try {
            Object value = HookUtil.getField(cellLayout, "mGridType");
            if (value != null) gridType = String.valueOf(value);
        } catch (Throwable ignored) {}
        if (gridType.isEmpty()) {
            Object value = HookUtil.invoke(cellLayout, "getGridType");
            if (value != null) gridType = String.valueOf(value);
        }

        boolean exact = false;
        Object exactValue = HookUtil.invoke(cellLayout, "isInLapTopAllApps");
        if (exactValue instanceof Boolean) exact = (Boolean) exactValue;

        StringBuilder ancestry = new StringBuilder();
        if (cellLayout instanceof View) {
            ViewParent parent = ((View) cellLayout).getParent();
            int depth = 0;
            while (parent != null && depth++ < 8) {
                if (ancestry.length() > 0) ancestry.append('>');
                ancestry.append(parent.getClass().getName());
                parent = parent.getParent();
            }
        }
        return WorkstationLayoutClassifier.matches(exact, gridType, ancestry.toString());
    }

    private static boolean homeBoolean(String name) {
        try {
            Field field = HomeGridHook.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int homeInt(String name) throws Exception {
        Field field = HomeGridHook.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static float homeFloat(String name) throws Exception {
        Field field = HomeGridHook.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(null);
    }
}
