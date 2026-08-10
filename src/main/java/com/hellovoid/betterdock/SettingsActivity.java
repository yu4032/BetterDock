package com.hellovoid.betterdock;

import android.content.SharedPreferences;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.graphics.Color;
import android.os.Bundle;
import android.net.Uri;
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
        migrateGridPreferences();
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#37474F"));
        if (useLegacyPreferenceUi()) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    protected boolean useLegacyPreferenceUi() { return true; }

    private void migrateGridPreferences() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = sp.edit();
        if (!sp.contains("grid_landscape_margin_left")) {
            int left = sp.getInt("grid_margin_left", 160);
            int right = sp.getInt("grid_margin_right", 160);
            int top = sp.getInt("grid_margin_top", 80);
            int bottom = sp.getInt("grid_margin_bottom", 80);
            e.putInt("grid_landscape_margin_left", left)
                .putInt("grid_landscape_margin_right", right)
                .putInt("grid_landscape_margin_top", top)
                .putInt("grid_landscape_margin_bottom", bottom)
                .putInt("grid_portrait_margin_left", top)
                .putInt("grid_portrait_margin_right", bottom)
                .putInt("grid_portrait_margin_top", right)
                .putInt("grid_portrait_margin_bottom", left).commit();
        }
        if (!sp.getBoolean("grid_margins_dp", false)) {
            float density = getResources().getDisplayMetrics().density;
            String[] keys = {
                "grid_landscape_margin_left", "grid_landscape_margin_right",
                "grid_landscape_margin_top", "grid_landscape_margin_bottom",
                "grid_portrait_margin_left", "grid_portrait_margin_right",
                "grid_portrait_margin_top", "grid_portrait_margin_bottom"
            };
            e = sp.edit();
            for (String key : keys) {
                int px = sp.getInt(key, key.contains("top") || key.contains("bottom") ? 80 : 160);
                e.putInt(key, Math.max(0, Math.min(600, Math.round(px / density))));
            }
            e.putBoolean("grid_margins_dp", true).commit();
        }
    }

    void launchExport() {
        exportConfigLauncher.launch("BetterDock-settings.json");
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
                json.put("_format", "betterdock-settings");
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
                String format = json.optString("_format", "betterdock-settings");
                if (!"betterdock-settings".equals(format))
                    throw new IOException("Not a BetterDock settings file");
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
        j.put("home_grid_8x4", sp.getBoolean("home_grid_8x4", true));
        j.put("grid_margins_dp", sp.getBoolean("grid_margins_dp", true));
        j.put("grid_landscape_margin_left", sp.getInt("grid_landscape_margin_left", 57));
        j.put("grid_landscape_margin_right", sp.getInt("grid_landscape_margin_right", 57));
        j.put("grid_landscape_margin_top", sp.getInt("grid_landscape_margin_top", 28));
        j.put("grid_landscape_margin_bottom", sp.getInt("grid_landscape_margin_bottom", 28));
        j.put("grid_portrait_margin_left", sp.getInt("grid_portrait_margin_left", 28));
        j.put("grid_portrait_margin_right", sp.getInt("grid_portrait_margin_right", 28));
        j.put("grid_portrait_margin_top", sp.getInt("grid_portrait_margin_top", 57));
        j.put("grid_portrait_margin_bottom", sp.getInt("grid_portrait_margin_bottom", 57));
        j.put("grid_landscape_row_gap", sp.getInt("grid_landscape_row_gap", 1));
        j.put("grid_portrait_row_gap", sp.getInt("grid_portrait_row_gap", 1));
        j.put("indicator_landscape_x", sp.getInt("indicator_landscape_x", 0));
        j.put("indicator_landscape_y", sp.getInt("indicator_landscape_y", 0));
        j.put("indicator_portrait_x", sp.getInt("indicator_portrait_x", 0));
        j.put("indicator_portrait_y", sp.getInt("indicator_portrait_y", 0));
        j.put("dock_customization", sp.getBoolean("dock_customization", true));
        j.put("light_mode", sp.getString("light_mode", "fixed"));
        j.put("blur_radius", sp.getInt("blur_radius", 100));
        j.put("height_offset", sp.getInt("height_offset", 0));
        j.put("width_offset", sp.getInt("width_offset", 0));
        j.put("corner_offset", sp.getInt("corner_offset", -1));
        j.put("blur_corner_offset", sp.getInt("blur_corner_offset", 0));
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
        return j;
    }

    private static void applyImportedParameters(JSONObject j, SharedPreferences.Editor e) {
        String lightMode = j.optString("light_mode", "");
        if ("fixed".equals(lightMode) || "dynamic".equals(lightMode) || "none".equals(lightMode))
            e.putString("light_mode", lightMode);
        String[] gridMargins = {
            "grid_landscape_margin_left", "grid_landscape_margin_right",
            "grid_landscape_margin_top", "grid_landscape_margin_bottom",
            "grid_portrait_margin_left", "grid_portrait_margin_right",
            "grid_portrait_margin_top", "grid_portrait_margin_bottom"
        };
        boolean importedDp = j.optBoolean("grid_margins_dp", false);
        for (String key : gridMargins) putInt(j, e, key, importedDp ? -600 : -2000, importedDp ? 600 : 2000);
        e.putBoolean("grid_margins_dp", importedDp);
        putInt(j, e, "grid_landscape_row_gap", importedDp ? -200 : -600, importedDp ? 400 : 1200);
        putInt(j, e, "grid_portrait_row_gap", importedDp ? -200 : -600, importedDp ? 400 : 1200);
        putInt(j, e, "indicator_landscape_x", -400, 400);
        putInt(j, e, "indicator_landscape_y", -400, 400);
        putInt(j, e, "indicator_portrait_x", -400, 400);
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
        putInt(j, e, "height_offset", -200, 200);
        putInt(j, e, "width_offset", -200, 200);
        putInt(j, e, "corner_offset", -50, 100);
        putInt(j, e, "blur_corner_offset", -50, 100);
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
        if (j.has("home_grid_8x4")) e.putBoolean("home_grid_8x4", j.optBoolean("home_grid_8x4"));
        if (j.has("dock_customization")) e.putBoolean("dock_customization", j.optBoolean("dock_customization"));
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

    void restartLauncher() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        new Thread(() -> {
            try {
                JSONObject j = collectParameters(sp);
                String json = j.toString(2).replace("'","'\\''");

                Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("echo '"+json+"' > /data/local/tmp/betterdock_config.json && "
                        + "chmod 644 /data/local/tmp/betterdock_config.json && "
                        + "am force-stop com.miui.home && sleep 1 && "
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
                .putString("light_mode", "dynamic")
                .putInt("blur_radius", 100)
                .putInt("height_offset", heightOffset)
                .putInt("width_offset", widthOffset)
                .putInt("corner_offset", cornerOffset)
                .putInt("blur_corner_offset", -2)
                .putBoolean("home_grid_8x4", true)
                .putBoolean("grid_margins_dp", true)
                .putInt("grid_landscape_margin_left", 57)
                .putInt("grid_landscape_margin_right", 57)
                .putInt("grid_landscape_margin_top", 28)
                .putInt("grid_landscape_margin_bottom", 28)
                .putInt("grid_portrait_margin_left", 28)
                .putInt("grid_portrait_margin_right", 28)
                .putInt("grid_portrait_margin_top", 57)
                .putInt("grid_portrait_margin_bottom", 57)
                .putInt("grid_landscape_row_gap", 1)
                .putInt("grid_portrait_row_gap", 1)
                .putInt("indicator_landscape_x", 0)
                .putInt("indicator_landscape_y", 0)
                .putInt("indicator_portrait_x", 0)
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
