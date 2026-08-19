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
    public void neutralCompositionKeepsNoToneOrHighlightButRestoresSafeDockStroke() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue("GPU backdrop remains the only injected visual child",
                renderer.contains("host.addView(gpuBackdrop"));
        assertFalse("tone/tint must stay out of the neutral GPU path",
                renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse("old advanced optical highlight must stay disabled",
                renderer.contains("enableSharpOptics")
                        || renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertFalse("GPU renderer must no longer erase the safe configured foreground stroke",
                renderer.contains("materialHost.setForeground(null)")
                        || renderer.contains("installForegroundSuppressor")
                        || renderer.contains("removeForegroundSuppressor"));
        assertTrue("the 307 shell must restore the safe replacement stroke after GPU install",
                hook.contains("DockStrokeRenderer.configureReplacingForeground("));
    }

    @Test
    public void producerBufferAlwaysMatchesViewRootSurfaceOrientation() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));
        assertTrue(view.contains("\"mSurfaceSize\"") && view.contains("Point"));
        assertTrue(view.contains("getInstallOrientation") && view.contains("getRotation()"));

        int start = view.indexOf("private ProducerGeometry readSurfaceGeometry");
        int end = view.indexOf("private static int readConfigRotation", start);
        assertTrue(start >= 0 && end > start);
        String region = view.substring(start, end);

        assertTrue("PassBlur producer width must stay in mSurfaceSize orientation",
                region.contains("int bufferWidth = surfaceWidth;"));
        assertTrue("PassBlur producer height must stay in mSurfaceSize orientation",
                region.contains("int bufferHeight = surfaceHeight;"));
        assertFalse("configRot must not physically swap the BufferQueue dimensions",
                region.contains("bufferWidth = surfaceHeight")
                        || region.contains("bufferHeight = surfaceWidth"));
        assertTrue("configRot remains lifecycle metadata for rotation-change detection",
                view.contains("boundConfigRotation")
                        && view.contains("configRotation = geometry.configRotation"));
        assertTrue(view.contains("setDefaultBufferSize(bufferWidth, bufferHeight)"));
    }

    @Test
    public void rotationResizesExistingProducerInPlaceWithoutNativeHotRebind() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));

        assertTrue("rotation path must have an in-place geometry refresh",
                view.contains("refreshProducerGeometryInPlace"));
        int start = view.indexOf("private void refreshProducerGeometryInPlace");
        int end = view.indexOf("private ProducerGeometry readSurfaceGeometry", start);
        assertTrue(start >= 0 && end > start);
        String region = view.substring(start, end);

        assertTrue("same producer SurfaceTexture must be resized like ViewRootImpl.checkSurTexSize",
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
                        || region.contains("bindProducerWhenReady("));

        assertTrue("pre-draw and SurfaceChanged must use the in-place refresh",
                view.contains("post(this::refreshProducerGeometryInPlace)")
                        && view.contains("refreshProducerGeometryInPlace();"));
        assertFalse("old hot rebind helper must be retired",
                view.contains("producer geometry changed; rebinding PassBlur"));
    }

    @Test
    public void independentSurfaceGetsExplicitWindowCropAndCornerRadius() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));
        assertTrue("SurfaceView output must establish a crop before applying rounded corners",
                view.contains("setWindowCrop")
                        && view.contains("getWidth()")
                        && view.contains("getHeight()"));
        assertTrue("rounded SurfaceControl corner radius must remain enabled",
                view.contains("setCornerRadius"));
        assertTrue("shape update must log the actual output geometry for device validation",
                view.contains("output shape crop=")
                        && view.contains("cornerRadius="));
    }

    @Test
    public void shaderUsesSurfaceTextureTransformWithoutSecondConfigRotation() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));

        assertTrue("SurfaceTexture transform must remain the final producer-to-texture mapping",
                view.contains("uTexMatrix * vec4(rootUv, 0.0, 1.0)"));
        assertFalse("shader must not apply a second explicit config rotation",
                view.contains("uniform int uConfigRot")
                        || view.contains("glUniform1i(rotation")
                        || view.contains("vec2(rootUv.y, 1.0 - rootUv.x)")
                        || view.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)")
                        || view.contains("vec2(1.0 - rootUv.y, rootUv.x)"));
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
