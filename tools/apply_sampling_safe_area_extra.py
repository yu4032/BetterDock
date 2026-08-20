from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text()


def write(rel, text):
    (ROOT / rel).write_text(text)
    print("updated", rel)


def replace(rel, old, new):
    text = read(rel)
    if old not in text:
        raise SystemExit(f"missing expected text in {rel}: {old[:120]!r}")
    write(rel, text.replace(old, new))


# Config schema: new semantics use new keys so historical 48/16 values are never reinterpreted.
replace(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Integer> CAPTURE_BLEED_TOP = integer(\n                "liquid_capture_bleed_top", 48, 48, 48, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = integer(\n                "liquid_capture_bleed_bottom", 16, 16, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_LEFT = integer(\n                "liquid_capture_bleed_left", 0, 0, 0, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_RIGHT = integer(\n                "liquid_capture_bleed_right", 0, 0, 0, 0, 256, ConfigKey.ExportMode.ALWAYS);''',
    '''        public static final ConfigKey<Integer> SAMPLING_EXTRA_TOP = integer(\n                "liquid_sampling_extra_top", 0, 0, 0, -256, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> SAMPLING_EXTRA_BOTTOM = integer(\n                "liquid_sampling_extra_bottom", 0, 0, 0, -256, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> SAMPLING_EXTRA_LEFT = integer(\n                "liquid_sampling_extra_left", 0, 0, 0, -256, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> SAMPLING_EXTRA_RIGHT = integer(\n                "liquid_sampling_extra_right", 0, 0, 0, -256, 256, ConfigKey.ExportMode.ALWAYS);''')

# Current preset: zero means pure automatic safe area.
for old, new in [
    ('values.put("liquid_capture_bleed_top", 48);', 'values.put("liquid_sampling_extra_top", 0);'),
    ('values.put("liquid_capture_bleed_bottom", 16);', 'values.put("liquid_sampling_extra_bottom", 0);'),
    ('values.put("liquid_capture_bleed_left", 0);', 'values.put("liquid_sampling_extra_left", 0);'),
    ('values.put("liquid_capture_bleed_right", 0);', 'values.put("liquid_sampling_extra_right", 0);'),
]:
    replace("src/main/java/com/hellovoid/liquiddock/config/PresetManager.java", old, new)

# Config reader: signed values are literal user extras, not absolute guard sizes.
replace(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final int captureBleedTopPx, captureBleedBottomPx,\n                captureBleedLeftPx, captureBleedRightPx;''',
    '''        final int samplingExtraTopPx, samplingExtraBottomPx,\n                samplingExtraLeftPx, samplingExtraRightPx;''')
replace(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            captureBleedTopPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_TOP.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback()), 0, 256);\n            captureBleedBottomPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback()), 0, 256);\n            captureBleedLeftPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_LEFT.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_LEFT.runtimeFallback()), 0, 256);\n            captureBleedRightPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.runtimeFallback()), 0, 256);''',
    '''            samplingExtraTopPx = clamp(c.i(ConfigSchema.Glass.SAMPLING_EXTRA_TOP.name(),\n                    ConfigSchema.Glass.SAMPLING_EXTRA_TOP.runtimeFallback()), -256, 256);\n            samplingExtraBottomPx = clamp(c.i(ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.name(),\n                    ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.runtimeFallback()), -256, 256);\n            samplingExtraLeftPx = clamp(c.i(ConfigSchema.Glass.SAMPLING_EXTRA_LEFT.name(),\n                    ConfigSchema.Glass.SAMPLING_EXTRA_LEFT.runtimeFallback()), -256, 256);\n            samplingExtraRightPx = clamp(c.i(ConfigSchema.Glass.SAMPLING_EXTRA_RIGHT.name(),\n                    ConfigSchema.Glass.SAMPLING_EXTRA_RIGHT.runtimeFallback()), -256, 256);''')

# Renderer: automatic optical/base guard first, then signed per-side user adjustment.
view_rel = "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
replace(
    view_rel,
    '''    // Left/right keep the fixed 32dp GPU overscan as a compatibility baseline and can add\n    // independent GUI pixel extras. Top/bottom remain fully controlled by their historical pixel\n    // values. Output remains clipped to the visible Dock itself.''',
    '''    // PrismalSampling computes the automatic optical safety ring. Left/right also retain the\n    // fixed 32dp compatibility baseline. GUI values are signed extras applied after that automatic\n    // guard: positive expands, negative shrinks, and the final inset never goes below zero.''')
