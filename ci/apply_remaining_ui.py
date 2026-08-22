from pathlib import Path
import re

UI = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
EN = Path("src/main/res/values/strings.xml")
ZH = Path("src/main/res/values-zh-rCN/strings.xml")


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 match, got {n}")
    return text.replace(old, new, 1)


u = UI.read_text()
u = once(
    u,
    "    Divider(R.string.page_divider), Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),\n"
    "    Stroke(R.string.page_stroke), Shadow(R.string.page_shadow), Data(R.string.page_data),\n",
    "    Divider(R.string.page_divider), Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),\n"
    "    LauncherHighlights(R.string.page_launcher_highlights),\n"
    "    Stroke(R.string.page_stroke), Shadow(R.string.page_shadow), Data(R.string.page_data),\n",
    "page enum",
)
u = once(
    u,
    "}\n\n// Ordinary UI writes are mirrored",
    "}\n\nprivate fun parentPage(page: Page): Page = when (page) {\n"
    "    Page.LauncherHighlights -> Page.Liquid\n"
    "    else -> Page.Home\n"
    "}\n\n// Ordinary UI writes are mirrored",
    "parent page",
)
u = once(
    u,
    "}\n\nprivate fun optionSummary(key: String): String = when (key) {",
    "}\n\nprivate data class HighlightToggleSpec(\n"
    "    val key: String,\n"
    "    val titleRes: Int,\n"
    "    val summaryRes: Int,\n"
    ")\n\nprivate fun optionSummary(key: String): String = when (key) {",
    "toggle spec",
)

# Keep the Liquid page user-facing: remove implementation branding/backend details.
u = u.replace('"Prismal · ', '"')
u = u.replace('"Prismal ', '"')
u = u.replace(
    '"LiquidDock 对 Prismal 高光总强度的兼容倍率"',
    '"控制整体高光强度"',
)
u = u.replace(
    '"控制透镜方向向中心偏转：0=Prismal 自动（normalStrength×0.9，最高 1.0）；1–100=手动覆盖"',
    '"控制折射方向向玻璃中心偏转的程度"',
)
u = u.replace(
    '"RGB 色散总强度；v1.0.6 Quick Start 有效默认值为 26"',
    '"控制红、绿、蓝通道分离形成的色散强度"',
)
u = u.replace(
    '"模糊半径；zero-copy 后端在半分辨率双通道 FBO 中使用，默认 2"',
    '"控制玻璃背景的模糊程度"',
)
u = u.replace('；当前 zero-copy 后端不使用', '（旧兼容路径）')
u = u.replace('"SDF 平滑"', '"圆角平滑"')
u = u.replace('"Fresnel 反射"', '"菲涅尔反射"')

# Move only the external stroke corner offset; internal blur corner remains on Dock.
u = once(u, '    IntSpec(ConfigSchema.Dock.CORNER_OFFSET, "描边圆角偏移"),\n', '', 'remove Dock corner')
u = once(
    u,
    'private val strokeSpecs = listOf(\n',
    'private val strokeSpecs = listOf(\n'
    '    IntSpec(ConfigSchema.Dock.CORNER_OFFSET, "描边圆角偏移", "dp", null, IntSection.StrokeGeometry),\n',
    'add Stroke corner',
)

# Shared folder/future-widget highlight switches; all default enabled to preserve current output.
u = once(
    u,
    '    IntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, "视差倍率", "%"),\n)\nprivate val strokeSpecs = listOf(\n',
    '    IntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, "视差倍率", "%"),\n)\n'
    'private val launcherHighlightSpecs = listOf(\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.SKY_HAZE, R.string.highlight_sky_haze, R.string.highlight_sky_haze_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.SPECULAR, R.string.highlight_specular, R.string.highlight_specular_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.LIT_RIM, R.string.highlight_lit_rim, R.string.highlight_lit_rim_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.OPPOSITE_RIM, R.string.highlight_opposite_rim, R.string.highlight_opposite_rim_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.CORNER_RIM, R.string.highlight_corner_rim, R.string.highlight_corner_rim_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.FACE_SHEEN, R.string.highlight_face_sheen, R.string.highlight_face_sheen_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.PLAIN_HIGHLIGHT, R.string.highlight_plain, R.string.highlight_plain_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.CAUSTICS, R.string.highlight_caustics, R.string.highlight_caustics_summary),\n'
    '    HighlightToggleSpec(LauncherHighlightPreferences.PRESS_GLOW, R.string.highlight_press_glow, R.string.highlight_press_glow_summary),\n'
    ')\nprivate val strokeSpecs = listOf(\n',
    'highlight specs',
)

u = once(u, '    BackHandler(enabled = page != Page.Home) { page = Page.Home }\n',
         '    BackHandler(enabled = page != Page.Home) { page = parentPage(page) }\n', 'back handler')
