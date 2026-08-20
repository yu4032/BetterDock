package com.hellovoid.liquiddock;

import com.hellovoid.prismal.PrismalParams;

/** Maps LiquidDock configuration into the portable Prismal module without changing model math. */
final class Miuix307PrismalAdapter {
    private Miuix307PrismalAdapter() {}

    static PrismalParams toPortable(Miuix307PrismalMaterial.Params p) {
        if (p == null) return PrismalParams.builder().build();
        PrismalParams.Builder b = PrismalParams.builder();
        b.ior = p.ior;
        b.glassThicknessPx = p.thicknessPx;
        b.normalStrength = p.normalStrength;
        b.displacementScale = p.displacementScale;
        b.heightTransitionWidthPx = p.heightTransitionWidthPx;
        b.sminSmoothingPx = p.sminSmoothingPx;
        b.refractionInsetPx = p.refractionInsetPx;
        b.edgeRefractionFalloff = p.edgeRefractionFalloff;
        b.liquidDome = p.liquidDome;
        b.fresnelReflect = p.fresnelReflect;
        b.lensRefractionScale = p.lensRefractionScale;
        b.lensDepthEffect = p.lensDepthEffect;
        b.chromaticAberration = p.chromaticAberration;
        b.dispersionR = p.dispersionR;
        b.dispersionB = p.dispersionB;
        b.vibrancy = p.vibrancy;
        b.plainHighlight = p.plainHighlight;
        b.brightness = p.brightness;
        b.highlightWidth = p.highlightWidth;
        b.lightDirX = p.lightDirX;
        b.lightDirY = p.lightDirY;
        b.specular = p.specularStrength;
        b.shininess = p.specularSharp;
        b.rimStrength = p.rimLight;
        b.causticIntensity = p.causticIntensity;
        b.shadowSoftness = p.shadowSoftness;
        b.transmittance = p.transmittance;
        b.backdropScaleX = p.backdropScaleX;
        b.backdropScaleY = p.backdropScaleY;
        b.parallaxScale = p.parallaxScale;
        b.blurRadiusPx = p.blurRadiusPx;
        b.tintR = p.tintR;
        b.tintG = p.tintG;
        b.tintB = p.tintB;
        b.tintA = p.tintA;
        b.shadowR = p.shadowR;
        b.shadowG = p.shadowG;
        b.shadowB = p.shadowB;
        b.shadowA = p.shadowA;
        b.showNormals = p.showNormals;
        return b.build();
    }
}
