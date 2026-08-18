package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for the read-only HyperOS SurfaceControl refraction probe. */
public class Miuix307SurfaceRefractionProbeTest {
    @Test
    public void probeDiscoversChargeShaderTransactionsWithoutApplyingThem() throws Exception {
        Path probePath = Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307SurfaceRefractionProbe.java");
        assertTrue(Files.exists(probePath));

        String probe = Files.readString(probePath);
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java"));

        assertTrue(probe.contains("setChargeAnim"));
        assertTrue(probe.contains("setChargeAnimProp"));
        assertTrue(probe.contains("SurfaceControl.Transaction.class.getMethod"));
        assertTrue(probe.contains("SurfaceView.class.getMethod(\"getSurfaceControl\")"));
        assertTrue(probe.contains("getViewRootImpl"));
        assertTrue(probe.contains("rootShared="));
        assertTrue(probe.contains("[DC][ZC][REFR]"));
        assertTrue(renderer.contains("Miuix307SurfaceRefractionProbe.probe(backdrop, materialHost)"));

        // This phase is discovery only: do not submit the vendor refraction transaction yet.
        assertFalse(probe.contains("setChargeAnim.invoke"));
        assertFalse(probe.contains("setChargeAnimProp.invoke"));
    }
}
