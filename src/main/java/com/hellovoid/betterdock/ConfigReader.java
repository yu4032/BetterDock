package com.hellovoid.betterdock;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import android.util.Log;

public class ConfigReader {
    private static final String PATH = "/data/local/tmp/betterdock_config.json";
    private static final int MAX_CONFIG_BYTES = 64 * 1024;
    private JSONObject json;

    private ConfigReader() {
        try {
            File f = new File(PATH);
            if (!f.exists()) { json = new JSONObject(); return; }
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
                json = data.length > 0
                    ? new JSONObject(new String(data, StandardCharsets.UTF_8))
                    : new JSONObject();
            }
        } catch (Throwable e) {
            Log.e("BetterDock", "Failed to read config", e);
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
