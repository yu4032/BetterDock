package com.hellovoid.liquiddock;

/** Pure root-space mapping for one launcher glass node. */
final class LauncherGlassGeometry {
    private LauncherGlassGeometry() {}

    static Snapshot resolve(
            int rootWidth, int rootHeight,
            float left, float top, float right, float bottom,
            float cornerRadius) {
        if (rootWidth <= 0 || rootHeight <= 0
                || !finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                || right <= left || bottom <= top) {
            return null;
        }
        if (right <= 0f || bottom <= 0f || left >= rootWidth || top >= rootHeight) return null;

        float clippedLeft = clamp(left, 0f, rootWidth);
        float clippedTop = clamp(top, 0f, rootHeight);
        float clippedRight = clamp(right, 0f, rootWidth);
        float clippedBottom = clamp(bottom, 0f, rootHeight);
        float width = clippedRight - clippedLeft;
        float height = clippedBottom - clippedTop;
        if (width <= 0f || height <= 0f) return null;

        return new Snapshot(
                clippedLeft, clippedTop, width, height,
                clippedLeft / rootWidth,
                (rootHeight - clippedBottom) / rootHeight,
                width / rootWidth,
                height / rootHeight,
                clippedLeft + width * 0.5f,
                clippedTop + height * 0.5f,
                Math.max(0f, Math.min(cornerRadius, Math.min(width, height) * 0.5f)));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Snapshot {
        final float left;
        final float top;
        final float width;
        final float height;
        final float cropLeft;
        final float cropBottom;
        final float cropWidth;
        final float cropHeight;
        final float centerX;
        final float centerY;
        final float cornerRadius;

        Snapshot(float left, float top, float width, float height,
                 float cropLeft, float cropBottom, float cropWidth, float cropHeight,
                 float centerX, float centerY, float cornerRadius) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.cropLeft = cropLeft;
            this.cropBottom = cropBottom;
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
            this.centerX = centerX;
            this.centerY = centerY;
            this.cornerRadius = cornerRadius;
        }

        boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return close(left, other.left) && close(top, other.top)
                    && close(width, other.width) && close(height, other.height)
                    && close(cornerRadius, other.cornerRadius);
        }

        private static boolean close(float a, float b) {
            return Math.abs(a - b) < 0.25f;
        }
    }
}
