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
import java.util.Map;

/** Runtime config reader. API101 Remote Preferences are the primary source. */
public class ConfigReader {
    public static final String REMOTE_GROUP = "config";

    private static final String[] LEGACY_PATHS = {
        "/data/user/0/com.miui.home/files/liquiddock_config.json",
        "/data/data/com.miui.home/files/liquiddock_config.json",
        "/data/local/tmp/liquiddock_config.json",
    };
    private static final int MAX_CONFIG_BYTES = 64 * 1024;

    private final Map<String, ?> prefs;
    private final JSONObject legacyJson;

    private ConfigReader() {
        Map<String, ?> loadedPrefs = Collections.emptyMap();
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(REMOTE_GROUP);
            Map<String, ?> all = remote.getAll();
            if (all != null && !all.isEmpty()) {
                loadedPrefs = new HashMap<>(all);
                Log.i("LiquidDock", "config loaded from API101 Remote Preferences: "
                        + loadedPrefs.size() + " keys");
            } else {
                Log.w("LiquidDock", "API101 Remote Preferences are empty; trying legacy config");
            }
        } catch (Throwable error) {
            Log.w("LiquidDock", "API101 Remote Preferences unavailable; trying legacy config", error);
        }
        prefs = loadedPrefs;
        legacyJson = prefs.isEmpty() ? readLegacyJson() : new JSONObject();
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
                    Log.i("LiquidDock", "config loaded from legacy path: " + path);
                    return new JSONObject(new String(data, StandardCharsets.UTF_8));
                }
            } catch (Throwable error) {
                Log.w("LiquidDock", "Failed to read legacy config: " + path, error);
            }
        }
        return new JSONObject();
    }

    public static ConfigReader load() { return new ConfigReader(); }

    public boolean has(String key) {
        return prefs.containsKey(key) || legacyJson.has(key);
    }

    public String s(String key, String def) {
        Object value = prefs.get(key);
        if (value != null) return String.valueOf(value);
        return legacyJson.optString(key, def);
    }

    public int i(String key, int def) {
        Object value = prefs.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); }
            catch (NumberFormatException ignored) {}
        }
        return legacyJson.optInt(key, def);
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
        return (float) legacyJson.optDouble(key, def);
    }

    public boolean b(String key, boolean def) {
        Object value = prefs.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return legacyJson.optBoolean(key, def);
    }
}
