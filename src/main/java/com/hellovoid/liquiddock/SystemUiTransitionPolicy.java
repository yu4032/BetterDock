package com.hellovoid.liquiddock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure classification for WMShell transitions relevant to LiquidDock visual handoff. */
final class SystemUiTransitionPolicy {
    enum Kind { NONE, APP_TO_LAUNCHER }

    static final class Change {
        final int displayId;
        final boolean homeTask;
        final boolean appTask;
        final boolean wallpaper;
        final boolean movingFront;
        final boolean movingBack;
        final boolean showWallpaper;

        Change(int displayId, boolean homeTask, boolean appTask, boolean wallpaper,
               boolean movingFront, boolean movingBack, boolean showWallpaper) {
            this.displayId = displayId;
            this.homeTask = homeTask;
            this.appTask = appTask;
            this.wallpaper = wallpaper;
            this.movingFront = movingFront;
            this.movingBack = movingBack;
            this.showWallpaper = showWallpaper;
        }
    }

    private static final class Evidence {
        boolean homeFront;
        boolean appBack;
        boolean wallpaperFront;
        boolean showWallpaper;
    }

    private SystemUiTransitionPolicy() {}

    static Kind classify(List<Change> changes) {
        if (changes == null || changes.isEmpty()) return Kind.NONE;
        Map<Integer, Evidence> byDisplay = new HashMap<>();
        for (Change change : changes) {
            if (change == null || change.displayId < 0) continue;
            Evidence evidence = byDisplay.computeIfAbsent(change.displayId, key -> new Evidence());
            evidence.homeFront |= change.homeTask && change.movingFront;
            evidence.appBack |= change.appTask && change.movingBack;
            evidence.wallpaperFront |= change.wallpaper && change.movingFront;
            evidence.showWallpaper |= change.showWallpaper;
        }
        for (Evidence evidence : byDisplay.values()) {
            if (evidence.homeFront && evidence.appBack
                    && (evidence.showWallpaper || evidence.wallpaperFront)) {
                return Kind.APP_TO_LAUNCHER;
            }
        }
        return Kind.NONE;
    }

    static int displayIdForAppToLauncher(List<Change> changes) {
        if (changes == null || changes.isEmpty()) return -1;
        Map<Integer, Evidence> byDisplay = new HashMap<>();
        for (Change change : changes) {
            if (change == null || change.displayId < 0) continue;
            Evidence evidence = byDisplay.computeIfAbsent(change.displayId, key -> new Evidence());
            evidence.homeFront |= change.homeTask && change.movingFront;
            evidence.appBack |= change.appTask && change.movingBack;
            evidence.wallpaperFront |= change.wallpaper && change.movingFront;
            evidence.showWallpaper |= change.showWallpaper;
        }
        for (Map.Entry<Integer, Evidence> entry : byDisplay.entrySet()) {
            Evidence evidence = entry.getValue();
            if (evidence.homeFront && evidence.appBack
                    && (evidence.showWallpaper || evidence.wallpaperFront)) {
                return entry.getKey();
            }
        }
        return -1;
    }
}
