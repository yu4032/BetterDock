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

    static int uniformGutter(int widthGap, int heightGap) {
        return Math.max(Math.max(0, widthGap), Math.max(0, heightGap));
    }

    /**
     * Returns {left, top, width, height} for a widget. The native grid gap on
     * each axis is preserved; only the missing amount needed to reach the
     * shared gutter is inset symmetrically. Thus a 2x2 widget has exactly the
     * same outer frame as two stacked 2x1 widgets, while every neighboring
     * widget pair keeps one consistent visible gap.
     */
    static int[] gridRect(int cellX, int cellY, int spanX, int spanY,
                          int[] xs, int[] ys, int cellWidth, int cellHeight,
                          int widthGap, int heightGap) {
        if (spanX <= 0 || spanY <= 0 || cellWidth <= 0 || cellHeight <= 0
                || xs == null || ys == null || cellX < 0 || cellY < 0
                || cellX + spanX > xs.length || cellY + spanY > ys.length) {
            return new int[]{0, 0, 0, 0};
        }

        int left = xs[cellX];
        int top = ys[cellY];
        int right = xs[cellX + spanX - 1] + cellWidth;
        int bottom = ys[cellY + spanY - 1] + cellHeight;

        int gutter = uniformGutter(widthGap, heightGap);
        int missingX = Math.max(0, gutter - Math.max(0, widthGap));
        int missingY = Math.max(0, gutter - Math.max(0, heightGap));

        int insetLeft = missingX / 2;
        int insetRight = missingX - insetLeft;
        int insetTop = missingY / 2;
        int insetBottom = missingY - insetTop;

        left += insetLeft;
        right -= insetRight;
        top += insetTop;
        bottom -= insetBottom;

        return new int[]{left, top, Math.max(0, right - left), Math.max(0, bottom - top)};
    }
}
