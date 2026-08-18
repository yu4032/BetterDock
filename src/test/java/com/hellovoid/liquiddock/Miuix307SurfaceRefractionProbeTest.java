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
        assertTrue("production probe source must exist", Files.exists(probePath));

        String probe = Files.readString(probePath);
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java"));

        assertTrue("must resolve setChargeAnim", probe.contains("setChargeAnim"));
        assertTrue("must resolve setChargeAnimProp", probe.contains("setChargeAnimProp"));
        assertTrue("must reflect SurfaceControl.Transaction methods",
                probe.contains("SurfaceControl.Transaction.class.getMethod"));
        assertTrue("must inspect whether an independent SurfaceView SurfaceControl is available",
                probe.contains("SurfaceView.class.getMethod(\"getSurfaceControl\")"));
        assertTrue("must inspect ViewRoot ownership", probe.contains("getViewRootImpl"));
        assertTrue("must report whether backdrop and host share a root",
                probe.contains("rootShared="));
        assertTrue("must use the refraction diagnostic tag", probe.contains("[DC][ZC][REFR]"));
        assertTrue("zero-copy renderer must schedule the read-only probe",
                renderer.contains("Miuix307SurfaceRefractionProbe.probe(backdrop, materialHost)"));

        // This phase is discovery only: do not submit the vendor refraction transaction yet.
        assertFalse("must not invoke setChargeAnim in discovery phase",
                probe.contains("setChargeAnim.invoke"));
        assertFalse("must not invoke setChargeAnimProp in discovery phase",
                probe.contains("setChargeAnimProp.invoke"));
    }
}
