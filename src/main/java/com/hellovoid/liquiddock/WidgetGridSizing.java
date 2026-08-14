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

    static int spanSize(int span, int cellSize, int gap, int startMargin, int endMargin) {
        if (span <= 0) return 0;
        int internalGaps = Math.max(0, span - 1) * gap;
        return span * cellSize + internalGaps - startMargin - endMargin;
    }
}
