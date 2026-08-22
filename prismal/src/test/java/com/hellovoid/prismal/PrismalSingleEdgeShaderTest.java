package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Ensures the shader actually compiled by PrismalRenderer has the LiquidDock corrections. */
public class PrismalSingleEdgeShaderTest {
    @Test
    public void runtimeFragmentUsesSmoothRoundedRectRefractionForNormalGlassAndEdgeNormalForWideDock() throws Exception {
        String shader = correctedRuntimeShader();

        assertTrue(shader.contains(
                "float glassAspect = max(u_glassSize.x, u_glassSize.y) / max(min(u_glassSize.x, u_glassSize.y), 1.0);"));
        assertTrue(shader.contains(
                "float smoothRectRefractionW = 1.0 - smoothstep(2.2, 3.2, glassAspect);"));
        assertTrue(shader.contains(
                "float smoothRectK = max(u_sminSmoothing, minDim * 0.12);"));
        assertTrue(shader.contains(
                "float smoothRectSd = sdRoundBox(pPx, halfSz, crMask, smoothRectK);"));
        assertTrue(shader.contains(
                "float smoothRectDx = 0.5 * (sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, crMask, smoothRectK) - sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, crMask, smoothRectK));"));
        assertTrue(shader.contains(
                "float smoothRectDy = 0.5 * (sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, crMask, smoothRectK) - sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, crMask, smoothRectK));"));
        assertTrue(shader.contains(
                "vec2 smoothRectGrad = vec2(smoothRectDx, smoothRectDy);"));
        assertTrue(shader.contains(
                "vec2 smoothRectDir = length(smoothRectGrad) > 1e-5 ? normalize(smoothRectGrad) : vec2(0.0);"));
        assertTrue(shader.contains(
                "float smoothRectInwardPx = max(0.0, -smoothRectSd);"));
        assertTrue(shader.contains(
                "vec2 edgeLensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);"));
        assertTrue(shader.contains(
                "vec2 lensDirBlend = mix(edgeLensDir, smoothRectDir, smoothRectRefractionW);"));
        assertTrue(shader.contains(
                "float lensInwardPx = mix(-sdIn, smoothRectInwardPx, smoothRectRefractionW);"));
        assertTrue(shader.contains(
                "dLens = circleMapRealistic(1.0 - (lensInwardPx / lensRh)) * (-u_lensRefractionPx);"));
        assertTrue(shader.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
        assertTrue(shader.contains("vec2 baseOffset = edgeRefractionUv;"));

        assertFalse(shader.contains("radialRefractionW"));
        assertFalse(shader.contains("radialRefractionRaw"));
        assertFalse(shader.contains("radialRefractionLen"));
        assertFalse(shader.contains("radialRefractionDir"));
        assertFalse(shader.contains("radialInwardPx"));
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
