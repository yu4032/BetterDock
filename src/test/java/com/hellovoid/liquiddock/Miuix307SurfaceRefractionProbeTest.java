package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the corrected HyperOS child refraction feasibility spike. */
public class Miuix307SurfaceRefractionProbeTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void rootProbeRemainsReadOnly() throws Exception {
        String probe = Files.readString(MAIN.resolve("Miuix307SurfaceRefractionProbe.java"));
        assertTrue(probe.contains("setChargeAnim"));
        assertTrue(probe.contains("setChargeAnimProp"));
        assertTrue(probe.contains("getViewRootImpl"));
        assertTrue(probe.contains("rootShared="));
        assertFalse(probe.contains("setChargeAnim.invoke"));
        assertFalse(probe.contains("setChargeAnimProp.invoke"));
    }

    @Test
    public void correctedChildExperimentTargetsIndependentSurfaceBelowBackdrop() throws Exception {
        String child = Files.readString(MAIN.resolve("Miuix307RefractionSurfaceProbeView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String experiment = Files.readString(MAIN.resolve("Miuix307RefractionExperiment.java"));

        assertTrue(child.contains("extends SurfaceView"));
        assertTrue(child.contains("Miuix307SurfaceRefractionProbe.probeChildSurface"));
        assertTrue(child.contains("Miuix307RefractionExperiment.apply(this)"));

        int childAdd = renderer.indexOf("host.addView(refractionSurface");
        int backdropAdd = renderer.indexOf("host.addView(backdrop");
        assertTrue("refraction Surface must be mounted below the exact background-blur backdrop",
                childAdd >= 0 && backdropAdd > childAdd);

        assertTrue(experiment.contains("childView.getSurfaceControl()"));
        assertFalse("the experiment must never target the shared Floating Dock ViewRoot",
                experiment.contains("getViewRootImpl"));
    }

    @Test
    public void correctedChargeAnimUsesNativeActiveAlphaAndDimRatio() throws Exception {
        String experiment = Files.readString(MAIN.resolve("Miuix307RefractionExperiment.java"));

        assertTrue(experiment.contains("new float[]{0.5f, 0.2f, 0.7f, 8.0f}"));
        assertTrue(experiment.contains("Float.valueOf(1.0f)"));
        assertTrue(experiment.contains("Float.valueOf(0.6f)"));
        assertTrue(experiment.contains("Boolean.FALSE"));
        assertFalse("the previous alpha=0/dimRatio=0 experiment was invalid",
                experiment.contains("Float.valueOf(0.0f),\n                    Float.valueOf(0.0f),\n                    Boolean.FALSE"));
    }
}