replace(
    view_rel,
    '''    private volatile int topOverscanPx = 48;\n    private volatile int bottomOverscanPx = 16;\n    private volatile int leftExtraOverscanPx;\n    private volatile int rightExtraOverscanPx;''',
    '''    private volatile int topSamplingExtraPx;\n    private volatile int bottomSamplingExtraPx;\n    private volatile int leftSamplingExtraPx;\n    private volatile int rightSamplingExtraPx;''')
replace(
    view_rel,
    '''        topOverscanPx = Math.max(0, glassConfig.captureBleedTopPx);\n        bottomOverscanPx = Math.max(0, glassConfig.captureBleedBottomPx);\n        leftExtraOverscanPx = Math.max(0, glassConfig.captureBleedLeftPx);\n        rightExtraOverscanPx = Math.max(0, glassConfig.captureBleedRightPx);''',
    '''        topSamplingExtraPx = glassConfig.samplingExtraTopPx;\n        bottomSamplingExtraPx = glassConfig.samplingExtraBottomPx;\n        leftSamplingExtraPx = glassConfig.samplingExtraLeftPx;\n        rightSamplingExtraPx = glassConfig.samplingExtraRightPx;''')
replace(
    view_rel,
    '''        int left = Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX);\n        int right = Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX);\n        int top = Math.max(Math.max(0, topOverscanPx), opticalY);\n        int bottom = Math.max(Math.max(0, bottomOverscanPx), opticalY);''',
    '''        int autoHorizontal = Math.max(horizontalOverscanPx(), opticalX);\n        int left = combineAutoGuardAndUserExtra(autoHorizontal, leftSamplingExtraPx);\n        int right = combineAutoGuardAndUserExtra(autoHorizontal, rightSamplingExtraPx);\n        int top = combineAutoGuardAndUserExtra(opticalY, topSamplingExtraPx);\n        int bottom = combineAutoGuardAndUserExtra(opticalY, bottomSamplingExtraPx);''')
replace(
    view_rel,
    '''    private static int[] fitInsetPairToTextureLimit(\n            int visible, int before, int after, int maxTextureSize) {''',
    '''    private static int combineAutoGuardAndUserExtra(int automaticGuardPx, int userExtraPx) {\n        long combined = (long) Math.max(0, automaticGuardPx) + userExtraPx;\n        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, combined));\n    }\n\n    private static int[] fitInsetPairToTextureLimit(\n            int visible, int before, int after, int maxTextureSize) {''')

# UI wording and summaries make the additive signed rule explicit.
ui_rel = "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"
for old, new in [
    ('"liquid_capture_bleed_top" -> "在 Dock 上方扩展 zero-copy 折射背景的采样区域"',
     '"liquid_sampling_extra_top" -> "最终上安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"'),
    ('"liquid_capture_bleed_bottom" -> "在 Dock 下方扩展 zero-copy 折射背景的采样区域"',
     '"liquid_sampling_extra_bottom" -> "最终下安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"'),
    ('"liquid_capture_bleed_left" -> "在默认 32dp 左侧采样之外继续扩展的 zero-copy 区域"',
     '"liquid_sampling_extra_left" -> "最终左安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"'),
    ('"liquid_capture_bleed_right" -> "在默认 32dp 右侧采样之外继续扩展的 zero-copy 区域"',
     '"liquid_sampling_extra_right" -> "最终右安全区 = 自动安全区 + 此值；可正可负，0 表示纯自动"'),
    ('IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, "上额外采样高度", "px")',
     'IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_TOP, "上安全区额外值", "px")'),
    ('IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, "下额外采样高度", "px")',
     'IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM, "下安全区额外值", "px")'),
    ('IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, "左额外采样宽度", "px")',
     'IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_LEFT, "左安全区额外值", "px")'),
    ('IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, "右额外采样宽度", "px")',
     'IntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_RIGHT, "右安全区额外值", "px")'),
]:
    replace(ui_rel, old, new)

