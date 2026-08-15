package com.hellovoid.liquiddock;

/** Chooses a backdrop source while keeping speculative Launcher transitions wallpaper-backed. */
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
        if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.RECENTS) {
            return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        // All Apps stays wallpaper-backed. localLayerAvailable is retained for source/API
        // compatibility with the 8ee84ed baseline but does not grant live capture authority.
        return Source.WALLPAPER;
    }
}
