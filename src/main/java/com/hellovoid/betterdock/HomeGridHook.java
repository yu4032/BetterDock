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

    static void install(ClassLoader classLoader, int left, int right, int top, int bottom,
                        boolean nativeTwoByTwoFolders) {
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
            installRotationTransform(classLoader);
            if (nativeTwoByTwoFolders) installNativeTwoByTwoFolders(classLoader);

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

    private static void installNativeTwoByTwoFolders(ClassLoader classLoader) {
        Class<?> deviceConfig = XposedHelpers.findClass(
            "com.miui.home.launcher.DeviceConfig", classLoader);
        XposedHelpers.findAndHookMethod(deviceConfig, "isMingouTrueBigFolderEnabled",
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

        Class<?> convertController = XposedHelpers.findClass(
            "com.miui.home.launcher.convertsize.FolderIconConvertSizeController",
            classLoader);
        hookTwoByTwoSpan(convertController, "getFolderSpanXFromType");
        hookTwoByTwoSpan(convertController, "getFolderSpanYFromType");

        Class<?> folderInfo = XposedHelpers.findClass(
            "com.miui.home.launcher.FolderInfo", classLoader);
        Class<?> folderSheet = XposedHelpers.findClass(
            "com.miui.home.launcher.folder.FolderSheet", classLoader);
        XposedHelpers.findAndHookMethod(folderSheet, "initListener", folderInfo,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    exposeMingouFolderChoice(param.thisObject);
                }
            });
        XposedBridge.log("[DC] 2x2 folder choice enabled: 多宫格-棉狗, type=26");
    }

    private static void hookTwoByTwoSpan(Class<?> controller, String method) {
        XposedHelpers.findAndHookMethod(controller, method, int.class,
            new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if ((Integer) param.args[0] == 26) param.setResult(2);
                }
            });
    }

    private static void exposeMingouFolderChoice(Object folderSheet) {
        String[] fields = {
            "mBigFolderBorderLayout2x2_16",
            "mBigFolderSelectBorder2x2_16",
            "mFolderPickerSelectBigFolderImg2x2_16"
        };
        for (String field : fields) {
            try {
                Object value = XposedHelpers.getObjectField(folderSheet, field);
                if (value instanceof android.view.View)
                    ((android.view.View) value).setVisibility(android.view.View.VISIBLE);
            } catch (Throwable ignored) {}
        }
        try {
            Object value = XposedHelpers.getObjectField(folderSheet,
                "mBigFolderCheckBox2x2_16");
            if (value instanceof android.view.View) {
                android.view.View choice = (android.view.View) value;
                choice.setVisibility(android.view.View.VISIBLE);
                choice.setContentDescription("多宫格-棉狗");
                android.view.ViewParent parent = choice.getParent();
                if (parent instanceof android.view.ViewGroup) {
                    android.view.ViewGroup group = (android.view.ViewGroup) parent;
                    android.view.ViewGroup.LayoutParams params = choice.getLayoutParams();
                    group.removeView(choice);
                    group.addView(choice, Math.min(1, group.getChildCount()), params);
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("[DC] 2x2 folder picker reorder failed: " + e);
        }
        try {
            Object name = XposedHelpers.getObjectField(folderSheet,
                "mBigFolderName2x2_16");
            if (name instanceof android.widget.TextView) {
                android.widget.TextView text = (android.widget.TextView) name;
                text.setText("多宫格-棉狗");
                text.setVisibility(android.view.View.VISIBLE);
            }
        } catch (Throwable e) {
            XposedBridge.log("[DC] 2x2 folder picker label failed: " + e);
        }
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
