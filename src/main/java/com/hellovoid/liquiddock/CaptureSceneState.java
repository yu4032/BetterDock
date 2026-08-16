package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private static final long GESTURE_TARGET_NANOS = 1_500_000_000L;
    // Failure recovery only. Normal APP -> HOME release is the exact Launcher animation-end
    // callback; this bound prevents a vendor-hook mismatch from pinning APP forever.
    private static final long APP_HOME_HANDOFF_WATCHDOG_NANOS = 2_000_000_000L;

    private CaptureScene desired = CaptureScene.UNKNOWN;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;
    private boolean appHomeHandoffPending;
    private long appHomeHandoffUntilNanos;

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
        if ("HOME_ANIMATION_END".equals(target)) {
            // A cancelled/replaced path may still deliver the old animator's end callback.
            // Only a completion paired with a pending APP -> HOME target may commit HOME.
            if (!appHomeHandoffPending) return;
            appHomeHandoffPending = false;
            appHomeHandoffUntilNanos = 0L;
            gestureTarget = CaptureScene.HOME;
            gestureTargetUntilNanos = nowNanos + GESTURE_TARGET_NANOS;
            setDesired(CaptureScene.HOME);
            return;
        }

        CaptureScene next = "HOME".equals(target) ? CaptureScene.HOME
                : "RECENTS".equals(target) ? CaptureScene.RECENTS : CaptureScene.APP;

        // GestureToHome is a destination announcement. When it comes from APP, HyperOS still
        // runs CLOSE_TO_HOME for roughly another spring cycle. Keep composed APP capture until
        // the exact animation-end callback instead of switching to wallpaper at this boundary.
        if (next == CaptureScene.HOME && desired == CaptureScene.APP) {
            appHomeHandoffPending = true;
            appHomeHandoffUntilNanos = nowNanos + APP_HOME_HANDOFF_WATCHDOG_NANOS;
            gestureTarget = null;
            gestureTargetUntilNanos = 0L;
            setDesired(CaptureScene.APP);
            return;
        }

        // A later APP/RECENTS target proves the pending HOME path was interrupted.
        if (next == CaptureScene.APP || next == CaptureScene.RECENTS) {
            appHomeHandoffPending = false;
            appHomeHandoffUntilNanos = 0L;
        }
        gestureTarget = next;
        gestureTargetUntilNanos = nowNanos + GESTURE_TARGET_NANOS;
        setDesired(gestureTarget);
    }

    boolean gestureTargetExpired(long nowNanos) {
        return gestureTarget != null && nowNanos >= gestureTargetUntilNanos;
    }

    void clearGestureTarget() { gestureTarget = null; }

    /** A confirmed APP ownership boundary proves a pending ordinary HOME target is stale. */
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
        if (appHomeHandoffPending && nowNanos < appHomeHandoffUntilNanos) {
            // Explicit special scenes still outrank an ordinary APP -> HOME visual hold.
            if (recentsVisible) return CaptureScene.RECENTS;
            if (allAppsActive) return CaptureScene.ALL_APPS;
            return CaptureScene.APP;
        }
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
        appHomeHandoffPending = false;
        appHomeHandoffUntilNanos = 0L;
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
