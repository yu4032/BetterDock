package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Runtime config reader backed by API101 Remote Preferences. */
public class ConfigReader {
    public static final String REMOTE_GROUP = "config";

    // One-time upgrade sources from the pre-API101 releases. They are consulted only
    // when the Remote Preferences group is empty, then copied into Remote Preferences.
    private static final String[] LEGACY_PATHS = {
        "/data/user/0/com.miui.home/files/liquiddock_config.json",
        "/data/data/com.miui.home/files/liquiddock_config.json",
        "/data/local/tmp/liquiddock_config.json",
    };
    private static final int MAX_CONFIG_BYTES = 64 * 1024;

    private final Map<String, ?> prefs;

    private ConfigReader() {
        Map<String, ?> loaded = Collections.emptyMap();
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(REMOTE_GROUP);
            Map<String, ?> all = remote.getAll();
            if (all == null || all.isEmpty()) {
                JSONObject legacy = readLegacyJson();
                if (legacy != null && legacy.length() > 0) {
                    migrateLegacyJson(remote, legacy);
                    all = remote.getAll();
                    Log.i("LiquidDock", "legacy config migrated to API101 Remote Preferences");
                }
            }
            if (all != null && !all.isEmpty()) {
                loaded = new HashMap<>(all);
                Log.i("LiquidDock", "config loaded from API101 Remote Preferences: "
                        + loaded.size() + " keys");
            } else {
                Log.w("LiquidDock", "API101 Remote Preferences are empty; using defaults");
            }
        } catch (Throwable error) {
            // There is deliberately no persistent JSON fallback here. Once API101 is in use,
            // runtime configuration has one source of truth. Defaults keep the hook safe if
            // the framework's remote service is temporarily unavailable.
            Log.w("LiquidDock", "API101 Remote Preferences unavailable; using defaults", error);
        }
        prefs = loaded;
    }

    private static JSONObject readLegacyJson() {
        for (String path : LEGACY_PATHS) {
            File file = new File(path);
            if (!file.exists()) continue;
            try (FileInputStream fis = new FileInputStream(file);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = fis.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_CONFIG_BYTES) {
                        throw new IllegalArgumentException("Config exceeds 64 KiB");
                    }
                    out.write(buf, 0, n);
                }
                byte[] data = out.toByteArray();
                if (data.length > 0) {
                    Log.i("LiquidDock", "legacy config found for API101 migration: " + path);
                    return new JSONObject(new String(data, StandardCharsets.UTF_8));
                }
            } catch (Throwable error) {
                Log.w("LiquidDock", "Failed to read legacy config for migration: " + path, error);
            }
        }
        return null;
    }

    private static void migrateLegacyJson(SharedPreferences remote, JSONObject json) {
        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) return;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
            else if (value instanceof Number) editor.putLong(key, ((Number) value).longValue());
            else if (value instanceof String) editor.putString(key, (String) value);
        }
        // Commit here because the caller immediately re-reads the group during launcher startup.
        editor.commit();
    }

    public static ConfigReader load() { return new ConfigReader(); }

    public boolean has(String key) { return prefs.containsKey(key); }

    public String s(String key, String def) {
        Object value = prefs.get(key);
        return value != null ? String.valueOf(value) : def;
    }

    public int i(String key, int def) {
        Object value = prefs.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public float f(String key, float def) {
        // Compose persists decimal-dp settings losslessly in <key>_tenths.
        Object tenths = prefs.get(key + "_tenths");
        if (tenths instanceof Number) return ((Number) tenths).intValue() / 10f;
        if (tenths instanceof String) {
            try { return Integer.parseInt((String) tenths) / 10f; }
            catch (NumberFormatException ignored) {}
        }

        Object value = prefs.get(key);
        if (value instanceof Number) return ((Number) value).floatValue();
        if (value instanceof String) {
            try { return Float.parseFloat((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public boolean b(String key, boolean def) {
        Object value = prefs.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }
}
