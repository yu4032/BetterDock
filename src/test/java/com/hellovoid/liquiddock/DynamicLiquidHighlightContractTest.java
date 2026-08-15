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
    public void activeBackendIsForwardedToSharpHost() throws IOException {
        String glass = source("DockLiquidGlassView.java");
        String host = source("DockLiquidGlassHostView.java");
        assertTrue("glass must publish active backend changes",
                glass.contains("ActiveBlurBackendListener"));
        assertTrue("glass must notify its backend listener",
                glass.contains("onActiveBlurBackendChanged(activeBlurBackend)"));
        assertTrue("host must receive backend changes without a second child View",
                host.contains("glass.setActiveBlurBackendListener(this::setActiveBlurBackend)"));
    }

    @Test
    public void advancedHostUsesRealtimeGeometricHighlightShader() throws IOException {
        String host = source("DockLiquidGlassHostView.java");
        assertTrue("advanced highlight must be computed with RuntimeShader",
                host.contains("RuntimeShader"));
        assertTrue("sharp highlight must use additive PLUS compositing",
                host.contains("BlendMode.PLUS"));
        assertTrue("host must own the final shared Dock shape clip",
                host.contains("DockShapePath.build") && host.contains("canvas.clipPath(clipPath)"));
        assertTrue("highlight must draw after the glass child inside the host clip",
                host.indexOf("super.dispatchDraw(canvas)") < host.indexOf("drawAdvancedHighlight(canvas)"));
        assertTrue("host must compute specular lighting", host.contains("specP"));
        assertTrue("host must compute rim lighting", host.contains("rimLitSide"));
        assertTrue("host must compute caustics", host.contains("caust"));
        assertTrue("host must expose dynamic highlight parameters",
                host.contains("setHighlightParams"));
        assertFalse("static Canvas gradient is replaced by the realtime shader",
                host.contains("LinearGradient"));
    }

    @Test
    public void dynamicHighlightParametersFollowGlassHotReload() throws IOException {
        String host = source("DockLiquidGlassHostView.java");
        assertTrue(host.contains("glass.normalStrength"));
        assertTrue(host.contains("glass.dome"));
        assertTrue(host.contains("glass.specularSharp"));
        assertTrue(host.contains("glass.specularStrength"));
        assertTrue(host.contains("glass.rimLight"));
        assertTrue(host.contains("glass.caustics"));
        assertTrue(host.contains("glass.edgeBand"));
        assertTrue(host.contains("glass.highlightAlpha"));
        assertTrue("reload must forward the dynamic model in one setter call",
                host.contains("setHighlightParams(glass.normalStrength, glass.dome"));
    }

    @Test
    public void squircleHighlightUsesSharedBezierGeometryAndStableRimBand() throws IOException {
        String host = source("DockLiquidGlassHostView.java");
        assertTrue("shader must know whether the shared Dock shape is a squircle",
                host.contains("uniform float squircleEnabled;"));
        assertTrue("shader must receive the same cubic control-point ratio as DockShapePath",
                host.contains("uniform float squircleCp;"));
        assertTrue("squircle distance must follow the cubic corner rather than sdRound only",
                host.contains("sdBezierSquircle"));
        assertTrue("all highlight geometry must route through the selected shape distance",
                host.contains("sdShape"));
        assertTrue("runtime must pass squircle mode to the highlight shader",
                host.contains("setFloatUniform(\"squircleEnabled\""));
        assertTrue("runtime must pass the shared squircle control point to the highlight shader",
                host.contains("setFloatUniform(\"squircleCp\""));
        assertTrue("highlight width must actually scale the advanced rim band",
                host.contains("uniform float highlightWidth;")
                        && host.contains("max(0.1,highlightWidth)"));
        assertFalse("reverse-edge smoothstep causes a hard/undefined corner transition",
                host.contains("smoothstep(bandR,bandR*0.06,edgeDist)"));
    }
}
