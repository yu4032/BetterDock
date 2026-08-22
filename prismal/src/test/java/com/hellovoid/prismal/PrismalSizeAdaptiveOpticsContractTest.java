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

    @Test
    public void largeGlassAttenuatesCenterSpecularCausticAndHighlight() throws Exception {
        String glsl = read("prismal/src/main/res/raw/prismal_fragment.glsl");
        String generated = read("prismal/src/main/java/com/hellovoid/prismal/PrismalShaderSources.java");

        String[] required = {
                "float largeGlass = smoothstep(60.0, 180.0, minDim);",
                "float specSizeScale = mix(1.0, 0.50, largeGlass);",
                "float causticSizeScale = mix(1.0, 0.30, largeGlass);",
                "float highlightSizeScale = mix(1.0, 0.50, largeGlass);",
                "float deepCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(minDim * 0.28, minDim * 0.72, edgeDist), largeGlass);",
                "float sp = u_specular * 1.05 * specSizeScale;",
                "color += (specP + specS) * deepCenterFade * vec3(0.99, 0.993, 1.0);",
                "plusHL *= mix(0.42, 0.06, smallGlass) * highlightSizeScale * deepCenterFade;",
                "float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height * causticSizeScale * deepCenterFade;"
        };

        for (String marker : required) {
            assertTrue("GLSL missing size-adaptive optics marker: " + marker, glsl.contains(marker));
            assertTrue("generated shader missing size-adaptive optics marker: " + marker, generated.contains(marker));
        }
    }
}
