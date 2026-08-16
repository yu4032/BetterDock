package com.hellovoid.liquiddock;

/** Preserves the established HOME/APP scene when a freeform task overlays it. */
final class LauncherSceneOwnershipPolicy {
    static final int WINDOWING_MODE_FREEFORM = 5;

    private LauncherSceneOwnershipPolicy() {}

    static boolean launcherOwnsScene(boolean launcherSignal, int foregroundWindowingMode,
                                     boolean previousLauncherOwnership,
                                     boolean previousOwnershipKnown) {
        if (foregroundWindowingMode == WINDOWING_MODE_FREEFORM) {
            return previousOwnershipKnown ? previousLauncherOwnership : true;
        }
        return launcherSignal;
    }
}
