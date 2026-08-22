package com.hellovoid.prismal;

/**
 * Adds one launcher-only edge highlight after the upstream white highlight chains were removed.
 *
 * <p>The highlight is intentionally restricted to a narrow edgeDist band. Its lighting direction
 * comes from compactCoord, the same continuous aspect-normalized field used by compact launcher
 * refraction, so it cannot inherit the rounded-rect axis seam or corner-selector cross.</p>
 */
final class PrismalLauncherCompactHighlightShader {
    private static final String OUTPUT =
            "gl_FragColor = vec4(color, opacity * u_transmittance);";

    private static final String COMPACT_SAFE_HIGHLIGHT = """
                // Launcher compact highlight: edge-only and continuous in direction.
                vec2 compactHiDir = length(compactCoord) > 1e-4 ? normalize(compactCoord) : vec2(0.0, -1.0);
                vec2 compactLightDir = normalize(u_lightDir + vec2(1e-5));
                float compactEdgeBand = clamp(minDim * 0.035, 1.0, 7.0);
                float compactEdge = (1.0 - smoothstep(0.0, compactEdgeBand, edgeDist))
                    * smoothstep(-2.0, 0.0, distMask);
                float compactLightDot = dot(compactHiDir, compactLightDir);
                float compactFront = pow(max(compactLightDot, 0.0), 2.4);
                float compactBack = pow(max(-compactLightDot, 0.0), 1.8);
                float compactHighlight = compactEdge
                    * clamp(u_plainHighlight, 0.0, 1.5)
                    * clamp(u_rimStrength, 0.0, 2.0)
                    * (0.035 + 0.10 * compactFront + 0.025 * compactBack)
                    * (0.55 + 0.45 * height);
                vec3 compactHighlightColor = vec3(0.985, 0.993, 1.0);
                color += compactHighlightColor * compactHighlight;

                gl_FragColor = vec4(color, opacity * u_transmittance);
            """;

    private PrismalLauncherCompactHighlightShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        int first = fragment.indexOf(OUTPUT);
        if (first < 0 || fragment.indexOf(OUTPUT, first + OUTPUT.length()) >= 0) {
            throw new IllegalStateException("Prismal launcher output contract changed");
        }
        return fragment.substring(0, first)
                + COMPACT_SAFE_HIGHLIGHT
                + fragment.substring(first + OUTPUT.length());
    }
}
