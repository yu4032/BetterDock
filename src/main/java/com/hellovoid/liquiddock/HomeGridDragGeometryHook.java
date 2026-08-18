package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.GridProfileConfig;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * Keeps MIUI's drag/drop rules on the same geometry as the visible 10x6 CellLayout.
 *
 * The stock drop rules convert points using DeviceConfig/GridConfig cellSize, while the
 * extended grid deliberately recomputes CellLayout.mCellWidth/mCellHeight and gaps without
 * mutating the global GridConfig cellSize. A thread-local context is used because the drop
 * rule objects themselves do not retain their owning CellLayout.
 */
final class HomeGridDragGeometryHook {
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String CELL_SCREEN = "com.miui.home.launcher.CellScreen";
    private static final String DRAG_OBJECT = "com.miui.home.launcher.DragObject";
    private static final String DEVICE_CONFIG = "com.miui.home.launcher.DeviceConfig";
    private static final String SWAP_RULE =
            "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces";
    private static final String SQUEEZE_RULE =
            "com.miui.home.launcher.compat.LayoutDropRuleSqueezePlaces";

    private static final ThreadLocal<Geometry> ACTIVE_GEOMETRY = new ThreadLocal<>();
    private static final ThreadLocal<Integer> TOUCH_CELL_HEIGHT = new ThreadLocal<>();

    private HomeGridDragGeometryHook() {}

    static void install(ClassLoader classLoader) {
        ConfigReader reader = ConfigReader.load();
        boolean enabled = reader.has(GridProfileConfig.ENABLED_KEY)
                ? reader.b(GridProfileConfig.ENABLED_KEY, GridProfileConfig.DEFAULT_ENABLED)
                : reader.b(GridProfileConfig.LEGACY_8X4_KEY, GridProfileConfig.DEFAULT_ENABLED);
        HomeGridProfile profile = HomeGridProfile.fromPersisted(reader.s(
                GridProfileConfig.PROFILE_KEY, GridProfileConfig.DEFAULT_PROFILE));
        if (!enabled || profile != HomeGridProfile.GRID_10X6) return;

        try {
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            Class<?> dragObject = Class.forName(DRAG_OBJECT, false, classLoader);
            installDropTargetContext(cellLayout, dragObject);
            installScaledTouchHeight(classLoader, dragObject);
            installSwapRule(classLoader);
            installSqueezeRule(classLoader);
            MainHook.log("[DC] 10x6 live drag geometry hooks installed");
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 live drag geometry unavailable: " + error);
        }
    }

