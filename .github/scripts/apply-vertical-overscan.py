from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one anchor, found {count}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


# Config schema: restore the historical pixel-valued public contract.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Integer> CAPTURE_BLEED_TOP = dp(\n                "liquid_capture_bleed_top", 17, -1, 48, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = dp(\n                "liquid_capture_bleed_bottom", 6, -1, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);''',
    '''        public static final ConfigKey<Integer> CAPTURE_BLEED_TOP = integer(\n                "liquid_capture_bleed_top", 48, 48, 48, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = integer(\n                "liquid_capture_bleed_bottom", 16, 16, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);''')

# Compose GUI: restore two independent whole-pixel controls and update their descriptions.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    "liquid_capture_bleed_top" -> "在 Dock 上方多捕获的折射取样区域"\n    "liquid_capture_bleed_bottom" -> "在 Dock 下方多捕获的折射取样区域"''',
    '''    "liquid_capture_bleed_top" -> "在 Dock 上方扩展 zero-copy 折射背景的采样区域"\n    "liquid_capture_bleed_bottom" -> "在 Dock 下方扩展 zero-copy 折射背景的采样区域"''')
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    IntSpec(ConfigSchema.Glass.LENS_REFRACTION, "透镜折射"),\n    IntSpec(ConfigSchema.Glass.CHROMATIC, "色散强度", ""),\n    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),''',
    '''    IntSpec(ConfigSchema.Glass.LENS_REFRACTION, "透镜折射"),\n    IntSpec(ConfigSchema.Glass.CHROMATIC, "色散强度", ""),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, "上额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, "下额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),''')

# Runtime config: surface the direct pixel values to the zero-copy renderer.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final int tintAlpha, tintR, tintG, tintB, specularSharp,\n                prismalShadowR, prismalShadowG, prismalShadowB, prismalShadowAlpha;''',
    '''        final int captureBleedTopPx, captureBleedBottomPx;\n        final int tintAlpha, tintR, tintG, tintB, specularSharp,\n                prismalShadowR, prismalShadowG, prismalShadowB, prismalShadowAlpha;''')
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            chromatic = c.i(ConfigSchema.Glass.CHROMATIC.name(),\n                    ConfigSchema.Glass.CHROMATIC.runtimeFallback());\n            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),''',
    '''            chromatic = c.i(ConfigSchema.Glass.CHROMATIC.name(),\n                    ConfigSchema.Glass.CHROMATIC.runtimeFallback());\n            captureBleedTopPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_TOP.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback()), 0, 256);\n            captureBleedBottomPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback()), 0, 256);\n            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),''')

# Renderer: retain fixed horizontal overscan, make vertical sampling asymmetric and configurable.
view = "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
replace_once(
    view,
    '''    // Keep real scene pixels around the visible Dock so refraction can see approaching content\n    // before it crosses the material edge. Output remains clipped to the Dock itself.\n    private static final float EDGE_OVERSCAN_DP = 32f;''',
    '''    // Left/right keep a fixed GPU overscan ring. Top/bottom use the historical GUI pixel\n    // controls so users can tune how early approaching content enters Prismal refraction.\n    // Output remains clipped to the visible Dock itself.\n    private static final float EDGE_OVERSCAN_DP = 32f;''')
replace_once(
    view,
    '''    private volatile int outputWidth;\n    private volatile int outputHeight;\n\n    // Stage A samples a real overscan ring around the visible Dock.''',
    '''    private volatile int outputWidth;\n    private volatile int outputHeight;\n    private volatile int topOverscanPx = 48;\n    private volatile int bottomOverscanPx = 16;\n\n    // Stage A samples a real overscan ring around the visible Dock.''')
replace_once(
    view,
    '''    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {\n        if (glassConfig == null || shuttingDown) return;\n        opticalParams = Miuix307PrismalMaterial.fromConfig(\n                glassConfig, getResources().getDisplayMetrics().density);\n        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));\n    }''',
    '''    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {\n        if (glassConfig == null || shuttingDown) return;\n        opticalParams = Miuix307PrismalMaterial.fromConfig(\n                glassConfig, getResources().getDisplayMetrics().density);\n        topOverscanPx = Math.max(0, glassConfig.captureBleedTopPx);\n        bottomOverscanPx = Math.max(0, glassConfig.captureBleedBottomPx);\n        updateBackdropMapping();\n        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));\n    }''')
replace_once(
    view,
    '''    private void ensureFboSize(int width, int height) {\n        int overscanPx = edgeOverscanPx();\n        int nextWidth = Math.max(1, width + overscanPx * 2);\n        int nextHeight = Math.max(1, height + overscanPx * 2);''',
    '''    private void ensureFboSize(int width, int height) {\n        int overscanPx = horizontalOverscanPx();\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int nextWidth = Math.max(1, width + overscanPx * 2);\n        int nextHeight = Math.max(1, height + topOverscanPx + bottomOverscanPx);''')
