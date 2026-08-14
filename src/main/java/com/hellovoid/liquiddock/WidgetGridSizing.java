package com.hellovoid.liquiddock;

/** Shared geometry for widget layouts on the custom CellLayout grid. */
final class WidgetGridSizing {
    private WidgetGridSizing() {}

    static boolean isSupportedSpec(int spanX, int spanY) {
        return (spanX == 1 && spanY == 1)
                || (spanX == 2 && spanY == 1)
                || (spanX == 2 && spanY == 2)
                || (spanX == 4 && spanY == 2);
    }

    /** Small visual inset around the whole occupied slot rectangle. */
    static int visualPadding(int cellWidth, int cellHeight) {
        int cell = Math.min(Math.max(0, cellWidth), Math.max(0, cellHeight));
        if (cell <= 0) return 0;
        return Math.max(1, Math.round(cell * 0.04f));
    }

    /**
     * Returns {left, top, width, height}. Each cell owns a seamless slot whose
     * internal boundaries lie at the midpoint of the launcher gap. A widget
     * takes the union of all occupied slots and then applies one fixed padding
     * around that union. Therefore all adjacent widgets have exactly 2*padding
     * visible spacing independent of span and of the launcher's X/Y grid gaps.
     */
    static int[] slotRect(int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys, int cellWidth, int cellHeight,
                          int padding) {
        if (spanX <= 0 || spanY <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || cellX < 0 || cellY < 0
                || cellX + spanX > xs.length || cellY + spanY > ys.length) {
            return new int[]{0, 0, 0, 0};
        }

        int endX = cellX + spanX;
        int endY = cellY + spanY;
        int safePadding = Math.max(0, padding);

        int left = slotBoundaryBefore(cellX, xs, cellWidth);
        int right = slotBoundaryAfter(endX - 1, xs, cellWidth);
        int top = slotBoundaryBefore(cellY, ys, cellHeight);
        int bottom = slotBoundaryAfter(endY - 1, ys, cellHeight);

        left += safePadding;
        top += safePadding;
        right -= safePadding;
        bottom -= safePadding;

        return new int[]{left, top, Math.max(0, right - left), Math.max(0, bottom - top)};
    }

    private static int slotBoundaryBefore(int index, int[] origins, int cellSize) {
        if (index <= 0) return origins[0];
        int previousEnd = origins[index - 1] + cellSize;
        return previousEnd + (origins[index] - previousEnd) / 2;
    }

    private static int slotBoundaryAfter(int index, int[] origins, int cellSize) {
        if (index >= origins.length - 1) return origins[index] + cellSize;
        int currentEnd = origins[index] + cellSize;
        return currentEnd + (origins[index + 1] - currentEnd) / 2;
    }
}
