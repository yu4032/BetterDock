package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiquidGlassLayerContractTest {
    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void advancedBackendBypassesShaderKernelButKeepsFallbackInSameShader()
            throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");

        assertTrue(glass.contains("uniform float shaderBlurEnabled"));
        assertTrue(glass.contains("shaderBlurEnabled < 0.5"));
        assertTrue(glass.contains("MiBlurBridge.applyContentBlur"));
        assertTrue(glass.contains("LiquidBlurBackendPolicy.activeBackend"));
    }

    @Test
    public void sharpStrokeAndHighlightLiveAboveSelfBlurredGlass() throws IOException {
        Path host = Paths.get("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
        Path overlay = Paths.get("src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java");
        assertTrue("Liquid Glass needs a final-clip host", Files.exists(host));
        assertTrue("Liquid Glass needs a sharp overlay", Files.exists(overlay));

        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        String overlaySource = read(overlay.toString());
        assertFalse("glass child must not own the configurable foreground stroke",
                glass.contains("setDockStrokeConfig(fullConfig.dock)"));
        assertTrue(overlaySource.contains("DockStrokeRenderer.configure"));
        assertTrue(overlaySource.contains("LinearGradient"));
    }

    @Test
    public void finalGeometryClipIsOutsideTheSelfBlurredRenderNode() throws IOException {
        String host = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");

        assertTrue(host.contains("dispatchDraw"));
        assertTrue(host.contains("clipPath"));
        assertTrue(glass.contains("advancedMaterialActive"));
        assertTrue("advanced backend must not pre-clip its own RenderNode",
                glass.contains("if (advancedMaterialActive)"));
    }

    @Test
    public void bothLauncherSetupPathsUseSharedLayerAssembly() throws IOException {
        String mainHook = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(mainHook.contains("DockLiquidGlassHostView"));
        assertTrue(mainHook.contains("installLiquidGlassLayer("));
    }

    @Test
    public void advancedBackendIsReappliedAfterViewReattach() throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");

        assertTrue("detach must forget compositor-active state after clearing it",
                glass.contains("advancedMaterialActive = false;\n"
                        + "        activeBlurBackend = LiquidBlurMode.SHADER;"));
        assertTrue("reattach must restore the requested advanced backend",
                glass.contains("requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL\n"
                        + "                && !advancedMaterialUnavailableForProcess\n"
                        + "                && !advancedMaterialActive"));
    }
}