replace_once(
    view,
    '''    private int edgeOverscanPx() {\n        float density = getResources().getDisplayMetrics().density;\n        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));\n    }''',
    '''    private int horizontalOverscanPx() {\n        float density = getResources().getDisplayMetrics().density;\n        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));\n    }''')
replace_once(
    view,
    '''        int overscanPx = edgeOverscanPx();\n        int sampleWidth = hostWidth + overscanPx * 2;\n        int sampleHeight = hostHeight + overscanPx * 2;\n        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(\n                hostScreen[0] - overscanPx, hostScreen[1] - overscanPx,\n                sampleWidth, sampleHeight,\n                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());''',
    '''        int overscanPx = horizontalOverscanPx();\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int sampleWidth = hostWidth + overscanPx * 2;\n        int sampleHeight = hostHeight + topOverscanPx + bottomOverscanPx;\n        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(\n                hostScreen[0] - overscanPx, hostScreen[1] - topOverscanPx,\n                sampleWidth, sampleHeight,\n                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());''')
replace_once(
    view,
    '''        float nextDockUvLeft = overscanPx / (float) sampleWidth;\n        float nextDockUvBottom = overscanPx / (float) sampleHeight;\n        float nextDockUvWidth = hostWidth / (float) sampleWidth;''',
    '''        float nextDockUvLeft = overscanPx / (float) sampleWidth;\n        float nextDockUvBottom = bottomOverscanPx / (float) sampleHeight;\n        float nextDockUvWidth = hostWidth / (float) sampleWidth;''')

# Default preset follows the restored historical GUI defaults and no longer writes DP sidecars.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/PresetManager.java",
    '''        putDp(values, "liquid_capture_bleed_top", 17f);\n        putDp(values, "liquid_capture_bleed_bottom", 16f);''',
    '''        values.put("liquid_capture_bleed_top", 48);\n        values.put("liquid_capture_bleed_bottom", 16);''')

# Migration: undo the temporary 2.0 DP interpretation before the remaining liquid DP migration.
migration = "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java"
replace_once(
    migration,
    '''    private static final String PRISMAL_OFFICIAL_PARITY_V3 =\n            "liquid_prismal_official_parity_v3";''',
    '''    private static final String PRISMAL_OFFICIAL_PARITY_V3 =\n            "liquid_prismal_official_parity_v3";\n    private static final String CAPTURE_BLEED_PIXELS_V4 =\n            "liquid_capture_bleed_pixels_v4";''')
replace_once(
    migration,
    '''        migrateGridToOffsets(preferences);\n        migrateCornersToDp(context, preferences);\n        migrateLiquidDimensionsToDp(context, preferences);''',
    '''        migrateGridToOffsets(preferences);\n        migrateCornersToDp(context, preferences);\n        migrateCaptureBleedToPixels(context, preferences);\n        migrateLiquidDimensionsToDp(context, preferences);''')
replace_once(
    migration,
    '''    private static void migrateLiquidDimensionsToDp(Context context, SharedPreferences sp) {\n        if (sp.getBoolean("liquid_dimensions_dp", false)) return;\n        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);\n        SharedPreferences.Editor e = sp.edit();\n        e.putInt("liquid_blur", Math.round(sp.getInt("liquid_blur", 6) / density));\n        e.putInt("liquid_capture_bleed_top",\n                Math.round(sp.getInt("liquid_capture_bleed_top", 48) / density));\n        e.putInt("liquid_capture_bleed_bottom",\n                Math.round(sp.getInt("liquid_capture_bleed_bottom", 16) / density));\n        e.putBoolean("liquid_dimensions_dp", true).commit();\n    }''',
    '''    private static void migrateCaptureBleedToPixels(Context context, SharedPreferences sp) {\n        if (sp.getBoolean(CAPTURE_BLEED_PIXELS_V4, false)) return;\n        float density = Math.max(0.1f, context.getResources().getDisplayMetrics().density);\n        boolean storedAsDp = sp.getBoolean("liquid_dimensions_dp", false);\n        SharedPreferences.Editor e = sp.edit();\n        migrateCaptureBleedPixelValue(\n                sp, e, "liquid_capture_bleed_top", 48, storedAsDp, density);\n        migrateCaptureBleedPixelValue(\n                sp, e, "liquid_capture_bleed_bottom", 16, storedAsDp, density);\n        e.putBoolean(CAPTURE_BLEED_PIXELS_V4, true).commit();\n    }\n\n    private static void migrateCaptureBleedPixelValue(\n            SharedPreferences sp, SharedPreferences.Editor e, String key,\n            int historicalDefaultPx, boolean storedAsDp, float density) {\n        boolean hasDirect = sp.contains(key);\n        boolean hasTenths = sp.contains(key + "_tenths");\n        if (!hasDirect && !hasTenths) {\n            e.remove(key + "_tenths");\n            return;\n        }\n        float storedValue = hasTenths\n                ? sp.getInt(key + "_tenths", historicalDefaultPx * 10) / 10f\n                : sp.getInt(key, historicalDefaultPx);\n        e.putInt(key, captureBleedPixels(\n                storedValue, storedAsDp, density, historicalDefaultPx));\n        e.remove(key + "_tenths");\n    }\n\n    static int captureBleedPixels(\n            float storedValue, boolean storedAsDp, float density, int historicalDefaultPx) {\n        float safeDensity = Math.max(0.1f, density);\n        float pixels = storedValue;\n        if (storedAsDp) {\n            float oldMigratedDefaultDp = Math.round(historicalDefaultPx / safeDensity);\n            pixels = Math.abs(storedValue - oldMigratedDefaultDp) <= 0.0001f\n                    ? historicalDefaultPx\n                    : storedValue * safeDensity;\n        }\n        return Math.max(0, Math.min(256, Math.round(pixels)));\n    }\n\n    private static void migrateLiquidDimensionsToDp(Context context, SharedPreferences sp) {\n        if (sp.getBoolean("liquid_dimensions_dp", false)) return;\n        float density = Math.max(1f, context.getResources().getDisplayMetrics().density);\n        SharedPreferences.Editor e = sp.edit();\n        e.putInt("liquid_blur", Math.round(sp.getInt("liquid_blur", 6) / density));\n        e.putBoolean("liquid_dimensions_dp", true).commit();\n    }''')

