package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contract tests for the self-blurred child and sharp parent-host architecture. */
public class LiquidGlassLayerContractTest {
    private static String read(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void selfBlurDoesNotOwnStrokeOrAdvancedSharpHighlight() throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        String host = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");

        assertFalse("self-blurred glass body must not configure DockStrokeRenderer",
                glass.contains("DockStrokeRenderer.configure(this"));
        assertTrue("parent host must own configurable Dock stroke",
                host.contains("DockStrokeRenderer.configure(this"));
        assertTrue("ADVANCED sharp highlight must be drawn by the parent host",
                host.contains("drawAdvancedHighlight(canvas)"));
    }

    @Test
    public void glassBodyCanBypassShaderBlurWhenAdvancedBackendIsActive() throws IOException {
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
        assertTrue(glass.contains("shaderBlurEnabled"));
        assertTrue(glass.contains("advancedMaterialActive ? 0f : 1f"));
    }

    @Test
    public void sharpHostDoesNotReceiveMiuiSelfBlur() throws IOException {
        String host = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
        assertFalse(host.contains("MiBlurBridge"));
        assertFalse(host.contains("setMiSelfBlur"));
        String bridge = read("src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java");
        assertTrue("MiBlurBridge remains explicitly View-targeted", bridge.contains("applyContentBlur(View view"));
    }

    @Test
    public void finalGeometryClipAndSharpHighlightAreOutsideSelfBlurredRenderNode() throws IOException {
        String host = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java");
        String glass = read("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");

        assertTrue(host.contains("dispatchDraw"));
        assertTrue(host.contains("canvas.clipPath(clipPath)"));
        int child = host.indexOf("super.dispatchDraw(canvas)");
        int highlight = host.indexOf("drawAdvancedHighlight(canvas)");
        assertTrue("sharp highlight must be composited only after the self-blurred child",
                child >= 0 && highlight > child);
        assertTrue(glass.contains("advancedMaterialActive"));
        assertTrue("glass RenderNode must remain rectangular for advanced self blur",
                glass.contains("setClipToOutline(false)"));
    }

    @Test
    public void bothLauncherSetupPathsUseSharedLayerAssembly() throws IOException {
        String mainHook = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(mainHook.contains("DockLiquidGlassHostView"));
        assertTrue(mainHook.contains("installLiquidGlassLayer("));
        assertFalse(mainHook.contains("new DockStrokeOverlayView"));
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
