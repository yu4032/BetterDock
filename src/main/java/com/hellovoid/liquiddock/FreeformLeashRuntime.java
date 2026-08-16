package com.hellovoid.liquiddock;

/** Launcher-process rendezvous between Dock preflight and capture submission. */
final class FreeformLeashRuntime {
    private static volatile FreeformTaskLeashResolver resolver;

    private FreeformLeashRuntime() {}

    static void install(FreeformTaskLeashResolver value) {
        if (value != null) resolver = value;
    }

    static void demandProvider(boolean needed) {
        FreeformTaskLeashResolver value = resolver;
        if (value != null) value.setProviderDemanded(needed);
    }

    static boolean isProviderReady() {
        FreeformTaskLeashResolver value = resolver;
        return value != null && value.isProviderReady();
    }

    static FreeformTaskLeashResolver.Resolution resolveForCapture(int displayId) {
        FreeformTaskLeashResolver value = resolver;
        return value != null
                ? value.resolveVisibleLeashes(displayId)
                : FreeformTaskLeashResolver.Resolution.unavailable(true);
    }
}
