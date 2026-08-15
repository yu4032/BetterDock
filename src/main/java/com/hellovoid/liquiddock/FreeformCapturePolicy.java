package com.hellovoid.liquiddock;

/** Pure policy for deciding whether a task must be removed from a desktop backdrop. */
final class FreeformCapturePolicy {
    private FreeformCapturePolicy() {}

    static boolean shouldExclude(int windowingMode, boolean visible) {
        return visible
                && windowingMode == LauncherSceneOwnershipPolicy.WINDOWING_MODE_FREEFORM;
    }
}
