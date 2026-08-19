package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Canonical config and legacy-8x4 compatibility contract for selectable grid profiles. */
public class GridProfileConfigContractTest {

    @Test
    public void schemaUsesCanonicalMasterAndProfileWhileLegacyKeyIsImportOnly() {
        assertEquals("home_grid_extended", ConfigSchema.Grid.ENABLED.name());
        assertEquals(Boolean.FALSE, ConfigSchema.Grid.ENABLED.uiDefault());
        assertEquals("grid_profile", ConfigSchema.Grid.PROFILE.name());
        assertEquals("8x4", ConfigSchema.Grid.PROFILE.uiDefault());
        assertEquals("home_grid_8x4", ConfigSchema.Grid.LEGACY_8X4.name());
        assertEquals(ConfigKey.ExportMode.NEVER, ConfigSchema.Grid.LEGACY_8X4.exportMode());
    }

    @Test
    public void pureMigrationStatePreservesCanonicalValuesAndNormalizesProfile() {
        ConfigMigration.GridProfileState canonical = ConfigMigration.resolveGridProfileState(
                Boolean.FALSE, "10x6", Boolean.TRUE);
        assertFalse(canonical.enabled);
        assertEquals("10x6", canonical.profile);

        ConfigMigration.GridProfileState legacy = ConfigMigration.resolveGridProfileState(
                null, null, Boolean.TRUE);
        assertTrue(legacy.enabled);
        assertEquals("8x4", legacy.profile);

        ConfigMigration.GridProfileState invalid = ConfigMigration.resolveGridProfileState(
                Boolean.TRUE, "invalid", Boolean.FALSE);
        assertTrue(invalid.enabled);
        assertEquals("8x4", invalid.profile);
    }

    @Test
    public void legacyJsonImportsIntoCanonicalKeysOnly() {
        Map<String, Object> json = new HashMap<>();
        json.put("home_grid_8x4", true);

        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(Boolean.TRUE, imported.get("home_grid_extended"));
        assertEquals("8x4", imported.get("grid_profile"));
        assertFalse(imported.containsKey("home_grid_8x4"));
    }

    @Test
    public void canonicalJsonWinsAndInvalidProfilesNormalize() {
        Map<String, Object> json = new HashMap<>();
        json.put("home_grid_extended", false);
        json.put("grid_profile", "invalid");
        json.put("home_grid_8x4", true);

        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals(Boolean.FALSE, imported.get("home_grid_extended"));
        assertEquals("8x4", imported.get("grid_profile"));
        assertFalse(imported.containsKey("home_grid_8x4"));
    }

    @Test
    public void oldPreferenceSnapshotExportsCanonicalBackup() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("home_grid_8x4", true);

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE, exported.get("home_grid_extended"));
        assertEquals("8x4", exported.get("grid_profile"));
        assertFalse(exported.containsKey("home_grid_8x4"));
    }

    @Test
    public void canonicalTenBySixRoundTrips() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("home_grid_extended", true);
        prefs.put("grid_profile", "10x6");

        Map<String, Object> exported = ConfigCodec.exportValues(prefs);
        assertEquals(Boolean.TRUE, exported.get("home_grid_extended"));
        assertEquals("10x6", exported.get("grid_profile"));

        Map<String, Object> imported = ConfigCodec.importValues(exported);
        assertEquals(Boolean.TRUE, imported.get("home_grid_extended"));
        assertEquals("10x6", imported.get("grid_profile"));
    }
}
