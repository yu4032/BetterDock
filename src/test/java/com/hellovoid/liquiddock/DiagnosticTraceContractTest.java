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

    @Test public void systemUiTransitionSourceLogsEveryBoundaryNeededToLocalizeFlyFailure()
            throws Exception {
        String source = source("SystemUiTransitionSource.java");
        assertTrue(source.contains("[DC][TR-SRC] observer registered"));
        assertTrue(source.contains("[DC][TR-SRC] callback registered"));
        assertTrue(source.contains("[DC][TR-SRC] ready token="));
        assertTrue(source.contains("normalized="));
        assertTrue(source.contains("kind="));
        assertTrue(source.contains("callback="));
        assertTrue(source.contains("[DC][TR-SRC] push type="));
    }

    @Test public void launcherTransitionRuntimeLogsProviderRegistrationReceiveAndHoldDecision()
            throws Exception {
        String runtime = source("SystemUiTransitionRuntime.java");
        assertTrue(runtime.contains("[DC][TR] provider changed connected="));
        assertTrue(runtime.contains("[DC][TR] callback registration accepted="));
        assertTrue(runtime.contains("[DC][TR] callback received type="));
        assertTrue(runtime.contains("[DC][TR] hold start skipped reason="));
        assertTrue(runtime.contains("[DC][TR] APP_TO_LAUNCHER hold start"));
    }

    @Test public void dragReleaseTraceCoversVendorCallbackBarrierGlassAndCaptureResume()
            throws Exception {
        String drag = source("Miuix307DragCaptureHook.java");
        assertTrue(drag.contains("[DC][DRAG] vendor drop-finish callback"));
        assertTrue(drag.contains("[DC][DRAG] release barrier armed"));
        assertTrue(drag.contains("[DC][DRAG] release barrier passed"));
        assertTrue(drag.contains("[DC][DRAG] finish capture glass="));
        assertTrue(drag.contains("[DC][DRAG] state before="));
        assertTrue(drag.contains("[DC][DRAG] state after="));
        assertTrue(drag.contains("dragFrozen="));
        assertTrue(drag.contains("systemDrag="));
        assertTrue(drag.contains("allowed="));
    }
}
