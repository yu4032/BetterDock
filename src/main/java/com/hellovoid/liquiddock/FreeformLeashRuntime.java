package com.hellovoid.liquiddock;

/** Launcher-process rendezvous between Dock preflight and capture submission. */
final class FreeformLeashRuntime {
    private static volatile FreeformTaskLeashResolver resolver;
    private static volatile boolean captureGateInstalled;

    private FreeformLeashRuntime() {}

    static void install(FreeformTaskLeashResolver value) {
        if (value != null) resolver = value;
    }

    static void setCaptureGateInstalled(boolean installed) {
        captureGateInstalled = installed;
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
