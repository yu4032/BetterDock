package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Guards the known-good startup-safe Prismal runtime while the artifact is re-investigated. */
public class PrismalOpticsBaselineRestorationTest {
    @Test
    public void experimentalOpticsChainIsFullyRemoved() throws Exception {
        Path transform = Path.of("../ci/prismal_size_adaptive_optics_transform.py");
        Path opticsContract = Path.of("src/test/java/com/hellovoid/prismal/PrismalSizeAdaptiveOpticsContractTest.java");
        Path workflow = Path.of("../.github/workflows/wallpaper-only-ci.yml");
        Path shaderPatch = Path.of("src/main/java/com/hellovoid/prismal/PrismalSingleEdgeShader.java");

        assertFalse("experimental optics transform must be removed", Files.exists(transform));
        assertFalse("experimental optics contract must be removed", Files.exists(opticsContract));

        String workflowText = Files.readString(workflow, StandardCharsets.UTF_8);
        assertFalse(workflowText.contains("prismal_size_adaptive_optics_transform.py"));
        assertTrue(workflowText.contains("name: LiquidDock-wallpaper-only-startup-safe-debug"));

        String shader = Files.readString(shaderPatch, StandardCharsets.UTF_8);
        assertTrue(shader.contains("private static final String EDGE_NORMAL_LENS_DIRECTION"));
        assertTrue(shader.contains(
                "vec2 lensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);"));
        assertFalse(shader.contains("smoothRectRefractionW"));
        assertFalse(shader.contains("radialRefractionW"));
        assertFalse(shader.contains("SHAPE_ADAPTIVE_D_LENS_BLOCK"));
    }
}