u = once(
    u,
    '                    if (page != Page.Home) TextButton(text = stringResource(R.string.action_back), onClick = { page = Page.Home })\n',
    '                    if (page != Page.Home) TextButton(text = stringResource(R.string.action_back), onClick = { page = parentPage(page) })\n',
    'top back',
)
u = once(
    u,
    '                Page.Liquid -> LiquidPage(padding, prefs, masterEnabled)\n'
    '                Page.Stroke -> StrokePage(padding, prefs, masterEnabled)\n',
    '                Page.Liquid -> LiquidPage(padding, prefs, masterEnabled) { page = Page.LauncherHighlights }\n'
    '                Page.LauncherHighlights -> LauncherHighlightsPage(padding, prefs, masterEnabled)\n'
    '                Page.Stroke -> StrokePage(padding, prefs, masterEnabled)\n',
    'page dispatch',
)

old = '''@Composable
private fun LiquidPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }
    SettingsList(
        padding,
        stringResource(R.string.page_liquid),
        "当前使用 PassBlur → OES → Prismal zero-copy；修改光学参数后点击右上角“重启桌面”生效。",
    ) {
        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable), stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        BooleanSetting(prefs, ConfigSchema.Glass.PRISMAL_SHOW_NORMALS,
            "显示法线",
            "调试：用 RGB 直接显示当前表面法线",
            masterEnabled && liquidGlass)
        liquidSpecs.forEach { IntSetting(prefs, it, masterEnabled && liquidGlass) }
    }
}
'''
# The two global Prismal-prefix removals above already change the old normals strings.
if old not in u:
    old = old.replace('"显示法线"', '"Prismal · 显示法线"').replace('"调试：用 RGB 直接显示当前表面法线"', '"调试：用 RGB 直接显示当前 Prismal 表面法线"')
new = '''@Composable
private fun LiquidPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
    openLauncherHighlights: () -> Unit,
) {
    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }
    SettingsList(
        padding,
        stringResource(R.string.page_liquid),
        stringResource(R.string.liquid_header_summary),
    ) {
        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable), stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        ArrowPreference(
            stringResource(R.string.launcher_highlights_entry),
            summary = stringResource(R.string.launcher_highlights_entry_summary),
            enabled = masterEnabled && liquidGlass,
            onClick = openLauncherHighlights,
        )
        BooleanSetting(
            prefs,
            ConfigSchema.Glass.PRISMAL_SHOW_NORMALS,
            "显示表面法线（调试）",
            "用颜色显示表面法线方向，便于调试折射与光照",
            masterEnabled && liquidGlass,
        )
        liquidSpecs.forEach { IntSetting(prefs, it, masterEnabled && liquidGlass) }
    }
}

@Composable
private fun LauncherHighlightsPage(
    padding: PaddingValues,
    prefs: SharedPreferences,
    masterEnabled: Boolean,
) {
    val liquidEnabled = prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())
    SettingsList(
        padding,
        stringResource(R.string.page_launcher_highlights),
        stringResource(R.string.launcher_highlights_header_summary),
    ) {
        launcherHighlightSpecs.forEach { spec ->
            RawBooleanSetting(
                prefs, spec.key, true,
                stringResource(spec.titleRes), stringResource(spec.summaryRes),
                masterEnabled && liquidEnabled,
            )
        }
    }
}
'''
u = once(u, old, new, 'Liquid and highlight pages')

u = once(
    u,
    '@Composable\nprivate fun IntSetting(prefs: SharedPreferences, spec: IntSpec, enabledOverride: Boolean? = null) {\n',
    '''@Composable
private fun RawBooleanSetting(
    prefs: SharedPreferences,
    key: String,
    default: Boolean,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
) {
    var value by remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchPreference(
        checked = value,
        onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply() },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
private fun IntSetting(prefs: SharedPreferences, spec: IntSpec, enabledOverride: Boolean? = null) {
''',
    'raw boolean control',
)

for forbidden in (
    'Prismal ·', 'PassBlur → OES → Prismal zero-copy', 'Launcher Prismal',
    'zero-copy 后端', '双通道 FBO', 'v1.0.6 Quick Start',
):
    if forbidden in u:
        raise RuntimeError(f"forbidden Liquid UI phrase remains: {forbidden}")
UI.write_text(u)


def add_strings(path, replacements, block):
    t = path.read_text()
    for old, new in replacements:
        t = once(t, old, new, f"{path}:{old}")
    if 'name="page_launcher_highlights"' in t:
        raise RuntimeError(f"{path}: highlight strings already exist")
    t = once(t, '</resources>', block + '</resources>', f"{path}:insert")
    path.write_text(t)


