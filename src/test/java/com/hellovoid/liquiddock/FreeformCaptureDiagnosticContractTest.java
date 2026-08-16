package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FreeformCaptureDiagnosticContractTest {
    private static String read(String relativePath) throws Exception {
        Path path = Path.of(relativePath);
        assertTrue("missing source file: " + relativePath, Files.exists(path));
        return Files.readString(path);
    }

    @Test public void diagnosticIsOneShotAndReadOnly() throws Exception {
        String diag = read(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java");
        assertTrue(diag.contains("AtomicBoolean"));
        assertTrue(diag.contains("compareAndSet(false, true)"));
        assertTrue(diag.contains("LiquidDockDiag"));
        assertFalse(diag.contains("captureScreenAsync("));
        assertFalse(diag.contains("captureLayerAsync("));
        assertFalse(diag.contains("captureDisplay("));
    }

    @Test public void diagnosticSnapshotsAreIsolatedFromProductionCaches() throws Exception {
        String tasks = read(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        String layers = read(
                "src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java");
        assertTrue(tasks.contains("snapshotForDiagnostics"));
        assertTrue(layers.contains("snapshotForDiagnostics"));
        assertTrue(tasks.contains("DiagnosticSnapshot"));
        assertTrue(layers.contains("DiagnosticSnapshot"));
    }

    @Test public void dockInvokesDiagnosticWithoutRemovingSafetyFallback() throws Exception {
        String dock = read(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(dock.contains("FreeformCaptureDiagnostic.runOnce("));
        assertTrue(dock.contains("actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY"));
        assertTrue(dock.contains("!fullDisplayExclusions.safe"));
        assertTrue(dock.contains("actualSource = CaptureSourcePolicy.Source.WALLPAPER"));
    }
}
