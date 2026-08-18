package com.hellovoid.liquiddock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure classification for WMShell transitions relevant to LiquidDock visual handoff. */
final class SystemUiTransitionPolicy {
    enum Kind { NONE, APP_TO_LAUNCHER, LAUNCHER_TO_APP }

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
        boolean homeBack;
        boolean appFront;
        boolean appBack;
        boolean wallpaperFront;
        boolean showWallpaper;
    }

    private SystemUiTransitionPolicy() {}

    static Kind classify(List<Change> changes) {
        Map<Integer, Evidence> byDisplay = collect(changes);
        for (Evidence evidence : byDisplay.values()) {
            if (evidence.homeFront && evidence.appBack
                    && (evidence.showWallpaper || evidence.wallpaperFront)) {
                return Kind.APP_TO_LAUNCHER;
            }
            if (evidence.appFront && evidence.homeBack) {
                return Kind.LAUNCHER_TO_APP;
            }
        }
        return Kind.NONE;
    }

    static int displayIdFor(List<Change> changes, Kind kind) {
        if (kind == null || kind == Kind.NONE) return -1;
        Map<Integer, Evidence> byDisplay = collect(changes);
        for (Map.Entry<Integer, Evidence> entry : byDisplay.entrySet()) {
            Evidence evidence = entry.getValue();
            if (kind == Kind.APP_TO_LAUNCHER
                    && evidence.homeFront && evidence.appBack
                    && (evidence.showWallpaper || evidence.wallpaperFront)) {
                return entry.getKey();
            }
            if (kind == Kind.LAUNCHER_TO_APP && evidence.appFront && evidence.homeBack) {
                return entry.getKey();
            }
        }
        return -1;
    }

    static int displayIdForAppToLauncher(List<Change> changes) {
        return displayIdFor(changes, Kind.APP_TO_LAUNCHER);
    }

    private static Map<Integer, Evidence> collect(List<Change> changes) {
        Map<Integer, Evidence> byDisplay = new HashMap<>();
        if (changes == null || changes.isEmpty()) return byDisplay;
        for (Change change : changes) {
            if (change == null || change.displayId < 0) continue;
            Evidence evidence = byDisplay.computeIfAbsent(change.displayId, key -> new Evidence());
            evidence.homeFront |= change.homeTask && change.movingFront;
            evidence.homeBack |= change.homeTask && change.movingBack;
            evidence.appFront |= change.appTask && change.movingFront;
            evidence.appBack |= change.appTask && change.movingBack;
            evidence.wallpaperFront |= change.wallpaper && change.movingFront;
            evidence.showWallpaper |= change.showWallpaper;
        }
        return byDisplay;
    }
}
