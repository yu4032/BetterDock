package com.hellovoid.liquiddock;

/** Pure coordinate transform for HyperOS wallpaper surface zoom. */
final class WallpaperZoomTransform {
    static final class Result {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final float frameScale;
        final boolean corrected;

        Result(int left, int top, int right, int bottom, float frameScale, boolean corrected) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.frameScale = frameScale;
            this.corrected = corrected;
        }
    }

    private WallpaperZoomTransform() {}

    static Result adjust(int left, int top, int right, int bottom,
                         int displayWidth, int displayHeight,
                         float visualScale, float captureScale) {
        if (displayWidth <= 0 || displayHeight <= 0
                || !(captureScale > 0f)
                || !Float.isFinite(visualScale)
                || visualScale < 0.8f || visualScale > 1.25f
                || Math.abs(visualScale - 1f) < 0.0005f) {
            return new Result(left, top, right, bottom, captureScale, false);
        }

        float cx = displayWidth * 0.5f;
        float cy = displayHeight * 0.5f;
        int adjustedLeft = Math.round(cx + (left - cx) / visualScale);
        int adjustedTop = Math.round(cy + (top - cy) / visualScale);
        int adjustedRight = Math.round(cx + (right - cx) / visualScale);
        int adjustedBottom = Math.round(cy + (bottom - cy) / visualScale);

        adjustedLeft = clamp(adjustedLeft, 0, displayWidth - 1);
        adjustedTop = clamp(adjustedTop, 0, displayHeight - 1);
        adjustedRight = clamp(adjustedRight, adjustedLeft + 1, displayWidth);
        adjustedBottom = clamp(adjustedBottom, adjustedTop + 1, displayHeight);

        return new Result(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom,
                captureScale * visualScale, true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
