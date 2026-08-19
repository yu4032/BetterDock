package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.PresetManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Test;

/** Locks Prismal parity except for LiquidDock's intentional chromatic-strength override. */
public class PrismalOfficialParityV3Test {
    private static final float EPS = 0.0001f;

    @Test
    public void materialDefaultsMatchEffectiveOfficialQuickStart() {
        Miuix307PrismalMaterial.Params p = Miuix307PrismalMaterial.defaults(2f);

        assertEquals(1.55f, p.ior, EPS);
        assertEquals(36f, p.thicknessPx, EPS);
        assertEquals(1.15f, p.normalStrength, EPS);
        assertEquals(1.15f, p.displacementScale, EPS);
        assertEquals(38f, p.heightTransitionWidthPx, EPS);
        assertEquals(1.8f, p.sminSmoothingPx, EPS);
        assertEquals(20f, p.refractionInsetPx, EPS);
        assertEquals(4f, p.edgeRefractionFalloff, EPS);

        // PrismalFrameLayout constructor values survive applyBase() because applyBase does not
        // overwrite these controls. These are the effective Quick Start values, not renderer
        // field initializers.
        assertEquals(1.30f, p.liquidDome, EPS);
        assertEquals(1.98f, p.fresnelReflect, EPS);
        assertEquals(1.30f, p.lensRefractionScale, EPS);
        assertEquals(2f, p.chromaticAberration, EPS);
        assertEquals(2f, p.blurRadiusPx, EPS);
    }

    @Test
    public void defaultPresetWritesTheSameOfficialOpticalRecipe() {
        Map<String, Object> p = PresetManager.defaultValues();

        assertEquals(155, p.get("liquid_ior"));
        assertEquals(115, p.get("liquid_normal_strength"));
        assertEquals(130, p.get("liquid_dome"));
        assertEquals(2, p.get("liquid_chromatic"));
        assertEquals(2, p.get("liquid_blur"));
        assertEquals(20, p.get("liquid_blur_tenths"));
        assertEquals(18, p.get("liquid_thickness"));
        assertEquals(180, p.get("liquid_thickness_tenths"));
        assertEquals(1, p.get("liquid_lens_refraction"));
        assertEquals(13, p.get("liquid_lens_refraction_tenths"));
        assertEquals(152, p.get("liquid_specular_strength"));
        assertEquals(122, p.get("liquid_rim_light"));
        assertEquals(0, p.get("liquid_tint_r"));
        assertEquals(0, p.get("liquid_tint_g"));
        assertEquals(255, p.get("liquid_tint_b"));
        assertEquals(35, p.get("liquid_tint_alpha"));

        assertEquals(20, p.get("liquid_prismal_refraction_inset"));
        assertEquals(200, p.get("liquid_prismal_refraction_inset_tenths"));
        assertEquals(115, p.get("liquid_prismal_displacement_scale"));
        assertEquals(19, p.get("liquid_prismal_height_transition_width"));
        assertEquals(190, p.get("liquid_prismal_height_transition_width_tenths"));
        assertEquals(2, p.get("liquid_prismal_smin_smoothing"));
        assertEquals(18, p.get("liquid_prismal_smin_smoothing_tenths"));
        assertEquals(400, p.get("liquid_prismal_edge_refraction_falloff"));
        assertEquals(198, p.get("liquid_prismal_fresnel_reflect"));
        assertEquals(100, p.get("liquid_prismal_dispersion_r"));
        assertEquals(100, p.get("liquid_prismal_dispersion_b"));
        assertEquals(128, p.get("liquid_prismal_vibrancy"));
        assertEquals(8, p.get("liquid_prismal_plain_highlight"));
        assertEquals(-50, p.get("liquid_prismal_light_dir_x"));
        assertEquals(-80, p.get("liquid_prismal_light_dir_y"));
        assertEquals(255, p.get("liquid_prismal_shadow_r"));
        assertEquals(255, p.get("liquid_prismal_shadow_g"));
        assertEquals(255, p.get("liquid_prismal_shadow_b"));
        assertEquals(35, p.get("liquid_prismal_shadow_alpha"));
        assertEquals(1000, p.get("liquid_prismal_shadow_softness"));
        assertEquals(100, p.get("liquid_prismal_transmittance"));
        assertEquals(100, p.get("liquid_prismal_backdrop_scale_x"));
        assertEquals(100, p.get("liquid_prismal_backdrop_scale_y"));
        assertEquals(100, p.get("liquid_prismal_parallax_scale"));
        assertEquals(false, p.get("liquid_prismal_show_normals"));
    }

    @Test
    public void migrationV3CorrectsOnlyTheMistakenV2ParityDefaults() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java"));

        assertTrue(migration.contains("PRISMAL_OFFICIAL_PARITY_V3"));
        assertTrue(migration.contains("migratePrismalOfficialParityV3(preferences)"));
        assertTrue(migration.contains("\"liquid_dome\", 78, 130"));
        assertTrue(migration.contains("\"liquid_prismal_fresnel_reflect\", 100, 198"));
        assertTrue(migration.contains("\"liquid_chromatic\", 0, 2"));
        assertTrue(migration.contains("\"liquid_blur\", 2.5f, 2f"));
        assertTrue(migration.contains("\"liquid_lens_refraction\", 1f, 1.3f"));
    }
}
