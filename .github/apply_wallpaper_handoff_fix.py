from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, got {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private int cacheWallpaperId = -1;\n",
    "    private int cacheWallpaperId = -1;\n"
    "    // A wallpaper bitmap is only valid for the exact screen-space transform generation\n"
    "    // that produced it.  HOME transitions animate wallpaper offset/zoom independently of\n"
    "    // the Dock, so geometry containment alone is not a valid cache identity.\n"
    "    private volatile long wallpaperContentRevision;\n"
    "    private volatile long cacheWallpaperContentRevision = -1L;\n"
)

replace_once(
    "        wallpaperOffsetValid = true;\n"
    "        wallpaperOffsetXBits = xb;\n"
    "        wallpaperOffsetYBits = yb;\n"
    "        requestStateCapture();\n",
    "        wallpaperOffsetValid = true;\n"
    "        wallpaperOffsetXBits = xb;\n"
    "        wallpaperOffsetYBits = yb;\n"
    "        invalidateWallpaperCaptureContent(\"offset\");\n"
    "        requestStateCapture(\"wallpaper-offset\");\n"
)

replace_once(
    "        wallpaperDisplayOffsetValid = true;\n"
    "        wallpaperDisplayOffsetX = x;\n"
    "        wallpaperDisplayOffsetY = y;\n"
    "        requestStateCapture();\n",
    "        wallpaperDisplayOffsetValid = true;\n"
    "        wallpaperDisplayOffsetX = x;\n"
    "        wallpaperDisplayOffsetY = y;\n"
    "        invalidateWallpaperCaptureContent(\"display-offset\");\n"
    "        requestStateCapture(\"wallpaper-display-offset\");\n"
)

replace_once(
    "        wallpaperZoomValid = true;\n"
    "        wallpaperZoomBits = bits;\n"
    "        requestStateCapture();\n",
    "        wallpaperZoomValid = true;\n"
    "        wallpaperZoomBits = bits;\n"
    "        invalidateWallpaperCaptureContent(\"zoom\");\n"
    "        requestStateCapture(\"wallpaper-zoom\");\n"
)

replace_once(
    "        cacheDisplayHeight = -1;\n"
    "        cacheWallpaperId = -1;\n"
    "        if (old != null && old != capture && !old.isRecycled()) {\n",
    "        cacheDisplayHeight = -1;\n"
    "        cacheWallpaperId = -1;\n"
    "        cacheWallpaperContentRevision = -1L;\n"
    "        if (old != null && old != capture && !old.isRecycled()) {\n"
)

replace_once(
    "    /**\n"
    "     * Cheap state polling; never captures by itself when all tracked values are static.\n",
    "    /** Mark cached mode-2 pixels stale without dropping the currently displayed frame.\n"
    "     * The old bitmap stays alive until a fresh capture replaces it, avoiding a visible\n"
    "     * blank/flicker during the live-to-wallpaper handoff. */\n"
    "    private void invalidateWallpaperCaptureContent(String reason) {\n"
    "        wallpaperContentRevision++;\n"
    "        wallpaperCacheReady = false;\n"
    "        cacheWallpaperContentRevision = -1L;\n"
    "        logI(\"wallpaper capture content invalidated reason=\" + reason\n"
    "                + \" revision=\" + wallpaperContentRevision);\n"
    "    }\n\n"
    "    /**\n"
    "     * Cheap state polling; never captures by itself when all tracked values are static.\n"
)

