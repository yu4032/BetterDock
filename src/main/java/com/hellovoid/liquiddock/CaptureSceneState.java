package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.UNKNOWN;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;

    CaptureScene desired() { return desired; }
    long revision() { return revision; }
    boolean workstationSuspended() { return workstationSuspended; }
    boolean allAppsActive() { return allAppsActive; }
    boolean matches(CaptureScene scene, long expectedRevision) {
        return desired == scene && revision == expectedRevision;
    }

    void prearmRecents(long nowNanos) {
        gestureTarget = CaptureScene.RECENTS;
        gestureTargetUntilNanos = nowNanos + 700_000_000L;
        setDesired(CaptureScene.RECENTS);
    }

    void setGestureTarget(String target, long nowNanos) {
        gestureTarget = "HOME".equals(target) ? CaptureScene.HOME
                : "RECENTS".equals(target) ? CaptureScene.RECENTS : CaptureScene.APP;
        gestureTargetUntilNanos = nowNanos + 1_500_000_000L;
        setDesired(gestureTarget);
    }

    boolean gestureTargetExpired(long nowNanos) {
        return gestureTarget != null && nowNanos >= gestureTargetUntilNanos;
    }

    void clearGestureTarget() { gestureTarget = null; }

    /** A confirmed APP ownership boundary proves a pending HOME target is stale. */
    boolean clearGestureTargetIfHome() {
        if (gestureTarget != CaptureScene.HOME) return false;
        gestureTarget = null;
        gestureTargetUntilNanos = 0L;
        return true;
    }

    /** Stock laptop All Apps lives in a focusable LauncherOverlayWindow. */
    void setAllAppsActive(boolean active) {
        if (allAppsActive == active) return;
        allAppsActive = active;
        revision++;
        if (active) {
            desired = CaptureScene.ALL_APPS;
        } else if (desired == CaptureScene.ALL_APPS) {
            // The owning DockLiquidGlassView immediately refreshes against the current
            // SystemUI baseline. UNKNOWN is the neutral fail-closed interim value.
            desired = CaptureScene.UNKNOWN;
        }
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean ownershipKnown, boolean homeOwned) {
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (recentsVisible) return CaptureScene.RECENTS;
        if (allAppsActive) return CaptureScene.ALL_APPS;
        if (!ownershipKnown) return CaptureScene.UNKNOWN;
        return homeOwned ? CaptureScene.HOME : CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean ownershipKnown, boolean homeOwned) {
        CaptureScene next = resolve(nowNanos, recentsVisible, ownershipKnown, homeOwned);
        return setDesired(next);
    }

    void setWorkstationSuspended(boolean enabled, long nowNanos,
                                 boolean recentsVisible, boolean ownershipKnown,
                                 boolean homeOwned) {
        workstationSuspended = enabled;
        gestureTarget = null;
        revision++;
        desired = resolve(nowNanos, recentsVisible, ownershipKnown, homeOwned);
    }

    private boolean setDesired(CaptureScene next) {
        if (desired == next) return false;
        desired = next;
        revision++;
        return true;
    }
}
