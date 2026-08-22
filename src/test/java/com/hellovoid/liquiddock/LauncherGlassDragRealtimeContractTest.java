package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps real folder dragging realtime without reopening Recents-wide per-frame work. */
public class LauncherGlassDragRealtimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dragContainerBypassesAncestorTransformStability() throws Exception {
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(sink.contains("boolean requiresRealtimeBackdropTracking()"));
        assertTrue(sink.contains("DragContainer"));
        assertTrue(session.contains("sink.requiresRealtimeBackdropTracking()"));
        assertTrue(session.contains("localChanged || realtimeNode"));
    }

    @Test
    public void folderProducerRunsRealtimeOnlyDuringDrag() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(bridge.contains("static void setRealtimeUpdates(Binding binding, boolean enabled)"));
        assertTrue(session.contains("updateRealtimeProducerMode(realtimeBackdropTracking);"));
        assertTrue(session.contains("if (!producerRealtime)"));
        assertTrue(session.contains("Miuix307PassBlurBridge.pauseUpdates(binding);"));
    }
}
