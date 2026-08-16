package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Guards HOME wallpaper freshness when mode-1 live capture hands back to mode-2 wallpaper. */
public class WallpaperHandoffCacheContractTest {
    private static String glass() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    private static String method(String source, String startNeedle, String endNeedle) {
        int start = source.indexOf(startNeedle);
        int end = source.indexOf(endNeedle, start);
        assertTrue("missing method boundary: " + startNeedle, start >= 0 && end > start);
        return source.substring(start, end);
    }

    @Test public void wallpaperTransformsInvalidateCachedScreenSpaceContent() throws Exception {
        String source = glass();
        String offsets = method(source, "void onWallpaperOffsetChanged(",
                "void onWallpaperDisplayOffsetChanged(");
        String displayOffset = method(source, "void onWallpaperDisplayOffsetChanged(",
                "void onWallpaperZoomChanged(");
        String zoom = method(source, "void onWallpaperZoomChanged(",
                "@Override protected void onAttachedToWindow()");

        assertTrue(offsets.contains("invalidateWallpaperCaptureContent("));
        assertTrue(displayOffset.contains("invalidateWallpaperCaptureContent("));
        assertTrue(zoom.contains("invalidateWallpaperCaptureContent("));
    }

    @Test public void liveSceneToHomeInvalidatesPreTransitionWallpaperCache() throws Exception {
        String source = glass();
        String update = method(source, "private void updateDesiredScene()",
                "/** Hard power gate shared by every scene");

        assertTrue("APP/RECENTS live capture must not hand off to an old HOME cache",
                update.contains("prev != CaptureScene.HOME")
                        && update.contains("sceneState.desired() == CaptureScene.HOME")
                        && update.contains("invalidateWallpaperCaptureContent(\"scene-handoff-home\")"));
    }

    @Test public void wallpaperCacheIsBoundToContentRevision() throws Exception {
        String source = glass();
        assertTrue(source.contains("private volatile long wallpaperContentRevision;"));
        assertTrue(source.contains("private volatile long cacheWallpaperContentRevision = -1L;"));
        assertTrue(source.contains("final long wallpaperContentRevision;"));
        assertTrue(source.contains("cacheWallpaperContentRevision = req.wallpaperContentRevision;"));

        String cacheServe = method(source, "private boolean tryServeWallpaperFromCache(",
                "/** Deep-copy a mode-2 strip into the wallpaper cache");
        assertTrue("cache hit must match the exact wallpaper transform generation",
                cacheServe.contains("req.wallpaperContentRevision != cacheWallpaperContentRevision"));
    }

    @Test public void staleMode2CompletionCannotRepopulateCurrentCache() throws Exception {
        String source = glass();
        String handle = method(source, "private void handleCaptureResult(",
                "private void updateDynamicAppActivity(");
        assertTrue("old mode-2 result must be rejected after a wallpaper transform",
                handle.contains("requestScene == CaptureScene.HOME")
                        && handle.contains("request.wallpaperContentRevision != wallpaperContentRevision")
                        && handle.contains("stale-wallpaper-revision"));
    }
}
