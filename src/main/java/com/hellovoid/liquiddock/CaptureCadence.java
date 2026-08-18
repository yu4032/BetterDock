package com.hellovoid.liquiddock;

/**
 * Central capture-rate policy.
 *
 * APP sampling is the only path controlled by the adaptive dynamic/probe rates and their
 * user-configured power ceiling. RECENTS is direct user interaction and therefore has a fixed
 * interaction cadence: changing the dynamic APP sampling knobs must never slow a swipe gesture.
 */
final class CaptureCadence {
    private static final long INTERACTION_INTERVAL_NANOS = intervalForFps(60, 60, 60);

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
        // Recents follows a finger/overview animation, not APP pixel-motion probing. Keep its
        // latency independent from liquid_capture_power_limit_fps and the APP probe/active rate.
        if (scene == CaptureScene.RECENTS) return INTERACTION_INTERVAL_NANOS;

        long requested = baseIntervalNanos;
        if (dynamicEnabled && scene == CaptureScene.APP) {
            requested = nowNanos < dynamicActiveUntilNanos
                    ? dynamicIntervalNanos : probeIntervalNanos;
        }
        return Math.max(requested, powerLimitIntervalNanos);
    }

    private static long intervalForFps(int fps, int min, int max) {
        return 1_000_000_000L / Math.max(min, Math.min(max, fps));
    }
}
