package com.hellovoid.liquiddock;

/** Launcher-process rendezvous between Dock preflight and capture submission. */
final class FreeformLeashRuntime {
    private static final long PREFLIGHT_HINT_NANOS = 150_000_000L;
    private static final int PRESENCE_UNKNOWN = 0;
    private static final int PRESENCE_NONE = 1;
    private static final int PRESENCE_VISIBLE = 2;

    private static volatile FreeformTaskLeashResolver resolver;
    private static volatile boolean captureGateInstalled;
    private static volatile long preflightAtNanos;
    private static volatile int preflightDisplayId;
    private static volatile int preflightPresence = PRESENCE_UNKNOWN;

    private FreeformLeashRuntime() {}

    static void install(FreeformTaskLeashResolver value) {
        if (value != null) resolver = value;
    }

    static void setCaptureGateInstalled(boolean installed) {
        captureGateInstalled = installed;
    }

    static void updatePreflight(int displayId, boolean scanSucceeded, boolean visibleFreeform) {
        preflightDisplayId = displayId;
        preflightPresence = !scanSucceeded
                ? PRESENCE_UNKNOWN
                : (visibleFreeform ? PRESENCE_VISIBLE : PRESENCE_NONE);
        preflightAtNanos = System.nanoTime();
    }

    static boolean shouldResolveForCapture(int displayId) {
        if (!captureGateInstalled) return true;
        long age = System.nanoTime() - preflightAtNanos;
        return preflightDisplayId != displayId
                || age < 0L
                || age > PREFLIGHT_HINT_NANOS
                || preflightPresence != PRESENCE_NONE;
    }

    static void demandProvider(boolean needed) {
        FreeformTaskLeashResolver value = resolver;
        if (value != null) value.setProviderDemanded(needed);
    }

    static boolean isProviderReady() {
        FreeformTaskLeashResolver value = resolver;
        return captureGateInstalled && value != null && value.isProviderReady();
    }

    static FreeformTaskLeashResolver.Resolution resolveForCapture(int displayId) {
        FreeformTaskLeashResolver value = resolver;
        if (!captureGateInstalled || value == null) {
            return FreeformTaskLeashResolver.Resolution.unavailable(true);
        }
        return value.resolveVisibleLeashes(displayId);
    }
}
