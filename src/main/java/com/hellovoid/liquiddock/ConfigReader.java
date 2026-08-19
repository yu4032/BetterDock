package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Runtime config reader backed by API101 Remote Preferences. */
public class ConfigReader {
    public static final String REMOTE_GROUP = "config";
    private static final String ZERO_COPY_PIPELINE_KEY = "liquid_miuix_307_pipeline";

    private final Map<String, ?> prefs;

    ConfigReader(Map<String, ?> prefs) {
        this.prefs = new HashMap<>(prefs);
    }

    private ConfigReader(SharedPreferences remote) {
        Map<String, ?> all = remote.getAll();
        prefs = all == null ? Collections.emptyMap() : new HashMap<>(all);
    }

    private ConfigReader() {
        this(loadRemoteSnapshot());
    }

    private static Map<String, ?> loadRemoteSnapshot() {
        try {
            SharedPreferences remote = Api101Bridge.remotePreferences(REMOTE_GROUP);
            Map<String, ?> all = remote.getAll();
            if (all != null && !all.isEmpty()) {
                Map<String, ?> loaded = new HashMap<>(all);
                if (MainHook.debugLogging) {
                    Log.i("LiquidDock", "config loaded from API101 Remote Preferences: "
                            + loaded.size() + " keys");
                }
                return loaded;
            }
            Log.w("LiquidDock", "API101 Remote Preferences are empty; using defaults");
        } catch (Throwable error) {
            // Runtime config loading is deliberately read-only. One-time pre-API101
            // migration runs explicitly at the package-ready compatibility boundary.
            Log.w("LiquidDock", "API101 Remote Preferences unavailable; using defaults", error);
        }
        return Collections.emptyMap();
    }

    public static ConfigReader load() { return new ConfigReader(); }

    static ConfigReader load(SharedPreferences remote) { return new ConfigReader(remote); }

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
            try { return Integer.parseInt((String) tenths) / 10f;
            } catch (NumberFormatException ignored) {}
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
        // release/1.3.0 retires the Bitmap/screen-capture glass backend. Keep the persisted
        // compatibility key readable for old configs, but it can no longer opt back into the
        // retired path: liquid glass always enters the 307 PassBlur/OES pipeline.
        if (ZERO_COPY_PIPELINE_KEY.equals(key)) return true;
        Object value = prefs.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return def;
    }
}
