from pathlib import Path

ROOT = Path('.')

def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'missing expected block in {path}: {old[:100]!r}')
    p.write_text(text.replace(old, new, 1), encoding='utf-8')

# ---- Typed config: Divider becomes a peer of Dock ----
path = 'src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java'
replace_once(path,
'''    final Grid grid;
    final Dock dock;
    final Glass glass;
''',
'''    final Grid grid;
    final Dock dock;
    final Divider divider;
    final Glass glass;
''')
replace_once(path,
'''        grid = new Grid(c);
        dock = new Dock(c);
        glass = new Glass(c);
''',
'''        grid = new Grid(c);
        dock = new Dock(c);
        divider = new Divider(c);
        glass = new Glass(c);
''')
replace_once(path,
'''        final float strokeShadowRadius, shadowRadius, shadowSize, shadowY;
        final int strokeShadowAlpha, shadowAlpha;
        final float dividerWidthDp, dividerHeightScale, dividerYOffset;
        final int dividerColorR, dividerColorG, dividerColorB, dividerAlpha;
''',
'''        final float strokeShadowRadius, shadowRadius, shadowSize, shadowY;
        final int strokeShadowAlpha, shadowAlpha;
''')
replace_once(path,
'''            shadowAlpha = channel(c.i("dock_shadow_alpha", 140));
            shadowY = c.f("dock_shadow_y", 12);
            dividerWidthDp = c.f("dock_divider_width_dp", 0);
            dividerHeightScale = c.f("dock_divider_height_scale", 0);
            dividerYOffset = c.f("dock_divider_y_offset", 0);
            dividerColorR = c.i("dock_divider_color_r", 0);
            dividerColorG = c.i("dock_divider_color_g", 0);
            dividerColorB = c.i("dock_divider_color_b", 0);
            dividerAlpha = c.i("dock_divider_alpha", 0);
        }
    }

    static final class Glass {
''',
'''            shadowAlpha = channel(c.i("dock_shadow_alpha", 140));
            shadowY = c.f("dock_shadow_y", 12);
        }
    }

    /** Divider customization is independent from Dock geometry and unit switches. */
    static final class Divider {
        final boolean enabled, explicitMode;
        final float widthDp, heightPercent, yOffsetDp;
        final int colorR, colorG, colorB, alpha;

        Divider(ConfigReader c) {
            boolean hasLegacyConfig = c.has("dock_divider_width_dp")
                    || c.has("dock_divider_height_scale")
                    || c.has("dock_divider_y_offset")
                    || c.has("dock_divider_color_r")
                    || c.has("dock_divider_color_g")
                    || c.has("dock_divider_color_b")
                    || c.has("dock_divider_alpha");
            explicitMode = c.has("dock_divider_enabled");
            enabled = c.b("dock_divider_enabled", hasLegacyConfig);

            // Historical storage is tenths of dp. Normalize it here so the Hook only
            // sees real dp and never knows about dock_dimensions_dp.
            widthDp = Math.max(0f, c.f("dock_divider_width_dp", 10) / 10f);
            heightPercent = clamp(c.f("dock_divider_height_scale", 60), 0f, 100f);
            yOffsetDp = c.f("dock_divider_y_offset", 0) / 10f;
            colorR = channel(c.i("dock_divider_color_r", 255));
            colorG = channel(c.i("dock_divider_color_g", 255));
            colorB = channel(c.i("dock_divider_color_b", 255));
            alpha = channel(c.i("dock_divider_alpha", 128));
        }
    }

    static final class Glass {
''')
replace_once(path,
'''    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
''',
'''    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
''')

