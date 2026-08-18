package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackdropVisualPolicyTest {
    @Test public void identicalWallpaperSignatureIsRejectedDuringPointerAppCapture() {
        long wallpaper = 0x123456789abcdef0L;
        assertTrue(BackdropVisualPolicy.isWallpaperLikeSignature(wallpaper, wallpaper));
    }

    @Test public void smallUniformWallpaperDimmingIsStillWallpaperLike() {
        long wallpaper = 0x7777777777777777L;
        long dimmed = 0x6666666666666666L;
        assertTrue(BackdropVisualPolicy.isWallpaperLikeSignature(dimmed, wallpaper));
    }

    @Test public void spatiallyDifferentAppContentIsNotWallpaperLike() {
        long wallpaper = 0x1111111111111111L;
        long app = 0xf1e2d3c4b5a69788L;
        assertFalse(BackdropVisualPolicy.isWallpaperLikeSignature(app, wallpaper));
    }

    @Test public void guardIsScopedTo307PointerAppOnly() {
        assertTrue(BackdropVisualPolicy.shouldRejectWallpaperLikeFrame(
                true, CaptureScene.APP, true, true));
        assertFalse(BackdropVisualPolicy.shouldRejectWallpaperLikeFrame(
                false, CaptureScene.APP, true, true));
        assertFalse(BackdropVisualPolicy.shouldRejectWallpaperLikeFrame(
                true, CaptureScene.APP, false, true));
        assertFalse(BackdropVisualPolicy.shouldRejectWallpaperLikeFrame(
                true, CaptureScene.RECENTS, true, true));
        assertFalse(BackdropVisualPolicy.shouldRejectWallpaperLikeFrame(
                true, CaptureScene.APP, true, false));
    }
}
