package com.hellovoid.liquiddock;

import android.opengl.GLES20;

/**
 * Upstream-Prismal optical parameter adapter for the HyperOS 3.0.307 zero-copy backend.
 *
 * The glass equations themselves live in {@link Miuix307PrismalShader}. This class only preserves
 * Prismal's parameter semantics and uploads uniforms. PassBlur/OES coordinate mapping is deliberately
 * kept out of the material so changing compositor geometry cannot alter the optical model.
 */
final class Miuix307PrismalMaterial {
    static final class Params {
        final float ior;
        final float thicknessPx;
        final float normalStrength;
        final float displacementScale;
        final float heightTransitionWidthPx;
        final float sminSmoothingPx;
        final float refractionInsetPx;
        final float edgeRefractionFalloff;
        final float liquidDome;
        final float fresnelReflect;
        final float lensRefractionScale;
        final float chromaticAberration;
        final float dispersionR;
        final float dispersionB;
        final float vibrancy;
        final float plainHighlight;
        final float brightness;
        final float highlightWidth;
        final float lightDirX;
        final float lightDirY;
        final float specularStrength;
        final float specularSharp;
        final float rimLight;
        final float causticIntensity;
        final float shadowSoftness;
        final float transmittance;
        final float backdropScaleX;
        final float backdropScaleY;
        final float parallaxScale;
        final float blurRadiusPx;
        final float tintR;
        final float tintG;
        final float tintB;
        final float tintA;
        final float shadowR;
        final float shadowG;
        final float shadowB;
        final float shadowA;
        final boolean showNormals;

        Params(
                float ior,
                float thicknessPx,
                float normalStrength,
                float displacementScale,
                float heightTransitionWidthPx,
                float sminSmoothingPx,
                float refractionInsetPx,
                float edgeRefractionFalloff,
                float liquidDome,
                float fresnelReflect,
                float lensRefractionScale,
                float chromaticAberration,
                float dispersionR,
                float dispersionB,
                float vibrancy,
                float plainHighlight,
                float brightness,
                float highlightWidth,
                float lightDirX,
                float lightDirY,
                float specularStrength,
                float specularSharp,
                float rimLight,
                float causticIntensity,
                float shadowSoftness,
                float transmittance,
                float backdropScaleX,
                float backdropScaleY,
                float parallaxScale,
                float blurRadiusPx,
                float tintR,
                float tintG,
                float tintB,
                float tintA,
                float shadowR,
                float shadowG,
                float shadowB,
                float shadowA,
                boolean showNormals) {
            this.ior = ior;
            this.thicknessPx = thicknessPx;
            this.normalStrength = normalStrength;
            this.displacementScale = displacementScale;
            this.heightTransitionWidthPx = heightTransitionWidthPx;
            this.sminSmoothingPx = sminSmoothingPx;
            this.refractionInsetPx = refractionInsetPx;
            this.edgeRefractionFalloff = edgeRefractionFalloff;
            this.liquidDome = liquidDome;
            this.fresnelReflect = fresnelReflect;
            this.lensRefractionScale = lensRefractionScale;
            this.chromaticAberration = chromaticAberration;
            this.dispersionR = dispersionR;
            this.dispersionB = dispersionB;
            this.vibrancy = vibrancy;
            this.plainHighlight = plainHighlight;
            this.brightness = brightness;
            this.highlightWidth = highlightWidth;
            this.lightDirX = lightDirX;
            this.lightDirY = lightDirY;
            this.specularStrength = specularStrength;
            this.specularSharp = specularSharp;
            this.rimLight = rimLight;
            this.causticIntensity = causticIntensity;
            this.shadowSoftness = shadowSoftness;
            this.transmittance = transmittance;
            this.backdropScaleX = backdropScaleX;
            this.backdropScaleY = backdropScaleY;
            this.parallaxScale = parallaxScale;
            this.blurRadiusPx = blurRadiusPx;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
            this.tintA = tintA;
            this.shadowR = shadowR;
            this.shadowG = shadowG;
            this.shadowB = shadowB;
            this.shadowA = shadowA;
            this.showNormals = showNormals;
        }
    }

