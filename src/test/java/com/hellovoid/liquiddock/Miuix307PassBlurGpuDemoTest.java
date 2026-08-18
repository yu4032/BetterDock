package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur -> OES GPU demo. */
public class Miuix307PassBlurGpuDemoTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void passBlurBridgeBindsRootProducerAndCanUnbindCleanly() throws Exception {
        Path path = MAIN.resolve("Miuix307PassBlurBridge.java");
        assertTrue("PassBlur bridge must exist", Files.exists(path));
        String bridge = Files.readString(path);
        assertTrue(bridge.contains("getViewRootImpl") && bridge.contains("getSurfaceControl"));
        assertTrue(bridge.contains("\"SetPassBlurSurface\""));
        assertTrue(bridge.contains("\"setUpdateTextureFlag\""));
        assertTrue(bridge.contains("1.0f"));
        assertTrue(bridge.contains("\"setMiBlurWinExc\""));
        assertTrue(bridge.contains("outputView.getSurfaceControl()"));
        assertTrue(bridge.contains("surfaceName(SurfaceControl surface)")
                && bridge.contains("getDeclaredMethod(\"getName\")"));
        assertTrue(bridge.contains("surfaceName(rootSurface)")
                && bridge.contains("surfaceName(outputSurface)"));
        assertTrue(bridge.contains("NavigationBar")
                && bridge.contains("StatusBar")
                && bridge.contains("GestureStub")
                && bridge.contains("DockAssistantView"));
        assertTrue(bridge.contains("SetPassBlurSurface") && bridge.contains("null"));
        assertTrue(bridge.contains("Boolean.FALSE"));
        assertFalse(bridge.contains("captureScreenAsync")
                || bridge.contains("ScreenshotHardwareBuffer")
                || bridge.contains("Bitmap"));
        assertFalse(bridge.contains("setChargeAnim") || bridge.contains("WaterWave"));
    }

    @Test
    public void gpuViewUsesSmoothRoundedEdgeLensWithExactCenterPassthrough() throws Exception {
        Path path = MAIN.resolve("Miuix307PassBlurGpuView.java");
        assertTrue(Files.exists(path));
        String view = Files.readString(path);

        assertTrue(view.contains("extends GLSurfaceView")
                && view.contains("implements GLSurfaceView.Renderer"));
        assertTrue(view.contains("SurfaceTexture")
                && view.contains("new Surface(")
                && view.contains("producerSurface"));
        assertTrue(view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES")
                && view.contains("samplerExternalOES"));
        assertTrue(view.contains("RENDERMODE_WHEN_DIRTY")
                && view.contains("setOnFrameAvailableListener")
                && view.contains("requestRender()"));
        assertTrue(view.contains("updateTexImage()") && view.contains("getTransformMatrix"));
        assertTrue(view.contains("getLocationInWindow") && view.contains("uCrop"));

        assertTrue("lens must follow rounded Dock geometry",
                view.contains("sdRoundRect")
                        && view.contains("uViewSize")
                        && view.contains("uGlassRadius"));
        assertTrue("lens must fade continuously from edge to an exact passthrough center",
                view.contains("edgeWeight")
                        && view.contains("smoothstep")
                        && view.contains("mix(vUv, refractedUv, edgeWeight)"));
        assertFalse("diagnostic horizontal grating must be retired",
                view.contains("sin(") || view.contains("vUv.x > 0.5"));

        assertTrue(view.contains("isGpuBackdropActive()")
                && view.contains("isActivationExhausted()")
                && view.contains("first OES frame")
                && view.contains("first GLES backdrop draw"));
        assertTrue(view.contains("Miuix307PassBlurBridge.unbind")
                && view.contains("currentTexture.release()")
                && view.contains("currentProducer.release()"));
        assertFalse(view.contains("captureScreenAsync")
                || view.contains("ScreenshotHardwareBuffer")
                || view.contains("Bitmap")
                || view.contains("BitmapShader"));
        assertFalse(view.contains("setChargeAnim") || view.contains("WaterWave"));
    }

    @Test
    public void transparentDemoCompositionContainsNoToneHighlightOrStroke() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue("GPU view must be the only visual child installed by the demo renderer",
                renderer.contains("host.addView(gpuBackdrop"));
        assertFalse("diagnostic tone/tint layer must be removed",
                renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse("diagnostic must not enable the old sharp optical overlay",
                renderer.contains("enableSharpOptics")
                        || renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertTrue("renderer must expose whether the GPU diagnostic is installed",
                renderer.contains("static boolean isInstalled()"));
        assertTrue("install must clear any pre-existing foreground/stroke immediately",
                renderer.contains("materialHost.setForeground(null)"));
        assertTrue("renderer must keep Launcher/native stroke from reappearing while active",
                renderer.contains("ViewTreeObserver.OnPreDrawListener")
                        && renderer.contains("installForegroundSuppressor")
                        && renderer.contains("removeForegroundSuppressor")
                        && renderer.contains("getForeground() != null")
                        && renderer.contains("setForeground(null)"));
    }

    @Test
    public void producerGeometryMirrorsViewRootPassBlurRotationContract() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));
        assertTrue(view.contains("\"mSurfaceSize\"") && view.contains("Point"));
        assertTrue(view.contains("getInstallOrientation") && view.contains("getRotation()"));
        assertTrue(view.contains("configRotation == 1 || configRotation == 3"));
        assertTrue(view.contains("setDefaultBufferSize(bufferWidth, bufferHeight)"));
        assertFalse(view.contains("setDefaultBufferSize(rootWidth, rootHeight)"));
    }

    @Test
    public void rotationAndSurfaceChangesRebindThePassBlurProducer() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));
        assertTrue(view.contains("boundSurfaceWidth")
                && view.contains("boundSurfaceHeight")
                && view.contains("boundConfigRotation"));
        assertTrue(view.contains("boundOutputSurface") && view.contains("isSameSurface"));
        assertTrue(view.contains("rebindProducerForGeometryChange")
                && view.contains("Miuix307PassBlurBridge.unbind"));
        assertTrue(view.contains("onSurfaceChanged")
                && view.contains("post(this::rebindProducerForGeometryChange)"));
        assertTrue(view.contains("geometryChangedSinceBind")
                && view.contains("rebindProducerForGeometryChange"));
        assertTrue(view.contains("hasConsumedFrame = false")
                && view.contains("frameAvailable.set(false)"));
    }

    @Test
    public void shaderAppliesExplicitConfigRotationBeforeSurfaceTextureTransform() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));
        assertTrue(view.contains("uniform int uConfigRot")
                && view.contains("glUniform1i(rotation"));
        assertTrue(view.contains("vec2(rootUv.y, 1.0 - rootUv.x)"));
        assertTrue(view.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)"));
        assertTrue(view.contains("vec2(1.0 - rootUv.y, rootUv.x)"));
        assertTrue(view.indexOf("uConfigRot") < view.indexOf("uTexMatrix * vec4"));
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
