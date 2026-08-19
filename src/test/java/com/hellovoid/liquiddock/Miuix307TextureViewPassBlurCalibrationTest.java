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
                        && view.contains("new SurfaceTexture(oesTexture)"));
        assertTrue("input and output must remain distinct GPU surfaces",
                view.contains("inputSurfaceTexture")
                        && view.contains("outputSurfaceTexture")
                        && view.contains("outputWindowSurface"));
    }

    @Test
    public void fullPrismalOpticsReplaceDiagnosticLensWithoutLeavingGpuPath() throws Exception {
        Path viewPath = MAIN.resolve("Miuix307PassBlurTextureView.java");
        Path materialPath = MAIN.resolve("Miuix307PrismalMaterial.java");
        assertTrue(Files.exists(viewPath));
        assertTrue("full optical model must live in a dedicated material unit",
                Files.exists(materialPath));

        String view = Files.readString(viewPath);
        String material = Files.readString(materialPath);

        assertTrue("active TextureView must use the Prismal OES fragment shader",
                view.contains("FRAGMENT_SHADER = Miuix307PrismalMaterial.FRAGMENT_SHADER"));
        assertTrue("Prismal material must sample the live external OES producer",
                material.contains("samplerExternalOES uTexture")
                        && material.contains("texture2D(uTexture"));
        assertTrue("approved Stage-B mapping must remain inside every optical sample",
                material.contains("uBackdropRect")
                        && material.contains("uConfigRot")
                        && material.contains("uTexMatrix")
                        && material.contains("textureInputUv.x = (orientedUv.x - textureOffsetX) / textureScaleX")
                        && material.contains("uTexMatrix * vec4(textureInputUv, 0.0, 1.0)"));
        assertTrue("material must restore the droplet/meniscus height field and normal",
                material.contains("getHeightFromDist")
                        && material.contains("computeGradientHeight")
                        && material.contains("uLiquidDome")
                        && material.contains("uNormalStrength"));
        assertTrue("material must restore two-interface Snell refraction",
                material.contains("refract(-V, N, 1.0 / uIor)")
                        && material.contains("refract(refIn, -N, uIor)"));
        assertTrue("material must restore Fresnel and chromatic dispersion",
                material.contains("pow(1.0 - cosVNeff, 5.0)")
                        && material.contains("uChromaticAberration")
                        && material.contains("uvR")
                        && material.contains("uvB"));
        assertTrue("material must restore specular, rim and caustic lighting",
                material.contains("uSpecularSharp")
                        && material.contains("uSpecularStrength")
                        && material.contains("uRimLight")
                        && material.contains("uCausticStrength"));
        assertFalse("temporary fixed 14px diagnostic lens must be gone",
                view.contains("float displacementPx = 14.0")
                        || material.contains("float displacementPx = 14.0"));
        assertFalse("new material path must remain GPU-only",
                view.contains("Bitmap")
                        || view.contains("captureScreenAsync")
                        || view.contains("ScreenshotHardwareBuffer")
                        || view.contains("glReadPixels")
                        || material.contains("Bitmap")
                        || material.contains("glReadPixels"));
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
        int end = view.indexOf("private void updateBackdropMapping", start);
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
