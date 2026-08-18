package com.hellovoid.liquiddock;

/**
 * Central capture-rate policy. The persisted power limit belongs to idle APP backdrop capture;
 * direct pointer interaction and RECENTS are paced independently so a slow gesture cannot fall
 * back to the static-scene probe rate.
 */
final class CaptureCadence {
    private static final long INTERACTION_INTERVAL_NANOS = intervalForFps(60, 5, 165);
    // MOVE events normally arrive much faster than this. The short grace bridges scheduler/input
    // jitter without turning a completed gesture into continuous high-frequency capture.
    private static final long INTERACTION_GRACE_NANOS = 120_000_000L;

    private final long baseIntervalNanos;
    private long dynamicIntervalNanos = intervalForFps(30, 5, 120);
    private long probeIntervalNanos = intervalForFps(3, 1, 10);
    private long powerLimitIntervalNanos = intervalForFps(20, 5, 60);
    private long interactionActiveUntilNanos;

    CaptureCadence(int baseFps) { baseIntervalNanos = intervalForFps(baseFps, 5, 165); }

    void setDynamicFps(int activeFps, int probeFps) {
        dynamicIntervalNanos = intervalForFps(activeFps, 5, 120);
        probeIntervalNanos = intervalForFps(probeFps, 1, 10);
    }

    void setPowerLimitFps(int fps) {
        powerLimitIntervalNanos = intervalForFps(fps, 5, 60);
    }

    void noteInteraction(long nowNanos) {
        interactionActiveUntilNanos = Math.max(
                interactionActiveUntilNanos, nowNanos + INTERACTION_GRACE_NANOS);
    }

    void clearInteraction() {
        interactionActiveUntilNanos = 0L;
    }

    long intervalNanos(CaptureScene scene, boolean dynamicEnabled,
                       long dynamicActiveUntilNanos, long nowNanos) {
        // Pointer activity wins before scene-specific adaptive policy. In particular, the first
        // part of a Dock-to-Recents swipe is still APP, but must not run at APP probe/power FPS.
        if (nowNanos < interactionActiveUntilNanos) return INTERACTION_INTERVAL_NANOS;

        // Exact/target Recents remains responsive between pointer events while launcher animation
        // state continues to dirty the source.
        if (scene == CaptureScene.RECENTS) return INTERACTION_INTERVAL_NANOS;

        long requested = baseIntervalNanos;
        if (scene == CaptureScene.APP) {
            if (dynamicEnabled) {
                requested = nowNanos < dynamicActiveUntilNanos
                        ? dynamicIntervalNanos : probeIntervalNanos;
            }
            // The persisted capture power limit is an idle APP budget. HOME and ALL_APPS capture
            // only on explicit state/geometry events and do not inherit this APP cap.
            return Math.max(requested, powerLimitIntervalNanos);
        }
        return requested;
    }

    private static long intervalForFps(int fps, int min, int max) {
        return 1_000_000_000L / Math.max(min, Math.min(max, fps));
    }
}
