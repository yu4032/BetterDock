package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps real folder dragging spatially realtime without reopening the wallpaper producer. */
public class LauncherGlassDragRealtimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dragContainerMarksGeometryChangedEveryPredraw() throws Exception {
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));

        assertTrue(sink.contains("isInDragContainer(material)"));
        assertTrue(sink.contains("DragContainer"));
        assertTrue(sink.contains("changed |= isInDragContainer(material);"));
    }

    @Test
    public void dragDoesNotReenableContinuousWallpaperProducer() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(session.contains("node.geometryStability.select(old, observed, localChanged)"));
        assertTrue(session.contains("Miuix307PassBlurBridge.pauseUpdates(binding);"));
    }
}
