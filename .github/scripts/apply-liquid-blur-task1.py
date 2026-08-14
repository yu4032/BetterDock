from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1))


replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<Boolean> DIMENSIONS_DP = bool(
                "liquid_dimensions_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLUR = dp(''',
    '''        public static final ConfigKey<Boolean> DIMENSIONS_DP = bool(
                "liquid_dimensions_dp", true, false, true, ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<String> BLUR_MODE = string(
                "liquid_blur_mode", "shader", "shader", "shader",
                ConfigKey.ExportMode.ALWAYS);
        public static final ConfigKey<Integer> BLUR = dp(''',
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        add(keys, Glass.ENABLED, Glass.DIMENSIONS_DP, Glass.BLUR, Glass.CHROMATIC,''',
    '''        add(keys, Glass.ENABLED, Glass.DIMENSIONS_DP, Glass.BLUR_MODE, Glass.BLUR, Glass.CHROMATIC,''',
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''    private static ConfigKey<Integer> integer(String name, Integer uiDefault,''',
    '''    private static ConfigKey<String> string(String name, String uiDefault,
                                             String runtimeFallback, String exportDefault,
                                             ConfigKey.ExportMode exportMode) {
        return new ConfigKey<>(name, ConfigKey.Type.STRING, uiDefault, runtimeFallback,
                exportDefault, null, null, ConfigKey.StorageMode.DIRECT, exportMode);
    }

    private static ConfigKey<Integer> integer(String name, Integer uiDefault,''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java",
    '''        } else if (key.type() == ConfigKey.Type.INT && value instanceof Number) {
            out.put(key.name(), clamp(((Number) value).intValue(), key.minInt(), key.maxInt()));
        }
''',
    '''        } else if (key.type() == ConfigKey.Type.INT && value instanceof Number) {
            out.put(key.name(), clamp(((Number) value).intValue(), key.minInt(), key.maxInt()));
        } else if (key.type() == ConfigKey.Type.STRING && value != null) {
            out.put(key.name(), String.valueOf(value));
        }
''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/PresetManager.java",
    '''            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else {
                editor.putInt(entry.getKey(), (Integer) value);
            }
''',
    '''            if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            }
''',
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/PresetManager.java",
    '''        values.put("liquid_glass", true);
        values.put("liquid_dimensions_dp", true);
        values.put("liquid_ior", 170);''',
    '''        values.put("liquid_glass", true);
        values.put("liquid_dimensions_dp", true);
        values.put("liquid_blur_mode", "shader");
        values.put("liquid_ior", 170);''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture;
        final float blur, chromatic, captureScale, thickness, ior, normalStrength, dome,''',
    '''        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture;
        final LiquidBlurMode blurMode;
        final float blur, chromatic, captureScale, thickness, ior, normalStrength, dome,''',
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),
                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());
            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());''',
    '''            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),
                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());
            blurMode = LiquidBlurMode.fromPersisted(c.s(ConfigSchema.Glass.BLUR_MODE.name(),
                    ConfigSchema.Glass.BLUR_MODE.runtimeFallback()));
            blur = c.f(ConfigSchema.Glass.BLUR.name(), ConfigSchema.Glass.BLUR.runtimeFallback());''',
)

replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),
            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        BooleanSetting(prefs, ConfigSchema.Glass.DYNAMIC_APP_CAPTURE, stringResource(R.string.liquid_dynamic_capture),''',
    '''        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),
            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        StringDropdown(
            prefs,
            ConfigSchema.Glass.BLUR_MODE.name(),
            "模糊方式",
            ConfigSchema.Glass.BLUR_MODE.uiDefault(),
            listOf(
                "标准 Shader 模糊" to "shader",
                "高级材质模糊" to "advanced_material",
            ),
            masterEnabled && liquidGlass,
        )
        BooleanSetting(prefs, ConfigSchema.Glass.DYNAMIC_APP_CAPTURE, stringResource(R.string.liquid_dynamic_capture),''',
)

replace_once(
    "src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java",
    '''        assertEquals(99, exported.size());''',
    '''        assertEquals(100, exported.size());''',
)
