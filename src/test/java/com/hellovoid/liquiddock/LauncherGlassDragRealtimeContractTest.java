package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps launcher dragging spatially realtime without making static sinks chase DragContainer. */
public class LauncherGlassDragRealtimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void sharedOverlayTracksSourceBeforeTraversalInsteadOfStaticSink() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));

        assertTrue(overlay.contains("Choreographer.FrameCallback"));
        assertTrue(overlay.contains("syncFromSource()"));
        assertTrue(overlay.contains("source.getLocationOnScreen"));
        assertTrue(overlay.contains("carrier.setX"));
        assertTrue(overlay.contains("carrier.setY"));
        assertFalse(sink.contains("changed |= isInDragContainer(material);"));
    }

    @Test
    public void dragDoesNotReenableContinuousWallpaperProducer() throws Exception {
        String overlay = Files.readString(MAIN.resolve("LauncherGlassDragOverlay.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertFalse(overlay.contains("requestSingleUpdate"));
        assertFalse(overlay.contains("setRealtimeUpdates"));
        assertTrue(session.contains("Miuix307PassBlurBridge.pauseUpdates(binding);"));
    }
}