# ---- Runtime Hook: independent enable + fixed dp semantics, with legacy sentinel mode ----
(ROOT / 'src/main/java/com/hellovoid/liquiddock/DockDividerHook.java').write_text(r'''package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

/** Controls workstation/laptop Dock divider lines independently from Dock geometry. */
final class DockDividerHook {
    private DockDividerHook() {}
    private static int channel(int v) { return Math.max(0, Math.min(v, 255)); }

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder",
                    "bindView",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        LiquidDockConfig.Divider cfg = LiquidDockConfig.load().divider;
                        if (!cfg.enabled) return result;

                        View line = (View) HookUtil.invoke(chain.getThisObject(), "getContent");
                        if (line == null) return result;
                        ViewGroup.MarginLayoutParams lp =
                                (ViewGroup.MarginLayoutParams) line.getLayoutParams();
                        float density = line.getResources().getDisplayMetrics().density;

                        if (cfg.explicitMode) {
                            // New mode: every configured value is literal; zero is valid.
                            lp.width = Math.max(0, Math.round(cfg.widthDp * density));
                            int parentH = ((View) line.getParent()).getHeight();
                            if (parentH <= 0) parentH = lp.height > 0 ? lp.height : line.getHeight();
                            if (parentH > 0) {
                                int targetH = Math.round(parentH * cfg.heightPercent / 100f);
                                lp.topMargin = (parentH - targetH) / 2
                                        + Math.round(cfg.yOffsetDp * density);
                                lp.height = targetH;
                            }
                            line.setLayoutParams(lp);
                            line.setBackgroundColor(Color.argb(channel(cfg.alpha),
                                    channel(cfg.colorR), channel(cfg.colorG), channel(cfg.colorB)));
                        } else {
                            // Compatibility mode: pre-split configs used zero as "system default".
                            if (cfg.widthDp != 0f)
                                lp.width = Math.round(cfg.widthDp * density);
                            if (cfg.heightPercent != 0f) {
                                int parentH = ((View) line.getParent()).getHeight();
                                if (parentH <= 0) parentH = lp.height > 0 ? lp.height : line.getHeight();
                                if (parentH > 0) {
                                    int targetH = Math.round(parentH * cfg.heightPercent / 100f);
                                    lp.topMargin = (parentH - targetH) / 2;
                                    lp.height = targetH;
                                }
                            }
                            if (cfg.yOffsetDp != 0f)
                                lp.topMargin += Math.round(cfg.yOffsetDp * density);
                            line.setLayoutParams(lp);
                            boolean hasColor = cfg.colorR != 0 || cfg.colorG != 0 || cfg.colorB != 0;
                            boolean hasAlpha = cfg.alpha != 0;
                            if (hasColor || hasAlpha) {
                                int color = Color.rgb(hasColor ? channel(cfg.colorR) : 255,
                                        hasColor ? channel(cfg.colorG) : 255,
                                        hasColor ? channel(cfg.colorB) : 255);
                                if (hasAlpha) color = Color.argb(channel(cfg.alpha),
                                        Color.red(color), Color.green(color), Color.blue(color));
                                line.setBackgroundColor(color);
                            }
                        }
                        return result;
                    });
            MainHook.log("[DC] dock divider hook installed");
        } catch (Throwable e) {
            MainHook.log("[DC] dock divider hook unavailable: " + e);
        }
    }
}
''', encoding='utf-8')

