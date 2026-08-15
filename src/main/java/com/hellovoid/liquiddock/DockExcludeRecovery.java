package com.hellovoid.liquiddock;

/**
 * Recovery state for mode-1 Floating Dock exclusion.
 * A SurfaceControl EINVAL first drops the native handle but keeps fresh name exclusion.
 * If name-only exclusion is also rejected, capture stays suspended until the Dock layer
 * generation changes (or the caller explicitly invalidates the whole window cache).
 */
final class DockExcludeRecovery {
    private enum Mode { SURFACE_AND_NAME, NAME_ONLY, SUSPENDED }

    private Mode mode = Mode.SURFACE_AND_NAME;

    boolean includeSurfaceControl() {
        return mode == Mode.SURFACE_AND_NAME;
    }

    boolean suspended() {
        return mode == Mode.SUSPENDED;
    }

    void onInvalidArgument() {
        if (mode == Mode.SURFACE_AND_NAME) {
            mode = Mode.NAME_ONLY;
        } else if (mode == Mode.NAME_ONLY) {
            mode = Mode.SUSPENDED;
        }
    }

    void onSurfaceGenerationChanged() {
        mode = Mode.SURFACE_AND_NAME;
    }
}
