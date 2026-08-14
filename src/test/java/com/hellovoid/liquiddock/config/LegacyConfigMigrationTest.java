package com.hellovoid.liquiddock.config;

import com.hellovoid.liquiddock.TestSharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LegacyConfigMigrationTest {
    @Test
    public void explicitProcessStartMigrationCommitsEverySupportedLegacyValueType() {
        TestSharedPreferences remote = new TestSharedPreferences(new HashMap<>());
        Map<String, Object> legacy = new HashMap<>();
        legacy.put("enabled", true);
        legacy.put("int_value", 7);
        legacy.put("long_value", 8L);
        legacy.put("float_value", 1.25f);
        legacy.put("double_value", 2.5d);
        legacy.put("string_value", "kept");

        LegacyConfigMigration.migrateLegacyValues(remote, legacy);

        assertEquals(legacy.get("enabled"), remote.getAll().get("enabled"));
        assertEquals(legacy.get("int_value"), remote.getAll().get("int_value"));
        assertEquals(legacy.get("long_value"), remote.getAll().get("long_value"));
        assertEquals(legacy.get("float_value"), remote.getAll().get("float_value"));
        assertEquals(2.5f, remote.getAll().get("double_value"));
        assertEquals(legacy.get("string_value"), remote.getAll().get("string_value"));
        assertEquals(1, remote.editCount());
        assertEquals(1, remote.commitCount());
    }
}
