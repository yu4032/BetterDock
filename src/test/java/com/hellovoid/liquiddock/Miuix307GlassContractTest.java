package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source contract for the device-validated MiuiX 307 shell and zero-copy Prismal backend. */
public class Miuix307GlassContractTest {
    private static final Path MAIN = Paths.get("src/main/java/com/hellovoid/liquiddock");

    private static String read(String file) throws IOException {
        return Files.readString(MAIN.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void blurBridgeProvidesRealtimePassWindowBlur() throws IOException {
        String source = read("MiBlurBridge.java");
        assertTrue(source.contains("setPassWindowBlurEnabled"));
        assertTrue(source.contains("setMiViewBlurMode"));
        assertTrue(source.contains("setMiBackgroundBlurRadius"));
        assertTrue(source.contains("applyPassWindowBlur"));
        assertTrue(source.contains("clearPassWindowBlur"));
    }

    @Test
    public void materialPipelineUsesPassBlurTextureViewAsOnlyGlassRenderer() throws IOException {
        String hook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String pipeline = read("Miuix307MaterialPipeline.java");
        String factory = read("LiquidGlassFactory.java");

        assertTrue(hook.contains("Miuix307ZeroCopyRenderer.install"));
        assertTrue(hook.contains("DockLiquidGlassHostView"));
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue(renderer.contains("host.addView(gpuBackdrop"));
        assertFalse(renderer.contains("Miuix307ZeroCopyToneView"));
        assertFalse(renderer.contains("LiquidBlurMode.ADVANCED_MATERIAL"));
        assertTrue(renderer.contains("legacy capture retired"));
        assertFalse(renderer.contains("LiquidGlassFactory.create"));
        assertFalse(factory.contains("config.blur"));
        assertFalse(factory.contains("setBlurMode"));
        assertTrue(pipeline.contains("setBackgroundWidth"));
        assertTrue(pipeline.contains("setBackgroundHeight"));
        assertFalse(pipeline.contains("new Miuix307RefractionView"));
    }

    @Test
    public void zeroCopyModeIsolatedFromLegacyCaptureLifecycle() throws IOException {
        String mainHook = read("MainHook.java");
        String module = read("ModuleMain.java");
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue(mainHook.contains("MiuiX 307 material active; legacy liquid capture bypassed"));
        assertTrue(mainHook.indexOf("Miuix307MaterialPipeline.install")
                < mainHook.indexOf("installLiquidGlassCaptureHooks(classLoader)"));
        assertFalse(pipeline.contains("installLiquidGlassCaptureHooks"));
        assertFalse(module.contains("Miuix307CaptureOwnershipHook.install"));
        assertFalse(module.contains("Miuix307GestureBackdropHoldHook.install"));
        assertFalse(module.contains("Miuix307RecentsInputHook.install"));
    }

    @Test
    public void nativeMiuixDrawableIsPreservedAsGeometrySource() throws IOException {
        String source = read("MiuixGlassHook.java");
        assertFalse(source.contains("setBackground(null)"));
        assertTrue(source.contains("mBackground"));
        assertTrue(source.contains("geometry source"));
    }

    @Test
    public void recreatedMiuixBackgroundRebindsGlassWithoutLauncherSetupViews() throws IOException {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String hook = read("MiuixGlassHook.java");

        assertTrue(hook.contains("isBoundTo"));
        assertTrue(pipeline.contains("ensureGlassBound"));
        assertTrue(pipeline.contains("MiuiX 307 background instance changed"));

        int width = pipeline.indexOf("setBackgroundWidth");
        int height = pipeline.indexOf("setBackgroundHeight");
        int radius = pipeline.indexOf("setBackgroundRadius");
        assertTrue(width >= 0 && pipeline.indexOf("ensureGlassBound", width) > width);
        assertTrue(height >= 0 && pipeline.indexOf("ensureGlassBound", height) > height);
        assertTrue(radius >= 0 && pipeline.indexOf("ensureGlassBound", radius) > radius);
    }

    @Test
    public void vendorGpuBlurIsReassertivelyDisabledAroundZeroCopyMaterial() throws IOException {
        String bridge = read("MiBlurBridge.java");
        String hook = read("MiuixGlassHook.java");
        String renderer = read("Miuix307ZeroCopyRenderer.java");
        String passBlur = read("Miuix307PassBlurBridge.java");

        assertTrue(bridge.contains("setPassWindowBlurRadius"));
        assertTrue(bridge.contains("clearPassWindowBlur"));
        assertTrue(hook.contains("suppressVendorGpuBlur"));
        assertTrue(hook.contains("MiBlurBridge.setPassWindowBlurRadius(dockBg, 0)"));
        assertTrue(hook.contains("MiBlurBridge.clearPassWindowBlur(dockBg)"));
        assertTrue(hook.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
        assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
        assertTrue(passBlur.contains("DEMO_SCALE = 1.0f"));
    }
}
