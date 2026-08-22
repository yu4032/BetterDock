package com.hellovoid.prismal;

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
    public void compactGlassUsesRadialCenterFadeInsteadOfRoundedBoxDepth() throws Exception {
        String glsl = read("src/main/res/raw/prismal_fragment.glsl");
        String generated = read("src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float compactGlass = 1.0 - smoothstep(48.0, 76.0, minDim);",
                "float compactSpecScale = mix(1.0, 0.30, compactGlass);",
                "float compactCausticScale = mix(1.0, 0.15, compactGlass);",
                "float compactHighlightScale = mix(1.0, 0.40, compactGlass);",
                "vec2 compactNorm = pPx / max(halfSz, vec2(1.0));",
                "float compactRadius = length(compactNorm);",
                "float compactCenterFade = mix(1.0, 0.28 + 0.72 * smoothstep(0.18, 0.88, compactRadius), compactGlass);",
                "float sp = u_specular * 1.05 * specSizeScale * compactSpecScale;",
                "color += (specP + specS) * deepCenterFade * compactCenterFade * vec3(0.99, 0.993, 1.0);",
                "plusHL *= mix(0.42, 0.06, smallGlass) * highlightSizeScale * compactHighlightScale * deepCenterFade * compactCenterFade;",
                "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height * causticSizeScale * compactCausticScale * deepCenterFade * compactCenterFade;"
        };

        for (String marker : required) {
            requireBoth(glsl, generated, marker);
        }
    }
}
