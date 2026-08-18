package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the device diagnostic build used to localize transition and drag freezes. */
public class DiagnosticTraceContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void entryInstallsDiagnosticProbeInSystemUiAndLauncher() throws Exception {
        String entry = source("ModuleMain.java");
        assertTrue(entry.contains("DiagnosticTraceHook.installSystemUi(classLoader)"));
        assertTrue(entry.contains("DiagnosticTraceHook.installLauncher(classLoader)"));
    }

    @Test public void transitionProbeCoversObserverClassifierPushRegistrationReceiveAndHold()
            throws Exception {
        String probe = source("DiagnosticTraceHook.java");
        assertTrue(probe.contains("[DC][TR-SRC] observer registered"));
        assertTrue(probe.contains("[DC][TR-SRC] ready token="));
        assertTrue(probe.contains("[DC][TR-SRC] classify normalized="));
        assertTrue(probe.contains("[DC][TR-SRC] callback registered"));
        assertTrue(probe.contains("[DC][TR-SRC] push type="));
        assertTrue(probe.contains("[DC][TR] provider changed connected="));
        assertTrue(probe.contains("[DC][TR] callback registration accepted="));
        assertTrue(probe.contains("[DC][TR] callback received type="));
        assertTrue(probe.contains("[DC][TR] hold request"));
        assertTrue(probe.contains("[DC][TR] hold resolved active="));
    }

    @Test public void dragProbeCoversVendorFinishBarrierAndGlassResumeState() throws Exception {
        String probe = source("DiagnosticTraceHook.java");
        assertTrue(probe.contains("[DC][DRAG] vendor drop-finish callback"));
        assertTrue(probe.contains("[DC][DRAG] finishDropSettling enter"));
        assertTrue(probe.contains("[DC][DRAG] finish capture glass="));
        assertTrue(probe.contains("[DC][DRAG] setDockDragging before"));
        assertTrue(probe.contains("[DC][DRAG] setDockDragging after"));
        assertTrue(probe.contains("dragFrozen="));
        assertTrue(probe.contains("systemDrag="));
        assertTrue(probe.contains("allowed="));
        assertTrue(probe.contains("kick="));
        assertTrue(probe.contains("dirty="));
    }
}
