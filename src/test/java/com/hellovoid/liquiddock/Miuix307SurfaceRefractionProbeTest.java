package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts after decompiling the real HyperOS charge-shader call chain. */
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
    public void surfaceViewChildIsNotUsedAsChargeShaderTarget() throws Exception {
        String child = Files.readString(MAIN.resolve("Miuix307RefractionSurfaceProbeView.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        // Device probing proved the child has its own SurfaceControl, but JADX call-graph analysis
        // proved MiuiShaderChargeView targets its ViewRoot SurfaceControl, not a SurfaceView child.
        assertTrue(child.contains("extends SurfaceView"));
        assertFalse("do not keep submitting the vendor charge shader to the non-native child target",
                child.contains("Miuix307RefractionExperiment.apply(this)"));
        assertFalse("zero-copy calibration must not insert the abandoned refraction SurfaceView",
                renderer.contains("host.addView(refractionSurface"));
    }
}
