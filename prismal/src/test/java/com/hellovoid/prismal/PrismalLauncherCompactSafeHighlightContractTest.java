package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the replacement launcher highlight to a continuous compact edge field. */
public class PrismalLauncherCompactSafeHighlightContractTest {
    private static final Path HIGHLIGHT = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalLauncherCompactHighlightShader.java");
    private static final Path COMPACT = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalLauncherCompactShader.java");

    @Test
    public void compactPipelineAddsSafeHighlightAfterRuntimeComponentGates() throws Exception {
        assertTrue("compact-safe highlight transform must exist", Files.exists(HIGHLIGHT));
        String compact = Files.readString(COMPACT, StandardCharsets.UTF_8);
        assertTrue(compact.contains("PrismalComponentGateShader.apply(corrected)"));
        assertTrue(compact.contains("PrismalLauncherCompactHighlightShader.apply(corrected)"));
    }

    @Test
    public void replacementHighlightUsesContinuousDirectionAndEdgeBandOnly() throws Exception {
        String source = Files.readString(HIGHLIGHT, StandardCharsets.UTF_8);
        assertTrue(source.contains(
                "vec2 compactHiDir = length(compactCoord) > 1e-4 ? normalize(compactCoord) : vec2(0.0, -1.0);"));
        assertTrue(source.contains(
                "float compactEdge = (1.0 - smoothstep(0.0, compactEdgeBand, edgeDist))"));
        assertTrue(source.contains(
                "color += compactHighlightColor * compactHighlight * u_componentCompactSafeHighlight;"));

        assertFalse("safe launcher highlight must not use the rounded-rect axis gradient",
                source.contains("gradLens"));
        assertFalse("safe launcher highlight must not rebuild diagonal/corner selector logic",
                source.contains("pairOpp") || source.contains("streakOpp")
                        || source.contains("max(abs(cn.x), abs(cn.y))"));
    }

    @Test
    public void transformedRuntimeShaderKeepsUpstreamChainsGatedAndAddsSafeEdgeHighlight() {
        String shader = PrismalLauncherCompactShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(shader.contains("if (u_componentSkyHaze > 0.5)"));
        assertTrue(shader.contains("if (u_componentSpecular > 0.5)"));
        assertTrue(shader.contains("if (u_componentPlainHighlight > 0.5)"));
        assertTrue(shader.contains("if (u_componentCaustics > 0.5)"));
        assertTrue(shader.contains(
                "color += compactHighlightColor * compactHighlight * u_componentCompactSafeHighlight;"));
    }
}
