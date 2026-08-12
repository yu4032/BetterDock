package com.hellovoid.liquiddock;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import android.util.Log;

public class ConfigReader {
    // Primary location: the launcher's real data dir.  /data/data/com.miui.home is a
    // symlink that ksu's mkdir -p refuses to create, which silently broke the GUI
    // JSON sync; /data/user/0/... is the canonical path and works under su.
    // Fallback: the historical /data/local/tmp path so pre-existing configs keep working.
    private static final String[] PATHS = {
        "/data/user/0/com.miui.home/files/liquiddock_config.json",
        "/data/data/com.miui.home/files/liquiddock_config.json",
        "/data/local/tmp/liquiddock_config.json",
    };
    private static final int MAX_CONFIG_BYTES = 64 * 1024;
    private JSONObject json;

    private ConfigReader() {
        try {
            JSONObject loaded = null;
            for (String path : PATHS) {
                File f = new File(path);
                if (!f.exists()) continue;
                try (FileInputStream fis = new FileInputStream(f);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    int total = 0;
                    while ((n = fis.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_CONFIG_BYTES)
                            throw new IllegalArgumentException("Config exceeds 64 KiB");
                        out.write(buf, 0, n);
                    }
                    byte[] data = out.toByteArray();
                    if (data.length > 0) {
                        loaded = new JSONObject(new String(data, StandardCharsets.UTF_8));
                        break;
                    }
                }
            }
            json = loaded != null ? loaded : new JSONObject();
        } catch (Throwable e) {
            Log.e("LiquidDock", "Failed to read config", e);
            json = new JSONObject();
        }
    }

    public static ConfigReader load() { return new ConfigReader(); }
    public boolean has(String k)              { return json.has(k); }
    public String  s(String k, String d)  { return json.optString(k, d); }
    public int     i(String k, int d)     { return json.optInt(k, d); }
    public float   f(String k, float d)   { return (float) json.optDouble(k, d); }
    public boolean b(String k, boolean d) { return json.optBoolean(k, d); }
}
