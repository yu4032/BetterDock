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
        assertTrue("must exclude the Floating Dock/root and output child to avoid feedback",
                bridge.contains("rootSurface.getName()")
                        && bridge.contains("outputSurface.getName()"));
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
}
