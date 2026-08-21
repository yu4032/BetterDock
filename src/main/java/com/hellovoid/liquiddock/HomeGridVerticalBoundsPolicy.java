package com.hellovoid.liquiddock;

/** Pure geometry for keeping a tall custom grid inside CellLayout's real vertical bounds. */
final class HomeGridVerticalBoundsPolicy {
    private HomeGridVerticalBoundsPolicy() {}

    static Geometry resolve(int height, int rows, int sourceCellSize, int requestedGap,
                            int dockBarHeight, int topAdjustment, int bottomAdjustment) {
        if (height <= 0 || rows <= 0) return new Geometry(0, 0, 0, 0, 0);

        int sourceCell = Math.max(1, sourceCellSize);
        int maxDock = Math.max(0, height - rows);
        int dock = Math.min(Math.max(0, dockBarHeight), maxDock);
        int contentHeight = Math.max(rows, height - dock);

        int gap = Math.max(0, requestedGap);
        if (rows > 1) {
            int maxGap = Math.max(0, (contentHeight - rows) / (rows - 1));
            gap = Math.min(gap, maxGap);
        } else {
            gap = 0;
        }

        int baseCell = Math.min(sourceCell,
                Math.max(1, (contentHeight - gap * Math.max(0, rows - 1)) / rows));
        int baseUsed = baseCell * rows + gap * Math.max(0, rows - 1);
        int spare = Math.max(0, contentHeight - baseUsed);
        int baseTop = spare / 2;
        int baseBottom = dock + (spare - baseTop);

        int top = Math.max(0, baseTop + topAdjustment);
        int bottom = Math.max(dock, baseBottom + bottomAdjustment);

        // Keep at least one pixel for every row even when user offsets are extreme.
        int marginBudget = Math.max(0, height - rows);
        top = Math.min(top, Math.max(0, marginBudget - dock));
        bottom = Math.min(bottom, Math.max(dock, marginBudget - top));

        int innerHeight = Math.max(rows, height - top - bottom);
        if (rows > 1) {
            int maxGap = Math.max(0, (innerHeight - rows) / (rows - 1));
            gap = Math.min(gap, maxGap);
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
