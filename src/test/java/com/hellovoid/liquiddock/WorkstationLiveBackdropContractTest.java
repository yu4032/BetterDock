package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Wiring contracts for workstation-only adaptive All Apps / Recents backdrop capture. */
public class WorkstationLiveBackdropContractTest {
    private static String source() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"),
                StandardCharsets.UTF_8);
    }

    private static String mainHookSource() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test public void workstationUsesDedicatedLiveSceneSourcePolicy() throws IOException {
        String source = source();
        assertTrue(source.contains("CaptureSourcePolicy.sourceForWorkstationScene("));
    }

    @Test public void fullDisplayFallbackNeverSkipsDockExclusionInWorkstation() throws IOException {
        String source = source();
        assertFalse("workstation full-display capture must not opt out of Dock exclusion",
                source.contains("&& !workstationMode;"));
        assertTrue("full-display capture must still resolve the Dock window surface",
                source.contains("requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY"));
    }

    @Test public void allAppsAndRecentsBoundariesStartWorkstationBurst() throws IOException {
        String source = source();
        assertTrue(source.contains("startWorkstationCaptureBurst(\"all-apps-enter\")"));
        assertTrue(source.contains("startWorkstationCaptureBurst(\"all-apps-exit\")"));
        assertTrue(source.contains("startWorkstationCaptureBurst(\"workstation-recents-enter\")"));
        assertTrue(source.contains("startWorkstationCaptureBurst(\"workstation-recents-exit\")"));
    }

    @Test public void workstationBurstContinuesOnlyWhileVisualBackgroundChanges() throws IOException {
        String source = source();
        assertTrue(source.contains("workstationCaptureBurst.onFrame(visualProbe.signature)"));
        assertTrue(source.contains("requestStateCapture(\"workstation-background-changing\")"));
        assertTrue(source.contains("finishWorkstationCaptureBurstIfSettled()"));
    }

    @Test public void exactOverviewLifecycleIsForwardedInWorkstationMode() throws IOException {
        String source = mainHookSource();
        assertFalse("workstation must not suppress exact Enter/ExitOverviewStateEvent callbacks",
                source.contains("if (glass != null && !workstationMode)\n"
                        + "                        glass.setOverviewActive(active, eventName);"));
        assertTrue("overview lifecycle must reach DockLiquidGlassView in every mode",
                source.contains("if (glass != null)\n"
                        + "                        glass.setOverviewActive(active, eventName);"));
    }
}
