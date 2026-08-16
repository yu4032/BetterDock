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

    @Test public void diagnosticSnapshotsAreIsolatedAndExposeMetadataFailures() throws Exception {
        String tasks = read(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        String layers = read(
                "src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java");
        String diag = read(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java");
        assertTrue(tasks.contains("snapshotForDiagnostics"));
        assertTrue(layers.contains("snapshotForDiagnostics"));
        assertTrue(tasks.contains("DiagnosticSnapshot"));
        assertTrue(layers.contains("DiagnosticSnapshot"));
        assertTrue(layers.contains("ownerUidReadableCount"));
        assertTrue(layers.contains("nameReadableCount"));
        assertTrue(layers.contains("layerMetadataError"));
        assertTrue(diag.contains("SURFACEFLINGER_EMPTY_RESULT"));
        assertTrue(diag.contains("LAYER_METADATA_API_FAILED"));
    }

    @Test public void resolverBoundaryTriggersDiagnosticAndDockKeepsSafetyFallback()
            throws Exception {
        String resolver = read(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        String dock = read(
                "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(resolver.contains("FreeformCaptureDiagnostic.runOnce("));
        assertTrue(resolver.contains("Throwable resolutionError"));
        assertTrue(dock.contains("actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY"));
        assertTrue(dock.contains("!fullDisplayExclusions.safe"));
        assertTrue(dock.contains("actualSource = CaptureSourcePolicy.Source.WALLPAPER"));
        assertFalse(dock.contains("FreeformCaptureDiagnostic.runOnce("));
    }
}
