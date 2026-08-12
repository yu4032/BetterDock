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
        implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService value) {
        service = value;
        try {
            SharedPreferences local = PreferenceManager.getDefaultSharedPreferences(this);
            syncToRemote(local);
        } catch (Throwable error) {
            Log.w("LiquidDock", "initial Remote Preferences seed failed", error);
        }
    }

    @Override
    public void onServiceDied(XposedService value) {
        if (service == value) service = null;
    }

    public static XposedService service() {
        return service;
    }

    public static SharedPreferences remotePreferences(String group) {
        XposedService value = service;
        return value != null ? value.getRemotePreferences(group) : null;
    }

    /** Mirror the existing settings store into API101 Remote Preferences. */
    public static boolean syncToRemote(SharedPreferences local) {
        SharedPreferences remote = remotePreferences(ConfigReader.REMOTE_GROUP);
        if (remote == null) return false;

        SharedPreferences.Editor editor = remote.edit();
        if (editor == null) return false;
        editor.clear();
        for (Map.Entry<String, ?> entry : local.getAll().entrySet()) {
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
        editor.apply();
        return true;
    }
}