    /** Every native drop-rule query made from findDropTargetPosition sees this page's geometry. */
    private static void installDropTargetContext(Class<?> cellLayout, Class<?> dragObject)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(cellLayout, "findDropTargetPosition",
                new Class<?>[]{dragObject, boolean.class});
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Geometry geometry = readGeometry(chain.getThisObject());
                    if (geometry == null) return chain.proceed();
                    Geometry previous = ACTIVE_GEOMETRY.get();
                    ACTIVE_GEOMETRY.set(geometry);
                    try {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    } finally {
                        restore(ACTIVE_GEOMETRY, previous);
                    }
                });
    }

    /**
     * CellScreen.translateTouchY() uses DeviceConfig.getCellHeight() only for the final-row
     * boundary while all row origins come from CellLayout.setupLayoutParam(). Supply the live
     * CellLayout height for that one native call without changing DeviceConfig globally.
     */
    private static void installScaledTouchHeight(ClassLoader classLoader, Class<?> dragObject)
            throws Exception {
        Class<?> cellScreen = Class.forName(CELL_SCREEN, false, classLoader);
        Method translateY = HookUtil.findMethodExact(cellScreen, "translateTouchY",
                new Class<?>[]{dragObject, float.class, float.class});
        Api101Bridge.module().hook(translateY)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object cellLayout;
                    try {
                        cellLayout = HookUtil.getField(chain.getThisObject(), "mCellLayout");
                    } catch (Throwable ignored) {
                        return chain.proceed();
                    }
                    Geometry geometry = readGeometry(cellLayout);
                    if (geometry == null) return chain.proceed();
                    Integer previous = TOUCH_CELL_HEIGHT.get();
                    TOUCH_CELL_HEIGHT.set(geometry.cellHeight);
                    try {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    } finally {
                        restore(TOUCH_CELL_HEIGHT, previous);
                    }
                });

        Class<?> deviceConfig = Class.forName(DEVICE_CONFIG, false, classLoader);
        Method getCellHeight = HookUtil.findMethodExact(
                deviceConfig, "getCellHeight", new Class<?>[0]);
        Api101Bridge.module().hook(getCellHeight)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Integer override = TOUCH_CELL_HEIGHT.get();
                    return override != null ? override : chain.proceed();
                });
    }

    private static void installSwapRule(ClassLoader classLoader) throws Exception {
        Class<?> rule = Class.forName(SWAP_RULE, false, classLoader);

        Method cellToPoint = HookUtil.findMethodExact(rule, "cellToPoint",
                new Class<?>[]{int.class, int.class, int[].class});
        Api101Bridge.module().hook(cellToPoint)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Geometry geometry = ACTIVE_GEOMETRY.get();
                    Object outValue = chain.getArg(2);
                    if (geometry == null || !(outValue instanceof int[])) return chain.proceed();
                    int[] out = (int[]) outValue;
                    if (out.length < 2) return chain.proceed();
                    int[] point = HomeGridDragGeometryPolicy.cellToPoint(
                            ((Number) chain.getArg(0)).intValue(),
                            ((Number) chain.getArg(1)).intValue(),
                            geometry.paddingLeft, geometry.paddingTop,
                            geometry.cellWidth, geometry.cellHeight,
                            geometry.widthGap, geometry.heightGap);
                    out[0] = point[0];
                    out[1] = point[1];
                    return null;
                });

        Method isLegalXY = HookUtil.findMethodExact(rule, "isLegalXY",
                new Class<?>[]{int.class, int.class, int.class, int.class});
        Api101Bridge.module().hook(isLegalXY)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Geometry geometry = ACTIVE_GEOMETRY.get();
                    if (geometry == null) return chain.proceed();
                    return HomeGridDragGeometryPolicy.isSwapPlacementLegal(
                            ((Number) chain.getArg(0)).intValue(),
                            ((Number) chain.getArg(1)).intValue(),
                            ((Number) chain.getArg(2)).intValue(),
                            ((Number) chain.getArg(3)).intValue(),
                            geometry.countX, geometry.countY);
                });
    }

    private static void installSqueezeRule(ClassLoader classLoader) throws Exception {
        Class<?> rule = Class.forName(SQUEEZE_RULE, false, classLoader);
        Method pointToCell = HookUtil.findMethodExact(rule, "pointToCell",
                new Class<?>[]{int.class, int.class, int[].class, int.class, int.class});
        Api101Bridge.module().hook(pointToCell)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Geometry geometry = ACTIVE_GEOMETRY.get();
                    Object outValue = chain.getArg(2);
                    if (geometry == null || !(outValue instanceof int[])) return chain.proceed();
                    int countX = ((Number) chain.getArg(3)).intValue();
                    int countY = ((Number) chain.getArg(4)).intValue();
                    if (countX != geometry.countX || countY != geometry.countY) {
                        return chain.proceed();
                    }
                    int[] out = (int[]) outValue;
                    if (out.length < 2) return chain.proceed();
                    int[] cell = HomeGridDragGeometryPolicy.pointToCell(
                            ((Number) chain.getArg(0)).intValue(),
                            ((Number) chain.getArg(1)).intValue(),
                            geometry.paddingLeft, geometry.paddingTop,
                            geometry.cellWidth, geometry.cellHeight,
                            geometry.widthGap, geometry.heightGap,
                            countX, countY);
                    out[0] = cell[0];
                    out[1] = cell[1];
                    return null;
                });
    }

    private static Geometry readGeometry(Object cellLayout) {
        if (cellLayout == null || MainHook.isWorkstationMode()) return null;
        try {
            int countX = HookUtil.getIntField(cellLayout, "mHCells");
            int countY = HookUtil.getIntField(cellLayout, "mVCells");
            if (!isTenBySix(countX, countY)) return null;
            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            if (cellWidth <= 0 || cellHeight <= 0) return null;
            return new Geometry(
                    countX, countY,
                    cellWidth, cellHeight,
                    Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap")),
                    Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap")),
                    HookUtil.getIntField(cellLayout, "mCellPaddingLeft"),
                    HookUtil.getIntField(cellLayout, "mCellPaddingTop"));
        } catch (Throwable error) {
            MainHook.log("[DC] live drag geometry read failed: " + error);
            return null;
        }
    }

    private static boolean isTenBySix(int countX, int countY) {
        return (countX == 10 && countY == 6) || (countX == 6 && countY == 10);
    }

    private static <T> void restore(ThreadLocal<T> local, T previous) {
        if (previous == null) local.remove();
        else local.set(previous);
    }

    private static final class Geometry {
        final int countX;
        final int countY;
        final int cellWidth;
        final int cellHeight;
        final int widthGap;
        final int heightGap;
        final int paddingLeft;
        final int paddingTop;

        Geometry(int countX, int countY,
                 int cellWidth, int cellHeight,
                 int widthGap, int heightGap,
                 int paddingLeft, int paddingTop) {
            this.countX = countX;
            this.countY = countY;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.widthGap = widthGap;
            this.heightGap = heightGap;
            this.paddingLeft = paddingLeft;
            this.paddingTop = paddingTop;
        }
    }
}
