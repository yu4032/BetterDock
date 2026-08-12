package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Configuration;
import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class HomeGridHook {
    private static final String PAD_CELL_COUNT =
        "com.miui.home.launcher.compat.LauncherCellCountCompatPadDevice";

    private static int landscapeLeft, landscapeRight, landscapeTop, landscapeBottom;
    private static int portraitLeft, portraitRight, portraitTop, portraitBottom;
    private static int landscapeRowGap, portraitRowGap;
    private static boolean grid8x4Enabled;
    private static volatile boolean workstationMode;
    private static int workstationHorizontalOffset;
    private static java.lang.ref.WeakReference<android.view.View> workspaceRef =
            new java.lang.ref.WeakReference<>(null);
    private static float density;
    private static int landscapeIndicatorY, portraitIndicatorY;
    private static final java.util.WeakHashMap<android.view.View, float[]>
        indicatorBaseTranslations = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<android.view.View,
        android.view.ViewTreeObserver.OnPreDrawListener> indicatorPositionGuards =
            new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<android.view.View, Boolean>
        loggedWidgetViews = new java.util.WeakHashMap<>();

    private HomeGridHook() {}

    static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        scheduleAllPageRefresh();
    }

    static void setWorkstationHorizontalOffset(int offset) {
        workstationHorizontalOffset = offset;
    }

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
        // Layout customization is intentionally all-or-nothing. When 8x4 is disabled,
        // leave MIUI's native 6x4 CellLayout, indicator and folder measurement untouched.
        if (!enableGrid8x4) {
            MainHook.log("[DC] home grid customization disabled; using stock layout");
            return;
        }
        try {
            Class<?> compat = Class.forName(PAD_CELL_COUNT, false, classLoader);
            hookAxis(compat, "getCellCountXMin", true);
            hookAxis(compat, "getCellCountXDef", true);
            hookAxis(compat, "getCellCountYMin", false);
            hookAxis(compat, "getCellCountYDef", false);
            Class<?> gridConfig = Class.forName(
                "com.miui.home.launcher.grid.GridConfig", false, classLoader);
            hookGridCountSetter(gridConfig, "setCountX");
            hookGridCountSetter(gridConfig, "setCountY");
            hookGridCountGetter(gridConfig, "getCountX");
            hookGridCountGetter(gridConfig, "getCountY");
            installRotationTransform(classLoader);
            installIndicatorPosition(classLoader);
            installCellLayoutMargins(classLoader);
            installSmallFolderAlignment(classLoader);
            installRotationRefresh(classLoader);
            installWorkspaceRefresh(classLoader);
            MainHook.log("[DC] home grid hooks: 8x4=" + enableGrid8x4 + " land="
                + landscapeLeft + "," + landscapeRight + ","
                + landscapeTop + "," + landscapeBottom + " port="
                + portraitLeft + "," + portraitRight + ","
                + portraitTop + "," + portraitBottom);

        } catch (Throwable e) {
            MainHook.log("[DC] home grid hook unavailable: " + e);
        }
    }

    /** FolderIcon1x1 calculates its top padding from GridConfig.cellSize. In 8x4 mode the
     * actual CellLayout cell is recomputed smaller while GridConfig retains the 6x4 size,
     * so folders are pushed lower than normal ItemIcons. Rebase only its vertical padding
     * on the parent CellLayout's real cell width; large folders/widgets are untouched. */
    private static void installSmallFolderAlignment(ClassLoader classLoader) {
        Class<?> folder;
        try {
            folder = Class.forName(
                    "com.miui.home.launcher.folder.FolderIcon1x1", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        HookUtil.hookMethod(folder, "onMeasure", new Class[]{int.class, int.class},
                chain -> {
                    android.view.View view = (android.view.View) chain.getThisObject();
                    Object parent = view.getParent();
                    Object config = null;
                    Integer original = null;
                    if (parent != null && parent.getClass().getName().endsWith("CellLayout")) {
                        try {
                            int cell = HookUtil.getIntField(parent, "mCellHeight");
                            config = HookUtil.getField(parent, "mGridConfig");
                            if (cell > 0 && config != null) {
                                original = HookUtil.getIntField(config, "cellSize");
                                HookUtil.setIntField(config, "cellSize", cell);
                            }
                        } catch (Throwable e) {
                            MainHook.log("[DC] small folder alignment failed: " + e);
                        }
                    }
                    Object result;
                    try {
                        result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    } catch (Throwable e) {
                        // Restore before rethrow
                        if (config != null && original != null) {
                            try { HookUtil.setIntField(config, "cellSize", original); }
                            catch (Throwable t) {
                                MainHook.log("[DC] small folder grid restore failed: " + t);
                            }
                        }
                        throw e;
                    }
                    if (config != null && original != null) {
                        try { HookUtil.setIntField(config, "cellSize", original); }
                        catch (Throwable e) {
                            MainHook.log("[DC] small folder grid restore failed: " + e);
                        }
                    }
                    return result;
                });
    }

    private static void installCellLayoutMargins(ClassLoader classLoader) {
        Class<?> cellLayout;
        try {
            cellLayout = Class.forName(
                "com.miui.home.launcher.CellLayout", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        HookUtil.hookMethod(cellLayout, "calculateXsAndYs", new Class[]{},
            chain -> {
                Object thisObj = chain.getThisObject();
                applyCellLayoutOffsets(thisObj);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                // Portrait GridConfig recalculates mHeightGap/mYs inside the original
                // method, undoing our pre-hook values. Re-apply the geometry and build
                // the final coordinate arrays after MIUI has finished.
                applyCellLayoutOffsets(thisObj);
                rebuildCellCoordinates(thisObj);
                return result;
            });
        HookUtil.hookMethod(cellLayout, "onLayout",
            new Class[]{boolean.class, int.class, int.class, int.class, int.class},
            chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                android.view.ViewGroup layout = (android.view.ViewGroup) chain.getThisObject();
                for (int i = 0; i < layout.getChildCount(); i++)
                    adaptTwoByOneWidget(layout, layout.getChildAt(i));
                return result;
            });
    }

    private static void rebuildCellCoordinates(Object cellLayout) {
        try {
            int countX = HookUtil.getIntField(cellLayout, "mHCells");
            int countY = HookUtil.getIntField(cellLayout, "mVCells");
            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = HookUtil.getIntField(cellLayout, "mWidthGap");
            int heightGap = HookUtil.getIntField(cellLayout, "mHeightGap");
            int left = HookUtil.getIntField(cellLayout, "mCellPaddingLeft");
            int top = HookUtil.getIntField(cellLayout, "mCellPaddingTop");
            if (countX <= 0 || countY <= 0 || cellWidth <= 0 || cellHeight <= 0) return;
            int[] xs = new int[countX];
            int[] ys = new int[countY];
            for (int x = 0; x < countX; x++) xs[x] = left + x * (cellWidth + widthGap);
            for (int y = 0; y < countY; y++) ys[y] = top + y * (cellHeight + heightGap);
            HookUtil.setField(cellLayout, "mXs", xs);
            HookUtil.setField(cellLayout, "mYs", ys);
        } catch (Throwable e) {
            MainHook.log("[DC] final cell coordinate rebuild failed: " + e);
        }
    }

    private static void adaptTwoByOneWidget(android.view.ViewGroup parent,
                                            android.view.View child) {
        try {
            Object info = child.getTag();
            if (info == null) return;
            int itemType = HookUtil.getIntField(info, "itemType");
            int spanX = HookUtil.getIntField(info, "spanX");
            int spanY = HookUtil.getIntField(info, "spanY");
            if (spanX != 2 || spanY != 1) return;
            synchronized (loggedWidgetViews) {
                if (!loggedWidgetViews.containsKey(child)) {
                    loggedWidgetViews.put(child, Boolean.TRUE);
                    MainHook.log("[DC] 2x1 runtime view type=" + itemType
                        + " class=" + child.getClass().getName()
                        + " bounds=" + child.getWidth() + "x" + child.getHeight()
                        + " children=" + (child instanceof android.view.ViewGroup
                            ? ((android.view.ViewGroup) child).getChildCount() : -1));
                }
            }
            if (itemType != 19) return;
            int widthGap = Math.max(0, HookUtil.getIntField(parent, "mWidthGap"));
            int visualCompensation = Math.round(
                child.getResources().getDisplayMetrics().density * 16f);
            int shrink = Math.max(widthGap, visualCompensation);
            int leftInset = shrink / 2;
            int rightInset = shrink - leftInset;
            int width = child.getWidth() - shrink;
            if (width <= 0) return;
            child.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    child.getHeight(), android.view.View.MeasureSpec.EXACTLY));
            child.layout(child.getLeft() + leftInset, child.getTop(),
                child.getRight() - rightInset, child.getBottom());
        } catch (Throwable e) {
            MainHook.log("[DC] 2x1 widget adaptation failed: " + e);
        }
    }

    private static void applyCellLayoutOffsets(Object cellLayout) {
        try {
            Object config = HookUtil.getField(cellLayout, "mGridConfig");
            if (config == null) return;
            android.view.View layout = (android.view.View) cellLayout;
            boolean portrait = layout.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
            int countX = (Integer) HookUtil.invoke(config, "getCountX");
            int countY = (Integer) HookUtil.invoke(config, "getCountY");
            if (countX <= 0 || countY <= 0) return;
            Object gridCells = HookUtil.getField(cellLayout, "mGridCell");
            if (gridCells != null) {
                int matrixX = java.lang.reflect.Array.getLength(gridCells);
                int matrixY = matrixX == 0 ? 0
                    : java.lang.reflect.Array.getLength(java.lang.reflect.Array.get(gridCells, 0));
                if (matrixX != countX || matrixY != countY) {
                    MainHook.log("[DC] grid count/matrix mismatch: config="
                        + countX + "x" + countY + " matrix=" + matrixX + "x" + matrixY);
                    countX = matrixX;
                    countY = matrixY;
                }
            }
            if (countX <= 0 || countY <= 0) return;
            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (xs == null || xs.length != countX)
                HookUtil.setField(cellLayout, "mXs", new int[countX]);
            if (ys == null || ys.length != countY)
                HookUtil.setField(cellLayout, "mYs", new int[countY]);
            HookUtil.setIntField(cellLayout, "mHCells", countX);
            HookUtil.setIntField(cellLayout, "mVCells", countY);
            int baseCell = (Integer) HookUtil.invoke(config, "getCellSize");
            int configLeft = (Integer) HookUtil.invoke(config, "getLeft");
            int baseTop = (Integer) HookUtil.invoke(config, "getTop");
            int baseWidthGap = HookUtil.getIntField(cellLayout, "mWidthGap");
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
                int dockBarHeight = 0;
                try {
                    dockBarHeight = Math.max(0, (Integer) HookUtil.invoke(
                            config, "getDockBarHeight"));
                } catch (Throwable ignored) {}
                int contentHeight = Math.max(baseCell * countY,
                        height - Math.min(height, dockBarHeight));
                baseWidthGap = 0;
                baseHeightGap = Math.max(1, Math.round(density));
                baseLeft = Math.max(0, (width - baseCell * countX) / 2);
                baseTop = Math.max(0, (contentHeight - baseCell * countY
                    - baseHeightGap * Math.max(0, countY - 1)) / 2);
            }

            int baseRight = width - (baseLeft + baseCell * countX
                + baseWidthGap * Math.max(0, countX - 1));
            int baseBottom = height - (baseTop + baseCell * countY
                + baseHeightGap * Math.max(0, countY - 1));
            boolean workstation = workstationMode || MainHook.isWorkstationMode();
            int left = baseLeft + (workstation ? workstationHorizontalOffset
                    : (portrait ? portraitLeft : landscapeLeft));
            int right = baseRight + (workstation ? workstationHorizontalOffset
                    : (portrait ? portraitRight : landscapeRight));
            int top = baseTop + (workstation ? 0 : (portrait ? portraitTop : landscapeTop));
            int bottom = baseBottom + (workstation ? 0 : (portrait ? portraitBottom : landscapeBottom));
            int rowGap = baseHeightGap + (workstation ? 0
                    : (portrait ? portraitRowGap : landscapeRowGap));

            int availableWidth = Math.max(countX, width - left - right);
            int availableHeight = height - top - bottom
                - rowGap * Math.max(0, countY - 1);
            int cellSize = Math.min(baseCell, Math.min(
                Math.max(1, availableWidth / countX),
                Math.max(1, availableHeight / countY)));
            int widthGap = countX > 1
                ? Math.max(0, availableWidth - cellSize * countX) / (countX - 1) : 0;
            HookUtil.setIntField(cellLayout, "mCellPaddingLeft", left);
            HookUtil.setIntField(cellLayout, "mCellPaddingTop", top);
            HookUtil.setIntField(cellLayout, "mCellWidth", cellSize);
            HookUtil.setIntField(cellLayout, "mCellHeight", cellSize);
            HookUtil.setIntField(cellLayout, "mWidthGap", widthGap);
            HookUtil.setIntField(cellLayout, "mHeightGap", rowGap);
        } catch (Throwable e) {
            MainHook.log("[DC] CellLayout offset apply failed: " + e);
        }
    }

    private static void installRotationRefresh(ClassLoader classLoader) {
        Class<?> launcher;
        try {
            launcher = Class.forName(
                "com.miui.home.launcher.Launcher", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
            new Class[]{Configuration.class}, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    final android.view.View workspace = (android.view.View)
                        HookUtil.getField(chain.getThisObject(), "mWorkspace");
                    if (workspace == null) return result;
                    workspaceRef = new java.lang.ref.WeakReference<>(workspace);
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
                    MainHook.log("[DC] rotation refresh hook failed: " + e);
                }
                return result;
            });
    }

    private static void installWorkspaceRefresh(ClassLoader classLoader) {
        HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                "setupViews", chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        Object candidate = HookUtil.getField(
                                chain.getThisObject(), "mWorkspace");
                        if (!(candidate instanceof android.view.View)) return result;
                        android.view.View workspace = (android.view.View) candidate;
                        workspaceRef = new java.lang.ref.WeakReference<>(workspace);
                        scheduleAllPageRefresh();
                    } catch (Throwable e) {
                        MainHook.log("[DC] workspace refresh bind failed: " + e);
                    }
                    return result;
                });
    }

    static void scheduleAllPageRefresh() {
        android.view.View workspace = workspaceRef.get();
        if (workspace == null) return;
        workspace.post(() -> refreshWorkspaceGrid(workspace));
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 180L);
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 500L);
    }

    private static void refreshWorkspaceGrid(android.view.View workspace) {
        try {
            int count = (Integer) HookUtil.invoke(workspace, "getScreenCount");
            for (int i = 0; i < count; i++) {
                Object cell = HookUtil.invoke(workspace, "getCellLayout",
                        new Class[]{int.class}, i);
                if (!(cell instanceof android.view.View)) continue;
                HookUtil.invoke(cell, "calculateXsAndYs");
                android.view.View page = (android.view.View) cell;
                page.forceLayout();
                page.requestLayout();
                page.invalidate();
            }
            workspace.forceLayout();
            workspace.requestLayout();
            workspace.invalidate();
        } catch (Throwable e) {
            MainHook.log("[DC] rotation grid refresh failed: " + e);
        }
    }

    private static void installIndicatorPosition(ClassLoader classLoader) {
        Class<?> screenView;
        Class<?> workspace;
        try {
            screenView = Class.forName(
                "com.miui.home.launcher.ScreenView", false, classLoader);
            workspace = Class.forName(
                "com.miui.home.launcher.Workspace", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        final Class<?> wsClass = workspace;
        HookUtil.hookMethod(screenView, "updateIndicatorPositions",
            new Class[]{int.class, boolean.class}, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object thisObj = chain.getThisObject();
                if (wsClass.isInstance(thisObj)) {
                    try {
                        Object indicator = HookUtil.invoke(thisObj, "getScreenIndicator");
                        if (indicator instanceof android.view.View)
                            restoreIndicatorTranslation((android.view.View) indicator);
                    } catch (Throwable e) {
                        MainHook.log("[DC] indicator offset failed: " + e);
                    }
                }
                Object result = chain.proceed(args);
                if (wsClass.isInstance(thisObj)) {
                    try {
                        Object indicator = HookUtil.invoke(thisObj, "getScreenIndicator");
                        if (indicator instanceof android.view.View)
                            captureAndApplyIndicatorTranslation((android.view.View) indicator);
                    } catch (Throwable e) {
                        MainHook.log("[DC] indicator offset failed: " + e);
                    }
                }
                return result;
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
        if (workstationMode || MainHook.isWorkstationMode()) {
            indicator.setTranslationX(base[0]);
            indicator.setTranslationY(base[1]);
            return;
        }
        boolean portrait = indicator.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_PORTRAIT;
        float targetY = base[1] + (portrait ? portraitIndicatorY : landscapeIndicatorY);
        indicator.setTranslationX(base[0]);
        if (indicator.getTranslationY() != targetY) indicator.setTranslationY(targetY);
    }

    private static void installRotationTransform(ClassLoader classLoader) {
        Class<?> helper;
        Class<?> transformInfo;
        try {
            helper = Class.forName(
                "com.miui.home.launcher.compat.LayoutTransformHelperGridChanged", false, classLoader);
            transformInfo = Class.forName(
                "com.miui.home.launcher.bean.LayoutTransformInfo", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Class<?> arrayType = java.lang.reflect.Array.newInstance(transformInfo, 0, 0).getClass();
        HookUtil.hookMethod(helper, "addOccupied",
            new Class[]{arrayType, transformInfo, int.class, int.class, int.class, int.class},
            chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object matrix = args[0];
                int cellX = (Integer) args[2];
                int cellY = (Integer) args[3];
                int spanX = (Integer) args[4];
                int spanY = (Integer) args[5];
                fillTransformMatrix(matrix, args[1],
                    cellX, cellY, spanX, spanY);
                return null;
            });
        HookUtil.hookMethod(helper, "transformToHVArray", new Class[]{},
            chain -> {
                Object thisObj = chain.getThisObject();
                int width = (Integer) HookUtil.invoke(thisObj, "getMHCells");
                int height = (Integer) HookUtil.invoke(thisObj, "getMVCells");
                Object matrix = HookUtil.invoke(thisObj, "getMDstOccupied");
                android.view.View[][] result = new android.view.View[width][height];
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        Object info = getTransformCell(matrix, x, y, width, height);
                        if (info == null) continue;
                        Object data = HookUtil.invoke(info, "getMData");
                        if (data instanceof android.view.View)
                            result[x][y] = (android.view.View) data;
                    }
                }
                return result;
            });
        Class<?> rule;
        try {
            rule = Class.forName(
                "com.miui.home.launcher.compat.LayoutTransformRuleGridChanged", false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        for (Constructor<?> ctor : rule.getDeclaredConstructors()) {
            HookUtil.hook(ctor, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                int h = (Integer) args[0];
                int v = (Integer) args[1];
                if (!((h == 8 && v == 4) || (h == 4 && v == 8))) return result;
                int[][] portrait = new int[][] {
                    {0, 0}, {2, 0}, {0, 2}, {2, 2},
                    {0, 4}, {2, 4}, {0, 6}, {2, 6}
                };
                int[][] landscape = new int[][] {
                    {0, 0}, {2, 0}, {4, 0}, {6, 0},
                    {0, 2}, {2, 2}, {4, 2}, {6, 2}
                };
                Object thisObj = chain.getThisObject();
                HookUtil.setField(thisObj, "vScreenCoordinate", portrait);
                HookUtil.setField(thisObj, "hScreenCoordinate", landscape);
                HookUtil.setIntField(thisObj, "totalBlocks", 8);
                return result;
            });
        }
        HookUtil.hookMethod(rule, "checkCellCount", new Class[]{},
            chain -> {
                Object thisObj = chain.getThisObject();
                int h = (Integer) HookUtil.invoke(thisObj, "getMHCells");
                int v = (Integer) HookUtil.invoke(thisObj, "getMVCells");
                if ((h == 8 && v == 4) || (h == 4 && v == 8)) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
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
        HookUtil.hookMethod(gridConfig, method, new Class[]{int.class},
            chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if ((Integer) args[0] == 6) args[0] = 8;
                return chain.proceed(args);
            });
    }

    private static void hookGridCountGetter(Class<?> gridConfig, String method) {
        HookUtil.hookMethod(gridConfig, method, new Class[]{},
            chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if ((Integer) result == 6) result = 8;
                return result;
            });
    }

    private static void hookAxis(Class<?> compat, String method, boolean xAxis) {
        HookUtil.hookMethod(compat, method, new Class[]{Context.class},
            chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Context context = (Context) args[0];
                boolean portrait = context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
                // Rotation mapping: portrait X/Y=4/8, landscape X/Y=8/4.
                return xAxis ? (portrait ? 4 : 8) : (portrait ? 8 : 4);
            });
    }

}
