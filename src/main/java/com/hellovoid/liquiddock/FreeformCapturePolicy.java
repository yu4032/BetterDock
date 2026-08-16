package com.hellovoid.liquiddock;

/** Pure policy for deciding whether a task may need removal from a desktop backdrop. */
final class FreeformCapturePolicy {
    private FreeformCapturePolicy() {}

    static boolean shouldExclude(int windowingMode, boolean visible) {
        if (!visible) return false;
        if (windowingMode < 0) return true; // unknown capability => fail closed
        return windowingMode == LauncherSceneOwnershipPolicy.WINDOWING_MODE_FREEFORM;
    }
}
