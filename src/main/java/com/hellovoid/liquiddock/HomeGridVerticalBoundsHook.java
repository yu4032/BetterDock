package com.hellovoid.liquiddock;

import android.content.res.Configuration;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Owns custom-grid vertical geometry independently from horizontal width. */
final class HomeGridVerticalBoundsHook {
    private HomeGridVerticalBoundsHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile, LiquidDockConfig.Grid grid) {
        if (!customGridEnabled || selectedProfile == null) return;
        try {
            Class<?> cellLayout = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, classLoader);

            Method calculate = HookUtil.findMethodExact(
                    cellLayout, "calculateXsAndYs", new Class<?>[0]);
            Api101Bridge.module().hook(calculate)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        fitVerticalGeometry(chain.getThisObject(), selectedProfile, grid);
                        return result;
                    });

            Method onLayout = HookUtil.findMethodExact(cellLayout, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            Api101Bridge.module().hook(onLayout)
                    .setPriority(XposedInterface.PRIORITY_LOWEST)
                    .intercept(chain -> {
                        fitVerticalGeometry(chain.getThisObject(), selectedProfile, grid);
                        Object result = chain.proceed();
                        fitVerticalGeometry(chain.getThisObject(), selectedProfile, grid);
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] custom-grid vertical geometry unavailable: " + error);
        }
    }

    private static void fitVerticalGeometry(Object target, HomeGridProfile profile,
                                            LiquidDockConfig.Grid grid) {
        if (!(target instanceof android.view.View) || MainHook.isWorkstationMode()) return;
        try {
            android.view.View view = (android.view.View) target;
            boolean portrait = view.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
            int height = view.getHeight();
            if (height <= 0) return;

            int countX = HookUtil.getIntField(target, "mHCells");
            int countY = HookUtil.getIntField(target, "mVCells");
            if (!profile.matchesCounts(countX, countY)) return;
            int rows = profile.rows(portrait);
            if (countY != rows) return;

            Object gridConfig = HookUtil.getField(target, "mGridConfig");
            if (gridConfig == null) return;
            int sourceCell = 0;
            try {
                Object value = HookUtil.invoke(gridConfig, "getCellSize");
                if (value instanceof Integer) sourceCell = (Integer) value;
            } catch (Throwable ignored) {}
            if (sourceCell <= 0) {
                try { sourceCell = HookUtil.getIntField(gridConfig, "cellSize"); }
                catch (Throwable ignored) {}
            }
            if (sourceCell <= 0) return;

            int dockBarHeight = 0;
            try {
                Object value = HookUtil.invoke(gridConfig, "getDockBarHeight");
                if (value instanceof Integer) dockBarHeight = Math.max(0, (Integer) value);
            } catch (Throwable ignored) {}

            float density = view.getResources().getDisplayMetrics().density;
            float scale = grid.dp ? density : 1f;
            int baseGap = Math.max(1, Math.round(density));
            float configuredGap = portrait ? grid.portraitRowGap : grid.landscapeRowGap;
            if (!grid.offsets) configuredGap -= grid.dp ? 1f : 3f;
            int gapAdjustment = Math.round(configuredGap * scale);
            int topAdjustment = Math.round(
                    (portrait ? grid.portraitTop : grid.landscapeTop) * scale);
            int bottomAdjustment = Math.round(
                    (portrait ? grid.portraitBottom : grid.landscapeBottom) * scale);

            HomeGridVerticalBoundsPolicy.Geometry geometry =
                    HomeGridVerticalBoundsPolicy.resolve(
                            height, rows, sourceCell, baseGap, gapAdjustment,
                            dockBarHeight, topAdjustment, bottomAdjustment);
            if (geometry.cellSize <= 0) return;

            int oldTop = HookUtil.getIntField(target, "mCellPaddingTop");
            int oldCell = HookUtil.getIntField(target, "mCellHeight");
            int oldGap = HookUtil.getIntField(target, "mHeightGap");
            if (oldTop == geometry.top && oldCell == geometry.cellSize
                    && oldGap == geometry.gap) {
                return;
            }

            HookUtil.setIntField(target, "mCellPaddingTop", geometry.top);
            HookUtil.setIntField(target, "mCellHeight", geometry.cellSize);
            HookUtil.setIntField(target, "mHeightGap", geometry.gap);
            rebuildYs(target, rows, geometry.top, geometry.cellSize, geometry.gap);
        } catch (Throwable error) {
            MainHook.log("[DC] custom-grid vertical geometry failed: " + error);
        }
    }

    private static void rebuildYs(Object target, int rows, int top,
                                  int cellSize, int gap) throws Exception {
        int[] ys = new int[rows];
        for (int y = 0; y < rows; y++) {
            ys[y] = top + y * (cellSize + gap);
        }
        HookUtil.setField(target, "mYs", ys);
    }
}
