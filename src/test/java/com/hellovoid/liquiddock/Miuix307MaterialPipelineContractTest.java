package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contract for the canonical HyperOS 3.0.307+ zero-copy material pipeline. */
public class Miuix307MaterialPipelineContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void mainSelectsZeroCopyGlassWithoutLegacyFallback() throws Exception {
        String hook = Files.readString(MAIN.resolve("MainHook.java"));
        assertTrue(hook.contains("Miuix307MaterialPipeline.install"));
        assertTrue(hook.contains("zero-copy material active"));
        assertFalse(hook.contains("legacy pipeline"));
        assertFalse(hook.contains("installLiquidGlassCaptureHooks"));
        assertFalse(hook.contains("DockLiquidGlassView"));
    }

    @Test public void materialPipelineUsesNativeGeometryAndPrismalHost() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String glassHook = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));

        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("HotSeatsListContentBlurBackground2"));
        assertTrue(pipeline.contains("MiuixGlassHook.install"));
        assertTrue(glassHook.contains("DockLiquidGlassHostView"));
        assertTrue(glassHook.contains("suppressVendorGpuBlur"));
        assertFalse(glassHook.contains("installCaptureFallback"));
        assertTrue(renderer.contains("new Miuix307PassBlurTextureView"));
    }

    @Test public void noSceneOrSystemUiAuthorityIsPartOfMaterialPipeline() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        assertFalse(pipeline.contains("GestureToHome"));
        assertFalse(pipeline.contains("CaptureScene"));
        assertFalse(pipeline.contains("Miuix307DragCaptureHook"));
        assertFalse(module.contains("SystemUi"));
    }
}
