from pathlib import Path

BRANCH_FILES = Path('.')


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one anchor, found {count}\n--- anchor ---\n{old}")
    p.write_text(text.replace(old, new, 1))


# ConfigSchema: canonical master/profile + import-only legacy key.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''    public static final class Grid {\n        public static final ConfigKey<Boolean> ENABLED = bool(\n                "home_grid_8x4", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> WIDGET_ADAPTATION = bool(''',
    '''    public static final class Grid {\n        public static final ConfigKey<Boolean> ENABLED = bool(\n                "home_grid_extended", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<String> PROFILE = string(\n                "grid_profile", "8x4", "8x4", "8x4", ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> LEGACY_8X4 = bool(\n                "home_grid_8x4", false, false, false, ConfigKey.ExportMode.NEVER);\n        public static final ConfigKey<Boolean> WIDGET_ADAPTATION = bool('''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        add(keys, Grid.ENABLED, Grid.WIDGET_ADAPTATION, Grid.MARGINS_DP, Grid.MARGINS_OFFSET,''',
    '''        add(keys, Grid.ENABLED, Grid.PROFILE, Grid.LEGACY_8X4,\n                Grid.WIDGET_ADAPTATION, Grid.MARGINS_DP, Grid.MARGINS_OFFSET,'''
)

# ConfigMigration: one owner for canonicalization of old home_grid_8x4 state.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java",
    '''package com.hellovoid.liquiddock.config;\n\nimport android.content.Context;''',
    '''package com.hellovoid.liquiddock.config;\n\nimport android.content.Context;\n\nimport com.hellovoid.liquiddock.HomeGridProfile;'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java",
    '''    private ConfigMigration() { }\n\n    public static void migrate(Context context, SharedPreferences preferences) {\n        migrateMergedHorizontal(preferences);''',
    '''    private ConfigMigration() { }\n\n    static final class GridProfileState {\n        final boolean enabled;\n        final String profile;\n\n        GridProfileState(boolean enabled, String profile) {\n            this.enabled = enabled;\n            this.profile = profile;\n        }\n    }\n\n    static GridProfileState resolveGridProfileState(\n            Boolean canonicalEnabled, String canonicalProfile, Boolean legacyEnabled) {\n        boolean enabled = canonicalEnabled != null\n                ? canonicalEnabled : legacyEnabled != null && legacyEnabled;\n        String profile = HomeGridProfile.fromPersisted(canonicalProfile).persistedValue();\n        return new GridProfileState(enabled, profile);\n    }\n\n    public static void migrate(Context context, SharedPreferences preferences) {\n        migrateGridProfile(preferences);\n        migrateMergedHorizontal(preferences);'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java",
    '''    /**\n     * One-time migration from the first PassBlur/Prismal adapter defaults''',
    '''    private static void migrateGridProfile(SharedPreferences sp) {\n        String enabledKey = ConfigSchema.Grid.ENABLED.name();\n        String profileKey = ConfigSchema.Grid.PROFILE.name();\n        String legacyKey = ConfigSchema.Grid.LEGACY_8X4.name();\n\n        Boolean canonicalEnabled = sp.contains(enabledKey)\n                ? sp.getBoolean(enabledKey, false) : null;\n        String canonicalProfile = sp.contains(profileKey)\n                ? sp.getString(profileKey, ConfigSchema.Grid.PROFILE.uiDefault()) : null;\n        Boolean legacyEnabled = sp.contains(legacyKey)\n                ? sp.getBoolean(legacyKey, false) : null;\n        GridProfileState state = resolveGridProfileState(\n                canonicalEnabled, canonicalProfile, legacyEnabled);\n\n        SharedPreferences.Editor editor = sp.edit();\n        boolean changed = false;\n        if (canonicalEnabled == null || canonicalEnabled != state.enabled) {\n            editor.putBoolean(enabledKey, state.enabled);\n            changed = true;\n        }\n        if (canonicalProfile == null || !state.profile.equals(canonicalProfile)) {\n            editor.putString(profileKey, state.profile);\n            changed = true;\n        }\n        if (sp.contains(legacyKey)) {\n            editor.remove(legacyKey);\n            changed = true;\n        }\n        if (changed) editor.commit();\n    }\n\n    /**\n     * One-time migration from the first PassBlur/Prismal adapter defaults'''
)

# ConfigCodec: canonical exports; old snapshots and legacy JSON still round-trip safely.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java",
    '''package com.hellovoid.liquiddock.config;\n\nimport java.util.LinkedHashMap;''',
    '''package com.hellovoid.liquiddock.config;\n\nimport com.hellovoid.liquiddock.HomeGridProfile;\n\nimport java.util.LinkedHashMap;'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java",
    '''        importLegacyHorizontalMargins(jsonValues, out);\n        importPreAxisLegacyMargins(jsonValues, out);''',
    '''        importLegacyGridProfile(jsonValues, out);\n        importLegacyHorizontalMargins(jsonValues, out);\n        importPreAxisLegacyMargins(jsonValues, out);'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java",
    '''    private static Object exportValue(ConfigKey<?> key, Map<String, ?> preferences) {\n        if (key == ConfigSchema.Dock.DIMENSIONS_DP || key == ConfigSchema.Glass.DIMENSIONS_DP) {''',
    '''    private static Object exportValue(ConfigKey<?> key, Map<String, ?> preferences) {\n        if (key == ConfigSchema.Grid.ENABLED && !preferences.containsKey(key.name())\n                && preferences.containsKey(ConfigSchema.Grid.LEGACY_8X4.name())) {\n            return booleanValue(preferences.get(ConfigSchema.Grid.LEGACY_8X4.name()));\n        }\n        if (key == ConfigSchema.Grid.PROFILE) {\n            Object value = preferences.get(key.name());\n            return HomeGridProfile.fromPersisted(\n                    value == null ? key.exportDefault() : String.valueOf(value)).persistedValue();\n        }\n        if (key == ConfigSchema.Dock.DIMENSIONS_DP || key == ConfigSchema.Glass.DIMENSIONS_DP) {'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java",
    '''        } else if (key.type() == ConfigKey.Type.STRING && value != null) {\n            out.put(key.name(), String.valueOf(value));\n        }\n    }\n\n    private static void importLegacyHorizontalMargins''',
    '''        } else if (key.type() == ConfigKey.Type.STRING && value != null) {\n            String string = String.valueOf(value);\n            if (key == ConfigSchema.Grid.PROFILE) {\n                string = HomeGridProfile.fromPersisted(string).persistedValue();\n            }\n            out.put(key.name(), string);\n        }\n    }\n\n    private static void importLegacyGridProfile(Map<String, ?> jsonValues,\n                                                Map<String, Object> out) {\n        boolean canonicalEnabled = jsonValues.containsKey(ConfigSchema.Grid.ENABLED.name());\n        boolean canonicalProfile = jsonValues.containsKey(ConfigSchema.Grid.PROFILE.name());\n        boolean legacy = jsonValues.containsKey(ConfigSchema.Grid.LEGACY_8X4.name());\n        if (!canonicalEnabled && legacy) {\n            out.put(ConfigSchema.Grid.ENABLED.name(),\n                    booleanValue(jsonValues.get(ConfigSchema.Grid.LEGACY_8X4.name())));\n        }\n        if ((canonicalEnabled || canonicalProfile || legacy)\n                && !out.containsKey(ConfigSchema.Grid.PROFILE.name())) {\n            out.put(ConfigSchema.Grid.PROFILE.name(), HomeGridProfile.GRID_8X4.persistedValue());\n        }\n        out.remove(ConfigSchema.Grid.LEGACY_8X4.name());\n    }\n\n    private static void importLegacyHorizontalMargins'''
)

# Typed runtime config: canonical master wins; legacy snapshot is only a fallback.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''    static final class Grid {\n        final boolean enabled, widgetAdaptation, dp, offsets;''',
    '''    static final class Grid {\n        final boolean enabled, widgetAdaptation, dp, offsets;\n        final HomeGridProfile profile;'''
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        Grid(ConfigReader c) {\n            enabled = c.b(ConfigSchema.Grid.ENABLED.name(),\n                    ConfigSchema.Grid.ENABLED.runtimeFallback());\n            widgetAdaptation = c.b(''',
    '''        Grid(ConfigReader c) {\n            enabled = c.has(ConfigSchema.Grid.ENABLED.name())\n                    ? c.b(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.runtimeFallback())\n                    : c.b(ConfigSchema.Grid.LEGACY_8X4.name(),\n                            ConfigSchema.Grid.LEGACY_8X4.runtimeFallback());\n            profile = HomeGridProfile.fromPersisted(c.s(ConfigSchema.Grid.PROFILE.name(),\n                    ConfigSchema.Grid.PROFILE.runtimeFallback()));\n            widgetAdaptation = c.b('''
)

# Default preset stores only canonical grid state.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/PresetManager.java",
    '''        values.put("liquiddock_enabled", true);\n        values.put("home_grid_8x4", false);\n        values.put("grid_widget_adaptation", false);''',
    '''        values.put("liquiddock_enabled", true);\n        values.put("home_grid_extended", false);\n        values.put("grid_profile", "8x4");\n        values.put("grid_widget_adaptation", false);'''
)

# Compose Grid page uses the existing StringDropdown primitive; no new UI framework.
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''private fun GridPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {\n    var grid8x4 by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())) }\n    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {\n        item { PageHeader(stringResource(R.string.page_grid), stringResource(R.string.grid_header_summary)) }\n        item { SmallTitle(stringResource(R.string.category_grid)) }\n        item {\n            SettingsCard {\n                BooleanSetting(prefs, ConfigSchema.Grid.ENABLED, stringResource(R.string.enable_grid_8x4), stringResource(R.string.enable_grid_8x4_summary), masterEnabled) { grid8x4 = it }\n                BooleanSetting(prefs, ConfigSchema.Grid.WIDGET_ADAPTATION, stringResource(R.string.enable_widget_adaptation), stringResource(R.string.enable_widget_adaptation_summary), masterEnabled && grid8x4)\n            }\n        }\n        item { SmallTitle(stringResource(R.string.category_landscape)) }\n        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y" }.forEach { IntSetting(prefs, it, masterEnabled && grid8x4) } } }\n        item { SmallTitle(stringResource(R.string.category_portrait)) }\n        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y" }.forEach { IntSetting(prefs, it, masterEnabled && grid8x4) } } }\n    }\n}''',
    '''private fun GridPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {\n    var customGrid by remember { mutableStateOf(prefs.getBoolean(\n        ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())) }\n    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {\n        item { PageHeader(stringResource(R.string.page_grid), stringResource(R.string.grid_header_summary)) }\n        item { SmallTitle(stringResource(R.string.category_grid)) }\n        item {\n            SettingsCard {\n                BooleanSetting(\n                    prefs, ConfigSchema.Grid.ENABLED,\n                    stringResource(R.string.enable_extended_grid),\n                    stringResource(R.string.enable_extended_grid_summary), masterEnabled\n                ) { customGrid = it }\n                StringDropdown(\n                    prefs = prefs,\n                    key = ConfigSchema.Grid.PROFILE.name(),\n                    title = stringResource(R.string.grid_profile),\n                    default = ConfigSchema.Grid.PROFILE.uiDefault(),\n                    options = listOf(\n                        "8×4 / 4×8" to "8x4",\n                        "10×6 / 6×10" to "10x6",\n                    ),\n                    enabled = masterEnabled && customGrid,\n                )\n                BooleanSetting(\n                    prefs, ConfigSchema.Grid.WIDGET_ADAPTATION,\n                    stringResource(R.string.enable_widget_adaptation),\n                    stringResource(R.string.enable_widget_adaptation_summary),\n                    masterEnabled && customGrid\n                )\n            }\n        }\n        item { SmallTitle(stringResource(R.string.category_landscape)) }\n        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y" }.forEach { IntSetting(prefs, it, masterEnabled && customGrid) } } }\n        item { SmallTitle(stringResource(R.string.category_portrait)) }\n        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y" }.forEach { IntSetting(prefs, it, masterEnabled && customGrid) } } }\n    }\n}'''
)

# English + Chinese labels.
replace_once(
    "src/main/res/values/strings.xml",
    '''    <string name="home_grid_summary">8×4 / 4×8 grid, orientation spacing, and page indicator</string>''',
    '''    <string name="home_grid_summary">Selectable 8×4 / 4×8 or 10×6 / 6×10 grid, orientation spacing, and page indicator</string>'''
)
replace_once(
    "src/main/res/values/strings.xml",
    '''    <string name="enable_grid_8x4">Enable 8×4 / 4×8 grid</string>\n    <string name="enable_grid_8x4_summary">Uses the stock MIUI layout when disabled; all settings below require this switch</string>''',
    '''    <string name="enable_extended_grid">Enable optional extended layout</string>\n    <string name="enable_extended_grid_summary">Uses stock MIUI layout when disabled; enable to choose 8×4 / 4×8 or 10×6 / 6×10</string>\n    <string name="grid_profile">Extended layout profile</string>'''
)
replace_once(
    "src/main/res/values-zh-rCN/strings.xml",
    '''    <string name="home_grid_summary">8×4 / 4×8、横竖屏边距与指示器</string>''',
    '''    <string name="home_grid_summary">可选 8×4 / 4×8 或 10×6 / 6×10、横竖屏边距与指示器</string>'''
)
replace_once(
    "src/main/res/values-zh-rCN/strings.xml",
    '''    <string name="enable_grid_8x4">启用 8×4 / 4×8 网格</string>\n    <string name="enable_grid_8x4_summary">关闭时完全使用 MIUI 原生布局；下方全部参数仅在开启后生效</string>''',
    '''    <string name="enable_extended_grid">启用可选扩展布局</string>\n    <string name="enable_extended_grid_summary">关闭时完全使用 MIUI 原生布局；开启后可选择 8×4 / 4×8 或 10×6 / 6×10</string>\n    <string name="grid_profile">扩展布局规格</string>'''
)

# Existing tests: update historical export/preset expectations to canonical format.
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        prefs.put("home_grid_8x4", true);''',
    '''        prefs.put("home_grid_extended", true);\n        prefs.put("grid_profile", "10x6");'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        expected.put("home_grid_8x4", true);''',
    '''        expected.put("home_grid_extended", true);\n        expected.put("grid_profile", "10x6");'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        assertEquals(128, exported.size());\n        assertEquals(Boolean.TRUE, exported.get("liquiddock_enabled"));\n        assertEquals(Boolean.FALSE, exported.get("home_grid_8x4"));''',
    '''        assertEquals(129, exported.size());\n        assertEquals(Boolean.TRUE, exported.get("liquiddock_enabled"));\n        assertEquals(Boolean.FALSE, exported.get("home_grid_extended"));\n        assertEquals("8x4", exported.get("grid_profile"));\n        assertFalse(exported.containsKey("home_grid_8x4"));'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java",
    '''        expected.put("liquiddock_enabled", true);\n        expected.put("home_grid_8x4", false);\n        expected.put("grid_widget_adaptation", false);''',
    '''        expected.put("liquiddock_enabled", true);\n        expected.put("home_grid_extended", false);\n        expected.put("grid_profile", "8x4");\n        expected.put("grid_widget_adaptation", false);'''
)
replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java",
    '''        assertEquals(Boolean.FALSE, PresetManager.defaultValues().get("home_grid_8x4"));''',
    '''        assertEquals(Boolean.FALSE, PresetManager.defaultValues().get("home_grid_extended"));\n        assertEquals("8x4", PresetManager.defaultValues().get("grid_profile"));'''
)

print("Task 2 grid profile config/UI patch applied")
