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

        assertTrue("must target the ViewRoot SurfaceControl",
                bridge.contains("getViewRootImpl") && bridge.contains("getSurfaceControl"));
        assertTrue("must use the framework PassBlur producer Surface entry point",
                bridge.contains("\"SetPassBlurSurface\""));
        assertTrue("must enable compositor texture production",
                bridge.contains("\"setUpdateTextureFlag\""));
        assertTrue("first demo must keep validated full-resolution sfScale=1.0",
                bridge.contains("1.0f"));
        assertTrue("must exclude layers from the captured backdrop",
                bridge.contains("\"setMiBlurWinExc\""));
        assertTrue("must identify the independent output child SurfaceControl",
                bridge.contains("outputView.getSurfaceControl()"));
        assertTrue("SurfaceControl.getName is hidden/package-private on this build, so use a helper",
                bridge.contains("surfaceName(SurfaceControl surface)")
                        && bridge.contains("getDeclaredMethod(\"getName\")"));
        assertTrue("must exclude the Floating Dock/root and output child to avoid feedback",
                bridge.contains("surfaceName(rootSurface)")
                        && bridge.contains("surfaceName(outputSurface)"));
        assertTrue("must exclude common system overlays",
                bridge.contains("NavigationBar")
                        && bridge.contains("StatusBar")
                        && bridge.contains("GestureStub")
                        && bridge.contains("DockAssistantView"));
        assertTrue("unbind must clear the PassBlur producer",
                bridge.contains("SetPassBlurSurface") && bridge.contains("null"));
        assertTrue("unbind must stop SF texture updates",
                bridge.contains("Boolean.FALSE"));

        assertFalse("bridge must not use screenshot capture",
                bridge.contains("captureScreenAsync")
                        || bridge.contains("ScreenshotHardwareBuffer")
                        || bridge.contains("Bitmap"));
        assertFalse("bridge must not reuse fixed charging/water-wave effects",
                bridge.contains("setChargeAnim")
                        || bridge.contains("WaterWave"));
    }

    @Test
    public void gpuViewConsumesPassBlurAsExternalTextureAndShowsSplitDistortion() throws Exception {
        Path path = MAIN.resolve("Miuix307PassBlurGpuView.java");
        assertTrue("GPU demo view must exist", Files.exists(path));
        String view = Files.readString(path);

        assertTrue("output must be an independent GLSurfaceView",
                view.contains("extends GLSurfaceView")
                        && view.contains("implements GLSurfaceView.Renderer"));
        assertTrue("input must be a BufferQueue SurfaceTexture",
                view.contains("SurfaceTexture") && view.contains("new Surface(surfaceTexture)"));
        assertTrue("input must remain a GPU external texture",
                view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES")
                        && view.contains("samplerExternalOES"));
        assertTrue("rendering must be frame driven, not a polling capture loop",
                view.contains("RENDERMODE_WHEN_DIRTY")
                        && view.contains("setOnFrameAvailableListener")
                        && view.contains("requestRender()"));
        assertTrue("consumer must acquire the newest queued GPU buffer",
                view.contains("updateTexImage()")
                        && view.contains("getTransformMatrix"));
        assertTrue("demo must map the Dock crop inside the root producer texture",
                view.contains("getLocationInWindow")
                        && view.contains("uCrop"));
        assertTrue("right side must apply an unmistakable spatial UV displacement",
                view.contains("sin(")
                        && view.contains("vUv.x > 0.5"));
        assertTrue("successful first draw must expose a renderer activation contract",
                view.contains("isGpuBackdropActive()")
                        && view.contains("isActivationExhausted()")
                        && view.contains("first OES frame")
                        && view.contains("first GLES backdrop draw"));
        assertTrue("clear must detach the SF producer before releasing GPU inputs",
                view.contains("Miuix307PassBlurBridge.unbind")
                        && view.contains("surfaceTexture.release()")
                        && view.contains("producerSurface.release()"));

        assertFalse("GPU demo must contain no CPU/capture backdrop path",
                view.contains("captureScreenAsync")
                        || view.contains("ScreenshotHardwareBuffer")
                        || view.contains("Bitmap")
                        || view.contains("BitmapShader"));
        assertFalse("GPU demo must not use fixed vendor animation effects",
                view.contains("setChargeAnim")
                        || view.contains("WaterWave"));
    }

    @Test
    public void producerGeometryMirrorsViewRootPassBlurRotationContract() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));

        assertTrue("buffer sizing must use ViewRootImpl.mSurfaceSize rather than View width/height",
                view.contains("\"mSurfaceSize\"")
                        && view.contains("Point"));
        assertTrue("rotation must include display install orientation like ViewRootImpl.checkConfigRot",
                view.contains("getInstallOrientation")
                        && view.contains("getRotation()"));
        assertTrue("90/270 rotations must swap producer buffer dimensions",
                view.contains("configRotation == 1 || configRotation == 3"));
        assertTrue("SurfaceTexture must be resized from the resolved producer geometry",
                view.contains("surfaceTexture.setDefaultBufferSize(bufferWidth, bufferHeight)"));
        assertFalse("producer sizing must no longer use root View dimensions as the buffer size",
                view.contains("surfaceTexture.setDefaultBufferSize(rootWidth, rootHeight)"));
    }

    @Test
    public void rotationAndSurfaceChangesRebindThePassBlurProducer() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));

        assertTrue("the bound geometry must be remembered",
                view.contains("boundSurfaceWidth")
                        && view.contains("boundSurfaceHeight")
                        && view.contains("boundConfigRotation"));
        assertTrue("output SurfaceControl identity must be part of the binding contract",
                view.contains("boundOutputSurface")
                        && view.contains("isSameSurface"));
        assertTrue("surface/rotation changes must trigger a producer rebind",
                view.contains("rebindProducerForGeometryChange")
                        && view.contains("Miuix307PassBlurBridge.unbind"));
        assertTrue("onSurfaceChanged must schedule the geometry rebind",
                view.contains("onSurfaceChanged")
                        && view.contains("post(this::rebindProducerForGeometryChange)"));
        assertTrue("pre-draw must also detect root/config changes during rotation",
                view.contains("geometryChangedSinceBind")
                        && view.contains("rebindProducerForGeometryChange"));
        assertTrue("a rebind must clear stale frame state before waiting for the new producer",
                view.contains("hasConsumedFrame = false")
                        && view.contains("frameAvailable.set(false)"));
    }

    @Test
    public void shaderAppliesExplicitConfigRotationBeforeSurfaceTextureTransform() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurGpuView.java"));

        assertTrue("shader must receive the ViewRoot-compatible config rotation",
                view.contains("uniform int uConfigRot")
                        && view.contains("glUniform1i(rotation"));
        assertTrue("rotation 90 must remap coordinates explicitly",
                view.contains("vec2(rootUv.y, 1.0 - rootUv.x)"));
        assertTrue("rotation 180 must remap coordinates explicitly",
                view.contains("vec2(1.0 - rootUv.x, 1.0 - rootUv.y)"));
        assertTrue("rotation 270 must remap coordinates explicitly",
                view.contains("vec2(1.0 - rootUv.y, rootUv.x)"));
        assertTrue("SurfaceTexture matrix must remain the final sampling transform",
                view.indexOf("uConfigRot") < view.indexOf("uTexMatrix * vec4"));
    }

    @Test
    public void validationTimeoutLeavesDiagnosticTransparentInsteadOfFallingBackToCapture() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        int validation = hook.indexOf("private static void scheduleZeroCopyValidation");
        int fallbackDefinition = hook.indexOf("private static void installCaptureFallback");
        assertTrue(validation >= 0 && fallbackDefinition > validation);
        String validationRegion = hook.substring(validation, fallbackDefinition);

        assertFalse("timeout must not install the old capture glass during GPU diagnosis",
                validationRegion.contains("installCaptureFallback("));
        assertTrue("timeout must make the diagnostic state explicit in logs",
                validationRegion.contains("legacy capture disabled"));
    }
}
