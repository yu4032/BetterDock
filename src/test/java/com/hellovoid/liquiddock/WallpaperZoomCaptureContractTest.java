package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class WallpaperZoomCaptureContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test public void zoomScaleInvalidatesWallpaperCacheAndRequestsFreshMode2() throws Exception {
        String patch = read(".github/apply_wallpaper_zoom_sync.py");
        assertTrue(patch.contains("setLauncherWallpaperVisualScale"));
        assertTrue(patch.contains("wallpaperTransformRevision++"));
        assertTrue(patch.contains("wallpaperCacheReady = false"));
        assertTrue(patch.contains("clearWallpaperCacheSafely()"));
        assertTrue(patch.contains("requestStateCapture(\"wallpaper-zoom\")"));
    }

    @Test public void homeGestureDropsStaticCacheBeforeFirstZoomCallback() throws Exception {
        String patch = read(".github/apply_wallpaper_zoom_sync.py");
        int home = patch.indexOf("if (\"HOME\".equals(target))");
        int ready = patch.indexOf("wallpaperCacheReady = false", home);
        int clear = patch.indexOf("clearWallpaperCacheSafely()", ready);
        int marker = patch.indexOf("HOME gesture dropped wallpaper cache", clear);
        assertTrue(home >= 0 && ready > home && clear > ready && marker > clear);
        assertTrue(marker - home < 600);
    }

    @Test public void cacheIsBoundToTheZoomRevisionThatProducedIt() throws Exception {
        String patch = read(".github/apply_wallpaper_zoom_sync.py");
        assertTrue(patch.contains("cacheWallpaperTransformRevision"));
        assertTrue(patch.contains("cacheWallpaperTransformRevision != wallpaperTransformRevision"));
        assertTrue(patch.contains("requestWallpaperTransformRevision"));
        assertTrue(patch.contains("cacheWallpaperStrip(strip, request, requestWallpaperTransformRevision)"));
    }

    @Test public void conservativeCandidateDoesNotApplyInverseCropYet() throws Exception {
        String patch = read(".github/apply_wallpaper_zoom_sync.py");
        assertFalse(patch.contains("WallpaperZoomTransform.adjust("));
        assertFalse(patch.contains("captureScale * launcherWallpaperVisualScale"));
    }

    @Test public void finalRuntimeUsesTypedGlassMethod() throws Exception {
        String runtime = read("src/main/java/com/hellovoid/liquiddock/WallpaperZoomRuntime.java");
        assertTrue(runtime.contains("glass.setLauncherWallpaperVisualScale(scale)"));
        assertFalse(runtime.contains("HookUtil.invoke(glass, \"setLauncherWallpaperVisualScale\""));
    }

    @Test public void workflowAppliesTheZoomSyncPatchBeforeTestsAndBuild() throws Exception {
        String workflow = read(".github/workflows/api101-build.yml");
        int patch = workflow.indexOf("python3 .github/apply_wallpaper_zoom_sync.py");
        int tests = workflow.indexOf("./gradlew testDebugUnitTest");
        int build = workflow.indexOf("./gradlew assembleDebug");
        assertTrue(patch >= 0 && tests > patch && build > tests);
    }
}
