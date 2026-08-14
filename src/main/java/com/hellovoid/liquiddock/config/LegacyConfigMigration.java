package com.hellovoid.liquiddock.config;

import android.content.SharedPreferences;
import android.util.Log;

import com.hellovoid.liquiddock.Api101Bridge;
import com.hellovoid.liquiddock.ConfigReader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** One-time compatibility bridge from pre-API101 JSON into Remote Preferences. */
public final class LegacyConfigMigration {
    private static final String[] LEGACY_PATHS = {
        "/data/user/0/com.miui.home/files/liquiddock_config.json",
        "/data/data/com.miui.home/files/liquiddock_config.json",
        "/data/local/tmp/liquiddock_config.json",
    };
    private static final int MAX_CONFIG_BYTES = 64 * 1024;

    private LegacyConfigMigration() {}

    /**
     * Runs once at launcher package readiness, before any runtime config snapshot is loaded.
     * Runtime config reads remain side-effect free even when Remote Preferences are empty.
     */
    public static void migrateAtProcessStart() {
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
            Map<String, ?> existing = remote.getAll();
            if (existing != null && !existing.isEmpty()) return;

            Map<String, Object> legacy = readLegacyValues();
            if (legacy == null || legacy.isEmpty()) return;

            migrateLegacyValues(remote, legacy);
            Log.i("LiquidDock", "legacy config migrated to API101 Remote Preferences");
        } catch (Throwable error) {
            // Migration is compatibility-only. Failure must not prevent launcher hooks from
            // loading defaults or any already-available Remote Preferences snapshot.
            Log.w("LiquidDock", "Failed to migrate legacy config to API101 Remote Preferences",
                    error);
        }
    }

    static void migrateLegacyValues(SharedPreferences remote, Map<String, ?> legacy) {
        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) return;
        for (Map.Entry<String, ?> entry : legacy.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
            else if (value instanceof Number) editor.putLong(key, ((Number) value).longValue());
            else if (value instanceof String) editor.putString(key, (String) value);
        }
        // Synchronous because config loading follows immediately during launcher startup.
        editor.commit();
    }

    private static Map<String, Object> readLegacyValues() {
        for (String path : LEGACY_PATHS) {
            File file = new File(path);
            if (!file.exists()) continue;
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                int total = 0;
                while ((count = fis.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_CONFIG_BYTES) {
                        throw new IllegalArgumentException("Config exceeds 64 KiB");
                    }
                    out.write(buffer, 0, count);
                }
                byte[] data = out.toByteArray();
                if (data.length == 0) continue;

                Log.i("LiquidDock", "legacy config found for API101 migration: " + path);
                JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));
                Map<String, Object> values = new LinkedHashMap<>();
                java.util.Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    values.put(key, json.opt(key));
                }
                return values;
            } catch (Throwable error) {
                Log.w("LiquidDock", "Failed to read legacy config for migration: " + path, error);
            }
        }
        return null;
    }
}
