package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 3.0.307 backdrop RuntimeShader refraction spike. */
public class Miuix307SurfaceRefractionProbeTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void chargingWaterWaveExperimentIsRetiredFromZeroCopyRenderer() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertFalse(renderer.contains("Miuix307RefractionSurfaceProbeView"));
        assertFalse(renderer.contains("Miuix307RefractionExperiment"));
        assertFalse(renderer.contains("Miuix307SurfaceRefractionProbe"));
        assertFalse(renderer.contains("SurfaceView"));
        assertFalse(renderer.contains("setChargeAnim"));
    }

    @Test
    public void backdropRuntimeEffectUsesSystemInjectedContentShaderWithoutCapture() throws Exception {
        String effect = Files.readString(MAIN.resolve("Miuix307BackdropRefractionEffect.java"));

        assertTrue(effect.contains("RuntimeShader"));
        assertTrue(effect.contains("uniform shader content;"));
        assertTrue(effect.contains("RenderEffect.createRuntimeShaderEffect"));
        assertTrue(effect.contains("\"content\""));
        assertTrue(effect.contains("setBackdropRenderEffect"));
        assertTrue(effect.contains("content.eval("));

        assertFalse(effect.contains("Bitmap"));
        assertFalse(effect.contains("BitmapShader"));
        assertFalse(effect.contains("captureScreenAsync"));
        assertFalse(effect.contains("SurfaceControl"));
        assertFalse(effect.contains("setChargeAnim"));
    }

    @Test
    public void destructiveDiagnosticCanForceOpaqueMagentaBeforeSamplingBackdrop() throws Exception {
        String effect = Files.readString(MAIN.resolve("Miuix307BackdropRefractionEffect.java"));

        assertTrue(effect.contains("uniform float diagnosticSolid;"));
        assertTrue(effect.contains("shader.setFloatUniform(\"diagnosticSolid\", 1.0f)"));
        assertTrue(effect.contains("half4(1.0,0.0,1.0,1.0)"));
    }

    @Test
    public void rendererOwnsBackdropEffectLifecycleWithoutReopeningPassWindowBlur() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(renderer.contains("Miuix307BackdropRefractionEffect.apply(backdrop)"));
        assertTrue(renderer.contains("Miuix307BackdropRefractionEffect.sync(backdrop)"));
        assertTrue(renderer.contains("Miuix307BackdropRefractionEffect.clear(backdrop)"));
        assertTrue(renderer.contains("EXPERIMENT_BLUR_RADIUS_PX = 5"));
        assertTrue(renderer.contains("effectiveBlurRadiusPx, !exactBackgroundBlur"));
        assertFalse(renderer.contains("applyPassWindowBlur"));
    }
}
