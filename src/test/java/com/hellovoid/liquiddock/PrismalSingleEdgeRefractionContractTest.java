package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contracts for a single, direction-consistent transmitted refraction field. */
public class PrismalSingleEdgeRefractionContractTest {
    private static final Path SHADER =
            Path.of("prismal/src/main/res/raw/prismal_fragment.glsl");

    @Test
    public void transmittedBackdropUsesOneEdgeRefractionInsteadOfThreeOffsetBands() throws Exception {
        String shader = Files.readString(SHADER);

        assertTrue("transmitted background must have one named edge-refraction vector",
                shader.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
        assertTrue("the backdrop sample must consume only that edge-refraction vector",
                shader.contains("vec2 baseOffset = edgeRefractionUv;"));

        assertFalse("parallax must not create a second transmitted displacement band",
                shader.contains("lensDeltaUv += parallax"));
        assertFalse("Snell displacement must not create a separate outer-edge step",
                shader.contains("vec2 snellOff ="));
        assertFalse("mid-band bulge must not create a third refraction step",
                shader.contains("vec2 bulgeUv ="));
        assertFalse("the old three-term transmitted offset must never return",
                shader.contains("lensDeltaUv + snellOff + bulgeUv"));
    }

    @Test
    public void textureSpaceRadialDirectionsUseTheYDownBasis() throws Exception {
        String shader = Files.readString(SHADER);

        assertTrue("Prismal's sampled backdrop UV is Y-down after the vertex flip",
                shader.contains("vec2 cKy = vec2(pPx.x, -pPx.y);"));
        assertTrue("chromatic direction must use the same Y-down radial basis",
                shader.contains("vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertFalse("raw Y-up shape coordinates must not directly drive a sampled UV direction",
                shader.contains("vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx)"));
    }
}
