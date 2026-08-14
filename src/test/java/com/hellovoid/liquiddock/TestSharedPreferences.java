package com.hellovoid.liquiddock;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory SharedPreferences implementation for observing real editor writes. */
public final class TestSharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private int editCount;
    private int commitCount;

    public TestSharedPreferences(Map<String, ?> initialValues) {
        values.putAll(initialValues);
    }

    public int editCount() { return editCount; }
    public int commitCount() { return commitCount; }

    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public String getString(String key, String defValue) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : defValue;
    }
    @Override public Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = values.get(key);
        if (!(value instanceof Set)) return defValues;
        @SuppressWarnings("unchecked") Set<String> strings = (Set<String>) value;
        return new HashSet<>(strings);
    }
    @Override public int getInt(String key, int defValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : defValue;
    }
    @Override public long getLong(String key, long defValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).longValue() : defValue;
    }
    @Override public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : defValue;
    }
    @Override public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }
    @Override public boolean contains(String key) { return values.containsKey(key); }

    @Override public Editor edit() {
        editCount++;
        return new MemoryEditor();
    }

    @Override public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {}

    private final class MemoryEditor implements Editor {
        private final Map<String, Object> updates = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override public Editor putString(String key, String value) {
            updates.put(key, value); return this;
        }
        @Override public Editor putStringSet(String key, Set<String> value) {
            updates.put(key, value == null ? null : new HashSet<>(value)); return this;
        }
        @Override public Editor putInt(String key, int value) {
            updates.put(key, value); return this;
        }
        @Override public Editor putLong(String key, long value) {
            updates.put(key, value); return this;
        }
        @Override public Editor putFloat(String key, float value) {
            updates.put(key, value); return this;
        }
        @Override public Editor putBoolean(String key, boolean value) {
            updates.put(key, value); return this;
        }
        @Override public Editor remove(String key) {
            removals.add(key); return this;
        }
        @Override public Editor clear() { clear = true; return this; }
        @Override public boolean commit() {
            commitCount++;
            applyUpdates();
            return true;
        }
        @Override public void apply() { applyUpdates(); }

        private void applyUpdates() {
            if (clear) values.clear();
            for (String key : removals) values.remove(key);
            values.putAll(updates);
        }
    }
}
