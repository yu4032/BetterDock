package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the compact launcher field separately from the validated Dock optical path. */
public class PrismalLauncherCompactOpticsContractTest {
    private static final Path COMPACT = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalLauncherCompactShader.java");
    private static final Path CHROMA = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalPixelDomainChromaShader.java");
    private static final Path DOCK = Path.of(
            "src/main/java/com/hellovoid/prismal/PrismalSingleEdgeShader.java");

    @Test
    public void compactLauncherUsesContinuousFullFootprintLensField() throws Exception {
        assertTrue("launcher-compact shader correction must exist", Files.exists(COMPACT));
        String source = Files.readString(COMPACT, StandardCharsets.UTF_8);

        assertTrue("compact lens direction must vary continuously from the center",
                source.contains("vec2 compactCoord = cKy / max(halfSz, vec2(1.0));")
                        && source.contains(
                        "vec2 lensDir = compactCoord / max(1.0, length(compactCoord));"));
        assertTrue("compact lens reach must span the short-axis half extent",
                source.contains("float lensRh = max(refractionHeight, minDim);"));
        assertTrue("compact transmitted field must have one base offset",
                source.contains("vec2 baseOffset = (dLens * lensDir) / u_resolution;"));
        assertFalse("compact replacement must not reintroduce the retired size-adaptive blend",
                source.contains("smoothRectRefractionW")
                        || source.contains("radialRefractionW")
                        || source.contains("SHAPE_ADAPTIVE_D_LENS_BLOCK"));
    }

    @Test
    public void launcherAndDockShareExactlyOnePixelDomainChromaCorrection() throws Exception {
        assertTrue("shared pixel-domain chroma correction must exist", Files.exists(CHROMA));
        String chroma = Files.readString(CHROMA, StandardCharsets.UTF_8);
        String compact = Files.readString(COMPACT, StandardCharsets.UTF_8);
        String dock = Files.readString(DOCK, StandardCharsets.UTF_8);

        assertTrue(chroma.contains(
                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertTrue(chroma.contains(
                "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;"));
        assertTrue("launcher must reuse the shared Dock chroma correction",
                compact.contains("PrismalPixelDomainChromaShader.apply(corrected)"));
        assertTrue("Dock must reuse the same shared chroma correction",
                dock.contains("PrismalPixelDomainChromaShader.apply(corrected)"));
    }

    @Test
    public void runtimeCompactTransformReplacesEveryTargetExactly() {
        String shader = PrismalLauncherCompactShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(shader.contains("vec2 compactCoord = cKy / max(halfSz, vec2(1.0));"));
        assertTrue(shader.contains("float lensRh = max(refractionHeight, minDim);"));
        assertTrue(shader.contains("vec2 baseOffset = (dLens * lensDir) / u_resolution;"));
        assertTrue(shader.contains(
                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertTrue(shader.contains(
                "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;"));

        assertFalse(shader.contains("u_lensDepthEffect * normalize(cenSafe)"));
        assertFalse(shader.contains("lensDeltaUv += parallax"));
        assertFalse(shader.contains("vec2 snellOff ="));
        assertFalse(shader.contains("vec2 bulgeUv ="));
        assertFalse(shader.contains("vec2 chromaPush = dispDir * chromaBase * pxNorm;"));
    }
}
