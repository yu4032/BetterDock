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
    private static int workstationAllAppsLandscapeHorizontalOffset;
    private static int workstationAllAppsLandscapeVerticalOffset;
    private static int workstationAllAppsPortraitHorizontalOffset;
    private static int workstationAllAppsPortraitVerticalOffset;
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
    private static final java.util.WeakHashMap<android.view.View, Long>
        preparedCellLayoutGeometry = new java.util.WeakHashMap<>();

    private HomeGridHook() {}

    static void setWorkstationMode(boolean enabled) {
        workstationMode = enabled;
        scheduleAllPageRefresh();
    }

    static void setWorkstationHorizontalOffset(int offset) {
        workstationHorizontalOffset = offset;
    }

    static void setWorkstationAllAppsOffsets(int landscapeHorizontal, int landscapeVertical,
                                                    int portraitHorizontal, int portraitVertical) {
        workstationAllAppsLandscapeHorizontalOffset = landscapeHorizontal;
        workstationAllAppsLandscapeVerticalOffset = landscapeVertical;
        workstationAllAppsPortraitHorizontalOffset = portraitHorizontal;
        workstationAllAppsPortraitVerticalOffset = portraitVertical;
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
                android.view.ViewGroup layout = (android.view.ViewGroup) chain.getThisObject();

                // setupViews can create off-screen pages before they have usable bounds.
                // The Workspace-level 0/180/500 ms refresh therefore fixes page 1 while
                // page 2 may still keep MIUI's stock 6x4-derived mXs/mYs. Prime each
                // CellLayout from its own first valid bounds, before MIUI positions its
                // children, so lazy/off-screen pages cannot miss the 8x4 geometry pass.
                prepareCellLayoutGeometryForLayout(layout);

                Object result = chain.proceed(args);
                for (int i = 0; i < layout.getChildCount(); i++)
                    adaptTwoByOneWidget(layout, layout.getChildAt(i));
                return result;
            });
    }

    private static void prepareCellLayoutGeometryForLayout(android.view.View layout) {
        if (!grid8x4Enabled) return;
        int width = layout.getWidth();
        int height = layout.getHeight();
        if (width <= 0 || height <= 0 || !sizeMatchesOrientation(layout, width, height)) return;

        int orientation = layout.getResources().getConfiguration().orientation;
        long signature = (((long) orientation & 0xffL) << 56)
                ^ (((long) width & 0x0fffffffL) << 28)
                ^ ((long) height & 0x0fffffffL);
        synchronized (preparedCellLayoutGeometry) {
            Long previous = preparedCellLayoutGeometry.get(layout);
            if (previous != null && previous == signature) return;
            // Mark before applying so a nested/requested layout cannot recurse forever.
            preparedCellLayoutGeometry.put(layout, signature);
        }

        applyCellLayoutOffsets(layout);
        rebuildCellCoordinates(layout);
        MainHook.log("[DC] CellLayout geometry prepared "
                + width + "x" + height + " orientation=" + orientation);
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
            // MIUI can invoke calculateXsAndYs() after Configuration has switched but
            // before this CellLayout has the new orientation bounds. Never write grid
            // geometry from the previous orientation's stable size.
            if (!sizeMatchesOrientation(layout, width, height)) return;

            boolean workstation = workstationMode || MainHook.isWorkstationMode();
            boolean workstationAllApps = workstation && isLaptopAllApps(cellLayout);

            // Laptop All Apps has its own GridType/GridConfig.  The system DEX identifies
            // it through CellLayout.isInLapTopAllApps() and
            // GRID_TYPE_IN_ALL_APPS_WORKSPACE. Preserve that dedicated geometry instead
            // of replacing it with the normal Workspace centering formula. The 8x4 count
            // still applies; cellSize below shrinks as needed to stay inside this layout.
            if (workstationAllApps) {
                baseLeft = Math.max(0, configLeft);
                baseTop = Math.max(0, baseTop);
                baseWidthGap = Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap"));
                baseHeightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            } else if (grid8x4Enabled) {
                // With the 8x4 count hooks MIUI can retain the opposite orientation's
                // GridConfig after rotation. Use the established Pad defaults as the
                // orientation-specific baseline for normal Workspace pages.
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
            if (workstationAllApps) {
                // The stock All Apps config is sized for its own icon/search/indicator
                // stack.  With 8 columns the old cell size can make the derived far
                // margins negative; keep the native near margins and let cellSize shrink.
                baseRight = Math.max(0, baseRight);
                baseBottom = Math.max(0, baseBottom);
            }

            int workstationX = workstationAllApps
                    ? (portrait ? workstationAllAppsPortraitHorizontalOffset
                            : workstationAllAppsLandscapeHorizontalOffset)
                    : workstationHorizontalOffset;
            int workstationY = workstationAllApps
                    ? (portrait ? workstationAllAppsPortraitVerticalOffset
                            : workstationAllAppsLandscapeVerticalOffset)
                    : 0;
            if (workstation) {
                // Offsets are translations, not symmetric insets. Clamp them against the
                // native margins so the adjusted grid can never be pushed off-screen.
                workstationX = Math.max(-baseLeft, Math.min(baseRight, workstationX));
                workstationY = Math.max(-baseTop, Math.min(baseBottom, workstationY));
            }
            int left = baseLeft + (workstation ? workstationX
                    : (portrait ? portraitLeft : landscapeLeft));
            int right = baseRight + (workstation ? -workstationX
                    : (portrait ? portraitRight : landscapeRight));
            int top = baseTop + (workstation ? workstationY
                    : (portrait ? portraitTop : landscapeTop));
            int bottom = baseBottom + (workstation ? -workstationY
                    : (portrait ? portraitBottom : landscapeBottom));
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

    private static boolean isLaptopAllApps(Object cellLayout) {
        try {
            Object result = HookUtil.invoke(cellLayout, "isInLapTopAllApps");
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
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
                    Object candidate = HookUtil.getField(chain.getThisObject(), "mWorkspace");
                    if (!(candidate instanceof android.view.View)) return result;
                    android.view.View workspace = (android.view.View) candidate;
                    workspaceRef = new java.lang.ref.WeakReference<>(workspace);
                    scheduleStableRotationRefresh(workspace);
                } catch (Throwable e) {
                    MainHook.log("[DC] rotation refresh hook failed: " + e);
                }
                return result;
            });
    }

    private static void scheduleStableRotationRefresh(final android.view.View workspace) {
        workspace.requestLayout();
        workspace.post(new Runnable() {
            private int lastWidth = -1;
            private int lastHeight = -1;
            private int stableFrames;
            private int frames;

            @Override public void run() {
                if (!workspace.isAttachedToWindow()) return;
                int width = workspace.getWidth();
                int height = workspace.getHeight();
                if (width > 0 && height > 0) {
                    if (width == lastWidth && height == lastHeight) {
                        stableFrames++;
                    } else {
                        lastWidth = width;
                        lastHeight = height;
                        stableFrames = 0;
                    }
                }
                frames++;
                // Stable old bounds are still wrong bounds. Only settle after Workspace
                // dimensions match the new Configuration orientation and stay unchanged
                // for two consecutive frames. Do not force a frame-count fallback: that
                // was the source of the persistent landscape-down / portrait-right drift.
                boolean orientationReady = width > 0 && height > 0
                    && sizeMatchesOrientation(workspace, width, height);
                if (orientationReady && stableFrames >= 2) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L);
                    workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L);
                    return;
                }
                if (frames >= 180) {
                    MainHook.log("[DC] rotation grid wait timed out: ws="
                            + width + "x" + height + " orientation="
                            + workspace.getResources().getConfiguration().orientation);
                    return;
                }
                workspace.postOnAnimation(this);
            }
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

    private static boolean sizeMatchesOrientation(android.view.View view,
                                                  int width, int height) {
        int orientation = view.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) return height >= width;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) return width >= height;
        return true;
    }

    private static void refreshWorkspaceGridIfReady(android.view.View workspace) {
        int width = workspace.getWidth();
        int height = workspace.getHeight();
        if (width <= 0 || height <= 0
                || !sizeMatchesOrientation(workspace, width, height)) return;
        refreshWorkspaceGrid(workspace);
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
            java.util.ArrayList<android.view.View> pages = new java.util.ArrayList<>();
            collectWorkspaceCellLayouts(workspace, pages);
            if (pages.isEmpty()) {
                MainHook.log("[DC] rotation grid refresh: no CellLayout descendants");
                return;
            }
            for (android.view.View page : pages) {
                if (!sizeMatchesOrientation(page, page.getWidth(), page.getHeight())) continue;
                HookUtil.invoke(page, "calculateXsAndYs");
                page.forceLayout();
                page.requestLayout();
                page.invalidate();
            }
            workspace.forceLayout();
            workspace.requestLayout();
            workspace.invalidate();
            MainHook.log("[DC] rotation grid refreshed pages=" + pages.size()
                    + " ws=" + workspace.getWidth() + "x" + workspace.getHeight());
        } catch (Throwable e) {
            MainHook.log("[DC] rotation grid refresh failed: " + e);
        }
    }

    private static void collectWorkspaceCellLayouts(android.view.View view,
                                                     java.util.List<android.view.View> out) {
        if (view == null) return;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())) {
            out.add(view);
            return;
        }
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectWorkspaceCellLayouts(group.getChildAt(i), out);
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
        // MIUI owns the occupied-matrix storage and indexing. Replacing addOccupied()
        // or transformToHVArray() here is unsafe because 8x4/4x8 matrices may be stored
        // transposed; guessing the orientation per item mixes [x][y] and [y][x] writes.
        // Only extend the rule metadata so MIUI's native transform can handle the new grid.
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
                if (!isEightByFourGrid(h, v)) return result;
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
                if (isEightByFourGrid(h, v)) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
    }

    private static boolean isEightByFourGrid(int h, int v) {
        return (h == 8 && v == 4) || (h == 4 && v == 8);
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
