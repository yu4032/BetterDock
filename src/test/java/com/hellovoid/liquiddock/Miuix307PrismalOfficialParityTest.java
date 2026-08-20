package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import com.hellovoid.liquiddock.config.PresetManager;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Regression tests for effective styropyr0/Prismal v1.0.6 Quick Start parity. */
public class Miuix307PrismalOfficialParityTest {
    private static final float EPS = 0.0001f;

    @Test
    public void defaultPresetMatchesEffectivePrismalV106QuickStart() {
        Map<String, Object> defaults = PresetManager.defaultValues();

        assertEquals(2, defaults.get("liquid_blur"));
        assertEquals(20, defaults.get("liquid_blur_tenths"));
        assertEquals(26, defaults.get("liquid_chromatic"));
        assertEquals(155, defaults.get("liquid_ior"));
        assertEquals(115, defaults.get("liquid_normal_strength"));
        assertEquals(130, defaults.get("liquid_dome"));
        assertEquals(13, defaults.get("liquid_lens_refraction_tenths"));
        assertEquals(0, defaults.get("liquid_depth_effect"));
        assertEquals(108, defaults.get("liquid_brightness"));
        assertEquals(152, defaults.get("liquid_specular_strength"));
        assertEquals(88, defaults.get("liquid_specular_sharp"));
        assertEquals(122, defaults.get("liquid_rim_light"));
        assertEquals(28, defaults.get("liquid_caustics"));
        assertEquals(200, defaults.get("liquid_prismal_refraction_inset_tenths"));
        assertEquals(115, defaults.get("liquid_prismal_displacement_scale"));
        assertEquals(190, defaults.get("liquid_prismal_height_transition_width_tenths"));
        assertEquals(18, defaults.get("liquid_prismal_smin_smoothing_tenths"));
        assertEquals(400, defaults.get("liquid_prismal_edge_refraction_falloff"));
        assertEquals(198, defaults.get("liquid_prismal_fresnel_reflect"));
        assertEquals(-50, defaults.get("liquid_prismal_light_dir_x"));
        assertEquals(-80, defaults.get("liquid_prismal_light_dir_y"));
        assertEquals(255, defaults.get("liquid_prismal_shadow_r"));
        assertEquals(255, defaults.get("liquid_prismal_shadow_g"));
        assertEquals(255, defaults.get("liquid_prismal_shadow_b"));
        assertEquals(35, defaults.get("liquid_prismal_shadow_alpha"));
        assertEquals(1000, defaults.get("liquid_prismal_shadow_softness"));
    }

    @Test
    public void zeroDepthControlUsesUpstreamNormalDerivedLensDepth() {
        Map<String, Object> values = new HashMap<>(PresetManager.defaultValues());
        values.put("liquid_depth_effect", 0);

        LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(new ConfigReader(values));
        Miuix307PrismalMaterial.Params params = Miuix307PrismalMaterial.fromConfig(glass, 1f);

        // PrismalGlassRenderer uploads clamp(normalStrength * 0.9, 0, 1).
        assertEquals(1.0f, params.lensDepthEffect, EPS);

        values.put("liquid_depth_effect", 35);
        glass = new LiquidDockConfig.Glass(new ConfigReader(values));
        params = Miuix307PrismalMaterial.fromConfig(glass, 1f);
        assertEquals(0.35f, params.lensDepthEffect, EPS);
    }

    @Test
    public void partialGlassConfigKeepsCanonicalQuickStartFallbacks() {
        Map<String, Object> partial = new HashMap<>();
        partial.put("liquid_glass", true);
        partial.put("liquid_brightness", 109);

        LiquidDockConfig.Glass glass = new LiquidDockConfig.Glass(new ConfigReader(partial));
        Miuix307PrismalMaterial.Params params = Miuix307PrismalMaterial.fromConfig(glass, 1f);

        assertEquals(2f, params.blurRadiusPx, EPS);
        assertEquals(26f, params.chromaticAberration, EPS);
        assertEquals(1.55f, params.ior, EPS);
        assertEquals(18f, params.thicknessPx, EPS);
        assertEquals(1.15f, params.normalStrength, EPS);
        assertEquals(1.15f, params.displacementScale, EPS);
        assertEquals(19f, params.heightTransitionWidthPx, EPS);
        assertEquals(1.8f, params.sminSmoothingPx, EPS);
        assertEquals(20f, params.refractionInsetPx, EPS);
        assertEquals(4f, params.edgeRefractionFalloff, EPS);
        assertEquals(1.30f, params.liquidDome, EPS);
        assertEquals(1.98f, params.fresnelReflect, EPS);
        assertEquals(1.30f, params.lensRefractionScale, EPS);
        assertEquals(1.0f, params.lensDepthEffect, EPS);
        assertEquals(1.09f, params.brightness, EPS);
        assertEquals(1.52f, params.specularStrength, EPS);
        assertEquals(88f, params.specularSharp, EPS);
        assertEquals(1.22f, params.rimLight, EPS);
        assertEquals(0.28f, params.causticIntensity, EPS);
        assertEquals(-0.5f, params.lightDirX, EPS);
        assertEquals(-0.8f, params.lightDirY, EPS);
        assertEquals(10f, params.shadowSoftness, EPS);
        assertEquals(35f / 255f, params.shadowA, EPS);
        assertEquals(35f / 255f, params.tintA, EPS);
        assertEquals(0f, params.tintR, EPS);
        assertEquals(0f, params.tintG, EPS);
        assertEquals(1f, params.tintB, EPS);
    }
}
