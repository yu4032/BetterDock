package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class LauncherGlassDragOverlayContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void oneReusableOverlayOwnsOneSharedSinkPerLauncherRoot() throws Exception {
        Path path = MAIN.resolve("LauncherGlassDragOverlay.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);

        assertTrue(source.contains("WeakHashMap<View, LauncherGlassDragOverlay> BY_ROOT"));
        assertTrue(source.contains("private final View carrier"));
        assertTrue(source.contains("private LauncherGlassSinkView sink"));
        assertTrue(source.contains("LauncherGlassSinkView.attachToMaterial"));
        assertTrue(source.contains("findDragContainerAncestor"));
        assertTrue(source.contains("OnPreDrawListener"));
        assertFalse(source.contains("Miuix307PassBlurBridge.bind"));
        assertFalse(source.contains("new Miuix307PassBlurTextureView"));
    }

    @Test
    public void dragMotionTracksSourceWithoutRefreshingWallpaperProducer() throws Exception {
        Path path = MAIN.resolve("LauncherGlassDragOverlay.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);

        assertTrue(source.contains("source.getLocationOnScreen"));
        assertTrue(source.contains("carrier.setX"));
        assertTrue(source.contains("carrier.setY"));
        assertTrue(source.contains("sink.requestLifecycleRefresh()"));
        assertFalse(source.contains("requestSingleUpdate"));
        assertFalse(source.contains("pauseUpdates"));
    }
}
