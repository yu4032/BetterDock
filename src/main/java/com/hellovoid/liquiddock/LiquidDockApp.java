package com.hellovoid.liquiddock;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.hellovoid.liquiddock.config.ConfigMigration;
import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.Map;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** Module-process service bridge used by the settings UI for API101 Remote Preferences. */
public final class LiquidDockApp extends Application
        implements XposedServiceHelper.OnServiceListener,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static volatile XposedService service;
    private SharedPreferences localPreferences;
    private boolean reconciling;

    @Override
    public void onCreate() {
        super.onCreate();
        localPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Upgrade the app-local authority before XposedService can synchronously bind and seed
        // Remote Preferences. Otherwise a stale local store can overwrite the Launcher's freshly
        // migrated API101 store during reconciliation.
        ConfigMigration.migrate(this, localPreferences);
        localPreferences.registerOnSharedPreferenceChangeListener(this);
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService value) {
        service = value;
        try {
            reconcileOnBind();
        } catch (Throwable error) {
            Log.w("LiquidDock", "initial Remote Preferences reconciliation failed", error);
        }
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (reconciling) return;
        try {
            boolean masterChange = ConfigSchema.Core.ENABLED.name().equals(key);
            // The master switch is a process-lifecycle boundary. Commit the complete current
            // snapshot before restarting so a bulk import cannot restart Launcher between keys.
            boolean synced = masterChange
                    ? syncToRemote(sharedPreferences)
                    : syncKeyToRemote(key, sharedPreferences, false);
            if (masterChange) {
                if (synced) restartLauncherAfterMasterChange();
                else Log.w("LiquidDock", "Master switch changed but Remote Preferences are unavailable; Launcher not restarted");
            }
        } catch (Throwable error) {
            Log.w("LiquidDock", "Remote Preferences sync failed for " + key, error);
        }
    }

    private void reconcileOnBind() {
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null || localPreferences == null) return;
        Map<String, ?> localAll = localPreferences.getAll();
        Map<String, ?> remoteAll = remote.getAll();

        // Normal upgrade path: the existing app-local settings are authoritative and are
        // copied once into API101 Remote Preferences.  If the local store is empty but the
        // injected module has already migrated legacy JSON into Remote Preferences, pull that
        // data back into the UI instead of accidentally clearing the newly migrated group.
        if ((localAll == null || localAll.isEmpty())
                && remoteAll != null && !remoteAll.isEmpty()) {
            reconciling = true;
            try {
                copyAll(remote, localPreferences);
                Log.i("LiquidDock", "seeded local UI prefs from API101 Remote Preferences");
            } finally {
                reconciling = false;
            }
        } else if (localAll != null && !localAll.isEmpty()) {
            syncToRemote(localPreferences);
            Log.i("LiquidDock", "seeded API101 Remote Preferences from local UI prefs");
        }
    }

    public static XposedService service() { return service; }

    public static SharedPreferences remotePreferences(String group) {
        XposedService value = service;
        return value != null ? value.getRemotePreferences(group) : null;
    }

    /** Full-seed only — used on initial bind and process-lifecycle configuration boundaries. */
    public static boolean syncToRemote(SharedPreferences local) {
        if (local == null) return false;
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return false;
        return copyAll(local, remote);
    }

    /** Incremental: sync one ordinary key without blocking slider/control interaction. */
    private static boolean syncKeyToRemote(
            String key, SharedPreferences source, boolean synchronous) {
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return false;
        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) return false;
        Map<String, ?> all = source.getAll();
        Object value = all.get(key);
        if (value == null) {
            editor.remove(key);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof java.util.Set) {
            @SuppressWarnings("unchecked")
            java.util.Set<String> strings = (java.util.Set<String>) value;
            editor.putStringSet(key, strings);
        }
        if (synchronous) return editor.commit();
        editor.apply();
        return true;
    }

    private static void restartLauncherAfterMasterChange() {
        LauncherRestart.restartAsync("master-switch");
    }

    private static boolean copyAll(SharedPreferences source, SharedPreferences destination) {
        SharedPreferences.Editor editor = destination.edit();
        if (editor == null) return false;
        editor.clear();
        for (Map.Entry<String, ?> entry : source.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof java.util.Set) {
                @SuppressWarnings("unchecked")
                java.util.Set<String> strings = (java.util.Set<String>) value;
                editor.putStringSet(key, strings);
            }
        }
        return editor.commit();
    }
}
