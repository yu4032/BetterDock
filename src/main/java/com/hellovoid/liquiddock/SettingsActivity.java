package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.graphics.Color;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import org.json.JSONObject;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> exportConfigLauncher =
        registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
            uri -> { if (uri != null) exportCurrentParameters(uri); });
    private final ActivityResultLauncher<String[]> importConfigLauncher =
        registerForActivityResult(new ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) importParameters(uri); });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        migratePreferences();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Window w = getWindow();
        // Status bar must follow the system night mode (and Miuix Monet theme): a hardcoded
        // color breaks HyperOS 3 — light mode shows dark icons on a dark bar, dark mode
        // shows white icons on a light bar.  Adapt bar color + icon appearance together.
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean night = uiMode == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            w.setStatusBarColor(Color.parseColor("#1A1A1E"));
            w.getDecorView().setSystemUiVisibility(0); // white icons on dark bar
        } else {
            w.setStatusBarColor(Color.parseColor("#F2F2F7"));
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        if (useLegacyPreferenceUi()) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    protected boolean useLegacyPreferenceUi() { return true; }

    private void migratePreferences() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        migrateMergedHorizontal(sp);
        migrateLegacyGridKeys(sp);
        migrateGridToDp(sp);
        migrateGridToOffsets(sp);
        migrateCornersToDp(sp);
        migrateLiquidDimensionsToDp(sp);
        migrateDockDimensionsToDp(sp);
        migrateAxisDistances(sp);
    }

    private void migrateAxisDistances(SharedPreferences sp) {
        SharedPreferences.Editor e = sp.edit();
        boolean changed = false;
        if (!sp.contains("grid_landscape_horizontal_distance")) {
            float left = readDpPreference(sp, "grid_landscape_margin_left");
            float right = readDpPreference(sp, "grid_landscape_margin_right");
            putDpPreference(e, "grid_landscape_horizontal_distance", (left + right) / 2f);
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
        float value = readDpPreference(sp, sourceA);
        if (sourceB != null) value = (value + readDpPreference(sp, sourceB)) / 2f;
        putDpPreference(e, target, value);
        return true;
    }

    private static float readDpPreference(SharedPreferences sp, String key) {
        String tenths = key + "_tenths";
        return sp.contains(tenths) ? sp.getInt(tenths, 0) / 10f : sp.getInt(key, 0);
    }

    private static void putDpPreference(SharedPreferences.Editor e, String key, float value) {
        e.putInt(key, Math.round(value));
        e.putInt(key + "_tenths", Math.round(value * 10f));
    }

    private void migrateDockDimensionsToDp(SharedPreferences sp) {
        if (sp.getBoolean("dock_dimensions_dp", false)) return;
        float density = Math.max(1f, getResources().getDisplayMetrics().density);
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

    private void migrateLiquidDimensionsToDp(SharedPreferences sp) {
        if (sp.getBoolean("liquid_dimensions_dp", false)) return;
        float density = Math.max(1f, getResources().getDisplayMetrics().density);
        SharedPreferences.Editor e = sp.edit();
        e.putInt("liquid_blur", Math.round(sp.getInt("liquid_blur", 6) / density));
        e.putInt("liquid_capture_bleed_top",
                Math.round(sp.getInt("liquid_capture_bleed_top", 48) / density));
        e.putInt("liquid_capture_bleed_bottom",
                Math.round(sp.getInt("liquid_capture_bleed_bottom", 16) / density));
        e.putBoolean("liquid_dimensions_dp", true).commit();
    }

    private void migrateMergedHorizontal(SharedPreferences sp) {
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

    private boolean copyMergedValueIfMissing(SharedPreferences sp,
                                             SharedPreferences.Editor editor,
                                             String source, String destination) {
        if (!sp.contains(source) || sp.contains(destination)) return false;
        editor.putInt(destination, sp.getInt(source, 0));
        return true;
    }

    private void migrateLegacyGridKeys(SharedPreferences sp) {
        if (!sp.contains("grid_landscape_margin_left")) {
            int left = sp.getInt("grid_margin_left", 160);
            int right = sp.getInt("grid_margin_right", 160);
            int top = sp.getInt("grid_margin_top", 80);
            int bottom = sp.getInt("grid_margin_bottom", 80);
            sp.edit().putInt("grid_landscape_margin_left", left)
                .putInt("grid_landscape_margin_right", right)
                .putInt("grid_landscape_margin_top", top)
                .putInt("grid_landscape_margin_bottom", bottom)
                .putInt("grid_portrait_margin_left", top)
                .putInt("grid_portrait_margin_right", bottom)
                .putInt("grid_portrait_margin_top", right)
                .putInt("grid_portrait_margin_bottom", left).commit();
        }
    }

    private void migrateGridToDp(SharedPreferences sp) {
        if (!sp.getBoolean("grid_margins_dp", false)) {
            float density = getResources().getDisplayMetrics().density;
            String[] keys = {
                "grid_landscape_margin_left", "grid_landscape_margin_right",
                "grid_landscape_margin_top", "grid_landscape_margin_bottom",
                "grid_portrait_margin_left", "grid_portrait_margin_right",
                "grid_portrait_margin_top", "grid_portrait_margin_bottom"
            };
            SharedPreferences.Editor e = sp.edit();
            for (String key : keys) {
                int px = sp.getInt(key, key.contains("top") || key.contains("bottom") ? 80 : 160);
                e.putInt(key, Math.max(-600, Math.min(600, Math.round(px / density))));
            }
            e.putBoolean("grid_margins_dp", true).commit();
        }
    }

    private void migrateGridToOffsets(SharedPreferences sp) {
        if (!sp.getBoolean("grid_margins_offset", false)) {
            sp.edit()
                .putInt("grid_landscape_margin_left", sp.getInt("grid_landscape_margin_left", 57) - 57)
                .putInt("grid_landscape_margin_right", sp.getInt("grid_landscape_margin_right", 57) - 57)
                .putInt("grid_landscape_margin_top", sp.getInt("grid_landscape_margin_top", 28) - 28)
                .putInt("grid_landscape_margin_bottom", sp.getInt("grid_landscape_margin_bottom", 28) - 28)
                .putInt("grid_portrait_margin_left", sp.getInt("grid_portrait_margin_left", 28) - 28)
                .putInt("grid_portrait_margin_right", sp.getInt("grid_portrait_margin_right", 28) - 28)
                .putInt("grid_portrait_margin_top", sp.getInt("grid_portrait_margin_top", 57) - 57)
                .putInt("grid_portrait_margin_bottom", sp.getInt("grid_portrait_margin_bottom", 57) - 57)
                .putInt("grid_landscape_row_gap", sp.getInt("grid_landscape_row_gap", 1) - 1)
                .putInt("grid_portrait_row_gap", sp.getInt("grid_portrait_row_gap", 1) - 1)
                .putBoolean("grid_margins_offset", true).commit();
        }
    }

    private void migrateCornersToDp(SharedPreferences sp) {
        if (!sp.getBoolean("corners_dp", false)) {
            float density = getResources().getDisplayMetrics().density;
            SharedPreferences.Editor corners = sp.edit();
            corners.putInt("corner_offset", sp.contains("corner_offset")
                ? Math.round(sp.getInt("corner_offset", -1) / density) : -1);
            corners.putInt("blur_corner_offset", sp.contains("blur_corner_offset")
                ? Math.round(sp.getInt("blur_corner_offset", 0) / density) : 0);
            corners.putBoolean("corners_dp", true).apply();
        }
    }

    void launchExport() {
        exportConfigLauncher.launch("LiquidDock-settings.json");
    }

    void launchImport() {
        importConfigLauncher.launch(new String[]{"application/json", "text/json", "text/plain"});
    }

    private void exportCurrentParameters(Uri uri) {
        new Thread(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Unable to open destination");
                JSONObject json = collectParameters(
                    PreferenceManager.getDefaultSharedPreferences(this));
                json.put("_format", "liquiddock-settings");
                json.put("_version", 2);
                out.write((json.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
                runOnUiThread(() -> Toast.makeText(this,
                    "Parameters exported", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                showError("Export failed: " + e.getMessage());
            }
        }).start();
    }

    private void importParameters(Uri uri) {
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException("Unable to open selected file");
                byte[] buffer = new byte[4096];
                int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 65536) throw new IOException("Config is larger than 64 KiB");
                    out.write(buffer, 0, count);
                }
                JSONObject json = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                String format = json.optString("_format", "liquiddock-settings");
                if (!"liquiddock-settings".equals(format))
                    throw new IOException("Not a LiquidDock settings file");
                SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(this).edit();
                applyImportedParameters(json, editor);
                if (!editor.commit()) throw new IOException("Unable to save imported settings");
                runOnUiThread(() -> {
                    Toast.makeText(this, "Parameters imported and applied", Toast.LENGTH_LONG).show();
                    restartLauncher();
                    recreate();
                });
            } catch (Exception e) {
                showError("Import failed: " + e.getMessage());
            }
        }).start();
    }

    private void showError(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private static JSONObject collectParameters(SharedPreferences sp) throws Exception {
        JSONObject j = new JSONObject();
        j.put("liquiddock_enabled", sp.getBoolean("liquiddock_enabled", true));
        j.put("home_grid_8x4", sp.getBoolean("home_grid_8x4", false));
        j.put("grid_margins_dp", sp.getBoolean("grid_margins_dp", true));
        j.put("grid_margins_offset", sp.getBoolean("grid_margins_offset", true));
        j.put("grid_landscape_horizontal_distance", readDpPreference(sp,
                "grid_landscape_horizontal_distance"));
        j.put("grid_landscape_top_distance", readDpPreference(sp,
                "grid_landscape_top_distance"));
        j.put("grid_landscape_bottom_distance", readDpPreference(sp,
                "grid_landscape_bottom_distance"));
        j.put("grid_portrait_horizontal_distance", readDpPreference(sp,
                "grid_portrait_horizontal_distance"));
        j.put("grid_portrait_top_distance", readDpPreference(sp,
                "grid_portrait_top_distance"));
        j.put("grid_portrait_bottom_distance", readDpPreference(sp,
                "grid_portrait_bottom_distance"));
        j.put("grid_landscape_margin_left", sp.getInt("grid_landscape_margin_left", 0));
        j.put("grid_landscape_margin_right", sp.getInt("grid_landscape_margin_right", 0));
        j.put("grid_landscape_margin_top", sp.getInt("grid_landscape_margin_top", 0));
        j.put("grid_landscape_margin_bottom", sp.getInt("grid_landscape_margin_bottom", 0));
        j.put("grid_portrait_margin_left", sp.getInt("grid_portrait_margin_left", 0));
        j.put("grid_portrait_margin_right", sp.getInt("grid_portrait_margin_right", 0));
        j.put("grid_portrait_margin_top", sp.getInt("grid_portrait_margin_top", 0));
        j.put("grid_portrait_margin_bottom", sp.getInt("grid_portrait_margin_bottom", 0));
        j.put("grid_landscape_row_gap", sp.getInt("grid_landscape_row_gap", 0));
        j.put("grid_portrait_row_gap", sp.getInt("grid_portrait_row_gap", 0));
        j.put("indicator_landscape_y", sp.getInt("indicator_landscape_y", 0));
        j.put("indicator_portrait_y", sp.getInt("indicator_portrait_y", 0));
        j.put("dock_customization", sp.getBoolean("dock_customization", true));
        j.put("dock_resize_animation", sp.getBoolean("dock_resize_animation", false));
        j.put("dock_smooth_resize_animation", sp.getBoolean("dock_smooth_resize_animation", true));
        if (sp.contains("dock_divider_enabled"))
            j.put("dock_divider_enabled", sp.getBoolean("dock_divider_enabled", false));
        String[] dividerKeys = {"dock_divider_width_dp", "dock_divider_height_scale",
                "dock_divider_y_offset", "dock_divider_color_r", "dock_divider_color_g",
                "dock_divider_color_b", "dock_divider_alpha"};
        for (String key : dividerKeys) if (sp.contains(key)) j.put(key, sp.getInt(key, 0));
        j.put("workstation_dock_customization",
                sp.getBoolean("workstation_dock_customization", false));
        j.put("workstation_dock_width_offset", readDpPreference(sp,
                "workstation_dock_width_offset"));
        j.put("workstation_grid_horizontal_offset", readDpPreference(sp,
                "workstation_grid_horizontal_offset"));
        j.put("workstation_all_apps_landscape_horizontal_offset", readDpPreference(sp,
                "workstation_all_apps_landscape_horizontal_offset"));
        j.put("workstation_all_apps_landscape_vertical_offset", readDpPreference(sp,
                "workstation_all_apps_landscape_vertical_offset"));
        j.put("workstation_all_apps_portrait_horizontal_offset", readDpPreference(sp,
                "workstation_all_apps_portrait_horizontal_offset"));
        j.put("workstation_all_apps_portrait_vertical_offset", readDpPreference(sp,
                "workstation_all_apps_portrait_vertical_offset"));
        j.put("workstation_dock_icon_top_offset", readDpPreference(sp,
                "workstation_dock_icon_top_offset"));
        j.put("workstation_dock_icon_bottom_offset", readDpPreference(sp,
                "workstation_dock_icon_bottom_offset"));
        j.put("dock_dimensions_dp", true);
        j.put("blur_radius", sp.getInt("blur_radius", 100));
        j.put("liquid_glass", sp.getBoolean("liquid_glass", false));
        j.put("liquid_dimensions_dp", true);
        j.put("liquid_blur", sp.getInt("liquid_blur", 6));
        j.put("liquid_thickness", sp.getInt("liquid_thickness", 18));
        j.put("liquid_ior", sp.getInt("liquid_ior", 155));
        j.put("liquid_normal_strength", sp.getInt("liquid_normal_strength", 115));
        j.put("liquid_dome", sp.getInt("liquid_dome", 100));
        j.put("liquid_lens_refraction", sp.getInt("liquid_lens_refraction", 12));
        j.put("liquid_chromatic", sp.getInt("liquid_chromatic", 8));
        j.put("liquid_tint_alpha", sp.getInt("liquid_tint_alpha", 38));
        j.put("liquid_tint_r", sp.getInt("liquid_tint_r", 238));
        j.put("liquid_tint_g", sp.getInt("liquid_tint_g", 244));
        j.put("liquid_tint_b", sp.getInt("liquid_tint_b", 255));
        j.put("liquid_highlight_width", sp.getInt("liquid_highlight_width", 100));
        j.put("liquid_highlight_alpha", sp.getInt("liquid_highlight_alpha", 100));
        j.put("liquid_depth_effect", sp.getInt("liquid_depth_effect", 8));
        j.put("liquid_brightness", sp.getInt("liquid_brightness", 108));
        j.put("liquid_specular_sharp", sp.getInt("liquid_specular_sharp", 88));
        j.put("liquid_specular_strength", sp.getInt("liquid_specular_strength", 105));
        j.put("liquid_rim_light", sp.getInt("liquid_rim_light", 100));
        j.put("liquid_caustics", sp.getInt("liquid_caustics", 28));
        j.put("liquid_edge_band", sp.getInt("liquid_edge_band", 32));
        j.put("liquid_capture_power_limit_fps", sp.getInt("liquid_capture_power_limit_fps", 20));
        j.put("liquid_dynamic_app_capture", sp.getBoolean("liquid_dynamic_app_capture", true));
        j.put("liquid_dynamic_app_probe_fps", sp.getInt("liquid_dynamic_app_probe_fps", 3));
        j.put("liquid_dynamic_motion_threshold", sp.getInt("liquid_dynamic_motion_threshold", 12));
        j.put("liquid_dynamic_bit_threshold", sp.getInt("liquid_dynamic_bit_threshold", 18));
        j.put("liquid_dynamic_hold_ms", sp.getInt("liquid_dynamic_hold_ms", 900));
        j.put("liquid_black_threshold", sp.getInt("liquid_black_threshold", 10));
        j.put("liquid_capture_scale", sp.getInt("liquid_capture_scale", 50));
        j.put("liquid_capture_stop_delay", sp.getInt("liquid_capture_stop_delay", 150));
        j.put("liquid_home_settle_delay", sp.getInt("liquid_home_settle_delay", 1200));
        j.put("liquid_recents_prearm_distance", readDpPreference(sp,
                "liquid_recents_prearm_distance"));
        j.put("liquid_capture_bleed_top", sp.getInt("liquid_capture_bleed_top", 48));
        j.put("liquid_capture_bleed_bottom", sp.getInt("liquid_capture_bleed_bottom", 16));
        j.put("height_offset", sp.getInt("height_offset", 0));
        j.put("width_offset", sp.getInt("width_offset", 0));
        j.put("corner_offset", sp.getInt("corner_offset", -1));
        j.put("blur_corner_offset", sp.getInt("blur_corner_offset", 0));
        j.put("corners_dp", sp.getBoolean("corners_dp", true));
        j.put("dock_stroke", sp.getBoolean("dock_stroke", true));
        j.put("stroke_base_r", sp.getInt("stroke_base_r", 255));
        j.put("stroke_base_g", sp.getInt("stroke_base_g", 255));
        j.put("stroke_base_b", sp.getInt("stroke_base_b", 255));
        j.put("stroke_base_alpha", sp.getInt("stroke_base_alpha", 255));
        j.put("squircle", sp.getBoolean("squircle", false));
        j.put("sq_stroke_w", sp.getInt("sq_stroke_w", 4));
        j.put("sq_stroke_off", sp.getInt("sq_stroke_off", 8));
        j.put("sq_outer_cp", sp.getInt("sq_outer_cp", 58));
        j.put("fill_diff", sp.getBoolean("fill_diff", false));
        j.put("stroke_w", sp.getInt("stroke_w", 2));
        j.put("std_stroke_w", sp.getInt("std_stroke_w", 4));
        j.put("dock_shadow", sp.getBoolean("dock_shadow", true));
        j.put("dock_shadow_radius", sp.getInt("dock_shadow_radius", 42));
        j.put("dock_shadow_size", sp.getInt("dock_shadow_size", 52));
        j.put("dock_shadow_alpha", sp.getInt("dock_shadow_alpha", 140));
        j.put("dock_shadow_y", sp.getInt("dock_shadow_y", 12));
        j.put("stroke_shadow", sp.getBoolean("stroke_shadow", false));
        j.put("shadow_radius", sp.getInt("shadow_radius", 8));
        j.put("shadow_alpha", sp.getInt("shadow_alpha", 70));
        j.put("dock_spacing", sp.getInt("dock_spacing", 0));
        j.put("dock_bottom_offset", sp.getInt("dock_bottom_offset", 0));
        String[] dpKeys = {
            "grid_landscape_horizontal_distance", "grid_landscape_top_distance",
            "grid_landscape_bottom_distance", "grid_portrait_horizontal_distance",
            "grid_portrait_top_distance", "grid_portrait_bottom_distance",
            "grid_landscape_margin_left", "grid_landscape_margin_right",
            "grid_landscape_margin_top", "grid_landscape_margin_bottom",
            "grid_portrait_margin_left", "grid_portrait_margin_right",
            "grid_portrait_margin_top", "grid_portrait_margin_bottom",
            "grid_landscape_row_gap", "grid_portrait_row_gap",
            "indicator_landscape_y", "indicator_portrait_y",
            "height_offset", "width_offset", "corner_offset", "blur_corner_offset",
            "workstation_dock_width_offset", "workstation_grid_horizontal_offset",
            "workstation_all_apps_landscape_horizontal_offset",
            "workstation_all_apps_landscape_vertical_offset",
            "workstation_all_apps_portrait_horizontal_offset",
            "workstation_all_apps_portrait_vertical_offset",
            "workstation_dock_icon_top_offset", "workstation_dock_icon_bottom_offset",
            "dock_spacing", "dock_bottom_offset", "liquid_blur", "liquid_thickness",
            "liquid_lens_refraction", "liquid_capture_bleed_top",
            "liquid_recents_prearm_distance", "liquid_home_settle_delay",
            "liquid_capture_bleed_bottom", "sq_stroke_w", "sq_stroke_off",
            "stroke_w", "std_stroke_w", "dock_shadow_radius", "dock_shadow_size",
            "dock_shadow_y", "shadow_radius"
        };
        for (String key : dpKeys) {
            String tenthsKey = key + "_tenths";
            if (sp.contains(tenthsKey)) j.put(key, sp.getInt(tenthsKey, 0) / 10.0);
        }
        return j;
    }

    private static void applyImportedParameters(JSONObject j, SharedPreferences.Editor e) {
        String[] gridMargins = {
            "grid_landscape_margin_left", "grid_landscape_margin_right",
            "grid_landscape_margin_top", "grid_landscape_margin_bottom",
            "grid_portrait_margin_left", "grid_portrait_margin_right",
            "grid_portrait_margin_top", "grid_portrait_margin_bottom"
        };
        boolean importedDp = j.optBoolean("grid_margins_dp", false);
        boolean importedOffsets = j.optBoolean("grid_margins_offset", false);
        for (String key : gridMargins) putInt(j, e, key, importedDp ? -600 : -2000, importedDp ? 600 : 2000);
        e.putBoolean("grid_margins_dp", importedDp);
        e.putBoolean("grid_margins_offset", importedOffsets);
        if (j.has("grid_landscape_margin_horizontal")) {
            int horizontal = j.optInt("grid_landscape_margin_horizontal", 0);
            e.putInt("grid_landscape_margin_left", horizontal)
                .putInt("grid_landscape_margin_right", horizontal);
        }
        if (j.has("grid_portrait_margin_horizontal")) {
            int horizontal = j.optInt("grid_portrait_margin_horizontal", 0);
            e.putInt("grid_portrait_margin_left", horizontal)
                .putInt("grid_portrait_margin_right", horizontal);
        }
        putInt(j, e, "grid_landscape_row_gap", importedDp ? -200 : -600, importedDp ? 400 : 1200);
        putInt(j, e, "grid_portrait_row_gap", importedDp ? -200 : -600, importedDp ? 400 : 1200);
        putInt(j, e, "indicator_landscape_y", -400, 400);
        putInt(j, e, "indicator_portrait_y", -400, 400);
        if (!j.has("grid_landscape_margin_left") && j.has("grid_margin_left")) {
            int left = Math.max(0, Math.min(400, j.optInt("grid_margin_left", 160)));
            int right = Math.max(0, Math.min(400, j.optInt("grid_margin_right", 160)));
            int top = Math.max(0, Math.min(400, j.optInt("grid_margin_top", 80)));
            int bottom = Math.max(0, Math.min(400, j.optInt("grid_margin_bottom", 80)));
            e.putInt("grid_landscape_margin_left", left)
                .putInt("grid_landscape_margin_right", right)
                .putInt("grid_landscape_margin_top", top)
                .putInt("grid_landscape_margin_bottom", bottom)
                .putInt("grid_portrait_margin_left", top)
                .putInt("grid_portrait_margin_right", bottom)
                .putInt("grid_portrait_margin_top", right)
                .putInt("grid_portrait_margin_bottom", left);
        }
        putInt(j, e, "blur_radius", 0, 400);
        putInt(j, e, "liquid_blur", 0, 60);
        putInt(j, e, "liquid_thickness", 1, 60);
        putInt(j, e, "liquid_ior", 100, 200);
        putInt(j, e, "liquid_normal_strength", 0, 300);
        putInt(j, e, "liquid_dome", 0, 200);
        putInt(j, e, "liquid_lens_refraction", 0, 60);
        putInt(j, e, "liquid_chromatic", 0, 40);
        putInt(j, e, "liquid_tint_alpha", 0, 160);
        putInt(j, e, "liquid_tint_r", 0, 255);
        putInt(j, e, "liquid_tint_g", 0, 255);
        putInt(j, e, "liquid_tint_b", 0, 255);
        putInt(j, e, "liquid_highlight_width", 20, 300);
        putInt(j, e, "liquid_highlight_alpha", 0, 200);
        putInt(j, e, "liquid_depth_effect", 0, 50);
        putInt(j, e, "liquid_brightness", 50, 200);
        putInt(j, e, "liquid_specular_sharp", 1, 200);
        putInt(j, e, "liquid_specular_strength", 0, 300);
        putInt(j, e, "liquid_rim_light", 0, 300);
        putInt(j, e, "liquid_caustics", 0, 100);
        putInt(j, e, "liquid_edge_band", 5, 100);
        putInt(j, e, "liquid_capture_power_limit_fps", 5, 60);
        putInt(j, e, "liquid_dynamic_app_probe_fps", 1, 10);
        putInt(j, e, "liquid_dynamic_motion_threshold", 1, 240);
        putInt(j, e, "liquid_dynamic_bit_threshold", 1, 64);
        putInt(j, e, "liquid_dynamic_hold_ms", 0, 5000);
        putInt(j, e, "liquid_black_threshold", 0, 64);
        putInt(j, e, "liquid_capture_scale", 10, 100);
        putInt(j, e, "liquid_capture_stop_delay", 0, 10000);
        putInt(j, e, "liquid_recents_prearm_distance", 1, 48);
        putInt(j, e, "liquid_capture_bleed_top", 0, 256);
        putInt(j, e, "liquid_capture_bleed_bottom", 0, 256);
        putInt(j, e, "height_offset", -200, 200);
        putInt(j, e, "width_offset", -200, 200);
        putInt(j, e, "workstation_dock_width_offset", -240, 240);
        putInt(j, e, "workstation_grid_horizontal_offset", -240, 240);
        putInt(j, e, "workstation_dock_icon_top_offset", -48, 48);
        putInt(j, e, "workstation_dock_icon_bottom_offset", -48, 48);
        putInt(j, e, "corner_offset", -50, 100);
        putInt(j, e, "blur_corner_offset", -50, 100);
        e.putBoolean("corners_dp", j.optBoolean("corners_dp", false));
        putInt(j, e, "stroke_base_r", 0, 255);
        putInt(j, e, "stroke_base_g", 0, 255);
        putInt(j, e, "stroke_base_b", 0, 255);
        putInt(j, e, "stroke_base_alpha", 0, 255);
        putInt(j, e, "sq_stroke_w", 1, 20);
        putInt(j, e, "sq_stroke_off", 0, 30);
        putInt(j, e, "sq_outer_cp", 40, 80);
        putInt(j, e, "stroke_w", 1, 10);
        putInt(j, e, "std_stroke_w", 1, 20);
        putInt(j, e, "dock_shadow_radius", 1, 80);
        putInt(j, e, "dock_shadow_size", 1, 120);
        putInt(j, e, "dock_shadow_alpha", 0, 200);
        putInt(j, e, "dock_shadow_y", -40, 40);
        putInt(j, e, "shadow_radius", 1, 40);
        putInt(j, e, "shadow_alpha", 0, 200);
        putInt(j, e, "dock_spacing", -10, 20);
        putInt(j, e, "dock_bottom_offset", 0, 80);
        putInt(j, e, "dock_divider_width_dp", 0, 160);
        putInt(j, e, "dock_divider_height_scale", 0, 100);
        putInt(j, e, "dock_divider_y_offset", -80, 80);
        putInt(j, e, "dock_divider_color_r", 0, 255);
        putInt(j, e, "dock_divider_color_g", 0, 255);
        putInt(j, e, "dock_divider_color_b", 0, 255);
        putInt(j, e, "dock_divider_alpha", 0, 255);
        String[] dpKeys = {
            "grid_landscape_horizontal_distance", "grid_landscape_top_distance",
            "grid_landscape_bottom_distance", "grid_portrait_horizontal_distance",
            "grid_portrait_top_distance", "grid_portrait_bottom_distance",
            "grid_landscape_margin_left", "grid_landscape_margin_right",
            "grid_landscape_margin_top", "grid_landscape_margin_bottom",
            "grid_portrait_margin_left", "grid_portrait_margin_right",
            "grid_portrait_margin_top", "grid_portrait_margin_bottom",
            "grid_landscape_row_gap", "grid_portrait_row_gap",
            "indicator_landscape_y", "indicator_portrait_y",
            "height_offset", "width_offset", "corner_offset", "blur_corner_offset",
            "workstation_dock_width_offset", "workstation_grid_horizontal_offset",
            "workstation_all_apps_landscape_horizontal_offset",
            "workstation_all_apps_landscape_vertical_offset",
            "workstation_all_apps_portrait_horizontal_offset",
            "workstation_all_apps_portrait_vertical_offset",
            "workstation_all_apps_horizontal_offset", "workstation_all_apps_vertical_offset",
            "workstation_dock_icon_top_offset", "workstation_dock_icon_bottom_offset",
            "dock_spacing", "dock_bottom_offset", "liquid_blur", "liquid_thickness",
            "liquid_lens_refraction", "liquid_capture_bleed_top",
            "liquid_recents_prearm_distance", "liquid_home_settle_delay",
            "liquid_capture_bleed_bottom", "sq_stroke_w", "sq_stroke_off",
            "stroke_w", "std_stroke_w", "dock_shadow_radius", "dock_shadow_size",
            "dock_shadow_y", "shadow_radius"
        };
        for (String key : dpKeys) putDp(j, e, key);
        if (j.has("home_grid_8x4")) e.putBoolean("home_grid_8x4", j.optBoolean("home_grid_8x4"));
        if (j.has("liquiddock_enabled")) e.putBoolean("liquiddock_enabled",
                j.optBoolean("liquiddock_enabled"));
        if (j.has("dock_customization")) e.putBoolean("dock_customization", j.optBoolean("dock_customization"));
        if (j.has("dock_resize_animation")) e.putBoolean(
                "dock_resize_animation", j.optBoolean("dock_resize_animation"));
        if (j.has("dock_smooth_resize_animation")) e.putBoolean(
                "dock_smooth_resize_animation", j.optBoolean("dock_smooth_resize_animation"));
        if (j.has("dock_divider_enabled")) e.putBoolean(
                "dock_divider_enabled", j.optBoolean("dock_divider_enabled"));
        if (j.has("workstation_dock_customization")) e.putBoolean(
                "workstation_dock_customization", j.optBoolean("workstation_dock_customization"));
        if (j.has("dock_dimensions_dp")) e.putBoolean("dock_dimensions_dp",
                j.optBoolean("dock_dimensions_dp"));
        if (j.has("liquid_glass")) e.putBoolean("liquid_glass", j.optBoolean("liquid_glass"));
        if (j.has("liquid_dimensions_dp")) e.putBoolean("liquid_dimensions_dp",
                j.optBoolean("liquid_dimensions_dp"));
        if (j.has("liquid_dynamic_app_capture")) e.putBoolean("liquid_dynamic_app_capture",
                j.optBoolean("liquid_dynamic_app_capture"));
        if (j.has("dock_stroke")) e.putBoolean("dock_stroke", j.optBoolean("dock_stroke"));
        if (j.has("squircle")) e.putBoolean("squircle", j.optBoolean("squircle"));
        if (j.has("fill_diff")) e.putBoolean("fill_diff", j.optBoolean("fill_diff"));
        if (j.has("dock_shadow")) e.putBoolean("dock_shadow", j.optBoolean("dock_shadow"));
        if (j.has("stroke_shadow")) e.putBoolean("stroke_shadow", j.optBoolean("stroke_shadow"));
    }

    private static void putInt(JSONObject j, SharedPreferences.Editor e,
                               String key, int min, int max) {
        if (!j.has(key)) return;
        int value = j.optInt(key, min);
        e.putInt(key, Math.max(min, Math.min(max, value)));
    }

    private static void putDp(JSONObject j, SharedPreferences.Editor e, String key) {
        if (!j.has(key)) return;
        double value = j.optDouble(key, 0.0);
        e.putInt(key, (int) Math.round(value));
        e.putInt(key + "_tenths", (int) Math.round(value * 10.0));
    }

    void restartLauncher() {
        // Configuration is already in API101 Remote Preferences. Root is used only to
        // restart MIUI Home so process-start-only hooks reload their settings.
        LiquidDockApp.syncToRemote(PreferenceManager.getDefaultSharedPreferences(this));
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("am force-stop com.miui.home && sleep 1 && "
                        + "am start -n com.miui.home/.launcher.Launcher\nexit\n");
                    os.flush();
                }
                int exitCode = p.waitFor();
                if (exitCode != 0) throw new IOException("su failed with exit code " + exitCode);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SettingsActivity activity = (SettingsActivity) requireActivity();
            Preference export = findPreference("export_config");
            if (export != null) export.setOnPreferenceClickListener(pref -> {
                activity.launchExport(); return true;
            });
            Preference importPref = findPreference("import_config");
            if (importPref != null) importPref.setOnPreferenceClickListener(pref -> {
                activity.launchImport(); return true;
            });
            Preference ipadPreset = findPreference("preset_ipad");
            if (ipadPreset != null) ipadPreset.setOnPreferenceClickListener(pref -> {
                applyIpadPreset();
                return true;
            });
            Preference restart = findPreference("restart_launcher");
            if (restart != null) restart.setOnPreferenceClickListener(pref -> {
                activity.restartLauncher();
                return true;
            });
        }

        private void applyIpadPreset() {
            Context context = requireContext();
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            float density = dm.density;
            float shortSideDp = Math.min(dm.widthPixels, dm.heightPixels) / density;
            float displayScale = Math.max(0.90f, Math.min(1.20f, shortSideDp / 668f));

            Resources launcherRes = null;
            String launcherPackage = "com.miui.home";
            try {
                launcherRes = context.createPackageContext(launcherPackage,
                    Context.CONTEXT_IGNORE_SECURITY).getResources();
            } catch (PackageManager.NameNotFoundException ignored) {}

            int icon = dimenPx(launcherRes, launcherPackage,
                "config_hotseats_icon_content_default_height", 60f, density);
            int cell = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_cell_width", 80f, density);
            int dockHeight = dimenPx(launcherRes, launcherPackage,
                "hotseats_height_land", 78f, density);
            int dockRadius = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_background_radius", 21f, density);
            int sidePadding = dimenPx(launcherRes, launcherPackage,
                "hotseats_list_content_padding_side", 9.3f, density);

            int targetGap = Math.round(14f * density * displayScale);
            int targetHeight = icon + Math.round(20f * density * displayScale);
            int targetRadius = Math.round(22f * density * displayScale);
            int targetSidePadding = Math.round(14f * density * displayScale);
            int spacing = Math.round((icon + targetGap - cell) / 2f);
            int heightOffset = targetHeight - dockHeight;
            int widthOffset = 2 * (targetSidePadding - sidePadding);
            int cornerOffset = targetRadius - dockRadius;
            int oneDp = Math.max(1, Math.round(density * displayScale));

            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt("blur_radius", 100)
                .putInt("height_offset", heightOffset)
                .putInt("width_offset", widthOffset)
                .putBoolean("corners_dp", true)
                .putInt("corner_offset", Math.round(cornerOffset / density))
                .putInt("blur_corner_offset", -1)
                .putBoolean("home_grid_8x4", true)
                .putBoolean("grid_margins_dp", true)
                .putBoolean("grid_margins_offset", true)
                .putInt("grid_landscape_margin_left", 0)
                .putInt("grid_landscape_margin_right", 0)
                .putInt("grid_landscape_margin_top", 0)
                .putInt("grid_landscape_margin_bottom", 0)
                .putInt("grid_portrait_margin_left", 0)
                .putInt("grid_portrait_margin_right", 0)
                .putInt("grid_portrait_margin_top", 0)
                .putInt("grid_portrait_margin_bottom", 0)
                .putInt("grid_landscape_row_gap", 0)
                .putInt("grid_portrait_row_gap", 0)
                .putInt("indicator_landscape_y", 0)
                .putInt("indicator_portrait_y", 0)
                .putBoolean("dock_customization", true)
                .putBoolean("dock_stroke", true)
                .putInt("stroke_base_r", 255)
                .putInt("stroke_base_g", 255)
                .putInt("stroke_base_b", 255)
                .putInt("stroke_base_alpha", 255)
                .putBoolean("squircle", true)
                .putInt("sq_stroke_w", oneDp)
                .putInt("sq_stroke_off", 0)
                .putInt("sq_outer_cp", 65)
                .putBoolean("fill_diff", true)
                .putInt("stroke_w", oneDp)
                .putInt("std_stroke_w", oneDp)
                .putBoolean("dock_shadow", true)
                .putInt("dock_shadow_radius", Math.round(10f * density * displayScale))
                .putInt("dock_shadow_size", Math.round(13f * density * displayScale))
                .putInt("dock_shadow_alpha", 140)
                .putInt("dock_shadow_y", Math.round(3f * density * displayScale))
                .putBoolean("stroke_shadow", false)
                .putInt("shadow_radius", Math.round(3f * density * displayScale))
                .putInt("shadow_alpha", 70)
                .putInt("dock_spacing", spacing)
                .putInt("dock_bottom_offset", Math.round(10f * density * displayScale))
                .commit();

            Toast.makeText(context,
                "iPad preset: spacing " + spacing + " px, height "
                    + signed(heightOffset) + " px, width " + signed(widthOffset)
                    + " px, radius " + signed(cornerOffset) + " px, bottom +"
                    + Math.round(10f * density * displayScale) + " px",
                Toast.LENGTH_LONG).show();
            ((SettingsActivity) requireActivity()).restartLauncher();
        }

        private static int dimenPx(Resources resources, String packageName,
                                   String name, float fallbackDp, float density) {
            if (resources != null) {
                int id = resources.getIdentifier(name, "dimen", packageName);
                if (id != 0) {
                    try { return resources.getDimensionPixelSize(id); }
                    catch (Resources.NotFoundException ignored) {}
                }
            }
            return Math.round(fallbackDp * density);
        }

        private static String signed(int value) {
            return value > 0 ? "+" + value : String.valueOf(value);
        }
    }
}
