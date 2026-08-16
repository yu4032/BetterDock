from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java")
text = path.read_text()


def replace_once(old: str, new: str, marker: str) -> None:
    global text
    if marker in text:
        return
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{marker}: expected exactly one anchor, found {count}")
    text = text.replace(old, new, 1)


replace_once(
'''    private boolean wallpaperZoomValid;\n    private int wallpaperZoomBits;\n''',
'''    private boolean wallpaperZoomValid;\n    private int wallpaperZoomBits;\n    // Launcher supplies the actual visual wallpaper scale each animation frame.  Keep its\n    // revision separate from WallpaperManager callbacks so cached mode-2 strips can never\n    // survive across two different visual transforms.\n    private float launcherWallpaperVisualScale = 1f;\n    private volatile long wallpaperTransformRevision;\n    private volatile long cacheWallpaperTransformRevision = -1L;\n''',
'launcherWallpaperVisualScale = 1f')

replace_once(
'''        cacheDisplayHeight = -1;\n        cacheWallpaperId = -1;\n        if (old != null && old != capture && !old.isRecycled()) {\n''',
'''        cacheDisplayHeight = -1;\n        cacheWallpaperId = -1;\n        cacheWallpaperTransformRevision = -1L;\n        if (old != null && old != capture && !old.isRecycled()) {\n''',
'cacheWallpaperTransformRevision = -1L;')

replace_once(
'''    @Override protected void onAttachedToWindow() {\n''',
'''    /** Visual wallpaper scale sampled from LocalWallpaperElement.updateTargetParams(float).\n     *  This is content-transform invalidation only; SystemUI remains the HOME/APP authority. */\n    void setLauncherWallpaperVisualScale(float scale) {\n        if (Looper.myLooper() != Looper.getMainLooper()) {\n            mainHandler.post(() -> setLauncherWallpaperVisualScale(scale));\n            return;\n        }\n        if (!Float.isFinite(scale) || scale < 0.8f || scale > 1.25f) return;\n        if (Math.abs(scale - launcherWallpaperVisualScale) < 0.0001f) return;\n        launcherWallpaperVisualScale = scale;\n        wallpaperTransformRevision++;\n        wallpaperCacheReady = false;\n        clearWallpaperCacheSafely();\n        logI("wallpaper zoom scale=" + scale\n                + " revision=" + wallpaperTransformRevision);\n\n        // Only HOME consumes wallpaper-only capture.  APP remains FULL_DISPLAY and Recents\n        // keeps its independently validated live lifecycle.  GestureToHome itself already\n        // requests the first HOME frame; subsequent scale edges keep that mode-2 frame fresh.\n        if (resolveCaptureScene() == CaptureScene.HOME) {\n            requestStateCapture("wallpaper-zoom");\n        }\n    }\n\n    @Override protected void onAttachedToWindow() {\n''',
'setLauncherWallpaperVisualScale(float scale)')

replace_once(
'''        final long requestSceneRevision = sceneState.revision();\n''',
'''        final long requestSceneRevision = sceneState.revision();\n        final long requestWallpaperTransformRevision = wallpaperTransformRevision;\n''',
'final long requestWallpaperTransformRevision = wallpaperTransformRevision;')

replace_once(
'''                            handleCaptureResult(bmp, req, generation, attempt,\n                                    requestScene, requestSceneRevision);\n''',
'''                            handleCaptureResult(bmp, req, generation, attempt,\n                                    requestScene, requestSceneRevision,\n                                    requestWallpaperTransformRevision);\n''',
'requestSceneRevision,\n                                    requestWallpaperTransformRevision')

replace_once(
'''    private void handleCaptureResult(Bitmap strip, CaptureRequest request, long generation,\n                                     long attempt, CaptureScene requestScene,\n                                     long requestSceneRevision) {\n''',
'''    private void handleCaptureResult(Bitmap strip, CaptureRequest request, long generation,\n                                     long attempt, CaptureScene requestScene,\n                                     long requestSceneRevision,\n                                     long requestWallpaperTransformRevision) {\n''',
'long requestWallpaperTransformRevision) {')

replace_once(
'''                cacheWallpaperStrip(strip, request);\n''',
'''                cacheWallpaperStrip(strip, request, requestWallpaperTransformRevision);\n''',
'cacheWallpaperStrip(strip, request, requestWallpaperTransformRevision);')

replace_once(
'''        if (!wallpaperCacheReady || wallpaperStripCache == null\n                || wallpaperStripCache.isRecycled() || cacheStripRect == null) return false;\n''',
'''        if (!wallpaperCacheReady || wallpaperStripCache == null\n                || wallpaperStripCache.isRecycled() || cacheStripRect == null\n                || cacheWallpaperTransformRevision != wallpaperTransformRevision) return false;\n''',
'cacheWallpaperTransformRevision != wallpaperTransformRevision')

replace_once(
'''    private void cacheWallpaperStrip(Bitmap strip, CaptureRequest req) {\n''',
'''    private void cacheWallpaperStrip(Bitmap strip, CaptureRequest req,\n                                      long transformRevision) {\n''',
'long transformRevision) {')

replace_once(
'''            cacheDisplayHeight = req.displayHeight;\n            try {\n''',
'''            cacheDisplayHeight = req.displayHeight;\n            cacheWallpaperTransformRevision = transformRevision;\n            try {\n''',
'cacheWallpaperTransformRevision = transformRevision;')

path.write_text(text)
print("wallpaper zoom cache synchronization patch applied")
