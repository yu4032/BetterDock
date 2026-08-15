package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Every capture attempt must execute against the exact mutable inputs snapshotted at start. */
public class CaptureRequestSnapshotContractTest {
    private static String glass() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void workerUsesAttemptScopedCaptureAndExclusionSnapshots() throws Exception {
        String s = glass();
        assertTrue(s.contains("final float requestCaptureScale = captureScale"));
        assertTrue(s.contains("final android.view.SurfaceControl requestDockWindowSurface"));
        assertTrue(s.contains("final String requestDockWindowLayerName"));
        assertTrue(s.contains("final String requestDragLayerName"));
        assertTrue(s.contains("new android.view.SurfaceControl[]{requestDockWindowSurface}"));
        assertTrue(s.contains("requestDockWindowLayerName != null"));
        assertTrue(s.contains("requestDragLayerName"));
    }

    @Test public void wallpaperIdIsPartOfRequestAndCacheIdentity() throws Exception {
        String s = glass();
        assertTrue(s.contains("final int requestWallpaperId"));
        assertTrue(s.contains("isWallpaperIdentityCurrent"));
        assertTrue(s.contains("requestWallpaperId, requestWallpaperTransformRevision"));
        assertTrue(s.contains("cacheWallpaperId = requestWallpaperId"));
    }
}
