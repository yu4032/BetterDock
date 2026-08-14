package com.hellovoid.liquiddock;

/**
 * Shared span to pixel conversion for widget layouts.
 * Keeps widget sizes derived from the same CellLayout geometry instead of
 * applying per-widget post-layout corrections.
 */
final class WidgetGridSizing {
    private WidgetGridSizing() {}

    static boolean isSupportedSpec(int spanX, int spanY) {
        return (spanX == 1 && spanY == 1)
                || (spanX == 2 && spanY == 1)
                || (spanX == 2 && spanY == 2)
                || (spanX == 4 && spanY == 2);
    }

    /**
     * Pixel footprint occupied by a span in the custom CellLayout grid.
     * CellLayout positions x/y from the cell coordinate and grid gaps; widget
     * margins do not move that origin, so subtracting them here leaves the
     * far edge short of the grid boundary.
     */
    static int gridSpanSize(int span, int cellSize, int gap) {
        if (span <= 0 || cellSize <= 0) return 0;
        int safeGap = Math.max(0, gap);
        return span * cellSize + Math.max(0, span - 1) * safeGap;
    }

    /**
     * Compatibility entry point used by HomeGridHook. Launcher margins are
     * intentionally ignored: the widget view should fill its complete grid
     * footprint rather than inherit MIUI's stock-grid visual inset.
     */
    static int spanSize(int span, int cellSize, int gap,
                        int startMargin, int endMargin) {
        return gridSpanSize(span, cellSize, gap);
    }
}
