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
        assertTrue("first demo must request full-resolution sfScale=1.0",
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
}
