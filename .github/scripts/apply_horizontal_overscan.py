from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one replacement anchor, found {count}\nANCHOR:\n{old}")
    p.write_text(text.replace(old, new, 1))


# Config schema: add new direct-pixel keys and export them.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = integer(\n                "liquid_capture_bleed_bottom", 16, 16, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> THICKNESS = dp(''',
    '''        public static final ConfigKey<Integer> CAPTURE_BLEED_BOTTOM = integer(\n                "liquid_capture_bleed_bottom", 16, 16, 16, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_LEFT = integer(\n                "liquid_capture_bleed_left", 0, 0, 0, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> CAPTURE_BLEED_RIGHT = integer(\n                "liquid_capture_bleed_right", 0, 0, 0, 0, 256, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> THICKNESS = dp('''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''                Glass.CAPTURE_BLEED_TOP, Glass.CAPTURE_BLEED_BOTTOM, Glass.THICKNESS,''',
    '''                Glass.CAPTURE_BLEED_TOP, Glass.CAPTURE_BLEED_BOTTOM,\n                Glass.CAPTURE_BLEED_LEFT, Glass.CAPTURE_BLEED_RIGHT, Glass.THICKNESS,'''
)

# Default preset: left/right are extras on top of the fixed 32dp base, so zero preserves visuals.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/PresetManager.java",
    '''        values.put("liquid_capture_bleed_top", 48);\n        values.put("liquid_capture_bleed_bottom", 16);\n        putDp(values, "liquid_recents_prearm_distance", 8f);''',
    '''        values.put("liquid_capture_bleed_top", 48);\n        values.put("liquid_capture_bleed_bottom", 16);\n        values.put("liquid_capture_bleed_left", 0);\n        values.put("liquid_capture_bleed_right", 0);\n        putDp(values, "liquid_recents_prearm_distance", 8f);'''
)

# Compose GUI and help text.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    "liquid_capture_bleed_top" -> "在 Dock 上方扩展 zero-copy 折射背景的采样区域"\n    "liquid_capture_bleed_bottom" -> "在 Dock 下方扩展 zero-copy 折射背景的采样区域"''',
    '''    "liquid_capture_bleed_top" -> "在 Dock 上方扩展 zero-copy 折射背景的采样区域"\n    "liquid_capture_bleed_bottom" -> "在 Dock 下方扩展 zero-copy 折射背景的采样区域"\n    "liquid_capture_bleed_left" -> "在默认 32dp 左侧采样之外继续扩展的 zero-copy 区域"\n    "liquid_capture_bleed_right" -> "在默认 32dp 右侧采样之外继续扩展的 zero-copy 区域"'''
)
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, "上额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, "下额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),''',
    '''    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, "上额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, "下额外采样高度", "px"),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, "左额外采样宽度", "px"),\n    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, "右额外采样宽度", "px"),\n    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),'''
)

# Runtime config reads direct-pixel extras.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final int captureBleedTopPx, captureBleedBottomPx;''',
    '''        final int captureBleedTopPx, captureBleedBottomPx,\n                captureBleedLeftPx, captureBleedRightPx;'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            captureBleedBottomPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback()), 0, 256);\n            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),''',
    '''            captureBleedBottomPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback()), 0, 256);\n            captureBleedLeftPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_LEFT.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_LEFT.runtimeFallback()), 0, 256);\n            captureBleedRightPx = clamp(c.i(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.name(),\n                    ConfigSchema.Glass.CAPTURE_BLEED_RIGHT.runtimeFallback()), 0, 256);\n            tintAlpha = channel(c.i(ConfigSchema.Glass.TINT_ALPHA.name(),'''
)

