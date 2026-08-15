package com.hellovoid.liquiddock;

/** Selects the compositor source that physically matches the scene behind the floating Dock. */
final class CaptureSourcePolicy {
    enum Source { WALLPAPER, FULL_DISPLAY }

    private CaptureSourcePolicy() {}

    static Source sourceFor(CaptureScene scene) {
        if (scene == CaptureScene.APP || scene == CaptureScene.RECENTS) {
            return Source.FULL_DISPLAY;
        }
        return Source.WALLPAPER;
    }
}
