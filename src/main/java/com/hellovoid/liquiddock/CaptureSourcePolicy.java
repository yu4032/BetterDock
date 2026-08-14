package com.hellovoid.liquiddock;

/** Chooses a backdrop source without ever full-display-capturing launcher-owned scenes. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY, LOCAL_LAYER }

    private CaptureSourcePolicy() {}

    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        // Recents and All Apps are rendered by stock Launcher-owned windows/surfaces.
        // Capture that root directly; if it is not available, fail closed to wallpaper.
        return localLayerAvailable ? Source.LOCAL_LAYER : Source.WALLPAPER;
    }
}
