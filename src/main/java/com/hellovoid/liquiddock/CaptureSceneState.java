package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationSuspended;
    // A HOME target emitted while HOME is already authoritative is only a transition hint.
    // If Launcher subsequently loses focus before that target expires, the target is stale
    // and must not outrank the now-authoritative APP lifecycle state for another 1.5 seconds.
    private boolean homeTargetStartedFromHome;

    CaptureScene desired() { return desired; }
    long revision() { return revision; }
    boolean workstationSuspended() { return workstationSuspended; }
    boolean matches(CaptureScene scene, long expectedRevision) {
        return desired == scene && revision == expectedRevision;
    }

    void prearmRecents(long nowNanos) {
        gestureTarget = CaptureScene.RECENTS;
        homeTargetStartedFromHome = false;
        gestureTargetUntilNanos = nowNanos + 700_000_000L;
        setDesired(CaptureScene.RECENTS);
    }

    void setGestureTarget(String target, long nowNanos) {
        CaptureScene next = "HOME".equals(target) ? CaptureScene.HOME
                : "RECENTS".equals(target) ? CaptureScene.RECENTS : CaptureScene.APP;
        homeTargetStartedFromHome = next == CaptureScene.HOME && desired == CaptureScene.HOME;
        gestureTarget = next;
        gestureTargetUntilNanos = nowNanos + 1_500_000_000L;
        setDesired(next);
    }

    boolean gestureTargetExpired(long nowNanos) {
        return gestureTarget != null && nowNanos >= gestureTargetUntilNanos;
    }

    void clearGestureTarget() {
        gestureTarget = null;
        gestureTargetUntilNanos = 0L;
        homeTargetStartedFromHome = false;
    }

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (recentsVisible) return CaptureScene.RECENTS;
        if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
        return CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean lifecycleKnown, boolean launcherResumed) {
        // HyperOS can emit GestureToHome while launching an app. The signal is stale only
        // when it originated from an already-HOME state and focus/lifecycle subsequently
        // proves Launcher is no longer resumed. A genuine APP/RECENTS -> HOME destination
        // remains authoritative while Launcher is still paused and is therefore preserved.
        if (gestureTarget == CaptureScene.HOME && homeTargetStartedFromHome
                && lifecycleKnown && !launcherResumed) {
            clearGestureTarget();
        }
        CaptureScene next = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
        return setDesired(next);
    }

    void setWorkstationSuspended(boolean enabled, long nowNanos,
                                 boolean recentsVisible, boolean lifecycleKnown,
                                 boolean launcherResumed) {
        workstationSuspended = enabled;
        clearGestureTarget();
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
