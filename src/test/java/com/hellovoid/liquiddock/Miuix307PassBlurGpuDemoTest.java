package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur -> OES -> TextureView diagnostic backend. */
public class Miuix307PassBlurGpuDemoTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void bridgeBindsPassBlurProducerAtFullScale() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(bridge.contains("SetPassBlurSurface"));
        assertTrue(bridge.contains("setUpdateTextureFlag"));
        assertTrue(bridge.contains("requestedScale"));
        assertTrue(bridge.contains("View materialHost, Surface producerSurface, float requestedScale"));
    }

    @Test
    public void activeRendererUsesTextureViewEgl() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue(view.contains("extends TextureView"));
        assertTrue(view.contains("EGL14.eglCreateWindowSurface"));
        assertTrue(view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES"));
    }

    @Test
    public void rootExclusionAvoidsTextureViewFeedbackLoop() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(bridge.contains("rootName"));
        assertFalse(bridge.contains("outputView.getSurfaceControl()"));
        assertFalse(bridge.contains("SurfaceView"));
    }

    @Test
    public void producerUsesRealViewRootSurfaceAndSurfaceSize() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("mSurfaceSize"));
        assertTrue(view.contains("getSurfaceControl"));
        assertTrue(view.contains("SurfaceControl rootSurface"));
        assertTrue(view.contains("surfaceWidth"));
        assertTrue(view.contains("surfaceHeight"));
    }

    @Test
    public void bufferGeometryTracksConfigRotation() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("if (configRotation == 1 || configRotation == 3)"));
        assertTrue(view.contains("bufferWidth = surfaceHeight"));
        assertTrue(view.contains("bufferHeight = surfaceWidth"));
        assertTrue(view.contains("setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight)"));
    }

    @Test
    public void rotationResizesExistingProducerInPlaceWithoutNativeHotRebind() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue("rotation path must have an in-place geometry refresh",
                view.contains("refreshProducerGeometryInPlace"));
        int start = view.indexOf("private void refreshProducerGeometryInPlace");
        int end = view.indexOf("private void updateBackdropMapping", start);
        assertTrue(start >= 0 && end > start);
        String region = view.substring(start, end);

        assertTrue("same input SurfaceTexture must be resized without producer teardown",
                region.contains("setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight)"));
        assertTrue("config rotation must update without tearing down the producer",
                region.contains("configRotation = geometry.configRotation"));
        assertTrue("bound dimensions must advance to the new geometry",
                region.contains("boundSurfaceWidth = geometry.surfaceWidth")
                        && region.contains("boundSurfaceHeight = geometry.surfaceHeight")
                        && region.contains("boundConfigRotation = geometry.configRotation"));
        assertFalse("geometry-only rotation must not null/unbind PassBlur",
                region.contains("Miuix307PassBlurBridge.unbind")
                        || region.contains("binding = null")
                        || region.contains("SetPassBlurSurface"));
        assertTrue("pre-draw must drive the in-place geometry refresh",
                view.contains("refreshProducerGeometryInPlace();"));
    }

    @Test
    public void activeTextureViewHasNoIndependentSurfaceControlShapeHack() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertFalse(view.contains("setWindowCrop"));
        assertFalse(view.contains("setCornerRadius"));
        assertFalse(view.contains("getSurfaceControl()"));
    }

    @Test
    public void stageBUsesExplicitConfigRotationBeforeSurfaceTextureTransform() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String material = Files.readString(MAIN.resolve("Miuix307PrismalMaterial.java"));

        assertTrue("optical displacement must remain Dock-local and feed the shared Stage-B sampler",
                material.contains("vec2 uvG = vUv + baseOffset")
                        && material.contains("uBackdropRect.xy + safeDockUv * uBackdropRect.zw"));
        assertTrue("configRot must inverse-orient the root UV in Stage B",
                material.contains("uniform int uConfigRot")
                        && material.contains("vec2(1.0 - rootUv.y, rootUv.x)")
                        && material.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)")
                        && material.contains("vec2(rootUv.y, 1.0 - rootUv.x)"));
        assertTrue("SurfaceTexture transform must remain the final producer-to-texture mapping after crop compensation",
                material.contains("vec2 textureInputUv = orientedUv")
                        && material.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertTrue("active TextureView must compile the extracted material shader",
                view.contains("FRAGMENT_SHADER = Miuix307PrismalMaterial.FRAGMENT_SHADER"));
    }

    @Test
    public void stageBDiagnosticsExposeTextureMatrixAndHostScreenGeometry() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("texture matrix=") && view.contains("formatTextureMatrix"));
        assertTrue(view.contains("stage-B mapping rootScreen="));
        assertTrue(view.contains("hostScreen="));
        assertTrue(view.contains("hostSize="));
        assertTrue(view.contains("rootSurface="));
        assertTrue(view.contains("backdropRect="));
        assertTrue(view.contains("mapped corners"));
        assertTrue(view.contains("configRot="));
    }

    @Test
    public void validationTimeoutLeavesDiagnosticTransparentInsteadOfFallingBackToCapture() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        int validation = hook.indexOf("private static void scheduleZeroCopyValidation");
        int fallbackDefinition = hook.indexOf("private static void installCaptureFallback");
        assertTrue(validation >= 0 && fallbackDefinition > validation);
        String validationRegion = hook.substring(validation, fallbackDefinition);
        assertFalse(validationRegion.contains("installCaptureFallback("));
        assertTrue(validationRegion.contains("legacy capture disabled"));
    }
}
