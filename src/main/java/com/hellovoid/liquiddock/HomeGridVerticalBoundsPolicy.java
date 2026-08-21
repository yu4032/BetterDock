package com.hellovoid.liquiddock;

/** Pure vertical geometry for a custom home grid. Horizontal width is intentionally absent. */
final class HomeGridVerticalBoundsPolicy {
    private HomeGridVerticalBoundsPolicy() {}

    static Geometry resolve(int height, int rows, int sourceCellSize,
                            int baseGap, int gapAdjustment, int dockBarHeight,
                            int topAdjustment, int bottomAdjustment) {
        if (height <= 0 || rows <= 0) return new Geometry(0, 0, 0, 0, 0);

        int sourceCell = Math.max(1, sourceCellSize);
        int dock = Math.min(Math.max(0, dockBarHeight), Math.max(0, height - rows));
        int contentHeight = Math.max(rows, height - dock);
        int baselineGap = Math.max(0, baseGap);
        if (rows > 1) {
            baselineGap = Math.min(baselineGap,
                    Math.max(0, (contentHeight - rows) / (rows - 1)));
        } else {
            baselineGap = 0;
        }

        int baselineCell = Math.min(sourceCell,
                Math.max(1, (contentHeight
                        - baselineGap * Math.max(0, rows - 1)) / rows));
        int baselineUsed = baselineCell * rows
                + baselineGap * Math.max(0, rows - 1);
        int spare = Math.max(0, contentHeight - baselineUsed);
        int baseTop = spare / 2;
        int baseBottom = dock + (spare - baseTop);

        int top = Math.max(0, baseTop + topAdjustment);
        int bottom = Math.max(dock, baseBottom + bottomAdjustment);

        // Preserve at least one pixel per row even for extreme user offsets.
        int marginBudget = Math.max(0, height - rows);
        top = Math.min(top, Math.max(0, marginBudget - dock));
        bottom = Math.min(bottom, Math.max(dock, marginBudget - top));

        int innerHeight = Math.max(rows, height - top - bottom);
        int gap = baselineGap + gapAdjustment;
        if (rows > 1) {
            gap = Math.min(gap, Math.max(0, (innerHeight - rows) / (rows - 1)));
        } else {
            gap = 0;
        }

        int cell = Math.min(sourceCell,
                Math.max(1, (innerHeight - gap * Math.max(0, rows - 1)) / rows));
        return new Geometry(top, bottom, cell, gap, dock);
    }

    static final class Geometry {
        final int top;
        final int bottom;
        final int cellSize;
        final int gap;
        final int dockBarHeight;

        Geometry(int top, int bottom, int cellSize, int gap, int dockBarHeight) {
            this.top = top;
            this.bottom = bottom;
            this.cellSize = cellSize;
            this.gap = gap;
            this.dockBarHeight = dockBarHeight;
        }

        int lastRowBottom(int rows) {
            if (rows <= 0) return top;
            return top + cellSize * rows + gap * Math.max(0, rows - 1);
        }
    }
}