# Zero-copy renderer: keep the 32dp base and add independent left/right pixel extras.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''    // Left/right keep a fixed GPU overscan ring. Top/bottom use the historical GUI pixel\n    // controls so users can tune how early approaching content enters Prismal refraction.\n    // Output remains clipped to the visible Dock itself.''',
    '''    // Left/right keep the fixed 32dp GPU overscan as a compatibility baseline and can add\n    // independent GUI pixel extras. Top/bottom remain fully controlled by their historical pixel\n    // values. Output remains clipped to the visible Dock itself.'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''    private volatile int topOverscanPx = 48;\n    private volatile int bottomOverscanPx = 16;''',
    '''    private volatile int topOverscanPx = 48;\n    private volatile int bottomOverscanPx = 16;\n    private volatile int leftExtraOverscanPx;\n    private volatile int rightExtraOverscanPx;'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''        topOverscanPx = Math.max(0, glassConfig.captureBleedTopPx);\n        bottomOverscanPx = Math.max(0, glassConfig.captureBleedBottomPx);\n        updateBackdropMapping();''',
    '''        topOverscanPx = Math.max(0, glassConfig.captureBleedTopPx);\n        bottomOverscanPx = Math.max(0, glassConfig.captureBleedBottomPx);\n        leftExtraOverscanPx = Math.max(0, glassConfig.captureBleedLeftPx);\n        rightExtraOverscanPx = Math.max(0, glassConfig.captureBleedRightPx);\n        updateBackdropMapping();'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''    private void ensureFboSize(int width, int height) {\n        int overscanPx = horizontalOverscanPx();\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int nextWidth = Math.max(1, width + overscanPx * 2);''',
    '''    private void ensureFboSize(int width, int height) {\n        int leftOverscanPx = horizontalOverscanPx() + Math.max(0, this.leftExtraOverscanPx);\n        int rightOverscanPx = horizontalOverscanPx() + Math.max(0, this.rightExtraOverscanPx);\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int nextWidth = Math.max(1, width + leftOverscanPx + rightOverscanPx);'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''        int overscanPx = horizontalOverscanPx();\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int sampleWidth = hostWidth + overscanPx * 2;\n        int sampleHeight = hostHeight + topOverscanPx + bottomOverscanPx;\n        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(\n                hostScreen[0] - overscanPx, hostScreen[1] - topOverscanPx,''',
    '''        int leftOverscanPx = horizontalOverscanPx() + Math.max(0, this.leftExtraOverscanPx);\n        int rightOverscanPx = horizontalOverscanPx() + Math.max(0, this.rightExtraOverscanPx);\n        int topOverscanPx = Math.max(0, this.topOverscanPx);\n        int bottomOverscanPx = Math.max(0, this.bottomOverscanPx);\n        int sampleWidth = hostWidth + leftOverscanPx + rightOverscanPx;\n        int sampleHeight = hostHeight + topOverscanPx + bottomOverscanPx;\n        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(\n                hostScreen[0] - leftOverscanPx, hostScreen[1] - topOverscanPx,'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java",
    '''        float nextDockUvLeft = overscanPx / (float) sampleWidth;''',
    '''        float nextDockUvLeft = leftOverscanPx / (float) sampleWidth;'''
)

# Existing config tests must acknowledge the two new exported/direct keys.
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java",
    '''        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, 48, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, 16, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 38, 0, 160);''',
    '''        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, 48, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, 16, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, 0, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, 0, 0, 256);\n        assertComposeIntSpec(ConfigSchema.Glass.TINT_ALPHA, 38, 0, 160);'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java",
    '''        expected.put("liquid_capture_bleed_top", 48);\n        expected.put("liquid_capture_bleed_bottom", 16);\n        expected.put("liquid_recents_prearm_distance", 8);''',
    '''        expected.put("liquid_capture_bleed_top", 48);\n        expected.put("liquid_capture_bleed_bottom", 16);\n        expected.put("liquid_capture_bleed_left", 0);\n        expected.put("liquid_capture_bleed_right", 0);\n        expected.put("liquid_recents_prearm_distance", 8);'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        assertEquals(126, exported.size());''',
    '''        assertEquals(128, exported.size());'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        assertEquals(48, exported.get("liquid_capture_bleed_top"));\n        assertEquals(1200, exported.get("liquid_home_settle_delay"));''',
    '''        assertEquals(48, exported.get("liquid_capture_bleed_top"));\n        assertEquals(16, exported.get("liquid_capture_bleed_bottom"));\n        assertEquals(0, exported.get("liquid_capture_bleed_left"));\n        assertEquals(0, exported.get("liquid_capture_bleed_right"));\n        assertEquals(1200, exported.get("liquid_home_settle_delay"));'''
)

print("horizontal overscan patch applied")
