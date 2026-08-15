package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contracts that keep workstation All Apps invisible to the Dock capture state machine. */
public class WorkstationAllAppsHomeBackdropContractTest {
    private static String installAllAppsCaptureHooks() throws IOException {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("private static void installAllAppsCaptureHooks(ClassLoader cl)");
        int end = source.indexOf("private static boolean isStockAllAppsState", start);
        assertTrue("installAllAppsCaptureHooks must remain present", start >= 0 && end > start);
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
    public void workstationLaptopAllAppsDoesNotEnterDockCaptureState() throws IOException {
        String method = installAllAppsCaptureHooks();
        int normalStart = method.indexOf("// Normal All Apps stays in the Launcher main window.");
        assertTrue("normal All Apps section must remain present", normalStart > 0);
        String laptop = method.substring(0, normalStart);

        assertEquals("show/close laptop callbacks must both bypass capture-state forwarding",
                2, count(laptop, "if (workstationMode) return chain.proceed("));
        assertEquals("non-workstation laptop compatibility path must retain its three state updates",
                3, count(laptop, "glass.setAllAppsActive("));
    }

    @Test
    public void normalAllAppsCaptureStatePathRemainsUnchanged() throws IOException {
        String method = installAllAppsCaptureHooks();
        int normalStart = method.indexOf("// Normal All Apps stays in the Launcher main window.");
        String normal = method.substring(normalStart);

        assertTrue("normal All Apps must still forward capture-state transitions",
                count(normal, "glass.setAllAppsActive(") >= 3);
        assertFalse("workstation bypass belongs only to the laptop section",
                normal.contains("if (workstationMode) return chain.proceed("));
    }
}
