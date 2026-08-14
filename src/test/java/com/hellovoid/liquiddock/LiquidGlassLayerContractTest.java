package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract tests for the split self-blur/body and sharp overlay layer architecture. */
public class LiquidGlassLayerContractTest {
    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void selfBlurDoesNotOwnStrokeOrHighlightOverlay() throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        String overlay = read("src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java");

        assertFalse("glass body must not configure DockStrokeRenderer",
                glass.contains("DockStrokeRenderer.configure(this"));
        assertTrue("sharp overlay must own configurable Dock stroke",
                overlay.contains("DockStrokeRenderer.configure(this"));
    }

    @Test
    public void glassBodyCanBypassShaderBlurWhenAdvancedBackendIsActive() throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(glass.contains("shaderBlurEnabled"));
        assertTrue(glass.contains("advancedMaterialActive ? 0f : 1f"));
    }

    @Test
    public void sharpOverlayDoesNotReceiveMiuiSelfBlur() throws IOException {
        String overlay = read("src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java");
        assertFalse(overlay.contains("MiBlurBridge"));
        assertFalse(overlay.contains("setMiSelfBlur"));
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

        assertTrue("detach must forget compositor-active state through the unified backend state path",
                glass.contains("MiBlurBridge.clearContentBlur(this);\n"
                        + "        setActiveBlurBackendState(LiquidBlurMode.SHADER);"));
        assertTrue("reattach must restore the requested advanced backend",
                glass.contains("requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL\n"
                        + "                && !advancedMaterialUnavailableForProcess\n"
                        + "                && !advancedMaterialActive"));
    }
}