en = '''    <string name="liquid_header_summary">Adjust blur, refraction, dispersion, color, and lighting; restart the launcher to apply changes.</string>
    <string name="page_launcher_highlights">Folder &amp; widget highlights</string>
    <string name="launcher_highlights_entry">Folder &amp; widget highlight layers</string>
    <string name="launcher_highlights_entry_summary">Choose the highlight layers used by folder glass and future widget glass</string>
    <string name="launcher_highlights_header_summary">Folder glass uses these highlight switches; future widget glass will reuse the same set.</string>
    <string name="highlight_sky_haze">Sky haze</string><string name="highlight_sky_haze_summary">Soft reflected haze near the glass edge</string>
    <string name="highlight_specular">Specular highlights</string><string name="highlight_specular_summary">Bright reflections from the primary light</string>
    <string name="highlight_lit_rim">Lit rim</string><string name="highlight_lit_rim_summary">Edge light on the side facing the primary light</string>
    <string name="highlight_opposite_rim">Opposite rim</string><string name="highlight_opposite_rim_summary">Secondary edge light opposite the primary light</string>
    <string name="highlight_corner_rim">Corner rim</string><string name="highlight_corner_rim_summary">Extra highlight concentrated around rounded corners</string>
    <string name="highlight_face_sheen">Face sheen</string><string name="highlight_face_sheen_summary">Broad soft sheen across the glass surface</string>
    <string name="highlight_plain">Base highlight</string><string name="highlight_plain_summary">Basic edge highlight controlled by the highlight intensity</string>
    <string name="highlight_caustics">Caustics</string><string name="highlight_caustics_summary">Focused light patterns from the curved glass surface</string>
    <string name="highlight_press_glow">Press glow</string><string name="highlight_press_glow_summary">Localized glow used by interactive press effects</string>
'''
zh = '''    <string name="liquid_header_summary">调整液态玻璃的模糊、折射、色散、颜色与光照效果；修改后重启桌面生效。</string>
    <string name="page_launcher_highlights">文件夹与小组件高光</string>
    <string name="launcher_highlights_entry">文件夹与小组件高光层</string>
    <string name="launcher_highlights_entry_summary">选择文件夹液态玻璃及未来小组件液态玻璃使用的高光层</string>
    <string name="launcher_highlights_header_summary">文件夹液态玻璃使用这些高光开关；未来小组件液态玻璃将共用同一组设置。</string>
    <string name="highlight_sky_haze">天空雾光</string><string name="highlight_sky_haze_summary">玻璃边缘附近的柔和环境反射雾光</string>
    <string name="highlight_specular">镜面高光</string><string name="highlight_specular_summary">主光源形成的明亮镜面反射</string>
    <string name="highlight_lit_rim">受光侧边缘</string><string name="highlight_lit_rim_summary">朝向主光源一侧的边缘高光</string>
    <string name="highlight_opposite_rim">背光侧边缘</string><string name="highlight_opposite_rim_summary">主光源相对一侧的辅助边缘高光</string>
    <string name="highlight_corner_rim">圆角高光</string><string name="highlight_corner_rim_summary">集中在圆角区域的附加高光</string>
    <string name="highlight_face_sheen">表面柔光</string><string name="highlight_face_sheen_summary">覆盖玻璃表面的宽幅柔和反光</string>
    <string name="highlight_plain">基础高光</string><string name="highlight_plain_summary">由基础高光强度参数控制的边缘高光</string>
    <string name="highlight_caustics">焦散</string><string name="highlight_caustics_summary">曲面玻璃聚焦形成的光纹</string>
    <string name="highlight_press_glow">按压辉光</string><string name="highlight_press_glow_summary">交互按压效果使用的局部辉光</string>
'''
add_strings(
    EN,
    [
        ('<string name="home_liquid_summary">Refraction, dispersion, blur backends, and adaptive local capture</string>', '<string name="home_liquid_summary">Blur, refraction, dispersion, color, lighting, and highlights</string>'),
        ('<string name="liquid_enable_summary">Captures only the area below the Dock and applies refraction with a selectable blur backend</string>', '<string name="liquid_enable_summary">Enables liquid glass on supported surfaces using the configured optical properties</string>'),
    ],
    en,
)
add_strings(
    ZH,
    [
        ('<string name="home_liquid_summary">折射、色散、模糊后端与自适应局部捕获</string>', '<string name="home_liquid_summary">模糊、折射、色散、颜色、光照与高光控制</string>'),
        ('<string name="liquid_enable_summary">按场景捕获 Dock 下方区域，使用折射 Shader 与可切换模糊后端</string>', '<string name="liquid_enable_summary">为支持的界面启用液态玻璃效果，并使用下方配置的光学属性</string>'),
    ],
    zh,
)

print("settings transform applied")
