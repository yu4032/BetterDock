package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the compact-launcher policy that removes incompatible upstream white highlight paths. */
public class PrismalLauncherNoWhiteHighlightContractTest {
    private static final Path SUPPRESSOR = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalLauncherHighlightSuppressionShader.java");
    private static final Path COMPACT = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalLauncherCompactShader.java");

    @Test
    public void compactLauncherAppliesDedicatedHighlightSuppressor() throws Exception {
        assertTrue("launcher highlight suppressor must exist", Files.exists(SUPPRESSOR));
        String compact = Files.readString(COMPACT, StandardCharsets.UTF_8);
        assertTrue(compact.contains("PrismalLauncherHighlightSuppressionShader.apply(corrected)"));
    }

    @Test
    public void transformedLauncherFragmentContainsNoWhiteAdditiveHighlightOutputs() throws Exception {
        String shader = PrismalLauncherCompactShader.apply(PrismalShaderSources.FRAGMENT);

        assertFalse(shader.contains("color = mix(color, mix(color, skyHaze"));
        assertFalse(shader.contains("color += (specP + specS)"));
        assertFalse(shader.contains("color += hiSoft * rimLitSide"));
        assertFalse(shader.contains("color += mix(hiVeil, oppTint"));
        assertFalse(shader.contains("color += hiSoft * rimCorner"));
        assertFalse(shader.contains("color += hiSoft * faceSheenSoft"));
        assertFalse(shader.contains("color += plusHL * vec3"));
        assertFalse(shader.contains("color += caust * vec3"));
        assertFalse(shader.contains("color += vec3(1.0) * pressGlow"));

        // The compact policy must not disable the actual background/refraction path.
        assertTrue(shader.contains("texture2D(u_backgroundTexture"));
        assertTrue(shader.contains("vec2 baseOffset = (dLens * lensDir) / u_resolution;"));
    }
}
