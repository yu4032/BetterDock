package com.hellovoid.liquiddock;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

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
            syncToRemote(sharedPreferences);
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

    /** Mirror the settings UI store into API101 Remote Preferences. */
    public static boolean syncToRemote(SharedPreferences local) {
        if (local == null) return false;
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return false;
        copyAll(local, remote);
        return true;
    }

    private static void copyAll(SharedPreferences source, SharedPreferences destination) {
        SharedPreferences.Editor editor = destination.edit();
        if (editor == null) return;
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
        editor.commit();
    }
}
