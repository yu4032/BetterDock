package com.hellovoid.liquiddock;

/** Pure conversion from screen coordinates into the stable Launcher root coordinate space. */
final class LauncherGlassScreenSpace {
    private LauncherGlassScreenSpace() {}

    static Bounds relativeToRoot(
            int rootScreenX, int rootScreenY,
            int left, int top, int right, int bottom) {
        return new Bounds(
                left - rootScreenX,
                top - rootScreenY,
                right - rootScreenX,
                bottom - rootScreenY);
    }

    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
