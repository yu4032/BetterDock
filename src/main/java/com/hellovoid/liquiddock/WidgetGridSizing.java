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

    /**
     * Returns {left, top, width, height} for the complete grid allocation.
     * mXs/mYs are cell origins, so the next origin is the exact boundary of
     * the next allocation slot. X and Y pitches are intentionally independent:
     * a 2x2 widget must stretch to two real rows even when the grid is not
     * square. Widget/provider padding remains responsible for visible spacing.
     */
    static int[] gridRect(int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys, int cellWidth, int cellHeight,
                          int widthGap, int heightGap) {
        if (spanX <= 0 || spanY <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || xs.length == 0 || ys.length == 0
                || cellX < 0 || cellY < 0
                || cellX + spanX > xs.length || cellY + spanY > ys.length) {
            return new int[]{0, 0, 0, 0};
        }

        int left = axisBoundary(xs, cellX, cellWidth, widthGap);
        int right = axisBoundary(xs, cellX + spanX, cellWidth, widthGap);
        int top = axisBoundary(ys, cellY, cellHeight, heightGap);
        int bottom = axisBoundary(ys, cellY + spanY, cellHeight, heightGap);

        return new int[]{
                left,
                top,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        };
    }

    private static int axisBoundary(int[] origins, int boundaryIndex,
                                    int cellSize, int gap) {
        if (boundaryIndex < origins.length) return origins[boundaryIndex];

        int last = origins.length - 1;
        int pitch;
        if (origins.length >= 2) {
            pitch = origins[last] - origins[last - 1];
        } else {
            pitch = cellSize + Math.max(0, gap);
        }
        if (pitch <= 0) pitch = Math.max(1, cellSize + Math.max(0, gap));
        return origins[last] + pitch;
    }
}
