package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hellovoid.liquiddock.config.PresetManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;

/** Locks the complete retirement of LiquidDock v1.2's legacy S-curve refraction path. */
public class LegacySCurveRetirementContractTest {
    private static final Path MAIN = Path.of("src/main");
    private static final Path PRISMAL_MAIN = Path.of("prismal/src/main");
    private static final Path MIGRATION = MAIN.resolve(
            "java/com/hellovoid/liquiddock/config/ConfigMigration.java");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".kt", ".glsl", ".xml", ".properties", ".json", ".txt");

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void currentConfigAndUiNoLongerExposeLegacySCurve() throws Exception {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String config = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        String presets = read("src/main/java/com/hellovoid/liquiddock/config/PresetManager.java");
        String ui = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

        assertFalse(schema.contains("LEGACY_S_CURVE"));
        assertFalse(schema.contains("liquid_legacy_s_curve"));
        assertFalse(config.contains("legacySCurveStrength"));
        assertFalse(presets.contains("liquid_legacy_s_curve"));
        assertFalse(ui.contains("v1.2 S形折射"));
        assertFalse(ui.contains("LEGACY_S_CURVE"));
    }

    @Test
    public void currentPresetDoesNotRecreateRetiredPreference() {
        assertFalse(PresetManager.defaultValues().containsKey("liquid_legacy_s_curve"));
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
    public void noProductionSourceRetainsLegacySymbolsOutsideOneWayPreferencePurge() throws Exception {
        String[] forbidden = {
                "LEGACY_S_CURVE",
                "legacySCurveStrength",
                "legacyLensRefractionPx",
                "legacyThicknessPx",
                "u_legacySCurveStrength",
                "u_legacyLensRefractionPx",
                "u_legacyThicknessPx",
                "v1.2 S形折射"
        };
        scanTextSources(MAIN, forbidden);
        scanTextSources(PRISMAL_MAIN, forbidden);
    }

    @Test
    public void migrationExplicitlyPurgesRetiredPreference() throws Exception {
        String migration = Files.readString(MIGRATION);
        assertTrue(migration.contains("removeRetiredGlassPreferences(preferences)"));
        assertTrue(migration.indexOf("removeRetiredGlassPreferences(preferences)")
                < migration.indexOf("resetUnsupportedGlassConfigGeneration(preferences)"));
        assertTrue(migration.contains("e.remove(\"liquid_legacy_s_curve\")"));
    }

    private static void scanTextSources(Path root, String[] forbidden) throws Exception {
        if (!Files.exists(root)) return;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                if (file.equals(MIGRATION) || !isTextSource(file)) continue;
                String source = Files.readString(file);
                for (String token : forbidden) {
                    assertFalse(file + " still contains retired token " + token,
                            source.contains(token));
                }
            }
        }
    }

    private static boolean isTextSource(Path file) {
        String name = file.getFileName().toString();
        for (String extension : TEXT_EXTENSIONS) {
            if (name.endsWith(extension)) return true;
        }
        return false;
    }
}
