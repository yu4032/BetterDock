package com.hellovoid.liquiddock;

/** Tracks frame-to-frame scroll motion for one ancestor without depending on Android View APIs. */
final class LauncherGlassScrollMotionTracker {
    private Object owner;
    private int scrollX;
    private int scrollY;
    private boolean initialized;

    boolean update(Object nextOwner, int nextScrollX, int nextScrollY) {
        if (nextOwner == null) {
            reset();
            return false;
        }
        if (!initialized || owner != nextOwner) {
            owner = nextOwner;
            scrollX = nextScrollX;
            scrollY = nextScrollY;
            initialized = true;
            return false;
        }
        boolean moved = scrollX != nextScrollX || scrollY != nextScrollY;
        scrollX = nextScrollX;
        scrollY = nextScrollY;
        return moved;
    }

    private void reset() {
        owner = null;
        scrollX = 0;
        scrollY = 0;
        initialized = false;
    }
}
