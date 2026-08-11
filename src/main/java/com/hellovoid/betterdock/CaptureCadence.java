package com.hellovoid.betterdock;

/** Central capture-rate policy. Values are intervals so the strictest power limit wins. */
final class CaptureCadence {
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
