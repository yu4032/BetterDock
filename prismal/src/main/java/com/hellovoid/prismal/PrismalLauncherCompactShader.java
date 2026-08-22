package com.hellovoid.prismal;

/**
 * Applies the compact launcher correction without changing the vendored upstream source.
 *
 * <p>Compact rounded rectangles expose two upstream assumptions that the extremely wide Dock
 * does not: the realistic rounded-rect gradient chooses one nearest axis in the interior,
 * producing diagonal direction seams, and the stock lens only exists inside refractionHeight,
 * leaving a rectangular zero-refraction core. Launcher compact glass uses one continuous
 * center-to-edge field across the short-axis half extent while preserving upstream masking,
 * dome, Fresnel, tint and blur. Chromatic separation is shared exactly with the validated Dock
 * pixel-domain correction. Upstream white highlight paths are removed and replaced by one
 * launcher-only continuous edge highlight that does not use gradLens axis selection.</p>
 */
final class PrismalLauncherCompactShader {
    private static final String UPSTREAM_LENS_DIRECTION = """
                vec2 cenSafe = cKy + vec2(1e-4, 1e-4);
                vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);
                float ldLen = length(lensDir);
                lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);
            """;

    private static final String COMPACT_LENS_DIRECTION = """
                // Compact launcher glass needs a continuous field through the center. Keeping the
                // aspect-normalized vector unnormalized near the origin makes displacement taper
                // smoothly to zero instead of introducing a center singularity or axis seam.
                vec2 compactCoord = cKy / max(halfSz, vec2(1.0));
                vec2 lensDir = compactCoord / max(1.0, length(compactCoord));
            """;

    private static final String UPSTREAM_LENS_REACH =
            "float lensRh = refractionHeight;";
    private static final String COMPACT_LENS_REACH =
            "float lensRh = max(refractionHeight, minDim);";

    private static final String UPSTREAM_TRANSMITTED_BLOCK = """
                vec2 lensDeltaUv = (dLens * lensDir) / u_resolution;
                float parallaxK = 0.052 * u_displacementScale;
                vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;
                lensDeltaUv += parallax;
                lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));

                float refrStr = height * (0.5 + F * 0.35);
                vec3 refIn = refract(-V, N, 1.0 / u_ior);
                vec3 refOut = (dot(refIn, refIn) < 0.001) ? vec3(0.0) : refract(refIn, -N, u_ior);
                vec2 snellOff = (refOut.xy * u_glassThickness * refrStr / u_resolution) * u_displacementScale;
                snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));

                vec2 bDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);
                float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));
                bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);
                bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;
                vec2 bulgeUv = bDir * bulge * u_glassSize / u_resolution;
                snellOff *= pxNorm * dropLens;
                bulgeUv *= pxNorm;

                vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;
            """;

    private static final String COMPACT_TRANSMITTED_BLOCK = """
                // One continuous transmitted field avoids reintroducing the rounded-rect axis seam
                // through parallax while the lens smoothly falls to zero at the exact center.
                vec2 baseOffset = (dLens * lensDir) / u_resolution;
            """;

    private PrismalLauncherCompactShader() {}

    static String apply(String upstreamFragment) {
        if (upstreamFragment == null) {
            throw new IllegalArgumentException("upstreamFragment == null");
        }
        String corrected = replaceExactlyOnce(
                upstreamFragment,
                UPSTREAM_LENS_DIRECTION,
                COMPACT_LENS_DIRECTION,
                "Prismal compact lens-direction block");
        corrected = replaceExactlyOnce(
                corrected,
                UPSTREAM_LENS_REACH,
                COMPACT_LENS_REACH,
                "Prismal compact lens reach");
        corrected = replaceExactlyOnce(
                corrected,
                UPSTREAM_TRANSMITTED_BLOCK,
                COMPACT_TRANSMITTED_BLOCK,
                "Prismal compact transmitted-refraction block");
        corrected = PrismalPixelDomainChromaShader.apply(corrected);
        corrected = PrismalLauncherHighlightSuppressionShader.apply(corrected);
        return PrismalLauncherCompactHighlightShader.apply(corrected);
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
