package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private static final long ALL_APPS_PREARM_NANOS = 900_000_000L;

    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    /** Revision changes only when the effective capture scene changes. */
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;
    private long allAppsPrearmUntilNanos;
    private ForegroundOwnership foregroundOwnership = ForegroundOwnership.UNKNOWN;
    private boolean launcherAwayHint;
    private boolean externalAppDockInteraction;

    CaptureScene desired() { return desired; }
    long revision() { return revision; }
    boolean workstationSuspended() { return workstationSuspended; }
    boolean allAppsActive() { return allAppsActive; }
    boolean externalAppForegroundConfirmed() {
        return foregroundOwnership == ForegroundOwnership.EXTERNAL;
    }
    ForegroundOwnership foregroundOwnership() { return foregroundOwnership; }
    boolean launcherAwayHint() { return launcherAwayHint; }
    boolean matches(CaptureScene scene, long expectedRevision) {
        return desired == scene && revision == expectedRevision;
    }

    void prearmRecents(long nowNanos) {
        gestureTarget = CaptureScene.RECENTS;
        gestureTargetUntilNanos = nowNanos + 700_000_000L;
        if (!allAppsActive) setDesired(CaptureScene.RECENTS);
    }

    void prearmAllApps(long nowNanos) {
        allAppsPrearmUntilNanos = nowNanos + ALL_APPS_PREARM_NANOS;
        if (!allAppsActive
                && foregroundOwnership != ForegroundOwnership.EXTERNAL
                && !externalAppDockInteraction) {
            setDesired(CaptureScene.ALL_APPS);
        }
    }

    boolean allAppsPrearmExpired(long nowNanos) {
        return allAppsPrearmUntilNanos != 0L && nowNanos >= allAppsPrearmUntilNanos;
    }

    void clearAllAppsPrearm() {
        allAppsPrearmUntilNanos = 0L;
    }

    void setGestureTarget(String target, long nowNanos) {
        gestureTarget = "HOME".equals(target) ? CaptureScene.HOME
                : "RECENTS".equals(target) ? CaptureScene.RECENTS : CaptureScene.APP;
        gestureTargetUntilNanos = nowNanos + 1_500_000_000L;
        if (allAppsActive) return;
        if (foregroundOwnership == ForegroundOwnership.EXTERNAL || externalAppDockInteraction) {
            setDesired(gestureTarget == CaptureScene.RECENTS
                    ? CaptureScene.RECENTS : CaptureScene.APP);
            return;
        }
        setDesired(gestureTarget);
    }

    boolean gestureTargetExpired(long nowNanos) {
        return gestureTarget != null && nowNanos >= gestureTargetUntilNanos;
    }

    void clearGestureTarget() {
        gestureTarget = null;
        gestureTargetUntilNanos = 0L;
    }

    /** Launcher focus loss proves a pending HOME target is stale. */
    boolean clearGestureTargetIfHome() {
        if (gestureTarget != CaptureScene.HOME) return false;
        clearGestureTarget();
        return true;
    }

    void setForegroundOwnership(ForegroundOwnership ownership) {
        ForegroundOwnership next = ownership == null ? ForegroundOwnership.UNKNOWN : ownership;
        if (foregroundOwnership == next) return;
        foregroundOwnership = next;
        if (next == ForegroundOwnership.EXTERNAL) {
            launcherAwayHint = true;
            clearAllAppsPrearm();
            if (gestureTarget == CaptureScene.HOME) clearGestureTarget();
        } else if (next == ForegroundOwnership.HOME) {
            launcherAwayHint = false;
            externalAppDockInteraction = false;
        }
        // Do not increment revision here. The owner immediately calls refresh(); only an
        // effective scene transition is allowed to invalidate an in-flight frame.
    }

    /** Focus/lifecycle evidence that Launcher left foreground. UNKNOWN cannot clear it. */
    void setLauncherAwayHint(boolean active) {
        if (launcherAwayHint == active) return;
        launcherAwayHint = active;
        if (active) {
            clearAllAppsPrearm();
            if (gestureTarget == CaptureScene.HOME) clearGestureTarget();
        }
        // refresh() owns revisioning; changing this hint alone is not frame identity.
    }

    /** Compatibility setter retained for existing tests/callers. */
    void setExternalAppForegroundConfirmed(boolean active) {
        if (active) {
            setForegroundOwnership(ForegroundOwnership.EXTERNAL);
        } else if (foregroundOwnership == ForegroundOwnership.EXTERNAL) {
            setForegroundOwnership(ForegroundOwnership.UNKNOWN);
        }
    }

    void setExternalAppDockInteraction(boolean active) {
        if (externalAppDockInteraction == active) return;
        externalAppDockInteraction = active;
        if (gestureTarget == CaptureScene.HOME) clearGestureTarget();
        // Interaction release is not a capture-identity change when persistent external
        // ownership still resolves to APP. refresh() owns revisioning.
    }

    /** Confirmed drawer state; normal transition callbacks use prearmAllApps instead. */
    void setAllAppsActive(boolean active) {
        if (allAppsActive == active) {
            if (active) clearAllAppsPrearm();
            return;
        }
        allAppsActive = active;
        clearAllAppsPrearm();
        if (active) {
            clearGestureTarget();
            setDesired(CaptureScene.ALL_APPS);
        }
        // On close the owner immediately calls refresh() with real launcher/foreground state.
    }

    private boolean confirmedHomeGestureActive(long nowNanos) {
        return foregroundOwnership == ForegroundOwnership.HOME
                && gestureTarget == CaptureScene.HOME
                && nowNanos < gestureTargetUntilNanos;
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        // Recents hide callbacks can trail GestureToHome by a frame.  A bounded HOME target is
        // allowed to beat that stale latch only after the physical top task has independently
        // confirmed Launcher HOME.  Speculative HOME hints from external apps never qualify.
        if (recentsVisible) {
            if (confirmedHomeGestureActive(nowNanos)) return CaptureScene.HOME;
            return CaptureScene.RECENTS;
        }
        if (allAppsActive) return CaptureScene.ALL_APPS;

        if (foregroundOwnership == ForegroundOwnership.EXTERNAL) {
            if (gestureTarget == CaptureScene.RECENTS
                    && nowNanos < gestureTargetUntilNanos) return CaptureScene.RECENTS;
            return CaptureScene.APP;
        }

        if (launcherAwayHint) {
            if (gestureTarget == CaptureScene.RECENTS
                    && nowNanos < gestureTargetUntilNanos) return CaptureScene.RECENTS;
            return CaptureScene.APP;
        }

        if (externalAppDockInteraction) {
            if (gestureTarget == CaptureScene.RECENTS
                    && nowNanos < gestureTargetUntilNanos) return CaptureScene.RECENTS;
            return CaptureScene.APP;
        }

        if (allAppsPrearmUntilNanos != 0L && nowNanos < allAppsPrearmUntilNanos) {
            return CaptureScene.ALL_APPS;
        }
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (foregroundOwnership == ForegroundOwnership.HOME) return CaptureScene.HOME;
        if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
        return CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean lifecycleKnown, boolean launcherResumed) {
        return setDesired(resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed));
    }

    void setWorkstationSuspended(boolean enabled, long nowNanos,
                                 boolean recentsVisible, boolean lifecycleKnown,
                                 boolean launcherResumed) {
        workstationSuspended = enabled;
        clearGestureTarget();
        clearAllAppsPrearm();
        foregroundOwnership = ForegroundOwnership.UNKNOWN;
        launcherAwayHint = false;
        externalAppDockInteraction = false;
        setDesired(resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed));
    }

    private boolean setDesired(CaptureScene next) {
        if (desired == next) return false;
        desired = next;
        revision++;
        return true;
    }
}
