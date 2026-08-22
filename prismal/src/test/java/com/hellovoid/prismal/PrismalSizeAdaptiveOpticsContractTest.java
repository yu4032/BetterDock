package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PrismalSizeAdaptiveOpticsContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static void requireBoth(String glsl, String generated, String marker) {
        assertTrue("GLSL missing size-adaptive optics marker: " + marker, glsl.contains(marker));
        assertTrue("generated shader missing size-adaptive optics marker: " + marker, generated.contains(marker));
    }

    private static void forbidBoth(String glsl, String generated, String marker) {
        assertFalse("GLSL still contains forbidden optics marker: " + marker, glsl.contains(marker));
        assertFalse("generated shader still contains forbidden optics marker: " + marker, generated.contains(marker));
    }

    @Test
    public void largeGlassAttenuatesCenterSpecularCausticAndHighlight() throws Exception {
        String glsl = read("src/main/res/raw/prismal_fragment.glsl");
        String generated = read("src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float largeGlass = smoothstep(60.0, 180.0, minDim);",
                "float specSizeScale = mix(1.0, 0.50, largeGlass);",
                "float causticSizeScale = mix(1.0, 0.30, largeGlass);",
                "float highlightSizeScale = mix(1.0, 0.50, largeGlass);",
                "float deepCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(minDim * 0.28, minDim * 0.72, edgeDist), largeGlass);"
        };

        for (String marker : required) {
            requireBoth(glsl, generated, marker);
        }
    }

    @Test
    public void compactGlassOnlyChangesFaceLightingStrength() throws Exception {
        String glsl = read("src/main/res/raw/prismal_fragment.glsl");
        String generated = read("src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float compactGlass = 1.0 - smoothstep(48.0, 76.0, minDim);",
                "float compactSpecScale = mix(1.0, 0.30, compactGlass);",
                "float compactCausticScale = mix(1.0, 0.15, compactGlass);",
                "float compactHighlightScale = mix(1.0, 0.40, compactGlass);",
                "float compactCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(0.18, 0.88, faceDepthT), compactGlass);",
                "float sp = u_specular * 1.05 * specSizeScale * compactSpecScale;",
                "color += (specP + specS) * deepCenterFade * compactCenterFade * vec3(0.99, 0.993, 1.0);",
                "plusHL *= mix(0.42, 0.06, smallGlass) * highlightSizeScale * compactHighlightScale * deepCenterFade * compactCenterFade;",
                "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height * causticSizeScale * compactCausticScale * deepCenterFade * compactCenterFade;"
        };

        for (String marker : required) {
            requireBoth(glsl, generated, marker);
        }
    }

    @Test
    public void faceNormalAndCausticUseContinuousSmoothRoundedRectSurface() throws Exception {
        String glsl = read("src/main/res/raw/prismal_fragment.glsl");
        String generated = read("src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float faceSmoothK = max(u_sminSmoothing, minDim * 0.12);",
                "float faceSd = sdRoundBox(pPx, halfSz, crMask, faceSmoothK);",
                "float faceCenterDepth = max(-sdRoundBox(vec2(0.0), halfSz, crMask, faceSmoothK), 1.0);",
                "float faceDepthT = clamp(max(0.0, -faceSd) / faceCenterDepth, 0.0, 1.0);",
                "float smoothFaceHeight = faceDepthT * faceDepthT * (3.0 - 2.0 * faceDepthT);",
                "float faceDx = 0.5 * (sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, crMask, faceSmoothK) - sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, crMask, faceSmoothK));",
                "float faceDy = 0.5 * (sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, crMask, faceSmoothK) - sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, crMask, faceSmoothK));",
                "float smoothFaceSlope = 6.0 * faceDepthT * (1.0 - faceDepthT) / faceCenterDepth;",
                "vec2 smoothFaceGrad = -vec2(faceDx, faceDy) * smoothFaceSlope;",
                "height = smoothFaceHeight;",
                "gradH = smoothFaceGrad;",
                "vec3 N = normalize(vec3(-gradH.x * u_normalStrength, -gradH.y * u_normalStrength, 1.0));",
                "float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);"
        };
        for (String marker : required) {
            requireBoth(glsl, generated, marker);
        }

        String[] forbidden = {
                "vec2 opticalNorm =",
                "float opticalRadius =",
                "float radialHeight =",
                "vec2 radialGrad =",
                "height = radialHeight;",
                "gradH = radialGrad;",
                "float radialSurfaceW =",
                "float compactCore =",
                "float compactRadialHeight =",
                "vec2 compactRadialGrad ="
        };
        for (String marker : forbidden) {
            forbidBoth(glsl, generated, marker);
        }
    }

    @Test
    public void roundedRectStillOwnsBoundaryAndEdgeShell() throws Exception {
        String glsl = read("src/main/res/raw/prismal_fragment.glsl");
        String generated = read("src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);",
                "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);",
                "vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);",
                "float shellRim = smoothstep(bandR, bandR * 0.06, edgeDist) * smoothstep(-2.2, 0.0, distMask);"
        };
        for (String marker : required) {
            requireBoth(glsl, generated, marker);
        }
    }
}
