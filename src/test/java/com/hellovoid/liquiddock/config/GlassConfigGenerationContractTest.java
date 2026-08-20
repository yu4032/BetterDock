package com.hellovoid.liquiddock.config;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlassConfigGenerationContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java");

    @Test
    public void glassConfigUsesGenerationResetInsteadOfHistoricalValueConversions() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("GLASS_CONFIG_GENERATION"));
        assertTrue(source.contains("resetUnsupportedGlassConfigGeneration(preferences)"));
        assertTrue(source.contains("key.startsWith(\"liquid_\")"));
        assertTrue(source.contains("\"liquid_glass\".equals(key)"));
        assertTrue(source.contains("\"liquid_miuix_307_pipeline\".equals(key)"));

        assertFalse(source.contains("migratePrismalParityV2"));
        assertFalse(source.contains("migratePrismalOfficialParityV3"));
        assertFalse(source.contains("migratePrismalOfficialParityV4"));
        assertFalse(source.contains("migrateLegacyLensScale"));
        assertFalse(source.contains("prismalLensScale"));
        assertFalse(source.contains("migrateCaptureBleedToPixels"));
        assertFalse(source.contains("migrateLiquidDimensionsToDp"));
    }
}
