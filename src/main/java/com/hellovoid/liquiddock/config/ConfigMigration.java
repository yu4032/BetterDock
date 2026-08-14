package com.hellovoid.liquiddock.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigMigration {
    private ConfigMigration() { }

    public static void migrate(Context context, SharedPreferences preferences) {
        migrateMergedHorizontal(preferences);
        migrateLegacyGridKeys(preferences);
        migrateGridToDp(context, preferences);
        migrateGridToOffsets(preferences);
        migrateCornersToDp(context, preferences);
        migrateLiquidDimensionsToDp(context, preferences);
        migrateDockDimensionsToDp(context, preferences);
        migrateAxisDistances(preferences);
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

    private static void migrateLiquidDimensionsToDp(Context context, SharedPreferences sp) {
        if (sp.getBoolean("liquid_dimensions_dp", false)) return;
        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);
        SharedPreferences.Editor e = sp.edit();
        e.putInt("liquid_blur", Math.round(sp.getInt("liquid_blur", 6) / density));
        e.putInt("liquid_capture_bleed_top",
                Math.round(sp.getInt("liquid_capture_bleed_top", 48) / density));
        e.putInt("liquid_capture_bleed_bottom",
                Math.round(sp.getInt("liquid_capture_bleed_bottom", 16) / density));
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
