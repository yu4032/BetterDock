package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contracts that keep workstation All Apps out of the Dock capture state machine. */
public class WorkstationAllAppsHomeBackdropContractTest {
    private static String mainHook() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("expected method markers must remain present", start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static int count(String source, String needle) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(needle, at)) >= 0; at += needle.length()) {
            count++;
        }
        return count;
    }

    @Test
    public void workstationAllAppsUsesUiOwnershipLatchInsteadOfCaptureState() throws IOException {
        String source = mainHook();
        String allAppsHooks = method(source,
                "private static void installAllAppsCaptureHooks(ClassLoader cl)",
                "private static boolean isStockAllAppsState");
        int normalStart = allAppsHooks.indexOf(
                "// Normal All Apps stays in the Launcher main window.");
        assertTrue("normal All Apps section must remain present", normalStart > 0);
        String laptop = allAppsHooks.substring(0, normalStart);

        assertTrue("workstation All Apps ownership must be a MainHook UI fact",
                source.contains("private static volatile boolean workstationAllAppsOpen;"));
        assertTrue("workstation show must claim overlay focus ownership before original code",
                laptop.contains("workstationAllAppsOpen = true;"));
        assertTrue("workstation close must release overlay focus ownership",
                laptop.contains("workstationAllAppsOpen = false;"));
        assertEquals("only the non-workstation compatibility path may forward laptop All Apps",
                3, count(laptop, "glass.setAllAppsActive("));
        assertTrue("workstation laptop callbacks must bypass capture-state forwarding",
                count(laptop, "if (workstationMode)") >= 2);
    }

    @Test
    public void workstationAllAppsFocusTransferStaysLauncherOwned() throws IOException {
        String source = mainHook();
        String captureHooks = method(source,
                "private static void installLiquidGlassCaptureHooks(ClassLoader cl)",
                "// ── helpers ──────────────────────────────────────────────────────");

        assertTrue("focus loss from the workstation All Apps overlay must be ignored",
                captureHooks.contains("if (workstationMode && workstationAllAppsOpen)"));
    }

    @Test
    public void everyAllAppsCaptureEntryBypassesWorkstationButNormalModeRemainsIntact()
            throws IOException {
        String source = mainHook();
        String allAppsHooks = method(source,
                "private static void installAllAppsCaptureHooks(ClassLoader cl)",
                "private static boolean isStockAllAppsState");
        int normalStart = allAppsHooks.indexOf(
                "// Normal All Apps stays in the Launcher main window.");
        String normal = allAppsHooks.substring(normalStart);

        assertEquals("both generic All Apps callbacks must bypass capture state in workstation",
                2, count(normal, "if (workstationMode) return chain.proceed("));
        assertTrue("normal mode must still forward the existing All Apps capture transitions",
                count(normal, "glass.setAllAppsActive(") >= 3);
        assertFalse("workstation ownership latch belongs only to the laptop overlay",
                normal.contains("workstationAllAppsOpen"));
        assertTrue("workstation Recents path must remain installed",
                source.contains("glass.onWorkstationRecentsButton();"));
    }
}
