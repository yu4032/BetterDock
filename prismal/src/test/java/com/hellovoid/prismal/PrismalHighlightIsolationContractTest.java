package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Ensures Launcher can gate highlights per draw while Dock keeps the unchanged all-enabled path. */
public class PrismalHighlightIsolationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/prismal");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test
    public void rendererAddsPerDrawProfileAndKeepsLegacyDockPathAllEnabled() throws Exception {
        Path profile = MAIN.resolve("PrismalHighlightProfile.java");
        assertTrue(Files.exists(profile));
        String renderer = read("PrismalRenderer.java");
        assertTrue(renderer.contains(
                "drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile)"));
        assertTrue(renderer.contains("drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED)"));
        assertFalse(renderer.contains("enum Mode"));
        assertFalse(renderer.contains("LAUNCHER_COMPACT"));
        assertFalse(renderer.contains("PrismalComponentControls"));
    }

    @Test
    public void shaderGatesExactlyTheNineHighlightsPresentInDockModel() throws Exception {
        Path gatePath = MAIN.resolve("PrismalComponentGateShader.java");
        assertTrue(Files.exists(gatePath));
        String gate = read("PrismalComponentGateShader.java");
        String[] uniforms = {
                "u_componentSkyHaze",
                "u_componentSpecular",
                "u_componentLitRim",
                "u_componentOppositeRim",
                "u_componentCornerRim",
                "u_componentFaceSheen",
                "u_componentPlainHighlight",
                "u_componentCaustics",
                "u_componentPressGlow"
        };
        for (String uniform : uniforms) assertTrue(uniform, gate.contains(uniform));
        assertFalse(gate.contains("CompactSafe"));

        String renderer = read("PrismalRenderer.java");
        assertTrue(renderer.contains("PrismalComponentGateShader.apply("));
        for (String uniform : uniforms) assertTrue(uniform, renderer.contains(uniform));
    }

    @Test
    public void profileDefaultsToAllEnabledSoExistingDockOutputIsPreserved() throws Exception {
        Path profilePath = MAIN.resolve("PrismalHighlightProfile.java");
        assertTrue(Files.exists(profilePath));
        String profile = read("PrismalHighlightProfile.java");
        assertTrue(profile.contains("public static final PrismalHighlightProfile ALL_ENABLED"));
        assertFalse(profile.contains("compactSafe"));
    }
}
