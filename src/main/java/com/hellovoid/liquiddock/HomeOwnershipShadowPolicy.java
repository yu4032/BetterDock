package com.hellovoid.liquiddock;

final class HomeOwnershipShadowPolicy {
    enum RecheckResult { TRANSIENT_MISMATCH, PERSISTENT_MISMATCH }

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
}
