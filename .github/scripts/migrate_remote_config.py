from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    return text.replace(old, new, 1)

compose_path = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
compose = compose_path.read_text(encoding="utf-8")
old_sync = '''// Debounced mirror of UI prefs to the launcher's data dir: the module (running inside
// com.miui.home) reads liquiddock_config.json from there, so every UI write must land
// in that file to take effect.  su is used for the write (launcher's dir is not writable
// by the settings app directly).
private val jsonSyncHandler = android.os.Handler(android.os.Looper.getMainLooper())
private var jsonSyncPrefs: SharedPreferences? = null
private var jsonSyncContext: Context? = null
private fun syncConfigNow(prefs: SharedPreferences, ctx: Context) {
    try {
        val json = org.json.JSONObject()
        for ((k, v) in prefs.all) {
            when (v) {
                is Int -> json.put(k, v)
                is Long -> json.put(k, v)
                is Float -> json.put(k, v)
                is Boolean -> json.put(k, v)
                is String -> json.put(k, v)
            }
        }
        val tmp = java.io.File(ctx.cacheDir, "liquiddock_config.json")
        tmp.writeText(json.toString())
        val target = "/data/user/0/com.miui.home/files/liquiddock_config.json"
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c",
            "mkdir -p /data/user/0/com.miui.home/files && cp "
                + tmp.absolutePath + " " + target + " && chmod 644 " + target))
        p.waitFor()
    } catch (e: Throwable) {
        android.util.Log.w("LiquidDock", "json sync failed", e)
    }
}
private val jsonSyncRunnable = Runnable {
    val prefs = jsonSyncPrefs ?: return@Runnable
    val ctx = jsonSyncContext ?: return@Runnable
    syncConfigNow(prefs, ctx)
}
private fun requestJsonSync(prefs: SharedPreferences, ctx: Context) {
    jsonSyncPrefs = prefs
    jsonSyncContext = ctx
    jsonSyncHandler.removeCallbacks(jsonSyncRunnable)
    jsonSyncHandler.postDelayed(jsonSyncRunnable, 400L)
}
'''
new_sync = '''// API101 Remote Preferences are the only runtime config transport.  The settings UI
// keeps AndroidX default preferences as its local state store; every write is mirrored
// to LSPosed's Remote Preferences without su, chmod, or cross-UID files.
private fun syncConfigNow(prefs: SharedPreferences, ctx: Context) {
    if (!LiquidDockApp.syncToRemote(prefs)) {
        android.util.Log.w("LiquidDock", "Remote Preferences service is not connected yet")
    }
}
private fun requestJsonSync(prefs: SharedPreferences, ctx: Context) {
    syncConfigNow(prefs, ctx)
}
'''
compose = replace_once(compose, old_sync, new_sync, "Compose legacy JSON sync")
compose_path.write_text(compose, encoding="utf-8")

settings_path = Path("src/main/java/com/hellovoid/liquiddock/SettingsActivity.java")
settings = settings_path.read_text(encoding="utf-8")
old_restart = '''    void restartLauncher() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        new Thread(() -> {
            try {
                JSONObject j = collectParameters(sp);
                String json = j.toString(2).replace("'","'\\\\''");

                Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("echo '"+json+"' > /data/local/tmp/liquiddock_config.json && "
                        + "chmod 644 /data/local/tmp/liquiddock_config.json && "
                        + "am force-stop com.miui.home && sleep 1 && "
                        + "am start -n com.miui.home/.launcher.Launcher\\nexit\\n");
                    os.flush();
                }
                int exitCode = p.waitFor();
                if (exitCode != 0) throw new IOException("su failed with exit code " + exitCode);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
'''
new_restart = '''    void restartLauncher() {
        // Configuration is already in API101 Remote Preferences. Root is used only to
        // restart MIUI Home so process-start-only hooks reload their settings.
        LiquidDockApp.syncToRemote(PreferenceManager.getDefaultSharedPreferences(this));
        new Thread(() -> {
            try {
                Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
                try (DataOutputStream os = new DataOutputStream(p.getOutputStream())) {
                    os.writeBytes("am force-stop com.miui.home && sleep 1 && "
                        + "am start -n com.miui.home/.launcher.Launcher\\nexit\\n");
                    os.flush();
                }
                int exitCode = p.waitFor();
                if (exitCode != 0) throw new IOException("su failed with exit code " + exitCode);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: "+e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
'''
settings = replace_once(settings, old_restart, new_restart, "SettingsActivity legacy JSON restart")
settings_path.write_text(settings, encoding="utf-8")

print("Remote Preferences cleanup patch applied")
