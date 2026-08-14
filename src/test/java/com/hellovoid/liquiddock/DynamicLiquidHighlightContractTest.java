package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level wiring contract for backend-aware liquid-glass highlights. */
public class DynamicLiquidHighlightContractTest {
    private static String source(String file) throws IOException {
        Path path = Paths.get("src/main/java/com/hellovoid/liquiddock/" + file);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    public void glassShaderRoutesHighlightByActiveBackend() throws IOException {
        String glass = source("DockLiquidGlassView.java");
        assertTrue("glass shader must expose a highlight gate",
                glass.contains("uniform float highlightEnabled;"));
        assertTrue("glass shader must preserve the existing highlight alpha control",
                glass.contains("uniform float highlightAlpha;"));
        assertTrue("glass draw path must feed the highlight gate",
                glass.contains("setFloatUniform(\"highlightEnabled\""));
        assertTrue("dynamic highlight contribution must be conditional",
                glass.contains("if(highlightEnabled>0.5)"));
    }

    @Test
    public void activeBackendIsForwardedToSharpOverlay() throws IOException {
        String glass = source("DockLiquidGlassView.java");
        String host = source("DockLiquidGlassHostView.java");
        assertTrue("glass must publish active backend changes",
                glass.contains("ActiveBlurBackendListener"));
        assertTrue("glass must notify its backend listener",
                glass.contains("onActiveBlurBackendChanged(activeBlurBackend)"));
        assertTrue("host must route the active backend to the overlay",
                host.contains("overlayView.setActiveBlurBackend(mode)"));
    }

    @Test
    public void advancedOverlayUsesRealtimeGeometricHighlightShader() throws IOException {
        String overlay = source("DockStrokeOverlayView.java");
        assertTrue("advanced highlight must be computed with RuntimeShader",
                overlay.contains("RuntimeShader"));
        assertTrue("sharp highlight must use additive PLUS compositing",
                overlay.contains("BlendMode.PLUS"));
        assertTrue("highlight must be clipped to the shared Dock shape",
                overlay.contains("DockShapePath.build"));
        assertTrue("highlight draw must clip before drawing the shader rectangle",
                overlay.contains("canvas.clipPath(shape)"));
        assertTrue("overlay must compute specular lighting",
                overlay.contains("specP"));
        assertTrue("overlay must compute rim lighting",
                overlay.contains("rimLitSide"));
        assertTrue("overlay must compute caustics",
                overlay.contains("caust"));
        assertTrue("overlay must expose dynamic highlight parameters",
                overlay.contains("setHighlightParams"));
        assertFalse("static Canvas gradient is replaced by the realtime shader",
                overlay.contains("LinearGradient"));
    }

    @Test
    public void dynamicHighlightParametersFollowGlassHotReload() throws IOException {
        String overlay = source("DockStrokeOverlayView.java");
        assertTrue(overlay.contains("glass.normalStrength"));
        assertTrue(overlay.contains("glass.dome"));
        assertTrue(overlay.contains("glass.specularSharp"));
        assertTrue(overlay.contains("glass.specularStrength"));
        assertTrue(overlay.contains("glass.rimLight"));
        assertTrue(overlay.contains("glass.caustics"));
        assertTrue(overlay.contains("glass.edgeBand"));
        assertTrue(overlay.contains("glass.highlightAlpha"));
        assertTrue("reload must forward the dynamic model in one setter call",
                overlay.contains("setHighlightParams(glass.normalStrength, glass.dome"));
    }

    @Test
    public void squircleHighlightUsesSharedBezierGeometryAndStableRimBand() throws IOException {
        String overlay = source("DockStrokeOverlayView.java");
        assertTrue("shader must know whether the shared Dock shape is a squircle",
                overlay.contains("uniform float squircleEnabled;"));
        assertTrue("shader must receive the same cubic control-point ratio as DockShapePath",
                overlay.contains("uniform float squircleCp;"));
        assertTrue("squircle distance must follow the cubic corner rather than sdRound only",
                overlay.contains("sdBezierSquircle"));
        assertTrue("all highlight geometry must route through the selected shape distance",
                overlay.contains("sdShape"));
        assertTrue("runtime must pass squircle mode to the highlight shader",
                overlay.contains("setFloatUniform(\"squircleEnabled\""));
        assertTrue("runtime must pass the shared squircle control point to the highlight shader",
                overlay.contains("setFloatUniform(\"squircleCp\""));
        assertTrue("highlight width must actually scale the advanced rim band",
                overlay.contains("uniform float highlightWidth;")
                        && overlay.contains("max(0.1,highlightWidth)"));
        assertFalse("reverse-edge smoothstep causes a hard/undefined corner transition",
                overlay.contains("smoothstep(bandR,bandR*0.06,edgeDist)"));
    }
}
