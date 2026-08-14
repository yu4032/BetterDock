package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
}
