package com.hellovoid.prismal;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Keeps legacy Dock params intact while allowing Launcher nodes to override only press input. */
public class PrismalInteractionOverrideContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/prismal");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test
    public void rendererAcceptsOptionalPerDrawInteractionWithoutChangingLegacyEntryPoints()
            throws Exception {
        String renderer = read("PrismalRenderer.java");

        assertTrue("existing Dock render path must remain",
                renderer.contains("public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params)"));
        assertTrue("existing highlight-profile draw path must remain",
                renderer.contains("drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile)"));
        assertTrue("Launcher needs one overload carrying per-node interaction",
                renderer.contains("PrismalInteractionState interactionState"));
        assertTrue("legacy draw path must delegate without forcing an interaction override",
                renderer.contains("drawGlass(geometry, params, highlightProfile, null)"));
    }

    @Test
    public void onlyPressUniformsUseInteractionOverride() throws Exception {
        String renderer = read("PrismalRenderer.java");

        assertTrue("press progress may be overridden per node",
                renderer.contains("interactionState != null ? interactionState.pressProgress : p.pressProgress"));
        assertTrue("glow X may be overridden per node",
                renderer.contains("interactionState != null ? interactionState.glowCenterX : p.glowCenterX"));
        assertTrue("glow Y may be overridden per node",
                renderer.contains("interactionState != null ? interactionState.glowCenterY : p.glowCenterY"));
        assertTrue("backdrop pinch remains part of the shared optical params",
                renderer.contains("uniform1f(\"u_backdropPinch\", p.backdropPinch)"));
        assertTrue("glow strength remains part of the shared optical params",
                renderer.contains("uniform1f(\"u_glowStrength\", p.glowStrength)"));
    }
}
