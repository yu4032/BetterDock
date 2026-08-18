package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for the read-only HyperOS SurfaceControl refraction probe. */
public class Miuix307SurfaceRefractionProbeTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void probeDiscoversChargeShaderTransactionsWithoutApplyingThem() throws Exception {
        Path probePath = MAIN.resolve("Miuix307SurfaceRefractionProbe.java");
        assertTrue("production probe source must exist", Files.exists(probePath));

        String probe = Files.readString(probePath);
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

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
        assertTrue("zero-copy renderer must schedule the read-only root probe",
                renderer.contains("Miuix307SurfaceRefractionProbe.probe(backdrop, materialHost)"));

        // Discovery only: never submit the vendor refraction transaction to the shared root.
        assertFalse("must not invoke setChargeAnim on the shared root",
                probe.contains("setChargeAnim.invoke"));
        assertFalse("must not invoke setChargeAnimProp on the shared root",
                probe.contains("setChargeAnimProp.invoke"));
    }

    @Test
    public void childSurfaceProbeCreatesIndependentLayerBelowBackdropWithoutShader() throws Exception {
        Path childPath = MAIN.resolve("Miuix307RefractionSurfaceProbeView.java");
        assertTrue("independent SurfaceView probe source must exist", Files.exists(childPath));

        String child = Files.readString(childPath);
        String probe = Files.readString(MAIN.resolve("Miuix307SurfaceRefractionProbe.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(child.contains("extends SurfaceView"));
        assertTrue(child.contains("implements SurfaceHolder.Callback"));
        assertTrue(child.contains("getHolder().addCallback(this)"));
        assertTrue(child.contains("PixelFormat.TRANSLUCENT"));
        assertTrue(child.contains("setZOrderOnTop(false)"));
        assertTrue(child.contains("getSurfaceControl()"));
        assertTrue(child.contains("Miuix307SurfaceRefractionProbe.probeChildSurface"));

        assertTrue(probe.contains("probeChildSurface(SurfaceView childView, View materialHost)"));
        assertTrue(probe.contains("childIndependent="));
        assertTrue(probe.contains("childValid="));
        assertTrue(probe.contains("childSurface="));
        assertTrue(probe.contains("rootSurface="));

        assertTrue(renderer.contains("new Miuix307RefractionSurfaceProbeView"));
        int childAdd = renderer.indexOf("host.addView(refractionSurface");
        int backdropAdd = renderer.indexOf("host.addView(backdrop");
        assertTrue("refraction child Surface must be below the existing blur backdrop",
                childAdd >= 0 && backdropAdd > childAdd);

        // This APK only proves independent compositor ownership. A/B shader application comes next.
        assertFalse(child.contains("setChargeAnim.invoke"));
        assertFalse(child.contains("setChargeAnimProp.invoke"));
        assertFalse(probe.contains("setChargeAnim.invoke"));
        assertFalse(probe.contains("setChargeAnimProp.invoke"));
    }
}
