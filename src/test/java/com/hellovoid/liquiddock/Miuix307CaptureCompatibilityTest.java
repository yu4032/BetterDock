package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for capture protections that existed before the specialized MiuiX 307 return. */
public class Miuix307CaptureCompatibilityTest {
    private static Path path(String file) {
        return Path.of("src/main/java/com/hellovoid/liquiddock/" + file);
    }

    private static String read(String file) throws Exception {
        return Files.readString(path(file));
    }

    @Test public void miuix307ReusesOriginalDragSurfaceExclusionWithoutLegacyHookBundle()
            throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        Path dragPath = path("Miuix307DragCaptureHook.java");
        assertTrue("307 must have a narrow drag-only compatibility hook", Files.exists(dragPath));
        String drag = Files.readString(dragPath);

        // Keep the original proven DragController -> drag Surface -> setDockDragging path.
        assertTrue(drag.contains("com.miui.home.launcher.DragController"));
        assertTrue(drag.contains("\"startDrag\""));
        assertTrue(drag.contains("\"endDrag\""));
        assertTrue(drag.contains("mDragObject"));
        assertTrue(drag.contains("mDragViews"));
        assertTrue(drag.contains("getSurfaceControl"));
        assertTrue(drag.contains("setDockDragging(true"));
        assertTrue(drag.contains("setDockDragging(false, null)"));
        assertTrue(pipeline.contains("Miuix307DragCaptureHook.install(classLoader)"));
        assertTrue(pipeline.contains("Miuix307DragCaptureHook.bind(background)"));

        // Never restore the complete legacy capture lifecycle just to regain drag exclusion.
        assertFalse(pipeline.contains("installLiquidGlassCaptureHooks"));
    }

    @Test public void freeformGateDiagnosticIsStateDeduplicatedNotPerFrame() throws Exception {
        String gate = read("FreeformCaptureLeashHook.java");

        // The final task-leash gate already owns the correct semantic decision. Instrument only
        // that boundary and dedupe identical states so dynamic APP capture cannot flood logcat.
        assertTrue(gate.contains("LAST_GATE_LOG_SIGNATURE"));
        assertTrue(gate.contains("logGateStateIfChanged"));
        assertTrue(gate.contains("visibleFreeform="));
        assertTrue(gate.contains("remoteLeashes="));
        assertTrue(gate.contains("action="));
        assertTrue(gate.contains("miuix307="));
        assertTrue(gate.contains("compareAndSet"));

        // Preserve the existing fail-closed and real-leash mechanisms.
        assertTrue(gate.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue(gate.contains("args[5] = 2"));
    }
}
