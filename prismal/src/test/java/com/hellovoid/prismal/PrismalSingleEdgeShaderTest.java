package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Ensures the shader actually compiled by PrismalRenderer has the LiquidDock corrections. */
public class PrismalSingleEdgeShaderTest {
    @Test
    public void runtimeFragmentUsesContinuousRefractionForNormalGlassAndEdgeNormalForWideDock() throws Exception {
        String shader = correctedRuntimeShader();

        assertTrue(shader.contains(
                "float glassAspect = max(u_glassSize.x, u_glassSize.y) / max(min(u_glassSize.x, u_glassSize.y), 1.0);"));
        assertTrue(shader.contains(
                "float radialRefractionW = 1.0 - smoothstep(2.2, 3.2, glassAspect);"));
        assertTrue(shader.contains(
                "vec2 radialRefractionRaw = cKy / max(halfSz, vec2(1.0));"));
        assertTrue(shader.contains(
                "float radialRefractionLen = length(radialRefractionRaw);"));
        assertTrue(shader.contains(
                "vec2 radialRefractionDir = radialRefractionLen > 1e-5 ? radialRefractionRaw / radialRefractionLen : vec2(0.0);"));
        assertTrue(shader.contains(
                "float radialInwardPx = max(0.0, (1.0 - radialRefractionLen) * minDim);"));
        assertTrue(shader.contains(
                "vec2 edgeLensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);"));
        assertTrue(shader.contains(
                "vec2 lensDirBlend = mix(edgeLensDir, radialRefractionDir, radialRefractionW);"));
        assertTrue(shader.contains(
                "float lensInwardPx = mix(-sdIn, radialInwardPx, radialRefractionW);"));
        assertTrue(shader.contains(
                "dLens = circleMapRealistic(1.0 - (lensInwardPx / lensRh)) * (-u_lensRefractionPx);"));
        assertTrue(shader.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
        assertTrue(shader.contains("vec2 baseOffset = edgeRefractionUv;"));
        assertFalse(shader.contains("u_lensDepthEffect * normalize(cenSafe)"));
        assertFalse(shader.contains("lensDeltaUv += parallax"));
        assertFalse(shader.contains("vec2 snellOff ="));
        assertFalse(shader.contains("vec2 bulgeUv ="));
        assertFalse(shader.contains("lensDeltaUv + snellOff + bulgeUv"));
    }

    @Test
    public void chromaticSamplingUsesTextureBasisAndShortAxisPixelScale() throws Exception {
        String shader = correctedRuntimeShader();

        assertTrue(shader.contains(
                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertTrue(shader.contains(
                "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;"));
        assertFalse(shader.contains(
                "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx)"));
        assertFalse(shader.contains("vec2 chromaPush = dispDir * chromaBase * pxNorm;"));
    }

    private static String correctedRuntimeShader() throws Exception {
        Class<?> patchClass;
        try {
            patchClass = Class.forName("com.hellovoid.prismal.PrismalSingleEdgeShader");
        } catch (ClassNotFoundException missing) {
            fail("PrismalSingleEdgeShader must correct the vendored upstream fragment at runtime");
            return "";
        }
        Method apply = patchClass.getDeclaredMethod("apply", String.class);
        apply.setAccessible(true);
        return (String) apply.invoke(null, PrismalShaderSources.FRAGMENT);
    }
}
