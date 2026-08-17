package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for capture protections that existed before the specialized MiuiX 307 return. */
public class Miuix307CaptureCompatibilityTest {
    private static String read(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + file));
    }

    @Test public void miuix307ReusesOriginalDragSurfaceExclusionWithoutLegacyHookBundle()
            throws Exception {
        String main = read("MainHook.java");
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glass = read("MiuixGlassHook.java");

        // Keep the original proven DragController -> setDockDragging path. The 307 early return
        // must install this narrow hook instead of the complete legacy capture lifecycle.
        assertTrue(main.contains("static void installDockDragHooks(ClassLoader cl)"));
        assertTrue(main.contains("MiuixGlassHook.currentGlass()"));
        assertTrue(main.contains("setDockDragging(true, resolveDragSurfaceLayerName"));
        assertTrue(main.contains("setDockDragging(false, null)"));
        assertTrue(glass.contains("static DockLiquidGlassView currentGlass()"));
        assertTrue(pipeline.contains("MainHook.installDockDragHooks(classLoader)"));
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
