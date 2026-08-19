package com.hellovoid.liquiddock;

/** Pure policy for rejecting a composed-display frame that has collapsed back to wallpaper. */
final class BackdropVisualPolicy {
    private BackdropVisualPolicy() {}

    static boolean shouldRejectWallpaperLikeFrame(boolean miuix307,
                                                  CaptureScene scene,
                                                  boolean pointerInteraction,
                                                  boolean wallpaperSignatureValid) {
        return miuix307
                && scene == CaptureScene.APP
                && pointerInteraction
                && wallpaperSignatureValid;
    }

    static boolean isWallpaperLikeSignature(long candidate, long wallpaper) {
        int raw = signatureDifference(candidate, wallpaper);
        if (raw <= 8) return true;

        // HyperOS can dim/brighten the exposed wallpaper during the gesture. Treat a nearly
        // uniform luminance offset as the same wallpaper while still requiring the 8x2 spatial
        // pattern to remain stable. This is intentionally the narrow comparator that previously
        // proved useful for APP-vs-wallpaper readiness; no retry/state machine is restored here.
        int sumDelta = 0;
        for (int i = 0; i < 16; i++) {
            int cv = (int) ((candidate >>> (i * 4)) & 0xF);
            int wv = (int) ((wallpaper >>> (i * 4)) & 0xF);
            sumDelta += cv - wv;
        }
        int globalDelta = Math.round(sumDelta / 16f);
        int residual = 0;
        for (int i = 0; i < 16; i++) {
            int cv = (int) ((candidate >>> (i * 4)) & 0xF);
            int wv = (int) ((wallpaper >>> (i * 4)) & 0xF);
            residual += Math.abs((cv - wv) - globalDelta);
        }
        return Math.abs(globalDelta) <= 3 && residual <= 8;
    }

    static int signatureDifference(long a, long b) {
        int difference = 0;
        for (int i = 0; i < 16; i++) {
            int av = (int) ((a >>> (i * 4)) & 0xF);
            int bv = (int) ((b >>> (i * 4)) & 0xF);
            difference += Math.abs(av - bv);
        }
        return difference;
    }
}
