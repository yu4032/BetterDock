package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for the feedback-safe HyperOS 307 TextureView + EGL calibration backend. */
public class Miuix307TextureViewPassBlurCalibrationTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void activeRendererUsesTextureViewEglInsteadOfIndependentSurfaceView() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        Path textureViewPath = MAIN.resolve("Miuix307PassBlurTextureView.java");

        assertTrue("calibration backend must have a dedicated TextureView implementation",
                Files.exists(textureViewPath));
        assertTrue("active renderer must instantiate the TextureView calibration backend",
                renderer.contains("Miuix307PassBlurTextureView"));
        assertFalse("retired GLSurfaceView backend must not be instantiated by active renderer",
                renderer.contains("new Miuix307PassBlurGpuView"));

        String view = Files.readString(textureViewPath);
        assertTrue(view.contains("extends TextureView")
                && view.contains("implements TextureView.SurfaceTextureListener"));
        assertTrue("TextureView output must be driven by an EGL window surface",
                view.contains("EGL14.eglCreateWindowSurface"));
        assertTrue("PassBlur input remains an external OES SurfaceTexture",
                view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES")
                        && view.contains("samplerExternalOES")
                        && view.contains("new SurfaceTexture(oesTexture)"));
        assertTrue("input and output must remain distinct GPU surfaces",
                view.contains("inputSurfaceTexture")
                        && view.contains("outputSurfaceTexture")
                        && view.contains("outputWindowSurface"));
    }

    @Test
    public void calibrationShaderIsStrictFullDomainPassthrough() throws Exception {
        Path path = MAIN.resolve("Miuix307PassBlurTextureView.java");
        assertTrue(Files.exists(path));
        String view = Files.readString(path);

        assertTrue("SurfaceTexture matrix must be the only producer texture transform",
                view.contains("uTexMatrix * vec4(vUv, 0.0, 1.0)"));
        assertFalse("calibration shader must not contain manual Dock crop",
                view.contains("uniform vec4 uCrop") || view.contains("uCrop."));
        assertFalse("calibration shader must not contain refraction or rounded edge lens terms",
                view.contains("sdRoundRect")
                        || view.contains("edgeWeight")
                        || view.contains("refractedUv")
                        || view.contains("uGlassRadius")
                        || view.contains("displacementPx"));
        assertFalse("normal calibration path must remain GPU-only",
                view.contains("Bitmap")
                        || view.contains("captureScreenAsync")
                        || view.contains("ScreenshotHardwareBuffer")
                        || view.contains("glReadPixels"));
    }

    @Test
    public void passBlurBridgeNoLongerDependsOnChildSurfaceViewExclusion() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));

        assertTrue("active bridge signature must bind the root directly to the producer Surface",
                bridge.contains("static Binding bind(")
                        && bridge.contains("View materialHost, Surface producerSurface, float requestedScale"));
        assertFalse("feedback avoidance must not depend on an independent child SurfaceView",
                bridge.contains("SurfaceView")
                        || bridge.contains("outputView.getSurfaceControl()"));
        assertTrue("Floating Dock root remains excluded from the PassBlur scene",
                bridge.contains("String[] exclusions") && bridge.contains("rootName"));
    }

    @Test
    public void calibrationRotationResizesInputProducerWithoutGeometryHotUnbind() throws Exception {
        Path path = MAIN.resolve("Miuix307PassBlurTextureView.java");
        assertTrue(Files.exists(path));
        String view = Files.readString(path);

        int start = view.indexOf("private void refreshProducerGeometryInPlace");
        int end = view.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String region = view.substring(start, end);

        assertTrue(region.contains("setDefaultBufferSize"));
        assertFalse("rotation/size changes must not unbind PassBlur",
                region.contains("Miuix307PassBlurBridge.unbind")
                        || region.contains("SetPassBlurSurface")
                        || region.contains("binding = null"));
    }

    @Test
    public void validationTimeoutStillDoesNotFallBackToLegacyCapture() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        int validation = hook.indexOf("private static void scheduleZeroCopyValidation");
        int fallbackDefinition = hook.indexOf("private static void installCaptureFallback");
        assertTrue(validation >= 0 && fallbackDefinition > validation);
        String validationRegion = hook.substring(validation, fallbackDefinition);

        assertTrue(validationRegion.contains("legacy capture disabled"));
        assertFalse(validationRegion.contains("installCaptureFallback("));
    }
}