    private Miuix307PrismalMaterial() {}

    /** Current Prismal renderer + PrismalLiquidGlass.applyBase() calibration. */
    static Params defaults(float density) {
        float d = Math.max(0.1f, density);
        return new Params(
                1.55f,
                18f * d,
                1.15f,
                1.15f,
                19f * d,
                1.8f,
                20f,
                4f,
                0.78f,
                1f,
                1f,
                0f,
                1f,
                1f,
                1.28f,
                0.08f,
                1.08f,
                1f,
                -0.5f,
                -0.8f,
                1.52f,
                88f,
                1.22f,
                0.28f,
                10f,
                1f,
                1f,
                1f,
                1f,
                2.5f,
                0f,
                0f,
                1f,
                35f / 255f,
                1f,
                1f,
                1f,
                35f / 255f,
                false);
    }

    /**
     * Live LiquidDock controls mapped one-to-one to Prismal units.
     *
     * `liquid_lens_refraction` historically used 12 as the neutral value. Values above 4 are
     * therefore treated as old persisted data and migrated in-memory by /12. New Prismal-scale
     * values are consumed directly. No legacy depthEffect multiplier is applied: upstream Prismal
     * derives lens depth from normalStrength itself.
     */
    static Params fromConfig(LiquidDockConfig.Glass glass, float density) {
        if (glass == null) return defaults(density);
        float d = Math.max(0.1f, density);
        float lensScale = glass.lensRefraction > 4f
                ? glass.lensRefraction / 12f
                : glass.lensRefraction;
        lensScale = Math.max(0.25f, lensScale);

        return new Params(
                glass.ior,
                Math.max(0f, glass.thickness * d),
                glass.normalStrength,
                glass.prismalDisplacementScale,
                Math.max(1f, glass.prismalHeightTransitionWidth * d),
                Math.max(0f, glass.prismalSminSmoothing),
                Math.max(0f, glass.prismalRefractionInset),
                Math.max(0.05f, glass.prismalEdgeRefractionFalloff),
                glass.dome,
                glass.prismalFresnelReflect,
                lensScale,
                Math.max(0f, glass.chromatic),
                glass.prismalDispersionR,
                glass.prismalDispersionB,
                glass.prismalVibrancy,
                glass.prismalPlainHighlight,
                glass.brightness,
                glass.highlightWidth,
                glass.prismalLightDirX,
                glass.prismalLightDirY,
                glass.specularStrength,
                glass.specularSharp,
                glass.rimLight,
                glass.caustics,
                glass.prismalShadowSoftness,
                glass.prismalTransmittance,
                glass.prismalBackdropScaleX,
                glass.prismalBackdropScaleY,
                glass.prismalParallaxScale,
                Math.max(0f, glass.blur),
                glass.tintR / 255f,
                glass.tintG / 255f,
                glass.tintB / 255f,
                glass.tintAlpha / 255f,
                glass.prismalShadowR / 255f,
                glass.prismalShadowG / 255f,
                glass.prismalShadowB / 255f,
                glass.prismalShadowAlpha / 255f,
                glass.prismalShowNormals);
    }

    static float blurSigma(Params p) {
        Params value = p != null ? p : defaults(1f);
        return Math.max(value.blurRadiusPx * 0.5f, 0.5f);
    }

