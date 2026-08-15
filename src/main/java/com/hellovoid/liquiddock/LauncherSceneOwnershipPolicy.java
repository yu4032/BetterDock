package com.hellovoid.liquiddock;

/** Distinguishes a real fullscreen APP takeover from a floating/freeform task above Launcher. */
final class LauncherSceneOwnershipPolicy {
    static final int WINDOWING_MODE_FREEFORM = 5;

    private LauncherSceneOwnershipPolicy() {}

    static boolean launcherOwnsScene(boolean launcherResumed, int foregroundWindowingMode) {
        return launcherResumed || foregroundWindowingMode == WINDOWING_MODE_FREEFORM;
    }
}
