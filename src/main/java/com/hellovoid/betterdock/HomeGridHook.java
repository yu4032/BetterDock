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
    private static int landscapeIndicatorX, landscapeIndicatorY;
    private static int portraitIndicatorX, portraitIndicatorY;
    private static final java.util.WeakHashMap<android.view.View, int[]>
        indicatorBaseMargins = new java.util.WeakHashMap<>();

    private HomeGridHook() {}

    static void install(ClassLoader classLoader,
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
        try {
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
            installIndicatorPosition(classLoader);
            installCellLayoutMargins(classLoader);

            Class<?> builder = XposedHelpers.findClass(
                "com.miui.home.launcher.grid.GridConfig$GridConfigBuilder", classLoader);
            XposedHelpers.findAndHookMethod(builder, "build", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    applyContainerMargins(param.getResult());
                }
            });
            XposedBridge.log("[DC] home grid enabled: land="
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
                    applyCellLayoutMargins(param.thisObject);
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

    private static void applyCellLayoutMargins(Object cellLayout) {
        try {
            Object config = XposedHelpers.getObjectField(cellLayout, "mGridConfig");
            if (config == null) return;
            int countX = XposedHelpers.getIntField(config, "countX");
            int countY = XposedHelpers.getIntField(config, "countY");
            int cell = XposedHelpers.getIntField(config, "cellSize");
            if (countX <= 0 || cell <= 0) return;
            android.graphics.Point realSize = new android.graphics.Point();
            android.view.Display display = ((android.view.View) cellLayout).getDisplay();
            if (display == null) return;
            display.getRealSize(realSize);
            int width = realSize.x;
            int height = realSize.y;
            if (width <= 0 || height <= 0) return;
            boolean portrait = countY > countX;
            if ((portrait && width > height) || (!portrait && height > width)) {
                int swap = width;
                width = height;
                height = swap;
            }
            int left = portrait ? portraitLeft : landscapeLeft;
            int right = portrait ? portraitRight : landscapeRight;
            int top = portrait ? portraitTop : landscapeTop;
            int bottom = portrait ? portraitBottom : landscapeBottom;
            int freeWidth = Math.max(0,
                width - left - right - cell * countX);
            int widthGap = countX > 1 ? freeWidth / (countX - 1) : 0;
            int heightGap = portrait ? portraitRowGap : landscapeRowGap;
            activeRowGap = heightGap;
            int availableHeight = height - top - bottom
                - heightGap * Math.max(0, countY - 1);
            int cellHeight = Math.max(1, availableHeight / countY);
            XposedHelpers.setIntField(cellLayout, "mCellPaddingLeft", left);
            XposedHelpers.setIntField(cellLayout, "mCellPaddingTop", top);
            XposedHelpers.setIntField(cellLayout, "mCellWidth", cell);
            XposedHelpers.setIntField(cellLayout, "mCellHeight", cellHeight);
            XposedHelpers.setIntField(cellLayout, "mWidthGap", widthGap);
            XposedHelpers.setIntField(cellLayout, "mHeightGap", heightGap);
        } catch (Throwable e) {
            XposedBridge.log("[DC] CellLayout margin apply failed: " + e);
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
                            applyIndicatorMargins((android.view.View) indicator);
                    } catch (Throwable e) {
                        XposedBridge.log("[DC] indicator offset failed: " + e);
                    }
                }
            });
    }

    private static void applyIndicatorMargins(android.view.View indicator) {
        android.view.ViewGroup.LayoutParams raw = indicator.getLayoutParams();
        if (!(raw instanceof android.widget.FrameLayout.LayoutParams)) return;
        android.widget.FrameLayout.LayoutParams lp =
            (android.widget.FrameLayout.LayoutParams) raw;
        int[] base;
        synchronized (indicatorBaseMargins) {
            base = indicatorBaseMargins.get(indicator);
            if (base == null) {
                base = new int[] { lp.leftMargin, lp.topMargin,
                    lp.rightMargin, lp.bottomMargin };
                indicatorBaseMargins.put(indicator, base);
            }
        }
        boolean portrait = indicator.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_PORTRAIT;
        int offsetX = portrait ? portraitIndicatorX : landscapeIndicatorX;
        int offsetY = portrait ? portraitIndicatorY : landscapeIndicatorY;
        int gravity = lp.gravity;
        int horizontal = android.view.Gravity.getAbsoluteGravity(gravity,
            indicator.getLayoutDirection()) & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
        int vertical = gravity & android.view.Gravity.VERTICAL_GRAVITY_MASK;

        lp.leftMargin = base[0];
        lp.topMargin = base[1];
        lp.rightMargin = base[2];
        lp.bottomMargin = base[3];
        if (horizontal == android.view.Gravity.RIGHT) lp.rightMargin -= offsetX;
        else lp.leftMargin += offsetX;
        if (vertical == android.view.Gravity.BOTTOM) lp.bottomMargin -= offsetY;
        else lp.topMargin += offsetY;
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

    private static void applyContainerMargins(Object config) {
        if (config == null) return;
        try {
            int width = XposedHelpers.getIntField(config, "width");
            int height = XposedHelpers.getIntField(config, "height");
            int countX = XposedHelpers.getIntField(config, "countX");
            int countY = XposedHelpers.getIntField(config, "countY");
            if (width <= 0 || height <= 0 || countX <= 0 || countY <= 0) return;

            boolean portrait = countY > countX;
            // MIUI may reuse the smaller landscape/portrait base GridConfig after
            // rotation, leaving width and height in the opposite orientation.
            if ((portrait && width > height) || (!portrait && height > width)) {
                int swap = width;
                width = height;
                height = swap;
            }
            int marginLeft = portrait ? portraitLeft : landscapeLeft;
            int marginRight = portrait ? portraitRight : landscapeRight;
            int marginTop = portrait ? portraitTop : landscapeTop;
            int marginBottom = portrait ? portraitBottom : landscapeBottom;
            int availableWidth = Math.max(countX,
                width - marginLeft - marginRight);
            int availableHeight = Math.max(countY,
                height - marginTop - marginBottom);
            int newCell = Math.max(1, Math.min(availableWidth / countX,
                availableHeight / countY));

            // Margins are absolute screen insets. Do not add MIUI's defaults or
            // center the unused remainder: zero must start at screen coordinate zero.
            XposedHelpers.setIntField(config, "cellSize", newCell);
            XposedHelpers.setIntField(config, "left", marginLeft);
            XposedHelpers.setIntField(config, "top", marginTop);
        } catch (Throwable e) {
            XposedBridge.log("[DC] home grid margin apply failed: " + e);
        }
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
