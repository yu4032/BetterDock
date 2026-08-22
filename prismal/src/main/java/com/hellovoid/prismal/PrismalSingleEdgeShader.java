package com.hellovoid.prismal;

/**
 * Applies LiquidDock's transmitted-refraction correction to the vendored Prismal shader.
 *
 * <p>The upstream source is intentionally kept byte-for-byte as the provenance baseline. Prismal's
 * stock fragment adds lens/parallax, Snell and a mid-band bulge into the sampled backdrop. LiquidDock
 * keeps one transmitted-refraction field. Very wide Dock glass keeps the translation-invariant local
 * edge normal, while ordinary folder/widget glass uses a continuous elliptical radial metric so the
 * nearest-edge X/Y branch cannot create a square center and four diagonal seams. Chromatic separation
 * is expressed in short-axis pixels before converting back to UV.</p>
 */
final class PrismalSingleEdgeShader {
    private static final String UPSTREAM_LENS_DIRECTION = """
                vec2 cenSafe = cKy + vec2(1e-4, 1e-4);
                vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);
                float ldLen = length(lensDir);
                lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);
            """;

    private static final String SHAPE_ADAPTIVE_LENS_DIRECTION = """
                // Ordinary folder/widget glass must not use the rounded-rect nearest-edge
                // direction through its interior: that field switches X/Y at |x|=|y| and
                // produces the visible four-wedge cross. Very wide Dock glass still needs
                // a translation-invariant straight-edge normal.
                float glassAspect = max(u_glassSize.x, u_glassSize.y) / max(min(u_glassSize.x, u_glassSize.y), 1.0);
                float radialRefractionW = 1.0 - smoothstep(2.2, 3.2, glassAspect);
                vec2 radialRefractionRaw = cKy / max(halfSz, vec2(1.0));
                float radialRefractionLen = length(radialRefractionRaw);
                vec2 radialRefractionDir = radialRefractionLen > 1e-5 ? radialRefractionRaw / radialRefractionLen : vec2(0.0);
                float radialInwardPx = max(0.0, (1.0 - radialRefractionLen) * minDim);
                vec2 edgeLensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);
                vec2 lensDirBlend = mix(edgeLensDir, radialRefractionDir, radialRefractionW);
                float lensDirLen = length(lensDirBlend);
                vec2 lensDir = lensDirLen > 1e-5 ? lensDirBlend / lensDirLen : vec2(0.0);
            """;

    private static final String UPSTREAM_D_LENS_BLOCK = """
                float lensRh = refractionHeight;
                float sdIn = min(sdKy, 0.0);
                float dLens = 0.0;
                if ((-sdKy) < lensRh) {
                    dLens = circleMapRealistic(1.0 - (-sdIn / lensRh)) * (-u_lensRefractionPx);
                    dLens *= (1.0 + clamp(u_pressProgress, 0.0, 1.0) * 0.45);
                }
            """;

    private static final String SHAPE_ADAPTIVE_D_LENS_BLOCK = """
                float lensRh = refractionHeight;
                float sdIn = min(sdKy, 0.0);
                // The wide-Dock path uses exact inward SDF distance. Normal glass uses the
                // same continuous elliptical metric as its direction, removing the square
                // zero-refraction core left by nearest-edge rounded-rect distance.
                float lensInwardPx = mix(-sdIn, radialInwardPx, radialRefractionW);
                float dLens = 0.0;
                if (lensInwardPx < lensRh) {
                    dLens = circleMapRealistic(1.0 - (lensInwardPx / lensRh)) * (-u_lensRefractionPx);
                    dLens *= (1.0 + clamp(u_pressProgress, 0.0, 1.0) * 0.45);
                }
            """;

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

    private static final String SINGLE_EDGE_TRANSMITTED_BLOCK = """
                // One transmitted field only. Its direction/distance are shape-adaptive above.
                vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;
                vec2 baseOffset = edgeRefractionUv;
            """;

    private static final String UPSTREAM_CHROMA_DIRECTION =
            "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);";
    private static final String TEXTURE_CHROMA_DIRECTION =
            "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);";

    private static final String UPSTREAM_CHROMA_PUSH =
            "vec2 chromaPush = dispDir * chromaBase * pxNorm;";
    private static final String PIXEL_DOMAIN_CHROMA_PUSH =
            "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;";

    private PrismalSingleEdgeShader() {}

    static String apply(String upstreamFragment) {
        if (upstreamFragment == null) {
            throw new IllegalArgumentException("upstreamFragment == null");
        }
        String corrected = replaceExactlyOnce(
                upstreamFragment,
                UPSTREAM_LENS_DIRECTION,
                SHAPE_ADAPTIVE_LENS_DIRECTION,
                "Prismal lens-direction block");
        corrected = replaceExactlyOnce(
                corrected,
                UPSTREAM_D_LENS_BLOCK,
                SHAPE_ADAPTIVE_D_LENS_BLOCK,
                "Prismal lens-distance block");
        corrected = replaceExactlyOnce(
                corrected,
                UPSTREAM_TRANSMITTED_BLOCK,
                SINGLE_EDGE_TRANSMITTED_BLOCK,
                "Prismal transmitted-refraction block");
        corrected = replaceExactlyOnce(
                corrected,
                UPSTREAM_CHROMA_DIRECTION,
                TEXTURE_CHROMA_DIRECTION,
                "Prismal chromatic direction");
        return replaceExactlyOnce(
                corrected,
                UPSTREAM_CHROMA_PUSH,
                PIXEL_DOMAIN_CHROMA_PUSH,
                "Prismal chromatic scale");
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
