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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigMigration;

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
        ConfigMigration.migrate(this, PreferenceManager.getDefaultSharedPreferences(this));
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
        JSONObject json = new JSONObject();
        for (Map.Entry<String, Object> entry : ConfigCodec.exportValues(sp.getAll()).entrySet()) {
            json.put(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private static void applyImportedParameters(JSONObject json, SharedPreferences.Editor editor) {
        for (Map.Entry<String, Object> entry : ConfigCodec.importValues(jsonToMap(json)).entrySet()) {
            putPreferenceValue(editor, entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            values.put(key, json.opt(key));
        }
        return values;
    }

    private static void putPreferenceValue(SharedPreferences.Editor editor, String key,
                                           Object value) {
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else {
            throw new IllegalArgumentException("Unsupported preference value for " + key);
        }
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
