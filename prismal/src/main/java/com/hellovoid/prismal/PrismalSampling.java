package com.hellovoid.prismal;

/** Conservative background-pixel reach for the unmodified upstream Prismal model. */
public final class PrismalSampling {
    private static final float BLUR_FBO_SCALE = 0.5f;
    private static final int BLUR_KERNEL_RADIUS = 15;

    private PrismalSampling() {}

    public static int requiredGuardPx(
            PrismalParams p0, float glassWidth, float glassHeight, boolean horizontal) {
        PrismalParams p = p0 != null ? p0 : PrismalParams.builder().build();
        float width = Math.max(1f, glassWidth);
        float height = Math.max(1f, glassHeight);
        float axis = horizontal ? width : height;
        float halfMin = Math.min(width, height) * 0.5f;
        float pxNorm = clamp(halfMin / 108f, 0.36f, 1f)
                + smoothstep(88f, 220f, halfMin) * 0.45f;

        float sampleScale = Math.max(0.01f,
                horizontal ? Math.abs(p.backdropScaleX) : Math.abs(p.backdropScaleY));
        float scaleExpansion = Math.max(0f, 1f / sampleScale - 1f) * axis * 0.5f;

        float dome = clamp(p.liquidDome, 0f, 2f);
        float refractionHeight = Math.max(p.heightTransitionWidthPx * (1f + 0.55f * dome), 1f);
        float lensPx = clamp(
                refractionHeight * 2f * Math.abs(p.displacementScale)
                        * Math.abs(p.lensRefractionScale),
                4f,
                Math.max(4f, Math.min(width, height) * 0.85f));
        float lens = lensPx * 1.45f * 1.12f;
        float parallax = 29f * 0.052f * Math.abs(p.displacementScale)
                * Math.abs(p.parallaxScale) * 1.12f;
        float snell = Math.abs(p.glassThicknessPx) * 0.85f
                * Math.abs(p.displacementScale) * 1.18f * pxNorm;
        float bulge = axis * (0.014f + 0.01f * dome) * pxNorm;
        float dispersion = Math.max(Math.abs(p.dispersionR), Math.abs(p.dispersionB));
        float chromatic = Math.abs(p.chromaticAberration) * 0.0018f
                * dispersion * pxNorm * axis;
        float reflection = 56f * pxNorm;
        float blur = BLUR_KERNEL_RADIUS / BLUR_FBO_SCALE;

        return Math.max(0, (int) Math.ceil(
                scaleExpansion + lens + parallax + snell + bulge
                        + chromatic + reflection + blur + 2f));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) return x < edge0 ? 0f : 1f;
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
