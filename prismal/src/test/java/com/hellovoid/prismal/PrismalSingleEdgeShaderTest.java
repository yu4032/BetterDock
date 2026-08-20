package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Ensures the shader actually compiled by PrismalRenderer has the Dock corrections. */
public class PrismalSingleEdgeShaderTest {
    @Test
    public void runtimeFragmentHasOneUniformEdgeRefractionField() throws Exception {
        String shader = correctedRuntimeShader();

        assertTrue(shader.contains(
                "vec2 lensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);"));
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
