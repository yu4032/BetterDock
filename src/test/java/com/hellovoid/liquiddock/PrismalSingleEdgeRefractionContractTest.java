package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the explicit runtime fork from upstream Prismal transmission. */
public class PrismalSingleEdgeRefractionContractTest {
    private static final Path PRISMAL = Path.of("prismal/src/main");

    @Test
    public void upstreamShaderRemainsVendoredWhileRendererCompilesCorrectedTransmission() throws Exception {
        String upstream = Files.readString(PRISMAL.resolve("res/raw/prismal_fragment.glsl"));
        String patch = Files.readString(PRISMAL.resolve(
                "java/com/hellovoid/prismal/PrismalSingleEdgeShader.java"));
        String renderer = Files.readString(PRISMAL.resolve(
                "java/com/hellovoid/prismal/PrismalRenderer.java"));

        assertTrue("vendored upstream remains available for provenance",
                upstream.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;"));
        assertTrue("runtime correction must collapse transmission to one edge vector",
                patch.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;")
                        && patch.contains("vec2 baseOffset = edgeRefractionUv;"));
        assertTrue("runtime correction must remove the two extra spatial bands",
                patch.contains("UPSTREAM_TRANSMITTED_BLOCK")
                        && patch.contains("SINGLE_EDGE_TRANSMITTED_BLOCK"));
        assertTrue("renderer must compile the corrected fragment, not the raw vendored fragment",
                renderer.contains(
                        "PrismalSingleEdgeShader.apply(PrismalShaderSources.FRAGMENT)"));
    }

    @Test
    public void correctionUsesTheYDownTextureBasisForChromaticSampling() throws Exception {
        String patch = Files.readString(PRISMAL.resolve(
                "java/com/hellovoid/prismal/PrismalSingleEdgeShader.java"));
        assertTrue(patch.contains(
                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertTrue(patch.contains("UPSTREAM_CHROMA_DIRECTION")
                && patch.contains("TEXTURE_CHROMA_DIRECTION"));
    }
}
