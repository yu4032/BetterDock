package com.hellovoid.liquiddock;

/** Selects the compositor source while keeping speculative Launcher transitions wallpaper-backed. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY }

    private CaptureSourcePolicy() {}

    /** API101 compatibility: only authoritative live scenes use the composed display. */
    static Source sourceFor(CaptureScene scene) {
        if (scene == CaptureScene.APP || scene == CaptureScene.RECENTS) {
            return Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }

    /** Legacy call shape: RECENTS has not yet crossed its authoritative lifecycle boundary. */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
        return sourceFor(scene, localLayerAvailable, false, false);
    }

    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed) {
        return sourceFor(scene, localLayerAvailable, recentsLiveConfirmed, false);
    }

    /** HOME becomes live only while a visible freeform task overlays the Launcher. */
    static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                            boolean recentsLiveConfirmed, boolean homeLiveBackdrop) {
        if (scene == null) return Source.WALLPAPER;
        if (scene == CaptureScene.HOME) {
            return homeLiveBackdrop ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
        if (scene == CaptureScene.RECENTS) {
            return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
        }
        return Source.WALLPAPER;
    }

    /** API101 intentionally has no LayerCapture path. Workstation All Apps and Recents use the
     * composed display with Dock/freeform exclusions; other workstation scenes stay wallpaper-backed. */
    static Source sourceForWorkstationScene(CaptureScene scene, boolean localLayerAvailable) {
        if (scene == CaptureScene.RECENTS || scene == CaptureScene.ALL_APPS) {
            return Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }
}
