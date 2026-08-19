package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Historical charge-refraction evidence plus the contract that the PassBlur demo no longer uses it. */
public class Miuix307SurfaceRefractionProbeTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void rootProbeRemainsReadOnlyHistoricalEvidence() throws Exception {
        String probe = Files.readString(MAIN.resolve("Miuix307SurfaceRefractionProbe.java"));
        assertTrue(probe.contains("setChargeAnim"));
        assertTrue(probe.contains("setChargeAnimProp"));
        assertTrue(probe.contains("getViewRootImpl"));
        assertTrue(probe.contains("rootShared="));
        assertFalse(probe.contains("setChargeAnim.invoke"));
        assertFalse(probe.contains("setChargeAnimProp.invoke"));
    }

    @Test
    public void passBlurDemoDoesNotMountTheFixedChargeWaterWaveChild() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        assertTrue(renderer.contains("Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("Miuix307RefractionSurfaceProbeView"));
        assertFalse(renderer.contains("Miuix307RefractionExperiment"));
        assertFalse(renderer.contains("Miuix307SurfaceRefractionProbe.probe"));
        assertFalse(renderer.contains("setChargeAnim"));
    }

    @Test
    public void oldChargeExperimentIsRetainedOnlyAsHistoricalReference() throws Exception {
        String experiment = Files.readString(MAIN.resolve("Miuix307RefractionExperiment.java"));
        assertTrue(experiment.contains("new float[]{0.5f, 0.2f, 0.7f, 8.0f}"));
        assertTrue(experiment.contains("Float.valueOf(1.0f)"));
        assertTrue(experiment.contains("Float.valueOf(0.6f)"));
        assertTrue(experiment.contains("Boolean.FALSE"));
    }
}
