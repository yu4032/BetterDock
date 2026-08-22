package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

/** Android-backed sidecar store; does not modify MIUI launcher database schema. */
final class HomeGridSharedPreferencesMemoryStore implements HomeGridOrientationMemoryStore {
    private final SharedPreferences preferences;

    HomeGridSharedPreferencesMemoryStore(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences == null");
        this.preferences = preferences;
    }

    @Override
    public String read(String key) {
        return preferences.getString(key, null);
    }

    @Override
    public void write(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    @Override
    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }
}