# Existing schema tests: bleed is now intentionally DIRECT and has unified 48/16 defaults.
schema_test = "src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java"
replace_once(
    schema_test,
    '''        assertEquals(Integer.valueOf(-1), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());\n        assertEquals(Integer.valueOf(17), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());\n        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());''',
    '''        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());\n        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());\n        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());\n        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback());\n        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.uiDefault());\n        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.exportDefault());\n        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_TOP.storageMode());\n        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.storageMode());''')
replace_once(
    schema_test,
    '''        assertComposeIntSpec(ConfigSchema.Glass.CHROMATIC, 2, 0, 40);\n        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 38, 0, 160);''',
    '''        assertComposeIntSpec(ConfigSchema.Glass.CHROMATIC, 2, 0, 40);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, 48, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, 16, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 38, 0, 160);''')

# Preset test: direct pixel keys have no _tenths sidecars.
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java",
    '''        expected.put("liquid_capture_bleed_top", 17);\n        expected.put("liquid_capture_bleed_top_tenths", 170);\n        expected.put("liquid_capture_bleed_bottom", 16);\n        expected.put("liquid_capture_bleed_bottom_tenths", 160);''',
    '''        expected.put("liquid_capture_bleed_top", 48);\n        expected.put("liquid_capture_bleed_bottom", 16);''')

# Real migration conversion behavior, including recovery of old density-divided defaults.
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigMigrationTest.java",
    '''    @Test\n    public void allLegacyLensValuesPreserveTheirOpticalScale() {\n        assertEquals(1f, ConfigMigration.prismalLensScale(12f), 0.0001f);\n        assertEquals(1.5f, ConfigMigration.prismalLensScale(18f), 0.0001f);\n        assertEquals(2f, ConfigMigration.prismalLensScale(24f), 0.0001f);\n        assertEquals(0.25f, ConfigMigration.prismalLensScale(0f), 0.0001f);\n    }\n}''',
    '''    @Test\n    public void allLegacyLensValuesPreserveTheirOpticalScale() {\n        assertEquals(1f, ConfigMigration.prismalLensScale(12f), 0.0001f);\n        assertEquals(1.5f, ConfigMigration.prismalLensScale(18f), 0.0001f);\n        assertEquals(2f, ConfigMigration.prismalLensScale(24f), 0.0001f);\n        assertEquals(0.25f, ConfigMigration.prismalLensScale(0f), 0.0001f);\n    }\n\n    @Test\n    public void captureBleedRollbackRestoresPixelSemantics() {\n        assertEquals(48, ConfigMigration.captureBleedPixels(48f, false, 3f, 48));\n        assertEquals(16, ConfigMigration.captureBleedPixels(16f, false, 3f, 16));\n        assertEquals(48, ConfigMigration.captureBleedPixels(16f, true, 3f, 48));\n        assertEquals(16, ConfigMigration.captureBleedPixels(5f, true, 3f, 16));\n        assertEquals(60, ConfigMigration.captureBleedPixels(20f, true, 3f, 48));\n        assertEquals(256, ConfigMigration.captureBleedPixels(999f, false, 3f, 48));\n    }\n}''')

print("vertical overscan patch applied")
