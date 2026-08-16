package com.hellovoid.liquiddock;

final class HomeOwnershipShadowPolicy {
    enum RecheckResult { TRANSIENT_MISMATCH, PERSISTENT_MISMATCH }
    enum SystemUiBaseline { HOME, APP, UNKNOWN }

    private HomeOwnershipShadowPolicy() {}

    static boolean matches(boolean launcherHome, boolean systemUiHome) {
        return launcherHome == systemUiHome;
    }

    static boolean baselineEligible(boolean overview, boolean allApps, boolean workstation) {
        return !overview && !allApps && !workstation;
    }

    static RecheckResult recheckResult(boolean launcherHome, boolean systemUiHome) {
        return matches(launcherHome, systemUiHome)
                ? RecheckResult.TRANSIENT_MISMATCH
                : RecheckResult.PERSISTENT_MISMATCH;
    }

    static SystemUiBaseline systemUiBaseline(boolean homeVisible,
                                             int homeTaskId,
                                             int topFullscreenTaskId) {
        if (topFullscreenTaskId >= 0 && topFullscreenTaskId != homeTaskId) {
            return SystemUiBaseline.APP;
        }
        if (homeVisible) return SystemUiBaseline.HOME;
        return SystemUiBaseline.UNKNOWN;
    }

    static boolean matchesLauncher(boolean launcherHome, SystemUiBaseline systemUiBaseline) {
        if (systemUiBaseline == SystemUiBaseline.UNKNOWN) return false;
        return launcherHome == (systemUiBaseline == SystemUiBaseline.HOME);
    }
}