# ---- Compose UI: separate top-level Divider page ----
path = 'src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt'
replace_once(path,
'''private enum class Page(val titleRes: Int) {
    Home(R.string.app_name), Grid(R.string.page_grid), Dock(R.string.page_dock),
    Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),
''',
'''private enum class Page(val titleRes: Int) {
    Home(R.string.app_name), Grid(R.string.page_grid), Dock(R.string.page_dock),
    Divider(R.string.page_divider), Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),
''')
replace_once(path,
'''    "dock_divider_color_r" -> "分隔竖线颜色 · 红，全为 0 使用系统默认"
    "dock_divider_color_g" -> "分隔竖线颜色 · 绿，全为 0 使用系统默认"
    "dock_divider_color_b" -> "分隔竖线颜色 · 蓝，全为 0 使用系统默认"
    "dock_divider_alpha" -> "分隔竖线不透明度，0 使用系统默认"
''',
'''    "dock_divider_color_r" -> "分隔竖线颜色 · 红"
    "dock_divider_color_g" -> "分隔竖线颜色 · 绿"
    "dock_divider_color_b" -> "分隔竖线颜色 · 蓝"
    "dock_divider_alpha" -> "分隔竖线不透明度"
''')
replace_once(path,
'''    IntSpec("dock_spacing", "Dock 图标间距", 0, -8, 12, "dp"),
    IntSpec("dock_bottom_offset", "Dock 底部偏移", 0, -30, 40, "dp"),
    IntSpec("dock_divider_width_dp", "分隔线宽度", 0, 0, 160, "dp×10"),
    IntSpec("dock_divider_height_scale", "分隔线高度比例", 0, 0, 100, "%"),
    IntSpec("dock_divider_y_offset", "分隔线垂直偏移", 0, -80, 80, "dp×10"),
    IntSpec("dock_divider_color_r", "分隔线颜色 · 红", 0, 0, 255, ""),
    IntSpec("dock_divider_color_g", "分隔线颜色 · 绿", 0, 0, 255, ""),
    IntSpec("dock_divider_color_b", "分隔线颜色 · 蓝", 0, 0, 255, ""),
    IntSpec("dock_divider_alpha", "分隔线透明度", 0, 0, 255, ""),
)
private val workstationSpecs = listOf(
''',
'''    IntSpec("dock_spacing", "Dock 图标间距", 0, -8, 12, "dp"),
    IntSpec("dock_bottom_offset", "Dock 底部偏移", 0, -30, 40, "dp"),
)
private val dividerSpecs = listOf(
    IntSpec("dock_divider_width_dp", "分隔线宽度", 10, 0, 160, "dp×10"),
    IntSpec("dock_divider_height_scale", "分隔线高度比例", 60, 0, 100, "%"),
    IntSpec("dock_divider_y_offset", "分隔线垂直偏移", 0, -80, 80, "dp×10"),
    IntSpec("dock_divider_color_r", "分隔线颜色 · 红", 255, 0, 255, ""),
    IntSpec("dock_divider_color_g", "分隔线颜色 · 绿", 255, 0, 255, ""),
    IntSpec("dock_divider_color_b", "分隔线颜色 · 蓝", 255, 0, 255, ""),
    IntSpec("dock_divider_alpha", "分隔线透明度", 128, 0, 255, ""),
)
private val dividerKeys = dividerSpecs.map { it.key }
private fun hasLegacyDividerConfig(prefs: SharedPreferences): Boolean =
    dividerKeys.any(prefs::contains)
private fun ensureDividerDefaults(prefs: SharedPreferences) {
    val e = prefs.edit()
    dividerSpecs.forEach { if (!prefs.contains(it.key)) e.putInt(it.key, it.default) }
    e.apply()
}
private val workstationSpecs = listOf(
''')
replace_once(path,
'''                Page.Grid -> GridPage(padding, prefs, masterEnabled)
                Page.Dock -> DockPage(padding, prefs, masterEnabled)
                Page.Workstation -> WorkstationPage(padding, prefs, masterEnabled)
''',
'''                Page.Grid -> GridPage(padding, prefs, masterEnabled)
                Page.Dock -> DockPage(padding, prefs, masterEnabled)
                Page.Divider -> DividerPage(padding, prefs, masterEnabled)
                Page.Workstation -> WorkstationPage(padding, prefs, masterEnabled)
''')
replace_once(path,
'''                ArrowPreference(stringResource(R.string.page_dock), summary = stringResource(R.string.home_dock_summary), onClick = { open(Page.Dock) })
                ArrowPreference(stringResource(R.string.page_workstation), summary = stringResource(R.string.home_workstation_summary), onClick = { open(Page.Workstation) })
''',
'''                ArrowPreference(stringResource(R.string.page_dock), summary = stringResource(R.string.home_dock_summary), onClick = { open(Page.Dock) })
                ArrowPreference(stringResource(R.string.page_divider), summary = stringResource(R.string.home_divider_summary), onClick = { open(Page.Divider) })
                ArrowPreference(stringResource(R.string.page_workstation), summary = stringResource(R.string.home_workstation_summary), onClick = { open(Page.Workstation) })
''')
replace_once(path,
'''@Composable
private fun WorkstationPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
''',
'''@Composable
private fun DividerPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val legacyDefault = remember { hasLegacyDividerConfig(prefs) }
    var enabled by remember {
        mutableStateOf(prefs.getBoolean("dock_divider_enabled", legacyDefault))
    }
    SettingsList(padding, stringResource(R.string.page_divider)) {
        BooleanSetting(prefs, "dock_divider_enabled", "自定义 Dock 分隔线", legacyDefault,
            "独立于 Dock 尺寸、模糊和单位开关；宽度与偏移固定使用 dp",
            masterEnabled) {
            enabled = it
            if (it) ensureDividerDefaults(prefs)
        }
        dividerSpecs.forEach { IntSetting(prefs, it, masterEnabled && enabled) }
    }
}

@Composable
private fun WorkstationPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
''')
replace_once(path,
'''        .putBoolean("workstation_dock_customization", false)
        .putInt("blur_radius", 100)
''',
'''        .putBoolean("workstation_dock_customization", false)
        .putBoolean("dock_divider_enabled", false)
        .putInt("blur_radius", 100)
''')

