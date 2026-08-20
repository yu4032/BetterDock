package com.hellovoid.prismal;

/**
 * Applies LiquidDock's narrow transmitted-refraction correction to the vendored Prismal shader.
 *
 * <p>The upstream source is intentionally kept byte-for-byte as the provenance baseline. Prismal's
 * stock fragment adds lens/parallax, Snell and a mid-band bulge into the sampled backdrop. On the
 * extremely wide Dock glass that produces three spatial refraction bands, while some vertical
 * sampled-UV directions are expressed in the opposite Y basis. This correction changes only the
 * transmitted backdrop displacement: one monotonic SDF edge lens remains, and chromatic radial
 * direction is expressed in the same Y-down texture basis as {@code v_screenTexCoord}. Normals,
 * dome, Fresnel, highlights, caustics, tint and blur remain upstream Prismal behavior.</p>
 */
final class PrismalSingleEdgeShader {
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
                // LiquidDock's Dock glass uses one spatial refraction field. dLens is maximal at
                // the silhouette and monotonically decays to zero over refractionHeight.
                vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;
                vec2 baseOffset = edgeRefractionUv;
            """;

    private static final String UPSTREAM_CHROMA_DIRECTION =
            "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);";
    private static final String TEXTURE_CHROMA_DIRECTION =
            "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);";

    private PrismalSingleEdgeShader() {}

    static String apply(String upstreamFragment) {
        if (upstreamFragment == null) {
            throw new IllegalArgumentException("upstreamFragment == null");
        }
        String corrected = replaceExactlyOnce(
                upstreamFragment,
                UPSTREAM_TRANSMITTED_BLOCK,
                SINGLE_EDGE_TRANSMITTED_BLOCK,
                "Prismal transmitted-refraction block");
        return replaceExactlyOnce(
                corrected,
                UPSTREAM_CHROMA_DIRECTION,
                TEXTURE_CHROMA_DIRECTION,
                "Prismal chromatic direction");
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
