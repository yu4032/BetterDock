package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;
    private boolean allAppsUsesHomeBackdrop;

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

    /** Launcher focus loss proves a pending HOME target is stale: an app has actually
     * taken the foreground. Do not clear APP/RECENTS because those targets are still
     * useful before lifecycle/focus catches up. */
    boolean clearGestureTargetIfHome() {
        if (gestureTarget != CaptureScene.HOME) return false;
        gestureTarget = null;
        gestureTargetUntilNanos = 0L;
        return true;
    }

    /** Stock laptop All Apps lives in a focusable LauncherOverlayWindow. It can make the
     * main Launcher window lose focus without an external app taking the foreground.
     * In workstation mode the All Apps UI remains observable, but it aliases HOME for
     * Dock backdrop/capture semantics. */
    void setAllAppsActive(boolean active, boolean useHomeBackdrop) {
        boolean nextUsesHomeBackdrop = active && useHomeBackdrop;
        if (allAppsActive == active && allAppsUsesHomeBackdrop == nextUsesHomeBackdrop) return;
        allAppsActive = active;
        allAppsUsesHomeBackdrop = nextUsesHomeBackdrop;

        if (useHomeBackdrop) {
            // Opening/closing workstation All Apps is not a capture-scene transition.
            // Preserve the current HOME backdrop and do not invalidate in-flight HOME frames.
            if (active && desired == CaptureScene.ALL_APPS) {
                desired = CaptureScene.HOME;
                revision++;
            }
            return;
        }

        revision++;
        if (active) {
            desired = CaptureScene.ALL_APPS;
        } else if (desired == CaptureScene.ALL_APPS) {
            // The owning DockLiquidGlassView immediately refreshes against real launcher
            // lifecycle/overview state. APP is only a neutral interim value here.
            desired = CaptureScene.APP;
        }
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (recentsVisible) return CaptureScene.RECENTS;
        if (allAppsActive) {
            return allAppsUsesHomeBackdrop ? CaptureScene.HOME : CaptureScene.ALL_APPS;
        }
        if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
        return CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean lifecycleKnown, boolean launcherResumed) {
        CaptureScene next = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
        return setDesired(next);
    }

    void setWorkstationSuspended(boolean enabled, long nowNanos,
                                     boolean recentsVisible, boolean lifecycleKnown,
                                     boolean launcherResumed) {
        workstationSuspended = enabled;
        gestureTarget = null;
        revision++;
        desired = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
    }

    private boolean setDesired(CaptureScene next) {
        if (desired == next) return false;
        desired = next;
        revision++;
        return true;
    }
}