replace_once(
    "        // Gesture events are prearm-only. Exact Overview enter/exit callbacks own the\n"
    "        // confirmed live-Recents boundary; HOME/APP still replace a cancelled path immediately.\n"
    "        sceneState.setGestureTarget(target, System.nanoTime());\n"
    "        updateDesiredScene();\n",
    "        // Gesture events are prearm-only. Exact Overview enter/exit callbacks own the\n"
    "        // confirmed live-Recents boundary; HOME/APP still replace a cancelled path immediately.\n"
    "        CaptureScene previousDesired = sceneState.desired();\n"
    "        sceneState.setGestureTarget(target, System.nanoTime());\n"
    "        if (\"HOME\".equals(target) && previousDesired != CaptureScene.HOME) {\n"
    "            // GestureToHome arrives before the remote animation is fully settled. Never\n"
    "            // hand that transition back to a pre-APP/pre-Recents wallpaper cache.\n"
    "            invalidateWallpaperCaptureContent(\"scene-handoff-home\");\n"
    "            lastCaptureStartNanos = 0L;\n"
    "        }\n"
    "        updateDesiredScene();\n"
)

replace_once(
    "        sourceDirty = true;\n"
    "        // Recents→HOME: the scene just flipped — capture immediately for instant\n"
    "        // wallpaper transition, don't wait for the next observation cycle.\n"
    "        if (prev == CaptureScene.RECENTS && sceneState.desired() != CaptureScene.RECENTS) {\n"
    "            lastCaptureStartNanos = 0L;\n"
    "            requestStateCapture(\"scene-settle-home\");\n"
    "        }\n",
    "        sourceDirty = true;\n"
    "        // A live APP/Recents composition and mode-2 wallpaper are different screen-space\n"
    "        // snapshots.  The first HOME request must therefore bypass any pre-transition cache.\n"
    "        if (prev != CaptureScene.HOME && sceneState.desired() == CaptureScene.HOME) {\n"
    "            invalidateWallpaperCaptureContent(\"scene-handoff-home\");\n"
    "            lastCaptureStartNanos = 0L;\n"
    "            requestStateCapture(\"scene-settle-home\");\n"
    "        } else if (prev == CaptureScene.RECENTS\n"
    "                && sceneState.desired() != CaptureScene.RECENTS) {\n"
    "            // Preserve the existing immediate Recents-exit boundary for non-HOME targets.\n"
    "            lastCaptureStartNanos = 0L;\n"
    "            requestStateCapture(\"scene-settle-non-recents\");\n"
    "        }\n"
)

replace_once(
    "                tmpDisplaySize.x >= tmpDisplaySize.y ? 1 : 0,\n"
    "                stripRect, tileRect, dockRect);\n",
    "                tmpDisplaySize.x >= tmpDisplaySize.y ? 1 : 0,\n"
    "                stripRect, tileRect, dockRect, wallpaperContentRevision);\n"
)

replace_once(
    "        if (rotationStabilizeUntilNanos != 0) return false;\n"
    "        // Orientation identity comes from the request that produced the strip, not from\n",
    "        if (rotationStabilizeUntilNanos != 0) return false;\n"
    "        if (req.wallpaperContentRevision != cacheWallpaperContentRevision) return false;\n"
    "        // Orientation identity comes from the request that produced the strip, not from\n"
)

replace_once(
    "            if (generation != captureGeneration\n"
    "                    || !sceneState.matches(requestScene, requestSceneRevision)\n"
    "                    || !isRequestOrientationCurrent(req)\n"
    "                    || !isCaptureAllowed()) {\n",
    "            if (generation != captureGeneration\n"
    "                    || !sceneState.matches(requestScene, requestSceneRevision)\n"
    "                    || req.wallpaperContentRevision != wallpaperContentRevision\n"
    "                    || !isRequestOrientationCurrent(req)\n"
    "                    || !isCaptureAllowed()) {\n"
)

replace_once(
    "        try {\n"
    "            if (strip == null || strip.isRecycled()) return;\n"
    "            Bitmap copy = strip.copy(Bitmap.Config.ARGB_8888, false);\n",
    "        try {\n"
    "            if (strip == null || strip.isRecycled()) return;\n"
    "            if (req.wallpaperContentRevision != wallpaperContentRevision) return;\n"
    "            Bitmap copy = strip.copy(Bitmap.Config.ARGB_8888, false);\n"
)

