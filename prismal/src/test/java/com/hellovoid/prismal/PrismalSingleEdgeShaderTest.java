package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/** Ensures the shader actually compiled by PrismalRenderer has the single-edge correction. */
public class PrismalSingleEdgeShaderTest {
    @Test
    public void runtimeFragmentHasOneTransmittedRefractionAndOneTextureBasis() throws Exception {
        Class<?> patchClass;
        try {
            patchClass = Class.forName("com.hellovoid.prismal.PrismalSingleEdgeShader");
        } catch (ClassNotFoundException missing) {
            fail("PrismalSingleEdgeShader must correct the vendored upstream fragment at runtime");
            return;
        }
        Method apply = patchClass.getDeclaredMethod("apply", String.class);
        apply.setAccessible(true);
        String shader = (String) apply.invoke(null, PrismalShaderSources.FRAGMENT);

        assertTrue(shader.contains("vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;"));
        assertTrue(shader.contains("vec2 baseOffset = edgeRefractionUv;"));
        assertFalse(shader.contains("lensDeltaUv += parallax"));
        assertFalse(shader.contains("vec2 snellOff ="));
        assertFalse(shader.contains("vec2 bulgeUv ="));
        assertFalse(shader.contains("lensDeltaUv + snellOff + bulgeUv"));
        assertTrue(shader.contains(
                "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);"));
        assertFalse(shader.contains(
                "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx)"));
    }
}
