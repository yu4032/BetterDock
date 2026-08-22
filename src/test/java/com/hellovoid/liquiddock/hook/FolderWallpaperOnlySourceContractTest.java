package com.hellovoid.liquiddock.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class FolderWallpaperOnlySourceContractTest {
    private static String source(String name) throws Exception {
        Path path = Path.of("src/main/java/com/hellovoid/liquiddock/hook/" + name);
        return Files.readString(path);
    }

    @Test
    public void sharedLauncherGlassUsesWallpaperOnlySource() throws Exception {
        String session = source("LauncherGlassSession.java");
        assertTrue(session.contains("LAUNCHER_SCENE_CAPTURE_ENABLED = false"));
        assertTrue(session.contains("if (!LAUNCHER_SCENE_CAPTURE_ENABLED)"));
    }

    @Test
    public void wallpaperOnlyPathKeepsCpuReadbackForbidden() throws Exception {
        String session = source("LauncherGlassSession.java");
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("ImageReader"));
        assertFalse(session.contains("glReadPixels"));
        assertFalse(session.contains("Bitmap.createBitmap"));
    }
}
