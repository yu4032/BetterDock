package com.hellovoid.liquiddock;

/** Chooses a backdrop source without ever full-display-capturing launcher-owned scenes. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY, LOCAL_LAYER }

    private CaptureSourcePolicy() {}

    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        // Stock Launcher keeps its Dock backdrop wallpaper-only in Launcher-owned scenes.
        // In particular, laptop All Apps renders a blurred wallpaper inside a separate
        // translucent LauncherOverlayWindow. Capturing that ViewRoot layer in isolation can
        // turn uncovered/transparent pixels black and then poison the persistent backdrop.
        // RECENTS and ALL_APPS therefore follow the stock wallpaper path regardless of whether
        // a local Launcher SurfaceControl happens to be available.
        return Source.WALLPAPER;
    }
}
