package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherGlassWorkspaceScrollRealtimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void sinkTracksOnlyWorkspaceAncestorScroll() throws Exception {
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));

        assertTrue(sink.contains("LauncherGlassScrollMotionTracker workspaceScrollMotion"));
        assertTrue(sink.contains("boolean consumeWorkspaceScrollMotion()"));
        assertTrue(sink.contains("findWorkspaceAncestor(material)"));
        assertTrue(sink.contains("workspace.getScrollX()"));
        assertTrue(sink.contains("workspace.getScrollY()"));
    }

    @Test
    public void sessionBypassesStabilityOnlyForWorkspaceScrollWithoutProducerRefresh() throws Exception {
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        assertTrue(session.contains("boolean workspaceScrollChanged = sink.consumeWorkspaceScrollMotion()"));
        assertTrue(session.contains("select(old, observed, localChanged, workspaceScrollChanged)"));
        assertFalse("workspace scroll must reuse the consumed wallpaper/backdrop source",
                session.contains("workspaceScrollChanged) requestFrame(true)"));
    }
}