# ---- String resources ----
for path, page, summary, master_old, master_new in [
    ('src/main/res/values/strings.xml', 'Dock divider',
     'Independent divider width, height, position, color, and opacity',
     'Disables all home-grid, Dock, liquid-glass, stroke, and shadow hooks when turned off',
     'Disables all home-grid, Dock, divider, liquid-glass, stroke, and shadow hooks when turned off'),
    ('src/main/res/values-zh-rCN/strings.xml', 'Dock 分隔线',
     '独立控制分隔线宽度、高度、位置、颜色与透明度',
     '关闭后停止主屏幕布局、Dock、液态玻璃、描边和阴影的全部 Hook',
     '关闭后停止主屏幕布局、Dock、分隔线、液态玻璃、描边和阴影的全部 Hook'),
]:
    replace_once(path,
        '    <string name="page_dock">' + ('Dock size and blur' if 'values/strings' in path else 'Dock 尺寸与模糊') + '</string>\n',
        '    <string name="page_dock">' + ('Dock size and blur' if 'values/strings' in path else 'Dock 尺寸与模糊') + '</string>\n'
        f'    <string name="page_divider">{page}</string>\n')
    replace_once(path,
        '    <string name="home_dock_summary">' + ('Size, corners, icon spacing, position, and blur' if 'values/strings' in path else '尺寸、圆角、图标间距、位置与模糊') + '</string>\n',
        '    <string name="home_dock_summary">' + ('Size, corners, icon spacing, position, and blur' if 'values/strings' in path else '尺寸、圆角、图标间距、位置与模糊') + '</string>\n'
        f'    <string name="home_divider_summary">{summary}</string>\n')
    replace_once(path, master_old, master_new)

# ---- Backup/import: preserve explicit-vs-legacy divider semantics ----
path = 'src/main/java/com/hellovoid/liquiddock/SettingsActivity.java'
replace_once(path,
'''        j.put("dock_smooth_resize_animation", sp.getBoolean("dock_smooth_resize_animation", true));
        j.put("workstation_dock_customization",
''',
'''        j.put("dock_smooth_resize_animation", sp.getBoolean("dock_smooth_resize_animation", true));
        if (sp.contains("dock_divider_enabled"))
            j.put("dock_divider_enabled", sp.getBoolean("dock_divider_enabled", false));
        String[] dividerKeys = {"dock_divider_width_dp", "dock_divider_height_scale",
                "dock_divider_y_offset", "dock_divider_color_r", "dock_divider_color_g",
                "dock_divider_color_b", "dock_divider_alpha"};
        for (String key : dividerKeys) if (sp.contains(key)) j.put(key, sp.getInt(key, 0));
        j.put("workstation_dock_customization",
''')
replace_once(path,
'''        putInt(j, e, "dock_spacing", -10, 20);
        putInt(j, e, "dock_bottom_offset", 0, 80);
        String[] dpKeys = {
''',
'''        putInt(j, e, "dock_spacing", -10, 20);
        putInt(j, e, "dock_bottom_offset", 0, 80);
        putInt(j, e, "dock_divider_width_dp", 0, 160);
        putInt(j, e, "dock_divider_height_scale", 0, 100);
        putInt(j, e, "dock_divider_y_offset", -80, 80);
        putInt(j, e, "dock_divider_color_r", 0, 255);
        putInt(j, e, "dock_divider_color_g", 0, 255);
        putInt(j, e, "dock_divider_color_b", 0, 255);
        putInt(j, e, "dock_divider_alpha", 0, 255);
        String[] dpKeys = {
''')
replace_once(path,
'''        if (j.has("dock_smooth_resize_animation")) e.putBoolean(
                "dock_smooth_resize_animation", j.optBoolean("dock_smooth_resize_animation"));
        if (j.has("workstation_dock_customization")) e.putBoolean(
''',
'''        if (j.has("dock_smooth_resize_animation")) e.putBoolean(
                "dock_smooth_resize_animation", j.optBoolean("dock_smooth_resize_animation"));
        if (j.has("dock_divider_enabled")) e.putBoolean(
                "dock_divider_enabled", j.optBoolean("dock_divider_enabled"));
        if (j.has("workstation_dock_customization")) e.putBoolean(
''')

