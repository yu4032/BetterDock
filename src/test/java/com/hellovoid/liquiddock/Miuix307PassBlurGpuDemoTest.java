package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur -> OES -> 2D -> Prismal backend. */
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
        assertTrue(view.contains("renderNormalizationPass"));
        assertTrue(view.contains("renderBlurPasses"));
        assertTrue(view.contains("renderMaterialPass"));
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
        assertTrue(view.contains("if (nextRotation == 1 || nextRotation == 3)"));
        assertTrue(view.contains("bufferWidth = surfaceHeight"));
        assertTrue(view.contains("bufferHeight = surfaceWidth"));
        assertTrue(view.contains("setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight)"));
    }

    @Test
    public void rotationResizesExistingProducerInPlaceWithoutNativeHotRebind() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("refreshProducerGeometryInPlace"));
        int start = view.indexOf("private void refreshProducerGeometryInPlace");
        int end = view.indexOf("private void updateBackdropMapping", start);
        assertTrue(start >= 0 && end > start);
        String region = view.substring(start, end);
        assertTrue(region.contains("setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight)"));
        assertTrue(region.contains("configRotation = geometry.configRotation"));
        assertTrue(region.contains("boundSurfaceWidth = geometry.surfaceWidth")
                && region.contains("boundSurfaceHeight = geometry.surfaceHeight")
                && region.contains("boundConfigRotation = geometry.configRotation"));
        assertFalse(region.contains("Miuix307PassBlurBridge.unbind")
                || region.contains("binding = null") || region.contains("SetPassBlurSurface"));
        assertTrue(view.contains("refreshProducerGeometryInPlace();"));
    }

    @Test
    public void activeTextureViewHasNoIndependentSurfaceControlShapeHack() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertFalse(view.contains("setWindowCrop"));
        assertFalse(view.contains("setCornerRadius"));
        assertFalse(view.contains("getSurfaceControl()"));
    }

    @Test
    public void stageBIsolatedBeforeUpstreamPrismalMaterial() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String adapter = Files.readString(MAIN.resolve("Miuix307PassBlurShaders.java"));
        String prismal = Files.readString(MAIN.resolve("Miuix307PrismalShader.java"));

        assertTrue(adapter.contains("uniform int uConfigRot")
                && adapter.contains("vec2(1.0 - rootUv.y, rootUv.x)")
                && adapter.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)")
                && adapter.contains("vec2(rootUv.y, 1.0 - rootUv.x)"));
        assertTrue(adapter.contains("compensateSurfaceTextureCropPreservingOrientation")
                && adapter.contains("float determinant")
                && adapter.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertTrue(view.contains("Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT"));
        assertTrue(view.contains("Miuix307PrismalShader.FRAGMENT_SHADER"));
        assertFalse(prismal.contains("uTexMatrix"));
        assertFalse(prismal.contains("uBackdropRect"));
        assertFalse(prismal.contains("samplerExternalOES"));
    }

    @Test
    public void stageBDiagnosticsExposeTextureMatrixCoverageAndHostGeometry() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("texture matrix=") && view.contains("formatTextureMatrix"));
        assertTrue(view.contains("stage-B mapping rootScreen="));
        assertTrue(view.contains("hostScreen="));
        assertTrue(view.contains("hostSize="));
        assertTrue(view.contains("rootSurface="));
        assertTrue(view.contains("backdropRect="));
        assertTrue(view.contains("validDockRect="));
        assertTrue(view.contains("coverage="));
        assertTrue(view.contains("mapped corners"));
        assertTrue(view.contains("configRot="));
    }

    @Test
    public void validationTimeoutFailsClosedWithoutCaptureFallback() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        assertTrue(hook.contains("private static void scheduleZeroCopyValidation"));
        assertTrue(hook.contains("glass remains transparent"));
        assertFalse(hook.contains("installCaptureFallback"));
        assertFalse(hook.contains("LiquidGlassFactory"));
    }
}
