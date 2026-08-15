package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    private boolean allAppsActive;
    private boolean externalAppDockInteraction;

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

    /**
     * A Dock touch that begins while an external app owns foreground creates a temporary
     * source-domain lock. Until Launcher HOME is authoritatively confirmed, this interaction
     * must stay in the live APP/RECENTS domain even if a speculative GestureToHome object
     * has already been constructed. Source correctness is intentionally independent of the
     * optional dynamic-app continuous-capture cadence.
     */
    void setExternalAppDockInteraction(boolean active) {
        if (externalAppDockInteraction == active) return;
        externalAppDockInteraction = active;
        // A HOME constructor seen before/during an app Dock pull is only a navigation hint.
        // Never allow that stale hint to revive after ACTION_UP/CANCEL either.
        if (gestureTarget == CaptureScene.HOME) {
            gestureTarget = null;
            gestureTargetUntilNanos = 0L;
        }
        revision++;
    }

    /** Stock laptop All Apps lives in a focusable LauncherOverlayWindow. It can make the
     * main Launcher window lose focus without an external app taking the foreground. A
     * confirmed drawer open also invalidates any older gesture prearm. */
    void setAllAppsActive(boolean active) {
        if (allAppsActive == active) return;
        allAppsActive = active;
        revision++;
        if (active) {
            gestureTarget = null;
            gestureTargetUntilNanos = 0L;
            desired = CaptureScene.ALL_APPS;
        } else if (desired == CaptureScene.ALL_APPS) {
            // The owning DockLiquidGlassView immediately refreshes against real launcher
            // lifecycle/overview state. APP is only a neutral interim value here.
            desired = CaptureScene.APP;
        }
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        // Confirmed stock Launcher state outranks bounded gesture prearm. Gesture events exist
        // only to cover the first transition frame before these authoritative callbacks land.
        if (recentsVisible) return CaptureScene.RECENTS;
        if (allAppsActive) return CaptureScene.ALL_APPS;

        if (externalAppDockInteraction) {
            // A real HOME foreground transition may take ownership even before the finger is
            // released. Otherwise keep this Dock pull in the live source domain. RECENTS is
            // allowed because it uses the same FULL_DISPLAY source as APP.
            if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
            if (gestureTarget == CaptureScene.RECENTS
                    && nowNanos < gestureTargetUntilNanos) return CaptureScene.RECENTS;
            return CaptureScene.APP;
        }

        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
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
        externalAppDockInteraction = false;
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