# Retire old persisted keys without converting their historical 48/16 values.
replace(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java",
    '''    private static void removeRetiredGlassPreferences(SharedPreferences sp) {\n        if (!sp.contains("liquid_legacy_s_curve")) return;\n        SharedPreferences.Editor e = sp.edit();\n        e.remove("liquid_legacy_s_curve");\n        e.commit();\n    }''',
    '''    private static void removeRetiredGlassPreferences(SharedPreferences sp) {\n        boolean hasRetired = sp.contains("liquid_legacy_s_curve")\n                || sp.contains("liquid_capture_bleed_top")\n                || sp.contains("liquid_capture_bleed_bottom")\n                || sp.contains("liquid_capture_bleed_left")\n                || sp.contains("liquid_capture_bleed_right");\n        if (!hasRetired) return;\n        SharedPreferences.Editor e = sp.edit();\n        e.remove("liquid_legacy_s_curve");\n        e.remove("liquid_capture_bleed_top");\n        e.remove("liquid_capture_bleed_bottom");\n        e.remove("liquid_capture_bleed_left");\n        e.remove("liquid_capture_bleed_right");\n        e.commit();\n    }''')

# Existing config/preset/codec tests follow the new public settings contract.
schema_test = "src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java"
for old, new in [
    ('assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, 48, 0, 256);',
     'assertComposeIntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_TOP, 0, -256, 256);'),
    ('assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, 16, 0, 256);',
     'assertComposeIntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM, 0, -256, 256);'),
    ('assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, 0, 0, 256);',
     'assertComposeIntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_LEFT, 0, -256, 256);'),
    ('assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, 0, 0, 256);',
     'assertComposeIntSpec(ConfigSchema.Glass.SAMPLING_EXTRA_RIGHT, 0, -256, 256);'),
]:
    replace(schema_test, old, new)

preset_test = "src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java"
for old, new in [
    ('expected.put("liquid_capture_bleed_top", 48);', 'expected.put("liquid_sampling_extra_top", 0);'),
    ('expected.put("liquid_capture_bleed_bottom", 16);', 'expected.put("liquid_sampling_extra_bottom", 0);'),
    ('expected.put("liquid_capture_bleed_left", 0);', 'expected.put("liquid_sampling_extra_left", 0);'),
    ('expected.put("liquid_capture_bleed_right", 0);', 'expected.put("liquid_sampling_extra_right", 0);'),
]:
    replace(preset_test, old, new)

codec_test = "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java"
for old, new in [
    ('assertEquals(48, exported.get("liquid_capture_bleed_top"));', 'assertEquals(0, exported.get("liquid_sampling_extra_top"));'),
    ('assertEquals(16, exported.get("liquid_capture_bleed_bottom"));', 'assertEquals(0, exported.get("liquid_sampling_extra_bottom"));'),
    ('assertEquals(0, exported.get("liquid_capture_bleed_left"));', 'assertEquals(0, exported.get("liquid_sampling_extra_left"));'),
    ('assertEquals(0, exported.get("liquid_capture_bleed_right"));', 'assertEquals(0, exported.get("liquid_sampling_extra_right"));'),
]:
    replace(codec_test, old, new)

