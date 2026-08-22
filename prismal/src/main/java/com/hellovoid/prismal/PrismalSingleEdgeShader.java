package com.hellovoid.prismal;

/**
 * Applies LiquidDock's narrow transmitted-refraction correction to the vendored Prismal shader.
 *
 * <p>The upstream source is intentionally kept byte-for-byte as the provenance baseline. Prismal's
 * stock fragment adds lens/parallax, Snell and a mid-band bulge into the sampled backdrop. On the
 * extremely wide Dock glass that produces three spatial refraction bands. Its center-radial lens
 * bias also makes the straight-edge normal component position-dependent, and its chromatic offset
 * is expressed directly in normalized UV so horizontal fringe width grows with framebuffer width.
 * This correction keeps one monotonic SDF edge lens, aligns it to the local edge normal, and scales
 * chromatic separation in short-axis pixels before converting back to UV. Normals, dome, Fresnel,
 * highlights, caustics, tint and blur remain upstream Prismal behavior.</p>
 */
final class PrismalSingleEdgeShader {
    private static final String UPSTREAM_LENS_DIRECTION = """
                vec2 cenSafe = cKy + vec2(1e-4, 1e-4);
                vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);
                float ldLen = length(lensDir);
                lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);
            """;

    /** Folder/widget optics may radialize only the deep interior before this Dock correction runs. */
    private static final String SIZE_ADAPTIVE_LENS_DIRECTION = """
                vec2 lensDir = opticalDir + u_lensDepthEffect * opticalRadial;
                float ldLen = length(lensDir);
                lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);
            """;

    private static final String EDGE_NORMAL_LENS_DIRECTION = """
                // A straight Dock edge must have one translation-invariant refraction direction.
                // Keep the transmitted lens on the local SDF normal instead of biasing it toward
                // the center of an extremely wide glass rectangle.
                vec2 lensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);
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

    private static final String SIZE_ADAPTIVE_TRANSMITTED_BLOCK =
            UPSTREAM_TRANSMITTED_BLOCK.replace(
                    "vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;",
                    "vec2 parallax = (opticalDir * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;");

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

    private static final String UPSTREAM_CHROMA_PUSH =
            "vec2 chromaPush = dispDir * chromaBase * pxNorm;";
    private static final String PIXEL_DOMAIN_CHROMA_PUSH =
            "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;";

    private PrismalSingleEdgeShader() {}

    static String apply(String upstreamFragment) {
        if (upstreamFragment == null) {
            throw new IllegalArgumentException("upstreamFragment == null");
        }
        String corrected = replaceEitherExactlyOnce(
                upstreamFragment,
                UPSTREAM_LENS_DIRECTION,
                SIZE_ADAPTIVE_LENS_DIRECTION,
                EDGE_NORMAL_LENS_DIRECTION,
                "Prismal lens-direction block");
        corrected = replaceEitherExactlyOnce(
                corrected,
                UPSTREAM_TRANSMITTED_BLOCK,
                SIZE_ADAPTIVE_TRANSMITTED_BLOCK,
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

    private static String replaceEitherExactlyOnce(
            String source,
            String oldTextA,
            String oldTextB,
            String newText,
            String label) {
        int firstA = source.indexOf(oldTextA);
        int firstB = source.indexOf(oldTextB);
        if ((firstA >= 0) == (firstB >= 0)) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        String oldText = firstA >= 0 ? oldTextA : oldTextB;
        int first = firstA >= 0 ? firstA : firstB;
        if (source.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText + source.substring(first + oldText.length());
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
