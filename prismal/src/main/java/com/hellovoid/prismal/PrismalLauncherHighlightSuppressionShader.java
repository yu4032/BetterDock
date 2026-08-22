package com.hellovoid.prismal;

/**
 * Removes upstream additive white highlight paths that are not suitable for compact launcher glass.
 *
 * <p>The compact launcher uses its own continuous edge highlight after this transform. Backdrop
 * sampling, refraction, chromatic separation, blur, tint, shadows and sampled background
 * reflection remain untouched. Dock never uses this transform.</p>
 */
final class PrismalLauncherHighlightSuppressionShader {
    private PrismalLauncherHighlightSuppressionShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String corrected = fragment;
        corrected = suppressExactlyOnce(corrected,
                "color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);",
                "launcher sky haze");
        corrected = suppressExactlyOnce(corrected,
                "color += (specP + specS) * vec3(0.99, 0.993, 1.0);",
                "launcher specular");
        corrected = suppressExactlyOnce(corrected,
                "color += hiSoft * rimLitSide * rimScale;",
                "launcher lit rim");
        corrected = suppressExactlyOnce(corrected,
                "color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;",
                "launcher opposite rim");
        corrected = suppressExactlyOnce(corrected,
                "color += hiSoft * rimCorner * rimScale;",
                "launcher corner rim");
        corrected = suppressExactlyOnce(corrected,
                "color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;",
                "launcher face sheen");
        corrected = suppressExactlyOnce(corrected,
                "color += plusHL * vec3(0.99, 0.995, 1.0);",
                "launcher plain highlight");
        corrected = suppressExactlyOnce(corrected,
                "color += caust * vec3(1.0, 0.96, 0.90);",
                "launcher caustic highlight");
        corrected = suppressExactlyOnce(corrected,
                "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);",
                "launcher press glow");
        return corrected;
    }

    private static String suppressExactlyOnce(String source, String statement, String label) {
        int first = source.indexOf(statement);
        if (first < 0 || source.indexOf(statement, first + statement.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first)
                + "/* " + label + " disabled for compact launcher */"
                + source.substring(first + statement.length());
    }
}
