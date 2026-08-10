package com.hellovoid.betterdock;

import android.content.Context;
import android.content.res.Configuration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class HomeGridHook {
    private static final String PAD_CELL_COUNT =
        "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";

    private static int landscapeLeft, landscapeRight, landscapeTop, landscapeBottom;
    private static int portraitLeft, portraitRight, portraitTop, portraitBottom;
    private static int landscapeRowGap, portraitRowGap;
    private static boolean grid8x4Enabled;
    private static float density;
    private static int landscapeIndicatorY, portraitIndicatorY;
    private static final java.util.WeakHashMap<android.view.View, float[]>
        indicatorBaseTranslations = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<android.view.View,
        android.view.ViewTreeObserver.OnPreDrawListener> indicatorPositionGuards =
            new java.util.WeakHashMap<>();

    private HomeGridHook() {}

    static void install(ClassLoader classLoader, boolean enableGrid8x4,
                        int landLeft, int landRight, int landTop, int landBottom,
                        int portLeft, int portRight, int portTop, int portBottom,
                        int landRowGap, int portRowGap,
                        int landIndicatorY, int portIndicatorY) {
        landscapeLeft = landLeft;
        landscapeRight = landRight;
        landscapeTop = landTop;
        landscapeBottom = landBottom;
        portraitLeft = portLeft;
        portraitRight = portRight;
        portraitTop = portTop;
        portraitBottom = portBottom;
        landscapeRowGap = landRowGap;
        portraitRowGap = portRowGap;
        landscapeIndicatorY = landIndicatorY;
        portraitIndicatorY = portIndicatorY;
        grid8x4Enabled = enableGrid8x4;
        density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        try {
            if (enableGrid8x4) {
                Class<?> compat = XposedHelpers.findClass(PAD_CELL_COUNT, classLoader);
                hookAxis(compat, "getCellCountXMin", true);
                hookAxis(compat, "getCellCountXDef", true);
                hookAxis(compat, "getCellCountYMin", false);
                hookAxis(compat, "getCellCountYDef", false);
                Class<?> gridConfig = XposedHelpers.findClass(
                    "com.miui.home.launcher.grid.GridConfig", classLoader);
                hookGridCountSetter(gridConfig, "setCountX");
                hookGridCountSetter(gridConfig, "setCountY");
                hookGridCountGetter(gridConfig, "getCountX");
                hookGridCountGetter(gridConfig, "getCountY");
                installRotationTransform(classLoader);
            }
            installIndicatorPosition(classLoader);
            installCellLayoutMargins(classLoader);
            installRotationRefresh(classLoader);
            XposedBridge.log("[DC] home grid hooks: 8x4=" + enableGrid8x4 + " land="
                + landscapeLeft + "," + landscapeRight + ","
                + landscapeTop + "," + landscapeBottom + " port="
                + portraitLeft + "," + portraitRight + ","
                + portraitTop + "," + portraitBottom);

        } catch (Throwable e) {
            XposedBridge.log("[DC] home grid hook unavailable: " + e);
        }
    }

    private static void installCellLayoutMargins(ClassLoader classLoader) {
        Class<?> cellLayout = XposedHelpers.findClass(
            "com.miui.home.launcher.CellLayout", classLoader);
        XposedHelpers.findAndHookMethod(cellLayout, "calculateXsAndYs",
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    applyCellLayoutOffsets(param.thisObject);
                }
            });
    }

    private static void applyCellLayoutOffsets(Object cellLayout) {
        try {
            Object config = XposedHelpers.getObjectField(cellLayout, "mGridConfig");
            if (config == null) return;
            android.view.View layout = (android.view.View) cellLayout;
            boolean portrait = layout.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
            int countX = (Integer) XposedHelpers.callMethod(config, "getCountX");
            int countY = (Integer) XposedHelpers.callMethod(config, "getCountY");
            if (countX <= 0 || countY <= 0) return;
            Object gridCells = XposedHelpers.getObjectField(cellLayout, "mGridCell");
            if (gridCells != null) {
                int matrixX = java.lang.reflect.Array.getLength(gridCells);
                int matrixY = matrixX == 0 ? 0
                    : java.lang.reflect.Array.getLength(java.lang.reflect.Array.get(gridCells, 0));
                if (matrixX != countX || matrixY != countY) {
                    XposedBridge.log("[DC] grid count/matrix mismatch: config="
                        + countX + "x" + countY + " matrix=" + matrixX + "x" + matrixY);
                    countX = matrixX;
                    countY = matrixY;
                }
            }
            if (countX <= 0 || countY <= 0) return;
            int[] xs = (int[]) XposedHelpers.getObjectField(cellLayout, "mXs");
            int[] ys = (int[]) XposedHelpers.getObjectField(cellLayout, "mYs");
            if (xs == null || xs.length != countX)
                XposedHelpers.setObjectField(cellLayout, "mXs", new int[countX]);
            if (ys == null || ys.length != countY)
                XposedHelpers.setObjectField(cellLayout, "mYs", new int[countY]);
            XposedHelpers.setIntField(cellLayout, "mHCells", countX);
            XposedHelpers.setIntField(cellLayout, "mVCells", countY);
            int baseCell = (Integer) XposedHelpers.callMethod(config, "getCellSize");
            int configLeft = (Integer) XposedHelpers.callMethod(config, "getLeft");
            int baseTop = (Integer) XposedHelpers.callMethod(config, "getTop");
            int baseWidthGap = XposedHelpers.getIntField(cellLayout, "mWidthGap");
            int baseLeft = configLeft
                - Math.max(0, countX - 1) * (baseWidthGap / 2);
            int baseHeightGap = 1;
            if (baseCell <= 0) return;

            int width = layout.getWidth();
            int height = layout.getHeight();
            if (width <= 0 || height <= 0) return;

            // With the 8x4 count hooks MIUI can retain the opposite orientation's
            // GridConfig after rotation. Use the established Pad defaults as the
            // orientation-specific baseline; native 6x4 continues using MIUI's
            // live GridConfig values.
            if (grid8x4Enabled) {
                baseWidthGap = 0;
                baseHeightGap = Math.max(1, Math.round(density));
                baseLeft = Math.max(0, (width - baseCell * countX) / 2);
                baseTop = Math.max(0, (height - baseCell * countY
                    - baseHeightGap * Math.max(0, countY - 1)) / 2);
            }

            int baseRight = width - (baseLeft + baseCell * countX
                + baseWidthGap * Math.max(0, countX - 1));
            int baseBottom = height - (baseTop + baseCell * countY
                + baseHeightGap * Math.max(0, countY - 1));
            int left = baseLeft + (portrait ? portraitLeft : landscapeLeft);
            int right = baseRight + (portrait ? portraitRight : landscapeRight);
            int top = baseTop + (portrait ? portraitTop : landscapeTop);
            int bottom = baseBottom + (portrait ? portraitBottom : landscapeBottom);
            int rowGap = baseHeightGap
                + (portrait ? portraitRowGap : landscapeRowGap);

            int availableWidth = Math.max(countX, width - left - right);
            int cellWidth = Math.min(baseCell, Math.max(1, availableWidth / countX));
            int widthGap = countX > 1
                ? Math.max(0, availableWidth - cellWidth * countX) / (countX - 1) : 0;
            int availableHeight = height - top - bottom
                - rowGap * Math.max(0, countY - 1);
            int cellHeight = Math.max(1, availableHeight / countY);
            XposedHelpers.setIntField(cellLayout, "mCellPaddingLeft", left);
            XposedHelpers.setIntField(cellLayout, "mCellPaddingTop", top);
            XposedHelpers.setIntField(cellLayout, "mCellWidth", cellWidth);
            XposedHelpers.setIntField(cellLayout, "mCellHeight", cellHeight);
            XposedHelpers.setIntField(cellLayout, "mWidthGap", widthGap);
            XposedHelpers.setIntField(cellLayout, "mHeightGap", rowGap);
        } catch (Throwable e) {
            XposedBridge.log("[DC] CellLayout offset apply failed: " + e);
        }
    }

    private static void installRotationRefresh(ClassLoader classLoader) {
        Class<?> launcher = XposedHelpers.findClass(
            "com.miui.home.launcher.Launcher", classLoader);
        XposedHelpers.findAndHookMethod(launcher, "onConfigurationChanged",
            Configuration.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        final android.view.View workspace = (android.view.View)
                            XposedHelpers.getObjectField(param.thisObject, "mWorkspace");
                        if (workspace == null) return;
                        android.view.View.OnLayoutChangeListener listener =
                            new android.view.View.OnLayoutChangeListener() {
                                @Override public void onLayoutChange(android.view.View v,
                                        int left, int top, int right, int bottom,
                                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                    if (right <= left || bottom <= top) return;
                                    v.removeOnLayoutChangeListener(this);
                                    v.post(new Runnable() {
                                        @Override public void run() {
                                            refreshWorkspaceGrid(workspace);
                                        }
                                    });
                                }
                            };
                        workspace.addOnLayoutChangeListener(listener);
                        workspace.requestLayout();
                    } catch (Throwable e) {
                        XposedBridge.log("[DC] rotation refresh hook failed: " + e);
                    }
                }
            });
    }

    private static void refreshWorkspaceGrid(android.view.View workspace) {
        try {
            int count = (Integer) XposedHelpers.callMethod(workspace, "getScreenCount");
            for (int i = 0; i < count; i++) {
                Object cell = XposedHelpers.callMethod(workspace, "getCellLayout", i);
                if (!(cell instanceof android.view.View)) continue;
                XposedHelpers.callMethod(cell, "calculateXsAndYs");
                ((android.view.View) cell).requestLayout();
            }
        } catch (Throwable e) {
            XposedBridge.log("[DC] rotation grid refresh failed: " + e);
        }
    }

    private static void installIndicatorPosition(ClassLoader classLoader) {
        Class<?> screenView = XposedHelpers.findClass(
            "com.miui.home.launcher.ScreenView", classLoader);
        Class<?> workspace = XposedHelpers.findClass(
            "com.miui.home.launcher.Workspace", classLoader);
        XposedHelpers.findAndHookMethod(screenView, "updateIndicatorPositions",
            int.class, boolean.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!workspace.isInstance(param.thisObject)) return;
                    try {
                        Object indicator = XposedHelpers.callMethod(
                            param.thisObject, "getScreenIndicator");
                        if (indicator instanceof android.view.View)
                            restoreIndicatorTranslation((android.view.View) indicator);
                    } catch (Throwable e) {
                        XposedBridge.log("[DC] indicator offset failed: " + e);
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!workspace.isInstance(param.thisObject)) return;
                    try {
                        Object indicator = XposedHelpers.callMethod(
                            param.thisObject, "getScreenIndicator");
                        if (indicator instanceof android.view.View)
                            captureAndApplyIndicatorTranslation((android.view.View) indicator);
                    } catch (Throwable e) {
                        XposedBridge.log("[DC] indicator offset failed: " + e);
                    }
                }
            });
    }

    private static void restoreIndicatorTranslation(android.view.View indicator) {
        float[] base;
        synchronized (indicatorBaseTranslations) {
            base = indicatorBaseTranslations.get(indicator);
        }
        if (base == null) return;
        indicator.setTranslationX(base[0]);
        indicator.setTranslationY(base[1]);
    }

    private static void captureAndApplyIndicatorTranslation(android.view.View indicator) {
        float[] base = new float[] {
            indicator.getTranslationX(), indicator.getTranslationY()
        };
        synchronized (indicatorBaseTranslations) {
            indicatorBaseTranslations.put(indicator, base);
        }
        ensureIndicatorPositionGuard(indicator);
        applyIndicatorTranslation(indicator, base);
    }

    private static void ensureIndicatorPositionGuard(final android.view.View indicator) {
        synchronized (indicatorPositionGuards) {
            if (indicatorPositionGuards.containsKey(indicator)) return;
            android.view.ViewTreeObserver.OnPreDrawListener guard =
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        float[] base;
                        synchronized (indicatorBaseTranslations) {
                            base = indicatorBaseTranslations.get(indicator);
                        }
                        if (base != null) applyIndicatorTranslation(indicator, base);
                        return true;
                    }
                };
            indicatorPositionGuards.put(indicator, guard);
            indicator.getViewTreeObserver().addOnPreDrawListener(guard);
        }
    }

    private static void applyIndicatorTranslation(android.view.View indicator, float[] base) {
        boolean portrait = indicator.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_PORTRAIT;
        float targetY = base[1] + (portrait ? portraitIndicatorY : landscapeIndicatorY);
        indicator.setTranslationX(base[0]);
        if (indicator.getTranslationY() != targetY) indicator.setTranslationY(targetY);
    }

    private static void installRotationTransform(ClassLoader classLoader) {
        Class<?> helper = XposedHelpers.findClass(
            "com.miui.home.launcher.compat.LayoutTransformHelperGridChanged", classLoader);
        Class<?> transformInfo = XposedHelpers.findClass(
            "com.miui.home.launcher.bean.LayoutTransformInfo", classLoader);
        XposedHelpers.findAndHookMethod(helper, "addOccupied",
            java.lang.reflect.Array.newInstance(transformInfo, 0, 0).getClass(),
            transformInfo, int.class, int.class, int.class, int.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object matrix = param.args[0];
                    int cellX = (Integer) param.args[2];
                    int cellY = (Integer) param.args[3];
                    int spanX = (Integer) param.args[4];
                    int spanY = (Integer) param.args[5];
                    fillTransformMatrix(matrix, param.args[1],
                        cellX, cellY, spanX, spanY);
                    param.setResult(null);
                }
            });
        XposedHelpers.findAndHookMethod(helper, "transformToHVArray",
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    int width = (Integer) XposedHelpers.callMethod(
                        param.thisObject, "getMHCells");
                    int height = (Integer) XposedHelpers.callMethod(
                        param.thisObject, "getMVCells");
                    Object matrix = XposedHelpers.callMethod(
                        param.thisObject, "getMDstOccupied");
                    android.view.View[][] result = new android.view.View[width][height];
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            Object info = getTransformCell(matrix, x, y, width, height);
                            if (info == null) continue;
                            Object data = XposedHelpers.callMethod(info, "getMData");
                            if (data instanceof android.view.View)
                                result[x][y] = (android.view.View) data;
                        }
                    }
                    param.setResult(result);
                }
            });
        Class<?> rule = XposedHelpers.findClass(
            "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged", classLoader);
        XposedBridge.hookAllConstructors(rule, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                int h = (Integer) param.args[0];
                int v = (Integer) param.args[1];
                if (!((h == 8 && v == 4) || (h == 4 && v == 8))) return;
                int[][] portrait = new int[][] {
                    {0, 0}, {2, 0}, {0, 2}, {2, 2},
                    {0, 4}, {2, 4}, {0, 6}, {2, 6}
                };
                int[][] landscape = new int[][] {
                    {0, 0}, {2, 0}, {4, 0}, {6, 0},
                    {0, 2}, {2, 2}, {4, 2}, {6, 2}
                };
                XposedHelpers.setObjectField(param.thisObject,
                    "vScreenCoordinate", portrait);
                XposedHelpers.setObjectField(param.thisObject,
                    "hScreenCoordinate", landscape);
                XposedHelpers.setIntField(param.thisObject, "totalBlocks", 8);
            }
        });
        XposedHelpers.findAndHookMethod(rule, "checkCellCount", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                int h = (Integer) XposedHelpers.callMethod(param.thisObject, "getMHCells");
                int v = (Integer) XposedHelpers.callMethod(param.thisObject, "getMVCells");
                if ((h == 8 && v == 4) || (h == 4 && v == 8)) param.setResult(null);
            }
        });
    }

    private static void fillTransformMatrix(Object matrix, Object value,
                                            int cellX, int cellY,
                                            int spanX, int spanY) {
        int outer = java.lang.reflect.Array.getLength(matrix);
        int inner = outer == 0 ? 0
            : java.lang.reflect.Array.getLength(java.lang.reflect.Array.get(matrix, 0));
        boolean direct = cellX + spanX <= outer && cellY + spanY <= inner;
        boolean transposed = cellY + spanY <= outer && cellX + spanX <= inner;
        if (!direct && !transposed) return;
        for (int x = cellX; x < cellX + spanX; x++) {
            for (int y = cellY; y < cellY + spanY; y++) {
                int first = direct ? x : y;
                int second = direct ? y : x;
                Object row = java.lang.reflect.Array.get(matrix, first);
                java.lang.reflect.Array.set(row, second, value);
            }
        }
    }

    private static Object getTransformCell(Object matrix, int x, int y,
                                           int width, int height) {
        int outer = java.lang.reflect.Array.getLength(matrix);
        if (outer == 0) return null;
        int inner = java.lang.reflect.Array.getLength(java.lang.reflect.Array.get(matrix, 0));
        boolean direct = outer >= width && inner >= height;
        int first = direct ? x : y;
        int second = direct ? y : x;
        if (first < 0 || first >= outer) return null;
        Object row = java.lang.reflect.Array.get(matrix, first);
        if (second < 0 || second >= java.lang.reflect.Array.getLength(row)) return null;
        return java.lang.reflect.Array.get(row, second);
    }

    private static void hookGridCountSetter(Class<?> gridConfig, String method) {
        XposedHelpers.findAndHookMethod(gridConfig, method, int.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if ((Integer) param.args[0] == 6) param.args[0] = 8;
            }
        });
    }

    private static void hookGridCountGetter(Class<?> gridConfig, String method) {
        XposedHelpers.findAndHookMethod(gridConfig, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if ((Integer) param.getResult() == 6) param.setResult(8);
            }
        });
    }

    private static void hookAxis(Class<?> compat, String method, boolean xAxis) {
        XposedHelpers.findAndHookMethod(compat, method, Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) param.args[0];
                boolean portrait = context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
                // Rotation mapping: portrait X/Y=4/8, landscape X/Y=8/4.
                param.setResult(xAxis ? (portrait ? 4 : 8) : (portrait ? 8 : 4));
            }
        });
    }
}
