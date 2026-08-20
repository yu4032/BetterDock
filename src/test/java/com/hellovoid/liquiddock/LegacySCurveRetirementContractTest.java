package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the complete retirement of LiquidDock v1.2's legacy S-curve refraction path. */
public class LegacySCurveRetirementContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void currentConfigAndUiNoLongerExposeLegacySCurve() throws Exception {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String config = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        String ui = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

        assertFalse(schema.contains("LEGACY_S_CURVE"));
        assertFalse(schema.contains("liquid_legacy_s_curve"));
        assertFalse(config.contains("legacySCurveStrength"));
        assertFalse(ui.contains("v1.2 S形折射"));
        assertFalse(ui.contains("LEGACY_S_CURVE"));
    }

    @Test
    public void renderPathNoLongerContainsLegacySCurveOptics() throws Exception {
        String material = read("src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java");
        String shader = read("src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java");

        assertFalse(material.contains("legacySCurveStrength"));
        assertFalse(material.contains("legacyLensRefractionPx"));
        assertFalse(material.contains("legacyThicknessPx"));
        assertFalse(shader.contains("u_legacySCurveStrength"));
        assertFalse(shader.contains("u_legacyLensRefractionPx"));
        assertFalse(shader.contains("u_legacyThicknessPx"));
        assertFalse(shader.contains("LiquidDock v1.2.0"));
    }

    @Test
    public void migrationExplicitlyPurgesRetiredPreference() throws Exception {
        String migration = read("src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java");
        assertTrue(migration.contains("removeRetiredGlassPreferences(preferences)"));
        assertTrue(migration.contains("e.remove(\"liquid_legacy_s_curve\")"));
    }
}
