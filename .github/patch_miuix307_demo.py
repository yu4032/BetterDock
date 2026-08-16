from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one match, got {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",
    '''        public static final ConfigKey<String> BLUR_MODE = string(\n                "liquid_blur_mode", "shader", "shader", "shader",\n                ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> BLUR = dp(''',
    '''        public static final ConfigKey<String> BLUR_MODE = string(\n                "liquid_blur_mode", "shader", "shader", "shader",\n                ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Boolean> MIUIX_307_PIPELINE = bool(\n                "liquid_miuix_307_pipeline", false, false, false, ConfigKey.ExportMode.ALWAYS);\n        public static final ConfigKey<Integer> BLUR = dp(''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture;\n        final LiquidBlurMode blurMode;''',
    '''        final boolean enabled, dimensionsDp, dynamicAppCapture, fullscreenCapture, miuix307Pipeline;\n        final LiquidBlurMode blurMode;''',
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java",
    '''            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),\n                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());\n            blurMode = LiquidBlurMode.fromPersisted(c.s(ConfigSchema.Glass.BLUR_MODE.name(),''',
    '''            dimensionsDp = c.b(ConfigSchema.Glass.DIMENSIONS_DP.name(),\n                    ConfigSchema.Glass.DIMENSIONS_DP.runtimeFallback());\n            miuix307Pipeline = c.b(ConfigSchema.Glass.MIUIX_307_PIPELINE.name(),\n                    ConfigSchema.Glass.MIUIX_307_PIPELINE.runtimeFallback());\n            blurMode = LiquidBlurMode.fromPersisted(c.s(ConfigSchema.Glass.BLUR_MODE.name(),''',
)

replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }\n    var dynamicAppCapture by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.DYNAMIC_APP_CAPTURE.name(), ConfigSchema.Glass.DYNAMIC_APP_CAPTURE.uiDefault())) }\n    SettingsList(padding, stringResource(R.string.page_liquid)) {\n        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),\n            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }\n        StringDropdown(''',
    '''    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }\n    var miuix307Pipeline by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.MIUIX_307_PIPELINE.name(), ConfigSchema.Glass.MIUIX_307_PIPELINE.uiDefault())) }\n    var dynamicAppCapture by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.DYNAMIC_APP_CAPTURE.name(), ConfigSchema.Glass.DYNAMIC_APP_CAPTURE.uiDefault())) }\n    SettingsList(padding, stringResource(R.string.page_liquid)) {\n        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable),\n            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }\n        BooleanSetting(prefs, ConfigSchema.Glass.MIUIX_307_PIPELINE,\n            "HyperOS 3.0.307+ 新材质管线",\n            "实验：使用系统 MiuiX 实时材质模糊；关闭时完全保持旧捕获管线",\n            masterEnabled && liquidGlass) { miuix307Pipeline = it }\n        StringDropdown(''',
)
replace_once(
    "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt",
    '''            masterEnabled && liquidGlass,\n        )\n        BooleanSetting(prefs, ConfigSchema.Glass.DYNAMIC_APP_CAPTURE, stringResource(R.string.liquid_dynamic_capture),\n            stringResource(R.string.liquid_dynamic_capture_summary), masterEnabled && liquidGlass) { dynamicAppCapture = it }''',
    '''            masterEnabled && liquidGlass && !miuix307Pipeline,\n        )\n        BooleanSetting(prefs, ConfigSchema.Glass.DYNAMIC_APP_CAPTURE, stringResource(R.string.liquid_dynamic_capture),\n            stringResource(R.string.liquid_dynamic_capture_summary), masterEnabled && liquidGlass && !miuix307Pipeline) { dynamicAppCapture = it }''',
)

replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    '''        boolean dockCustomization = config.dock.enabled;\n        boolean liquidGlass = config.glass.enabled;\n        if (!dockCustomization && !liquidGlass) {''',
    '''        boolean dockCustomization = config.dock.enabled;\n        boolean liquidGlass = config.glass.enabled;\n        if (liquidGlass && config.glass.miuix307Pipeline) {\n            if (Miuix307DemoPipeline.install(classLoader, config)) {\n                log("[DC] MiuiX 307 demo active; legacy liquid capture bypassed");\n                return;\n            }\n            log("[DC] MiuiX 307 demo unavailable; falling back to legacy pipeline");\n        }\n        if (!dockCustomization && !liquidGlass) {''',
)

print("MiuiX 307 demo source patch applied")
