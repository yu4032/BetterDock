package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperZoomTransformTest {
    @Test public void scaleOneIsIdentity() {
        WallpaperZoomTransform.Result r = WallpaperZoomTransform.adjust(
                500, 1500, 2500, 1880, 3000, 2000, 1.0f, 0.5f);
        assertEquals(500, r.left);
        assertEquals(1500, r.top);
        assertEquals(2500, r.right);
        assertEquals(1880, r.bottom);
        assertEquals(0.5f, r.frameScale, 0.0001f);
        assertFalse(r.corrected);
    }

    @Test public void inverseScaleKeepsDisplayCenterFixed() {
        WallpaperZoomTransform.Result r = WallpaperZoomTransform.adjust(
                1400, 900, 1600, 1100, 3000, 2000, 1.2f, 0.5f);
        assertEquals(1417, r.left);
        assertEquals(917, r.top);
        assertEquals(1583, r.right);
        assertEquals(1083, r.bottom);
        assertEquals(0.6f, r.frameScale, 0.0001f);
        assertTrue(r.corrected);
    }

    @Test public void adjustedOutputSizeStaysCloseToOriginal() {
        WallpaperZoomTransform.Result r = WallpaperZoomTransform.adjust(
                558, 1548, 2449, 1880, 3008, 1880, 1.14f, 0.5f);
        float originalWidth = (2449 - 558) * 0.5f;
        float originalHeight = (1880 - 1548) * 0.5f;
        assertEquals(originalWidth, (r.right - r.left) * r.frameScale, 1.0f);
        assertEquals(originalHeight, (r.bottom - r.top) * r.frameScale, 1.0f);
        assertTrue(r.left >= 0 && r.top >= 0);
        assertTrue(r.right <= 3008 && r.bottom <= 1880);
    }

    @Test public void invalidScaleFallsBackToIdentity() {
        WallpaperZoomTransform.Result r = WallpaperZoomTransform.adjust(
                100, 200, 500, 600, 1000, 800, Float.NaN, 0.5f);
        assertEquals(100, r.left);
        assertEquals(200, r.top);
        assertEquals(500, r.right);
        assertEquals(600, r.bottom);
        assertEquals(0.5f, r.frameScale, 0.0001f);
        assertFalse(r.corrected);
    }
}