    static void applyUniforms(
            int program, Params p0, float cornerRadiusPx, int widthPx, int heightPx) {
        Params p = p0 != null ? p0 : defaults(1f);
        float width = Math.max(1, widthPx);
        float height = Math.max(1, heightPx);
        float minGlassDim = Math.min(width, height);
        float refractionHeight = Math.max(
                p.heightTransitionWidthPx * (1f + 0.55f * clamp(p.liquidDome, 0f, 2f)), 1f);
        float lensPx = refractionHeight * 2f * p.displacementScale * p.lensRefractionScale;
        lensPx = clamp(lensPx, 4f, Math.max(4f, minGlassDim * 0.85f));
        float lensDepth = Math.min(1f, Math.max(0f, p.normalStrength * 0.9f));

        uniform2f(program, "u_resolution", width, height);
        uniform2f(program, "u_glassSize", width, height);
        uniform4fRaw(program, "u_cornerRadii",
                cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx);
        uniform1f(program, "u_refractionInset", p.refractionInsetPx);
        uniform1f(program, "u_sminSmoothing", p.sminSmoothingPx);
        uniform1f(program, "u_edgeRefractionFalloff", p.edgeRefractionFalloff);

        uniform1f(program, "u_ior", p.ior);
        uniform1f(program, "u_glassThickness", p.thicknessPx);
        uniform1f(program, "u_normalStrength", p.normalStrength);
        uniform1f(program, "u_displacementScale", p.displacementScale);
        uniform1f(program, "u_heightTransitionWidth", p.heightTransitionWidthPx);
        uniform1f(program, "u_lensRefractionPx", lensPx);
        uniform1f(program, "u_lensDepthEffect", lensDepth);

        uniform1f(program, "u_chromaticAberration", p.chromaticAberration);
        uniform1f(program, "u_dispersionR", p.dispersionR);
        uniform1f(program, "u_dispersionB", p.dispersionB);
        uniform1f(program, "u_vibrancy", p.vibrancy);
        uniform1f(program, "u_plainHighlight", p.plainHighlight);
        uniform1f(program, "u_liquidDome", p.liquidDome);
        uniform1f(program, "u_fresnelReflect", p.fresnelReflect);
        uniform1f(program, "u_brightness", p.brightness);
        uniform4fRaw(program, "u_glassColor", p.tintR, p.tintG, p.tintB, p.tintA);
        uniform1f(program, "u_highlightWidth", p.highlightWidth);

        uniform2f(program, "u_lightDir", p.lightDirX, p.lightDirY);
        uniform1f(program, "u_specular", p.specularStrength);
        uniform1f(program, "u_shininess", p.specularSharp);
        uniform1f(program, "u_rimStrength", p.rimLight);
        uniform4fRaw(program, "u_shadowColor", p.shadowR, p.shadowG, p.shadowB, p.shadowA);
        uniform1f(program, "u_shadowSoftness", p.shadowSoftness);
        uniform1f(program, "u_causticIntensity", p.causticIntensity);
        uniform1f(program, "u_transmittance", p.transmittance);
        uniform2f(program, "u_backdropSampleScale", p.backdropScaleX, p.backdropScaleY);
        uniform1f(program, "u_parallaxScale", p.parallaxScale);

        uniform1f(program, "u_pressProgress", 0f);
        uniform1f(program, "u_backdropPinch", 1f);
        uniform2f(program, "u_glowCenter", 0.5f, 0.5f);
        uniform1f(program, "u_glowStrength", 1f);
        uniform1i(program, "u_showNormals", p.showNormals ? 1 : 0);
    }

    private static void uniform1f(int program, String name, float value) {
        int location = requireUniform(program, name);
        GLES20.glUniform1f(location, value);
    }

    private static void uniform1i(int program, String name, int value) {
        int location = requireUniform(program, name);
        GLES20.glUniform1i(location, value);
    }

    private static void uniform2f(int program, String name, float x, float y) {
        int location = requireUniform(program, name);
        GLES20.glUniform2f(location, x, y);
    }

    private static void uniform4fRaw(int program, String name, float r, float g, float b, float a) {
        int location = requireUniform(program, name);
        GLES20.glUniform4f(location, r, g, b, a);
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing Prismal uniform " + name);
        return location;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}