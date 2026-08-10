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
    private static int activeRowGap;
    private static Class<?> deviceConfigClass;
    private static boolean grid8x4Enabled;
    private static float density;
    private static int landscapeIndicatorX, landscapeIndicatorY;
    private static int portraitIndicatorX, portraitIndicatorY;
    private static final java.util.WeakHashMap<android.view.View, float[]>
        indicatorBaseTranslations = new java.util.WeakHashMap<>();

    private HomeGridHook() {}

    static void install(ClassLoader classLoader, boolean enableGrid8x4,
                        int landLeft, int landRight, int landTop, int landBottom,
                        int portLeft, int portRight, int portTop, int portBottom,
                        int landRowGap, int portRowGap,
                        int landIndicatorX, int landIndicatorY,
                        int portIndicatorX, int portIndicatorY) {
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
        landscapeIndicatorX = landIndicatorX;
        landscapeIndicatorY = landIndicatorY;
        portraitIndicatorX = portIndicatorX;
        portraitIndicatorY = portIndicatorY;
        grid8x4Enabled = enableGrid8x4;
        density = android.content.res.Resources.getSystem().getDisplayMetrics().density;
        try {
            deviceConfigClass = XposedHelpers.findClass(
                "com.miui.home.launcher.DeviceConfig", classLoader);
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
        XposedHelpers.findAndHookMethod(cellLayout, "calculateY",
            int.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    int index = (Integer) param.args[0];
                    int padding = (Integer) param.args[1];
                    int cell = (Integer) param.args[2];
                    param.setResult(padding + index * (cell + activeRowGap));
                }
            });
    }

    private static void applyCellLayoutOffsets(Object cellLayout) {
        try {
            Object config = XposedHelpers.getObjectField(cellLayout, "mGridConfig");
            if (config == null) return;
            int countX = XposedHelpers.getIntField(config, "countX");
            int countY = XposedHelpers.getIntField(config, "countY");
            if (countX <= 0 || countY <= 0) return;
            int baseCell = (Integer) XposedHelpers.callMethod(config, "getCellSize");
            int configLeft = (Integer) XposedHelpers.callMethod(config, "getLeft");
            int baseTop = (Integer) XposedHelpers.callMethod(config, "getTop");
            int baseWidthGap = deviceConfigClass == null ? 0 : (Integer)
                XposedHelpers.callStaticMethod(deviceConfigClass,
                    "getMingouAdaptWiderScreenWidthGap", baseCell);
            int baseLeft = configLeft
                - Math.max(0, countX - 1) * (baseWidthGap / 2);
            int baseHeightGap = 1;
            if (baseCell <= 0) return;

            android.graphics.Point size = new android.graphics.Point();
            android.view.Display display = ((android.view.View) cellLayout).getDisplay();
            if (display == null) return;
            display.getRealSize(size);
            boolean portrait = countY > countX;
            int width = portrait ? Math.min(size.x, size.y) : Math.max(size.x, size.y);
            int height = portrait ? Math.max(size.x, size.y) : Math.min(size.x, size.y);

            // With the 8x4 count hooks MIUI can retain the opposite orientation's
            // GridConfig after rotation. Use the established Pad defaults as the
            // orientation-specific baseline; native 6x4 continues using MIUI's
            // live GridConfig values.
            if (grid8x4Enabled) {
                baseLeft = Math.round((portrait ? 28f : 57f) * density);
                baseTop = Math.round((portrait ? 57f : 28f) * density);
                baseWidthGap = 0;
                baseHeightGap = Math.max(1, Math.round(density));
                int defaultRight = baseLeft;
                int available = Math.max(countX,
                    width - baseLeft - defaultRight);
                baseCell = Math.max(1, available / countX);
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
            activeRowGap = rowGap;

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
        boolean portrait = indicator.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_PORTRAIT;
        int offsetX = portrait ? portraitIndicatorX : landscapeIndicatorX;
        int offsetY = portrait ? portraitIndicatorY : landscapeIndicatorY;
        indicator.setTranslationX(base[0] + offsetX);
        indicator.setTranslationY(base[1] + offsetY);
    }

    private static void installRotationTransform(ClassLoader classLoader) {
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
