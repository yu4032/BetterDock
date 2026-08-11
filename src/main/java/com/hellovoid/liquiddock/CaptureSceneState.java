package com.hellovoid.liquiddock;

/** Owns scene transitions and revisioning used to reject stale asynchronous frames. */
final class CaptureSceneState {
    private CaptureScene desired = CaptureScene.APP;
    private CaptureScene gestureTarget;
    private long gestureTargetUntilNanos;
    private long revision;
    private boolean workstationWallpaperOnly;

    CaptureScene desired() { return desired; }
    long revision() { return revision; }
    boolean workstationWallpaperOnly() { return workstationWallpaperOnly; }
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

    CaptureScene resolve(long nowNanos, boolean recentsVisible,
                         boolean lifecycleKnown, boolean launcherResumed) {
        if (workstationWallpaperOnly) return CaptureScene.HOME;
        if (gestureTarget != null && nowNanos < gestureTargetUntilNanos) return gestureTarget;
        if (recentsVisible) return CaptureScene.RECENTS;
        if (lifecycleKnown && launcherResumed) return CaptureScene.HOME;
        return CaptureScene.APP;
    }

    boolean refresh(long nowNanos, boolean recentsVisible,
                    boolean lifecycleKnown, boolean launcherResumed) {
        CaptureScene next = resolve(nowNanos, recentsVisible, lifecycleKnown, launcherResumed);
        return setDesired(next);
    }

    void setWorkstationWallpaperOnly(boolean enabled, long nowNanos,
                                     boolean recentsVisible, boolean lifecycleKnown,
                                     boolean launcherResumed) {
        workstationWallpaperOnly = enabled;
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
