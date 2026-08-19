package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for the HyperOS 307 PassBlur GPU calibration and retired probe. */
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
        assertTrue("active bridge must bind directly to the producer Surface",
                bridge.contains("View materialHost, Surface producerSurface, float requestedScale"));
        assertFalse("active bridge must not query an independent output child surface",
                bridge.contains("outputView.getSurfaceControl()") || bridge.contains("surfaceName(outputSurface)"));
        assertTrue(bridge.contains("surfaceName(SurfaceControl surface)")
                && bridge.contains("getDeclaredMethod(\"getName\")"));
        assertTrue(bridge.contains("surfaceName(rootSurface)"));
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
    public void retiredGpuViewUsesSmoothRoundedEdgeLensWithExactCenterPassthrough() throws Exception {
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
        assertTrue("manual coordinate calculations may remain diagnostic-only",
                view.contains("getLocationInWindow") && view.contains("cropSF="));

        assertTrue("retired probe keeps its rounded Dock geometry evidence",
                view.contains("sdRoundRect")
                        && view.contains("uViewSize")
                        && view.contains("uGlassRadius"));
        assertTrue("retired probe keeps its edge lens evidence",
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
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        assertTrue(view.contains("\"mSurfaceSize\"") && view.contains("Point"));
        assertTrue(view.contains("getInstallOrientation") && view.contains("getRotation()"));

        int start = view.indexOf("private ProducerGeometry readSurfaceGeometry");
        int end = view.indexOf("private void logStageBDiagnostics", start);
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

        assertTrue("diagnostic lens must remain Dock-local before mapping into root surface space",
                view.contains("lensUv = mix(vUv, refractedUv, edgeWeight)")
                        && view.contains("uBackdropRect.xy + lensUv * uBackdropRect.zw"));
        assertTrue("configRot must inverse-orient the root UV in Stage B",
                view.contains("uniform int uConfigRot")
                        && view.contains("vec2(1.0 - rootUv.y, rootUv.x)")
                        && view.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)")
                        && view.contains("vec2(rootUv.y, 1.0 - rootUv.x)"));
        assertTrue("SurfaceTexture transform must remain the final producer-to-texture mapping",
                view.contains("uTexMatrix * vec4(orientedUv, 0.0, 1.0)"));
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
