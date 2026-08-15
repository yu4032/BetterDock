package com.hellovoid.liquiddock;

/** Owns visual hand-off rules when the capture source domain changes. */
final class BackdropTransitionPolicy {
    private BackdropTransitionPolicy() {}

    static boolean shouldDropInstalled(CaptureScene installed, CaptureScene target) {
        if (installed == null || target == null) return false;
        return CaptureSourcePolicy.sourceFor(installed) != CaptureSourcePolicy.sourceFor(target);
    }

    /**
     * HOME/ALL_APPS can safely fall back to the launcher's wallpaper-backed native Dock
     * while a fresh wallpaper frame is pending. APP/RECENTS cannot: exposing that native
     * background creates a visible wallpaper flash before the live mode-1 frame arrives.
     */
    static boolean shouldRevealNativeFallback(CaptureScene target) {
        if (target == null) return true;
        return CaptureSourcePolicy.sourceFor(target) == CaptureSourcePolicy.Source.WALLPAPER;
    }
}
