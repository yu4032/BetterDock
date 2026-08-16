package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source contract for the device-validated MiuiX 307 glass architecture. */
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
    public void materialPipelineUsesExistingPrismalGlassStack() throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String hook = read("MiuixGlassHook.java");
        String pipeline = read("Miuix307MaterialPipeline.java");

        assertTrue(hook.contains("LiquidGlassFactory.create"));
        assertTrue(hook.contains("DockLiquidGlassHostView"));
        assertTrue(hook.contains("applyPassWindowBlur"));
        assertTrue(pipeline.contains("setBackgroundWidth"));
        assertTrue(pipeline.contains("setBackgroundHeight"));
        assertFalse(pipeline.contains("new Miuix307RefractionView"));
    }

    @Test
    public void miuixModeIsolatedFromLegacyCaptureLifecycle() throws IOException {
        String source = read("MainHook.java");
        assertTrue(source.contains("miuiXDock"));
        assertTrue(source.contains("if (miuiXDock) return"));
        assertTrue(source.contains("MiuiX dock: skipping old capture/lifecycle hooks"));
    }

    @Test
    public void nativeMiuixDrawableIsPreserved() throws IOException {
        Path hookPath = MAIN.resolve("MiuixGlassHook.java");
        assertTrue("MiuiX glass hook must exist", Files.exists(hookPath));
        String source = read("MiuixGlassHook.java");
        assertFalse(source.contains("setBackground(null)"));
        assertTrue(source.contains("mBackground"));
    }
}
