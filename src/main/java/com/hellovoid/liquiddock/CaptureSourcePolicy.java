package com.hellovoid.liquiddock;

/** Chooses a backdrop source while keeping speculative or unknown transitions wallpaper-backed. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY, LOCAL_LAYER }

    private CaptureSourcePolicy() {}

    /** Legacy/baseline selector: RECENTS is unconfirmed and therefore remains wallpaper-backed. */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        return sourceFor(scene, localLayerAvailable, false);
    }

    /**
     * RECENTS becomes a live full-display source only after the exact Overview lifecycle
     * confirms entry. Haptic/distance/gesture prearm can select the RECENTS scene, but must
     * pass recentsLiveConfirmed=false and therefore stay on the safe wallpaper path.
     */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed) {
        return sourceFor(scene, localLayerAvailable, recentsLiveConfirmed, false);
    }

    /**
     * HOME and UNKNOWN are always wallpaper-backed. The homeLiveBackdrop parameter is retained
     * only for call-site/API compatibility with the existing Dock path; freeform task leashes
     * affect APP full-display exclusion, not HOME source ownership.
     */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed, boolean homeLiveBackdrop) {
        if (scene == null || scene == CaptureScene.UNKNOWN) return Source.WALLPAPER;
        if (scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.RECENTS) {
            return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }

    /**
     * Workstation All Apps is a Launcher-owned overlay, so its local layer is the cleanest
     * source. Recents is different: App -> Overview is a mixed SurfaceFlinger composition
     * containing the app/remote-animation leash plus Launcher task views. Capturing only the
     * Launcher ViewRoot drops the app part of that transition, so Recents must use the composed
     * display and let DockLiquidGlassView exclude the Floating Dock surface.
     */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.RECENTS) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.ALL_APPS) {
            return localLayerAvailable ? Source.LOCAL_LAYER : Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }
}
