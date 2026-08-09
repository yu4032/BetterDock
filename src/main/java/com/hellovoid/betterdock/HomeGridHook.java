package com.hellovoid.betterdock;

import android.content.Context;
import android.content.res.Configuration;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class HomeGridHook {
    private static final String PAD_CELL_COUNT =
        "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";

    private static int marginLeft;
    private static int marginRight;
    private static int marginTop;
    private static int marginBottom;

    private HomeGridHook() {}

    static void install(ClassLoader classLoader, int left, int right, int top, int bottom) {
        marginLeft = Math.max(0, left);
        marginRight = Math.max(0, right);
        marginTop = Math.max(0, top);
        marginBottom = Math.max(0, bottom);
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
            Class<?> builder = XposedHelpers.findClass(
                "com.miui.home.launcher.grid.GridConfig$GridConfigBuilder", classLoader);
            XposedHelpers.findAndHookMethod(builder, "build", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    applyContainerMargins(param.getResult());
                }
            });
            XposedBridge.log("[DC] home grid demo enabled: GridConfig 6->8, margins="
                + marginLeft + "," + marginRight + "," + marginTop + "," + marginBottom);

        } catch (Throwable e) {
            XposedBridge.log("[DC] home grid hook unavailable: " + e);
        }
    }

    private static void applyContainerMargins(Object config) {
        if (config == null) return;
        try {
            int width = XposedHelpers.getIntField(config, "width");
            int countX = XposedHelpers.getIntField(config, "countX");
            int countY = XposedHelpers.getIntField(config, "countY");
            int oldCell = XposedHelpers.getIntField(config, "cellSize");
            int oldTop = XposedHelpers.getIntField(config, "top");
            if (width <= 0 || countX <= 0 || countY <= 0 || oldCell <= 0) return;

            int availableWidth = Math.max(countX, width - marginLeft - marginRight);
            int oldGridHeight = oldCell * countY;
            int availableHeight = Math.max(countY,
                oldGridHeight - marginTop - marginBottom);
            int newCell = Math.max(1, Math.min(availableWidth / countX,
                availableHeight / countY));
            int newLeft = marginLeft
                + Math.max(0, availableWidth - newCell * countX) / 2;
            int newTop = oldTop + marginTop
                + Math.max(0, availableHeight - newCell * countY) / 2;

            XposedHelpers.setIntField(config, "cellSize", newCell);
            XposedHelpers.setIntField(config, "left", newLeft);
            XposedHelpers.setIntField(config, "top", newTop);
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
