package com.hellovoid.liquiddock;

/**
 * Central capture-rate policy. The persisted power limit belongs to APP backdrop capture only;
 * HOME/ALL_APPS are event-driven and RECENTS is paced by direct user interaction.
 */
final class CaptureCadence {
    private static final long RECENTS_INTERACTION_INTERVAL_NANOS = intervalForFps(60, 5, 165);

    private final long baseIntervalNanos;
    private long dynamicIntervalNanos = intervalForFps(30, 5, 120);
    private long probeIntervalNanos = intervalForFps(3, 1, 10);
    private long powerLimitIntervalNanos = intervalForFps(20, 5, 60);

    CaptureCadence(int baseFps) { baseIntervalNanos = intervalForFps(baseFps, 5, 165); }

    void setDynamicFps(int activeFps, int probeFps) {
        dynamicIntervalNanos = intervalForFps(activeFps, 5, 120);
        probeIntervalNanos = intervalForFps(probeFps, 1, 10);
    }

    void setPowerLimitFps(int fps) {
        powerLimitIntervalNanos = intervalForFps(fps, 5, 60);
    }

    long intervalNanos(CaptureScene scene, boolean dynamicEnabled,
                       long dynamicActiveUntilNanos, long nowNanos) {
        // Recents pixels are driven by finger/overview input. Never let the APP adaptive capture
        // setting turn a slow swipe into a low-FPS or stalled backdrop.
        if (scene == CaptureScene.RECENTS) return RECENTS_INTERACTION_INTERVAL_NANOS;

        long requested = baseIntervalNanos;
        if (scene == CaptureScene.APP) {
            if (dynamicEnabled) {
                requested = nowNanos < dynamicActiveUntilNanos
                        ? dynamicIntervalNanos : probeIntervalNanos;
            }
            // The persisted capture power limit is an APP-only budget. HOME and ALL_APPS capture
            // only on explicit state/geometry events and must not inherit a stale APP cap.
            return Math.max(requested, powerLimitIntervalNanos);
        }
        return requested;
    }

    private static long intervalForFps(int fps, int min, int max) {
        return 1_000_000_000L / Math.max(min, Math.min(max, fps));
    }
}
