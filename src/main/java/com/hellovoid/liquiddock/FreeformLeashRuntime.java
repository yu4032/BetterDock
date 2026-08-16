package com.hellovoid.liquiddock;

import android.os.IBinder;

/** Launcher-process rendezvous between capture setup and final mode-1 submission. */
final class FreeformLeashRuntime {
    private static volatile FreeformTaskLeashResolver resolver;
    private static volatile boolean captureGateInstalled;

    private FreeformLeashRuntime() {}

    static void install(FreeformTaskLeashResolver value) {
        if (value == null) return;
        resolver = value;
        value.setProviderDemanded(true);
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

    static IBinder providerBinderForDiagnostics() {
        FreeformTaskLeashResolver value = resolver;
        return value != null ? value.providerBinderForDiagnostics() : null;
    }

    static FreeformTaskLeashResolver.Resolution resolveForCapture(int displayId) {
        FreeformTaskLeashResolver value = resolver;
        if (!captureGateInstalled || value == null) {
            return FreeformTaskLeashResolver.Resolution.unavailable(true);
        }
        return value.resolveVisibleLeashes(displayId);
    }
}
