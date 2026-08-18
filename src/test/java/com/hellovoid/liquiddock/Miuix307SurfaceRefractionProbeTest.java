package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for the HyperOS SurfaceControl refraction experiment. */
public class Miuix307SurfaceRefractionProbeTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void rootProbeRemainsReadOnly() throws Exception {
        String probe = Files.readString(MAIN.resolve("Miuix307SurfaceRefractionProbe.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(probe.contains("setChargeAnim"));
        assertTrue(probe.contains("setChargeAnimProp"));
        assertTrue(probe.contains("getViewRootImpl"));
        assertTrue(probe.contains("rootShared="));
        assertTrue(renderer.contains("Miuix307SurfaceRefractionProbe.probe(backdrop, materialHost)"));
        assertFalse("shared-root probe must never submit refraction",
                probe.contains("setChargeAnim.invoke"));
        assertFalse("shared-root probe must never submit refraction properties",
                probe.contains("setChargeAnimProp.invoke"));
    }

    @Test
    public void childSurfaceRemainsIndependentAndBelowBlurBackdrop() throws Exception {
        String child = Files.readString(MAIN.resolve("Miuix307RefractionSurfaceProbeView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(child.contains("extends SurfaceView"));
        assertTrue(child.contains("getSurfaceControl()"));
        assertTrue(child.contains("Miuix307SurfaceRefractionProbe.probeChildSurface"));

        int childAdd = renderer.indexOf("host.addView(refractionSurface");
        int backdropAdd = renderer.indexOf("host.addView(backdrop");
        assertTrue("refraction child Surface must remain below the blur backdrop",
                childAdd >= 0 && backdropAdd > childAdd);
    }

    @Test
    public void spatialRefractionExperimentTargetsOnlyChildAndForcesBlurFive() throws Exception {
        Path experimentPath = MAIN.resolve("Miuix307RefractionExperiment.java");
        assertTrue("child-only refraction experiment must exist", Files.exists(experimentPath));

        String experiment = Files.readString(experimentPath);
        String child = Files.readString(MAIN.resolve("Miuix307RefractionSurfaceProbeView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(experiment.contains("apply(SurfaceView childView)"));
        assertTrue(experiment.contains("childView.getSurfaceControl()"));
        assertTrue(experiment.contains("new SurfaceControl.Transaction()"));
        assertTrue(experiment.contains("setChargeAnimProp"));
        assertTrue(experiment.contains("setChargeAnim"));
        assertTrue(experiment.contains("setChargeAnimProp.invoke"));
        assertTrue(experiment.contains("setChargeAnim.invoke"));
        assertTrue(experiment.contains("transaction.apply()"));
        assertTrue("must use SystemUI's active refraction tuple",
                experiment.contains("new float[]{0.5f, 0.2f, 0.7f, 8.0f}"));
        assertTrue("must keep black background disabled",
                experiment.contains("Boolean.FALSE"));
        assertTrue("child callback must be the only activation site",
                child.contains("Miuix307RefractionExperiment.apply(this)"));

        assertTrue("diagnostic build must pin actual compositor blur to 5px",
                renderer.contains("EXPERIMENT_BLUR_RADIUS_PX = 5"));
        assertTrue(renderer.contains("int effectiveBlurRadiusPx = EXPERIMENT_BLUR_RADIUS_PX"));
        assertTrue(renderer.contains("applyVendorBlurConfig(\n                    materialHost, backdrop, readHostRadius(host), effectiveBlurRadiusPx)"));
        assertTrue(renderer.contains("backdrop.setBlurRadius(effectiveBlurRadiusPx)"));

        // Never target the shared root SurfaceControl from the experiment.
        assertFalse(experiment.contains("getViewRootImpl"));
        assertFalse(experiment.contains("materialHost"));
    }
}
