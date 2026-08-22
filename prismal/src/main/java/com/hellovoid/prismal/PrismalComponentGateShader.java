package com.hellovoid.prismal;

/** Adds per-draw gates to the nine additive highlight layers used by the shared Dock optics model. */
final class PrismalComponentGateShader {
    private static final String DECLARATION_ANCHOR = "uniform float u_glowStrength;";
    private static final String COMPONENT_UNIFORMS = """
            uniform float u_componentSkyHaze;
            uniform float u_componentSpecular;
            uniform float u_componentLitRim;
            uniform float u_componentOppositeRim;
            uniform float u_componentCornerRim;
            uniform float u_componentFaceSheen;
            uniform float u_componentPlainHighlight;
            uniform float u_componentCaustics;
            uniform float u_componentPressGlow;
            """;

    private PrismalComponentGateShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String corrected = replaceExactlyOnce(
                fragment,
                DECLARATION_ANCHOR,
                DECLARATION_ANCHOR + "\n" + COMPONENT_UNIFORMS,
                "Prismal component uniform anchor");
        corrected = gateExactlyOnce(corrected,
                "color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);",
                "u_componentSkyHaze", "sky haze");
        corrected = gateExactlyOnce(corrected,
                "color += (specP + specS) * vec3(0.99, 0.993, 1.0);",
                "u_componentSpecular", "specular");
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * rimLitSide * rimScale;",
                "u_componentLitRim", "lit rim");
        corrected = gateExactlyOnce(corrected,
                "color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;",
                "u_componentOppositeRim", "opposite rim");
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * rimCorner * rimScale;",
                "u_componentCornerRim", "corner rim");
        corrected = gateExactlyOnce(corrected,
                "color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;",
                "u_componentFaceSheen", "face sheen");
        corrected = gateExactlyOnce(corrected,
                "color += plusHL * vec3(0.99, 0.995, 1.0);",
                "u_componentPlainHighlight", "plain highlight");
        corrected = gateExactlyOnce(corrected,
                "color += caust * vec3(1.0, 0.96, 0.90);",
                "u_componentCaustics", "caustics");
        corrected = gateExactlyOnce(corrected,
                "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);",
                "u_componentPressGlow", "press glow");
        return corrected;
    }

    private static String gateExactlyOnce(
            String source, String statement, String uniform, String label) {
        return replaceExactlyOnce(
                source,
                statement,
                "if (" + uniform + " > 0.5) { " + statement + " }",
                "Prismal " + label + " component");
    }

    private static String replaceExactlyOnce(
            String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText + source.substring(first + oldText.length());
    }
}