custom_test = "src/test/java/com/hellovoid/liquiddock/Miuix307GlassCustomizationContractTest.java"
for old, new in [
    ('glassConfig.captureBleedTopPx', 'glassConfig.samplingExtraTopPx'),
    ('glassConfig.captureBleedBottomPx', 'glassConfig.samplingExtraBottomPx'),
    ('glassConfig.captureBleedLeftPx', 'glassConfig.samplingExtraLeftPx'),
    ('glassConfig.captureBleedRightPx', 'glassConfig.samplingExtraRightPx'),
]:
    text = read(custom_test)
    if old in text:
        write(custom_test, text.replace(old, new))

# Replace the old positive-only GUI contract with the signed additive contract.
horiz_test = "src/test/java/com/hellovoid/liquiddock/Miuix307HorizontalOverscanGuiContractTest.java"
write(horiz_test, '''package com.hellovoid.liquiddock;\n\nimport static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertTrue;\n\nimport java.nio.file.Files;\nimport java.nio.file.Path;\n\nimport org.junit.Test;\n\n/** Contracts for signed per-side user adjustments on top of automatic Prismal sampling safety. */\npublic class Miuix307HorizontalOverscanGuiContractTest {\n    private static final Path MAIN = Path.of("src/main");\n\n    @Test\n    public void guiExposesSignedSamplingExtrasWithZeroAsPureAutomatic() throws Exception {\n        String schema = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/ConfigSchema.java"));\n        String ui = Files.readString(MAIN.resolve("kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));\n        String preset = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/config/PresetManager.java"));\n\n        assertTrue(schema.contains("SAMPLING_EXTRA_LEFT = integer(")\n                && schema.contains("\\\"liquid_sampling_extra_left\\\", 0, 0, 0, -256, 256"));\n        assertTrue(schema.contains("SAMPLING_EXTRA_RIGHT = integer(")\n                && schema.contains("\\\"liquid_sampling_extra_right\\\", 0, 0, 0, -256, 256"));\n        assertTrue(ui.contains("左安全区额外值") && ui.contains("右安全区额外值"));\n        assertTrue(ui.contains("可正可负，0 表示纯自动"));\n        assertTrue(preset.contains("values.put(\\\"liquid_sampling_extra_left\\\", 0);")\n                && preset.contains("values.put(\\\"liquid_sampling_extra_right\\\", 0);"));\n    }\n\n    @Test\n    public void zeroCopyRendererAddsSignedUserExtrasAfterAutomaticGuard() throws Exception {\n        String config = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/LiquidDockConfig.java"));\n        String view = Files.readString(MAIN.resolve("java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));\n\n        assertTrue(config.contains("samplingExtraLeftPx") && config.contains("samplingExtraRightPx"));\n        assertTrue(view.contains("leftSamplingExtraPx") && view.contains("rightSamplingExtraPx"));\n        assertTrue(view.contains("int autoHorizontal = Math.max(horizontalOverscanPx(), opticalX);"));\n        assertTrue(view.contains("combineAutoGuardAndUserExtra(autoHorizontal, leftSamplingExtraPx)"));\n        assertTrue(view.contains("combineAutoGuardAndUserExtra(autoHorizontal, rightSamplingExtraPx)"));\n        assertTrue(view.contains("width + insets.left + insets.right"));\n        assertTrue(view.contains("viewScreen[0] - insets.left"));\n        assertTrue(view.contains("insets.left / (float) sampleWidth"));\n        assertFalse("horizontal FBO width must not assume symmetric overscan",\n                view.contains("width + overscanPx * 2"));\n    }\n}\n''')

# Production audit: old capture-bleed semantics may survive only as one-way purge literals.
for path in (ROOT / "src/main").rglob("*"):
    if not path.is_file() or path.suffix not in {".java", ".kt", ".xml", ".glsl"}:
        continue
    text = path.read_text(errors="ignore")
    if path.name == "ConfigMigration.java":
        continue
    for token in ("CAPTURE_BLEED_", "captureBleed", "liquid_capture_bleed_"):
        if token in text:
            raise SystemExit(f"retired capture-bleed token {token!r} remains in {path.relative_to(ROOT)}")

print("signed sampling safe-area refactor applied")
