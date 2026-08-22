package com.hellovoid.prismal;

/** Applies the pixel-domain chromatic separation shared by Dock and launcher compact glass. */
final class PrismalPixelDomainChromaShader {
    private static final String UPSTREAM_CHROMA_DIRECTION =
            "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);";
    private static final String TEXTURE_CHROMA_DIRECTION =
            "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);";

    private static final String UPSTREAM_CHROMA_PUSH =
            "vec2 chromaPush = dispDir * chromaBase * pxNorm;";
    private static final String PIXEL_DOMAIN_CHROMA_PUSH =
            "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;";

    private PrismalPixelDomainChromaShader() {}

    static String apply(String fragment) {
        if (fragment == null) throw new IllegalArgumentException("fragment == null");
        String corrected = replaceExactlyOnce(
                fragment,
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