replace_once(
    "            cacheDisplayWidth = req.displayWidth;\n"
    "            cacheDisplayHeight = req.displayHeight;\n"
    "            try {\n",
    "            cacheDisplayWidth = req.displayWidth;\n"
    "            cacheDisplayHeight = req.displayHeight;\n"
    "            cacheWallpaperContentRevision = req.wallpaperContentRevision;\n"
    "            try {\n"
)

replace_once(
    "            if (activeCaptureAttempt != attempt) {\n"
    "                if (strip != null && !strip.isRecycled()) strip.recycle();\n"
    "                return;\n"
    "            }\n"
    "            // Black-frame guard: on HyperOS captureMode(2) against the wallpaper layer\n",
    "            if (activeCaptureAttempt != attempt) {\n"
    "                if (strip != null && !strip.isRecycled()) strip.recycle();\n"
    "                return;\n"
    "            }\n"
    "            if (requestScene == CaptureScene.HOME\n"
    "                    && request.wallpaperContentRevision != wallpaperContentRevision) {\n"
    "                if (strip != null && !strip.isRecycled()) strip.recycle();\n"
    "                mainHandler.post(() -> {\n"
    "                    if (activeCaptureAttempt != attempt) return;\n"
    "                    retireCaptureAttempt(attempt);\n"
    "                    sourceDirty = true;\n"
    "                    lastCaptureStartNanos = 0L;\n"
    "                    requestStateCapture(\"stale-wallpaper-revision\");\n"
    "                });\n"
    "                return;\n"
    "            }\n"
    "            // Black-frame guard: on HyperOS captureMode(2) against the wallpaper layer\n"
)

replace_once(
    "                if (generation != captureGeneration\n"
    "                        || !sceneState.matches(requestScene, requestSceneRevision)\n"
    "                        || !isRequestOrientationCurrent(request)\n"
    "                        || !isCaptureAllowed()) {\n",
    "                if (generation != captureGeneration\n"
    "                        || !sceneState.matches(requestScene, requestSceneRevision)\n"
    "                        || (requestScene == CaptureScene.HOME\n"
    "                            && request.wallpaperContentRevision != wallpaperContentRevision)\n"
    "                        || !isRequestOrientationCurrent(request)\n"
    "                        || !isCaptureAllowed()) {\n"
)

replace_once(
    "                if (generation != captureGeneration || !isRequestOrientationCurrent(request)\n"
    "                        || !isCaptureAllowed()) {\n",
    "                if (generation != captureGeneration || !isRequestOrientationCurrent(request)\n"
    "                        || (requestScene == CaptureScene.HOME\n"
    "                            && request.wallpaperContentRevision != wallpaperContentRevision)\n"
    "                        || !isCaptureAllowed()) {\n"
)

replace_once(
    "        final Rect stripRect;\n"
    "        final Rect tileRect;\n"
    "        final Rect dockRect;\n\n"
    "        CaptureRequest(int displayId, int rotation, int displayWidth, int displayHeight,\n"
    "                       int orientationIndex, Rect stripRect, Rect tileRect, Rect dockRect) {\n",
    "        final Rect stripRect;\n"
    "        final Rect tileRect;\n"
    "        final Rect dockRect;\n"
    "        final long wallpaperContentRevision;\n\n"
    "        CaptureRequest(int displayId, int rotation, int displayWidth, int displayHeight,\n"
    "                       int orientationIndex, Rect stripRect, Rect tileRect, Rect dockRect,\n"
    "                       long wallpaperContentRevision) {\n"
)

replace_once(
    "            this.tileRect = new Rect(tileRect);\n"
    "            this.dockRect = new Rect(dockRect);\n"
    "        }\n",
    "            this.tileRect = new Rect(tileRect);\n"
    "            this.dockRect = new Rect(dockRect);\n"
    "            this.wallpaperContentRevision = wallpaperContentRevision;\n"
    "        }\n"
)

path.write_text(text)
print("Applied wallpaper live-to-mode2 handoff revision fix")