# ---- Legacy Preference UI: independent fallback category ----
path = 'src/main/res/xml/preferences.xml'
divider_xml = r'''
    <PreferenceCategory android:title="Dock Divider">
        <SwitchPreference
            android:key="dock_divider_enabled"
            android:title="Customize Dock Divider"
            android:summary="Independent from Dock size, blur and unit settings"
            android:defaultValue="false" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_width_dp"
            android:title="Divider Width (0.1 dp)" android:summary="%d × 0.1 dp"
            android:defaultValue="10" app:min="0" app:max="160" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_height_scale"
            android:title="Divider Height" android:summary="%d%%"
            android:defaultValue="60" app:min="0" app:max="100" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_y_offset"
            android:title="Divider Y Offset (0.1 dp)" android:summary="%d × 0.1 dp"
            android:defaultValue="0" app:min="-80" app:max="80" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_color_r"
            android:title="Divider Red" android:summary="%d" android:defaultValue="255"
            app:min="0" app:max="255" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_color_g"
            android:title="Divider Green" android:summary="%d" android:defaultValue="255"
            app:min="0" app:max="255" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_color_b"
            android:title="Divider Blue" android:summary="%d" android:defaultValue="255"
            app:min="0" app:max="255" android:dependency="dock_divider_enabled" />
        <com.hellovoid.liquiddock.SeekBarPreference android:key="dock_divider_alpha"
            android:title="Divider Alpha" android:summary="%d" android:defaultValue="128"
            app:min="0" app:max="255" android:dependency="dock_divider_enabled" />
    </PreferenceCategory>

'''
replace_once(path,
'''    <PreferenceCategory android:title="Backup &amp; Restore">
''',
    divider_xml + '''    <PreferenceCategory android:title="Backup &amp; Restore">
''')

# ---- Documentation ----
(ROOT / 'DIVIDER.md').write_text(r'''# Dock 分隔竖线控制

## 独立配置

HyperOS 3 工作台模式 Dock 的图标分隔竖线由 `DockDividerHook` 独立控制。
它与普通 Dock 的尺寸、模糊、`dock_dimensions_dp` 和工作台 Dock 尺寸开关均无依赖。

Hook 点：`HotSeatsListContentAdapter$LineViewHolder.bindView()`。
系统完成 bind 后再覆盖分隔线 View，因此旋转、Dock 展开/收起都会重新应用。

## 参数

| key | 语义 |
|---|---|
| `dock_divider_enabled` | 独立总开关 |
| `dock_divider_width_dp` | 0–160，历史存储单位 0.1 dp，运行时规范化为 dp |
| `dock_divider_height_scale` | 0–100%，相对父容器高度 |
| `dock_divider_y_offset` | -80–80，历史存储单位 0.1 dp，正值下移 |
| `dock_divider_color_r/g/b` | 0–255 |
| `dock_divider_alpha` | 0–255 |

## 兼容规则

旧版本没有 `dock_divider_enabled`，并把数值 `0` 当作“保持系统默认”。升级后：

- 若存在任一旧 divider key 且没有新开关，自动进入 **legacy mode**，继续保持旧 sentinel 语义；
- 一旦 `dock_divider_enabled` 被明确写入，则进入 **explicit mode**，此时 `0` 是真正的可设置值；
- 新设置页在第一次主动开启 Divider 时会写入一组完整默认值，因此不会因为缺少字段而改变旧配置。

## 单位边界

Divider 的 dp 换算只在 `LiquidDockConfig.Divider` 与 `DockDividerHook` 内完成，绝不读取
`dock_dimensions_dp`。Dock 尺寸单位切换不会改变 Divider 的宽度或 Y 偏移。
''', encoding='utf-8')
