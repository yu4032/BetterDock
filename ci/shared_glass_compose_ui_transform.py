from pathlib import Path

PATH = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
source = PATH.read_text()

anchor = '''        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable), stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        ArrowPreference(
            title = stringResource(R.string.launcher_components_entry),
'''
replacement = '''        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable), stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        BooleanPreferenceSetting(
            prefs,
            "liquid_folder_glass",
            false,
            stringResource(R.string.liquid_folder_glass_title),
            stringResource(R.string.liquid_folder_glass_summary),
            masterEnabled && liquidGlass,
        )
        BooleanPreferenceSetting(
            prefs,
            "liquid_widget_glass",
            false,
            stringResource(R.string.liquid_widget_glass_title),
            stringResource(R.string.liquid_widget_glass_summary),
            masterEnabled && liquidGlass,
        )
        ArrowPreference(
            title = stringResource(R.string.launcher_components_entry),
'''

count = source.count(anchor)
if count != 1:
    raise SystemExit(f"LiquidPage shared-glass GUI anchor count={count}, expected 1")

source = source.replace(anchor, replacement, 1)
PATH.write_text(source)
