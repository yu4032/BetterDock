package com.hellovoid.liquiddock.config;

import android.content.Context;

import com.hellovoid.liquiddock.HomeGridProfile;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigMigration {
    private static final String PRISMAL_PARITY_V2 = "liquid_prismal_parity_v2";
    private static final String PRISMAL_OFFICIAL_PARITY_V3 =
            "liquid_prismal_official_parity_v3";
    private static final String CAPTURE_BLEED_PIXELS_V4 =
            "liquid_capture_bleed_pixels_v4";

    private ConfigMigration() { }

    static final class GridProfileState {
        final boolean enabled;
        final String profile;

        GridProfileState(boolean enabled, String profile) {
            this.enabled = enabled;
            this.profile = profile;
        }
    }

    static GridProfileState resolveGridProfileState(
            Boolean canonicalEnabled, String canonicalProfile, Boolean legacyEnabled) {
        boolean enabled = canonicalEnabled != null
                ? canonicalEnabled : legacyEnabled != null && legacyEnabled;
        String profile = HomeGridProfile.fromPersisted(canonicalProfile).persistedValue();
        return new GridProfileState(enabled, profile);
    }

    public static void migrate(Context context, SharedPreferences preferences) {
        migrateGridProfile(preferences);
        migrateMergedHorizontal(preferences);
        migrateLegacyGridKeys(preferences);
        migrateGridToDp(context, preferences);
        migrateGridToOffsets(preferences);
        migrateCornersToDp(context, preferences);
        migrateCaptureBleedToPixels(context, preferences);
        migrateLiquidDimensionsToDp(context, preferences);
        migrateDockDimensionsToDp(context, preferences);
        migrateAxisDistances(preferences);
        migratePrismalParityV2(preferences);
        migratePrismalOfficialParityV3(preferences);
    }

    private static void migrateGridProfile(SharedPreferences sp) {
        String enabledKey = ConfigSchema.Grid.ENABLED.name();
        String profileKey = ConfigSchema.Grid.PROFILE.name();
        String legacyKey = ConfigSchema.Grid.LEGACY_8X4.name();

        Boolean canonicalEnabled = sp.contains(enabledKey)
                ? sp.getBoolean(enabledKey, false) : null;
        String canonicalProfile = sp.contains(profileKey)
                ? sp.getString(profileKey, ConfigSchema.Grid.PROFILE.uiDefault()) : null;
        Boolean legacyEnabled = sp.contains(legacyKey)
                ? sp.getBoolean(legacyKey, false) : null;
        GridProfileState state = resolveGridProfileState(
                canonicalEnabled, canonicalProfile, legacyEnabled);

        SharedPreferences.Editor editor = sp.edit();
        boolean changed = false;
        if (canonicalEnabled == null || canonicalEnabled != state.enabled) {
            editor.putBoolean(enabledKey, state.enabled);
            changed = true;
        }
        if (canonicalProfile == null || !state.profile.equals(canonicalProfile)) {
            editor.putString(profileKey, state.profile);
            changed = true;
        }
        if (sp.contains(legacyKey)) {
            editor.remove(legacyKey);
            changed = true;
        }
        if (changed) editor.commit();
    }

    /**
     * One-time migration from the first PassBlur/Prismal adapter defaults to the V2 upstream
     * recipe. V3 below corrects the handful of values that V2 incorrectly took from renderer
     * field initializers instead of the effective PrismalFrameLayout + applyBase() state.
     */
    private static void migratePrismalParityV2(SharedPreferences sp) {
        if (sp.getBoolean(PRISMAL_PARITY_V2, false)) return;
        SharedPreferences.Editor e = sp.edit();

        migrateDpDefault(sp, e, "liquid_blur", 6f, 2.5f);
        migrateIntDefault(sp, e, "liquid_chromatic", 8, 0);
        migrateIntDefault(sp, e, "liquid_tint_alpha", 38, 35);
        migrateIntDefault(sp, e, "liquid_tint_r", 238, 0);
        migrateIntDefault(sp, e, "liquid_tint_g", 244, 0);
        migrateIntDefault(sp, e, "liquid_tint_b", 255, 255);
        migrateIntDefault(sp, e, "liquid_dome", 100, 78);
        migrateLegacyLensScale(sp, e);
        migrateIntDefault(sp, e, "liquid_specular_strength", 105, 152);
        migrateIntDefault(sp, e, "liquid_rim_light", 100, 122);

        migrateDpDefault(sp, e, "liquid_prismal_refraction_inset", 5f, 20f);
        migrateIntDefault(sp, e, "liquid_prismal_displacement_scale", 100, 115);
        migrateDpDefault(sp, e, "liquid_prismal_height_transition_width", 15f, 19f);
        migrateDpDefault(sp, e, "liquid_prismal_smin_smoothing", 2f, 1.8f);
        migrateIntDefault(sp, e, "liquid_prismal_edge_refraction_falloff", 200, 400);
        migrateIntDefault(sp, e, "liquid_prismal_fresnel_reflect", 79, 100);
        migrateIntDefault(sp, e, "liquid_prismal_light_dir_x", 100, -50);
        migrateIntDefault(sp, e, "liquid_prismal_light_dir_y", 62, -80);
        migrateIntDefault(sp, e, "liquid_prismal_shadow_r", 0, 255);
        migrateIntDefault(sp, e, "liquid_prismal_shadow_g", 0, 255);
        migrateIntDefault(sp, e, "liquid_prismal_shadow_b", 0, 255);
        migrateIntDefault(sp, e, "liquid_prismal_shadow_alpha", 0, 35);
        migrateIntDefault(sp, e, "liquid_prismal_shadow_softness", 100, 1000);

        e.putBoolean(PRISMAL_PARITY_V2, true).commit();
    }

    /**
     * Correct V2 values that were copied from PrismalGlassRenderer's private initializers.
     * Prismal's public Quick Start constructs PrismalFrameLayout first and then calls applyBase();
     * applyBase intentionally does not overwrite these five controls. Only absent values or exact
     * V2 defaults are changed, so ordinary user overrides survive the correction.
     */
    private static void migratePrismalOfficialParityV3(SharedPreferences sp) {
        if (sp.getBoolean(PRISMAL_OFFICIAL_PARITY_V3, false)) return;
        SharedPreferences.Editor e = sp.edit();

        migrateDpDefault(sp, e, "liquid_blur", 2.5f, 2f);
        migrateIntDefault(sp, e, "liquid_chromatic", 0, 2);
        migrateIntDefault(sp, e, "liquid_dome", 78, 130);
        migrateDpDefault(sp, e, "liquid_lens_refraction", 1f, 1.3f);
        migrateIntDefault(sp, e, "liquid_prismal_fresnel_reflect", 100, 198);

        e.putBoolean(PRISMAL_OFFICIAL_PARITY_V3, true).commit();
    }

    private static void migrateLegacyLensScale(
            SharedPreferences sp, SharedPreferences.Editor e) {
        String key = "liquid_lens_refraction";
        boolean present = sp.contains(key) || sp.contains(key + "_tenths");
        float oldValue;
        if (sp.contains(key + "_tenths")) {
            oldValue = sp.getInt(key + "_tenths", 120) / 10f;
        } else {
            oldValue = sp.getInt(key, 12);
        }
        // The first PassBlur adapter interpreted this control as value / 12. Preserve the exact
        // optical meaning for every legacy custom value, not only for the neutral default 12.
        float prismalScale = present ? prismalLensScale(oldValue) : 1f;
        putDpPreference(e, key, prismalScale);
    }

    static float prismalLensScale(float legacyValue) {
        return Math.max(0.25f, legacyValue / 12f);
    }

    private static void migrateIntDefault(
            SharedPreferences sp, SharedPreferences.Editor e,
            String key, int oldDefault, int newDefault) {
        if (!sp.contains(key) || sp.getInt(key, oldDefault) == oldDefault) {
            e.putInt(key, newDefault);
        }
    }

    private static void migrateDpDefault(
            SharedPreferences sp, SharedPreferences.Editor e,
            String key, float oldDefault, float newDefault) {
        boolean present = sp.contains(key) || sp.contains(key + "_tenths");
        float current;
        if (sp.contains(key + "_tenths")) {
            current = sp.getInt(key + "_tenths", Math.round(oldDefault * 10f)) / 10f;
        } else {
            current = sp.getInt(key, Math.round(oldDefault));
        }
        if (!present || Math.abs(current - oldDefault) <= 0.0001f) {
            putDpPreference(e, key, newDefault);
        }
    }

    private static void migrateAxisDistances(SharedPreferences sp) {
        SharedPreferences.Editor e = sp.edit();
        boolean changed = false;
        if (!sp.contains("grid_landscape_horizontal_distance")) {
            float left = readDpPreference(sp, "grid_landscape_margin_left");
            float right = readDpPreference(sp, "grid_landscape_margin_right");
            putDpPreference(e, "grid_landscape_horizontal_distance", axisDistance(left, right));
            changed = true;
        }
        changed |= migrateAxisValue(sp, e, "grid_landscape_top_distance",
                "grid_landscape_margin_top", null);
        changed |= migrateAxisValue(sp, e, "grid_landscape_bottom_distance",
                "grid_landscape_margin_bottom", null);
        changed |= migrateAxisValue(sp, e, "grid_portrait_horizontal_distance",
                "grid_portrait_margin_left", "grid_portrait_margin_right");
        changed |= migrateAxisValue(sp, e, "grid_portrait_top_distance",
                "grid_portrait_margin_top", null);
        changed |= migrateAxisValue(sp, e, "grid_portrait_bottom_distance",
                "grid_portrait_margin_bottom", null);
        if (changed) e.commit();
    }

    private static boolean migrateAxisValue(SharedPreferences sp, SharedPreferences.Editor e,
                                            String target, String sourceA, String sourceB) {
        if (sp.contains(target)) return false;
        float value = axisDistance(readDpPreference(sp, sourceA),
                sourceB == null ? null : readDpPreference(sp, sourceB));
        putDpPreference(e, target, value);
        return true;
    }

    private static float readDpPreference(SharedPreferences sp, String key) {
        String tenths = key + "_tenths";
        return sp.contains(tenths) ? sp.getInt(tenths, 0) / 10f : sp.getInt(key, 0);
    }

    private static void putDpPreference(SharedPreferences.Editor e, String key, float value) {
        e.putInt(key, directDpValue(value));
        e.putInt(key + "_tenths", tenthsDpValue(value));
    }

    private static void migrateDockDimensionsToDp(Context context, SharedPreferences sp) {
        if (sp.getBoolean("dock_dimensions_dp", false)) return;
        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        String[] keys = {"height_offset", "width_offset", "dock_spacing", "dock_bottom_offset",
                "indicator_landscape_y", "indicator_portrait_y", "sq_stroke_w", "sq_stroke_off",
                "stroke_w", "std_stroke_w", "dock_shadow_radius", "dock_shadow_size",
                "dock_shadow_y", "shadow_radius"};
        int[] defaults = {0, 0, 0, 0, 0, 0, 4, 8, 2, 4, 42, 52, 12, 8};
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < keys.length; i++) {
            e.putInt(keys[i], Math.round(sp.getInt(keys[i], defaults[i]) / density));
        }
        e.putBoolean("dock_dimensions_dp", true).commit();
    }

    private static void migrateCaptureBleedToPixels(Context context, SharedPreferences sp) {
        if (sp.getBoolean(CAPTURE_BLEED_PIXELS_V4, false)) return;
        float density = Math.max(0.1f, context.getResources().getDisplayMetrics().density);
        boolean storedAsDp = sp.getBoolean("liquid_dimensions_dp", false);
        SharedPreferences.Editor e = sp.edit();
        migrateCaptureBleedPixelValue(
                sp, e, "liquid_capture_bleed_top", 48, storedAsDp, density);
        migrateCaptureBleedPixelValue(
                sp, e, "liquid_capture_bleed_bottom", 16, storedAsDp, density);
        e.putBoolean(CAPTURE_BLEED_PIXELS_V4, true).commit();
    }

    private static void migrateCaptureBleedPixelValue(
            SharedPreferences sp, SharedPreferences.Editor e, String key,
            int historicalDefaultPx, boolean storedAsDp, float density) {
        boolean hasDirect = sp.contains(key);
        boolean hasTenths = sp.contains(key + "_tenths");
        if (!hasDirect && !hasTenths) {
            e.remove(key + "_tenths");
            return;
        }
        float storedValue = hasTenths
                ? sp.getInt(key + "_tenths", historicalDefaultPx * 10) / 10f
                : sp.getInt(key, historicalDefaultPx);
        e.putInt(key, captureBleedPixels(
                storedValue, storedAsDp, density, historicalDefaultPx));
        e.remove(key + "_tenths");
    }

    static int captureBleedPixels(
            float storedValue, boolean storedAsDp, float density, int historicalDefaultPx) {
        float safeDensity = Math.max(0.1f, density);
        float pixels = storedValue;
        if (storedAsDp) {
            float oldMigratedDefaultDp = Math.round(historicalDefaultPx / safeDensity);
            pixels = Math.abs(storedValue - oldMigratedDefaultDp) <= 0.0001f
                    ? historicalDefaultPx
                    : storedValue * safeDensity;
        }
        return Math.max(0, Math.min(256, Math.round(pixels)));
    }

    private static void migrateLiquidDimensionsToDp(Context context, SharedPreferences sp) {
        if (sp.getBoolean("liquid_dimensions_dp", false)) return;
        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        SharedPreferences.Editor e = sp.edit();
        e.putInt("liquid_blur", Math.round(sp.getInt("liquid_blur", 6) / density));
        e.putBoolean("liquid_dimensions_dp", true).commit();
    }

    private static void migrateMergedHorizontal(SharedPreferences sp) {
        SharedPreferences.Editor editor = sp.edit();
        boolean changed = copyMergedValueIfMissing(sp, editor,
            "grid_landscape_margin_horizontal", "grid_landscape_margin_left");
        changed |= copyMergedValueIfMissing(sp, editor,
            "grid_landscape_margin_horizontal", "grid_landscape_margin_right");
        changed |= copyMergedValueIfMissing(sp, editor,
            "grid_portrait_margin_horizontal", "grid_portrait_margin_left");
        changed |= copyMergedValueIfMissing(sp, editor,
            "grid_portrait_margin_horizontal", "grid_portrait_margin_right");
        if (changed) editor.commit();
    }

    private static boolean copyMergedValueIfMissing(SharedPreferences sp,
                                                    SharedPreferences.Editor editor,
                                                    String source, String destination) {
        if (!sp.contains(source) || sp.contains(destination)) return false;
        editor.putInt(destination, sp.getInt(source, 0));
        return true;
    }

    private static void migrateLegacyGridKeys(SharedPreferences sp) {
        if (!sp.contains("grid_landscape_margin_left")) {
            int left = sp.getInt("grid_margin_left", 160);
            int right = sp.getInt("grid_margin_right", 160);
            int top = sp.getInt("grid_margin_top", 80);
            int bottom = sp.getInt("grid_margin_bottom", 80);
            Map<String, Integer> values = legacyGridPlacements(left, right, top, bottom);
            SharedPreferences.Editor editor = sp.edit();
            for (Map.Entry<String, Integer> value : values.entrySet()) {
                editor.putInt(value.getKey(), value.getValue());
            }
            editor.commit();
        }
    }

    private static void migrateGridToDp(Context context, SharedPreferences sp) {
        if (!sp.getBoolean("grid_margins_dp", false)) {
            float density = context.getResources().getDisplayMetrics().density;
            String[] keys = {
                "grid_landscape_margin_left", "grid_landscape_margin_right",
                "grid_landscape_margin_top", "grid_landscape_margin_bottom",
                "grid_portrait_margin_left", "grid_portrait_margin_right",
                "grid_portrait_margin_top", "grid_portrait_margin_bottom"
            };
            SharedPreferences.Editor e = sp.edit();
            for (String key : keys) {
                int px = sp.getInt(key, key.contains("top") || key.contains("bottom") ? 80 : 160);
                e.putInt(key, gridDpValue(px, density));
            }
            e.putBoolean("grid_margins_dp", true).commit();
        }
    }

    private static void migrateGridToOffsets(SharedPreferences sp) {
        if (!sp.getBoolean("grid_margins_offset", false)) {
            sp.edit()
                .putInt("grid_landscape_margin_left", gridOffset("grid_landscape_margin_left",
                        sp.getInt("grid_landscape_margin_left", 57)))
                .putInt("grid_landscape_margin_right", gridOffset("grid_landscape_margin_right",
                        sp.getInt("grid_landscape_margin_right", 57)))
                .putInt("grid_landscape_margin_top", gridOffset("grid_landscape_margin_top",
                        sp.getInt("grid_landscape_margin_top", 28)))
                .putInt("grid_landscape_margin_bottom", gridOffset("grid_landscape_margin_bottom",
                        sp.getInt("grid_landscape_margin_bottom", 28)))
                .putInt("grid_portrait_margin_left", gridOffset("grid_portrait_margin_left",
                        sp.getInt("grid_portrait_margin_left", 28)))
                .putInt("grid_portrait_margin_right", gridOffset("grid_portrait_margin_right",
                        sp.getInt("grid_portrait_margin_right", 28)))
                .putInt("grid_portrait_margin_top", gridOffset("grid_portrait_margin_top",
                        sp.getInt("grid_portrait_margin_top", 57)))
                .putInt("grid_portrait_margin_bottom", gridOffset("grid_portrait_margin_bottom",
                        sp.getInt("grid_portrait_margin_bottom", 57)))
                .putInt("grid_landscape_row_gap", gridOffset("grid_landscape_row_gap",
                        sp.getInt("grid_landscape_row_gap", 1)))
                .putInt("grid_portrait_row_gap", gridOffset("grid_portrait_row_gap",
                        sp.getInt("grid_portrait_row_gap", 1)))
                .putBoolean("grid_margins_offset", true).commit();
        }
    }

    private static void migrateCornersToDp(Context context, SharedPreferences sp) {
        if (!sp.getBoolean("corners_dp", false)) {
            float density = context.getResources().getDisplayMetrics().density;
            SharedPreferences.Editor corners = sp.edit();
            corners.putInt("corner_offset", sp.contains("corner_offset")
                ? Math.round(sp.getInt("corner_offset", -1) / density) : -1);
            corners.putInt("blur_corner_offset", sp.contains("blur_corner_offset")
                ? Math.round(sp.getInt("blur_corner_offset", 0) / density) : 0);
            corners.putBoolean("corners_dp", true).apply();
        }
    }

    static Map<String, Integer> legacyGridPlacements(int left, int right, int top, int bottom) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("grid_landscape_margin_left", left);
        values.put("grid_landscape_margin_right", right);
        values.put("grid_landscape_margin_top", top);
        values.put("grid_landscape_margin_bottom", bottom);
        values.put("grid_portrait_margin_left", top);
        values.put("grid_portrait_margin_right", bottom);
        values.put("grid_portrait_margin_top", right);
        values.put("grid_portrait_margin_bottom", left);
        return values;
    }

    static int gridDpValue(int px, float density) {
        return Math.max(-600, Math.min(600, Math.round(px / density)));
    }

    static int gridOffset(String key, int value) {
        switch (key) {
            case "grid_landscape_margin_left":
            case "grid_landscape_margin_right":
            case "grid_portrait_margin_top":
            case "grid_portrait_margin_bottom":
                return value - 57;
            case "grid_landscape_margin_top":
            case "grid_landscape_margin_bottom":
            case "grid_portrait_margin_left":
            case "grid_portrait_margin_right":
                return value - 28;
            case "grid_landscape_row_gap":
            case "grid_portrait_row_gap":
                return value - 1;
            default:
                throw new IllegalArgumentException("Unknown grid offset key: " + key);
        }
    }

    static float axisDistance(float sourceA, Float sourceB) {
        return sourceB == null ? sourceA : (sourceA + sourceB) / 2f;
    }

    static int directDpValue(float value) {
        return Math.round(value);
    }

    static int tenthsDpValue(float value) {
        return Math.round(value * 10f);
    }
}
