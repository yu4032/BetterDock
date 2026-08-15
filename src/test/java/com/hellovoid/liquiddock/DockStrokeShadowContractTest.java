package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression contracts for the stroke-shadow setting after stroke rendering moved in-process. */
public class DockStrokeShadowContractTest {
    private static String source() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java"),
                StandardCharsets.UTF_8);
    }

    @Test public void styleConsumesExistingStrokeShadowConfig() throws IOException {
        String source = source();
        assertTrue(source.contains("final boolean shadowEnabled;"));
        assertTrue(source.contains("final float shadowRadiusPx;"));
        assertTrue(source.contains("final int shadowAlpha;"));
        assertTrue(source.contains("config.strokeShadow,"));
        assertTrue(source.contains("config.strokeShadowRadius * dimensionScale"));
        assertTrue(source.contains("config.strokeShadowAlpha"));
    }

    @Test public void shadowUsesSharedStrokeGeometryWithoutIndependentView() throws IOException {
        String source = source();
        assertTrue(source.contains("drawStrokeShadow(canvas, s);"));
        assertTrue(source.contains("buildShape(shadowOuter"));
        assertTrue(source.contains("buildShape(shadowInner"));
        assertTrue(source.contains("outerRect"));
        assertTrue(source.contains("innerRect"));
        assertFalse(source.contains("Path.Op."));
        assertFalse(source.contains("setLayerType(View.LAYER_TYPE_SOFTWARE"));
    }

    @Test public void shadowIsClippedOutOfDockInteriorAndDrawnBeforeStroke() throws IOException {
        String source = source();
        assertTrue(source.contains("canvas.clipPath(shadowOuter);"));
        assertTrue(source.contains("canvas.clipOutPath(shadowInner);"));
        int shadow = source.indexOf("drawStrokeShadow(canvas, s);");
        int stroke = source.indexOf("canvas.drawPath(outer, paint);", shadow);
        assertTrue("stroke shadow must be rendered before the foreground stroke",
                shadow >= 0 && stroke > shadow);
    }
}
