package com.hellovoid.betterdock

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class ComposeSettingsActivity : SettingsActivity() {
    override fun useLegacyPreferenceUi(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.MonetSystem) }
            MiuixTheme(controller = controller) { BetterDockSettings(this) }
        }
    }
}

private enum class Page(val title: String) {
    Home("BetterDock"), Grid("主屏幕布局"), Dock("Dock 尺寸与模糊"),
    Stroke("描边与流光"), Shadow("阴影"), Data("预设与数据")
}

private data class IntSpec(
    val key: String, val title: String, val default: Int,
    val min: Int, val max: Int, val unit: String = "px",
    val dependency: String? = null
)

private val gridSpecs = listOf(
    IntSpec("grid_landscape_margin_left", "横屏左边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_margin_right", "横屏右边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_margin_top", "横屏上边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_margin_bottom", "横屏下边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_margin_left", "竖屏左边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_margin_right", "竖屏右边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_margin_top", "竖屏上边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_margin_bottom", "竖屏下边距偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_row_gap", "横屏图标纵向间距偏移", 0, -200, 400, "dp"),
    IntSpec("grid_portrait_row_gap", "竖屏图标纵向间距偏移", 0, -200, 400, "dp"),
    IntSpec("indicator_landscape_y", "横屏指示器 Y", 0, -400, 400),
    IntSpec("indicator_portrait_y", "竖屏指示器 Y", 0, -400, 400),
)
private val dockSpecs = listOf(
    IntSpec("blur_radius", "模糊强度", 100, 0, 400, ""),
    IntSpec("height_offset", "高度偏移", 0, -200, 200),
    IntSpec("width_offset", "宽度偏移", 0, -200, 200),
    IntSpec("corner_offset", "描边圆角偏移", -1, -50, 100, "dp"),
    IntSpec("blur_corner_offset", "内部模糊圆角偏移", 0, -50, 100, "dp"),
    IntSpec("dock_spacing", "Dock 图标间距", 0, -10, 20),
    IntSpec("dock_bottom_offset", "Dock 底部偏移", 0, 0, 80),
)
private val strokeSpecs = listOf(
    IntSpec("stroke_base_r", "描边底色 · 红", 255, 0, 255, "", "dock_stroke"),
    IntSpec("stroke_base_g", "描边底色 · 绿", 255, 0, 255, "", "dock_stroke"),
    IntSpec("stroke_base_b", "描边底色 · 蓝", 255, 0, 255, "", "dock_stroke"),
    IntSpec("stroke_base_alpha", "描边底色 · 透明度", 255, 0, 255, "", "dock_stroke"),
    IntSpec("sq_stroke_w", "方圆形描边宽度", 4, 1, 20, "px", "squircle"),
    IntSpec("sq_stroke_off", "方圆形描边内缩", 8, 0, 30, "px", "squircle"),
    IntSpec("sq_outer_cp", "方圆曲线控制点", 58, 40, 80, "", "squircle"),
    IntSpec("stroke_w", "Fill-Diff 宽度", 2, 1, 10, "px", "fill_diff"),
    IntSpec("std_stroke_w", "标准描边宽度", 4, 1, 20),
)
private val shadowSpecs = listOf(
    IntSpec("dock_shadow_radius", "Dock 阴影柔化", 42, 1, 80, "px", "dock_shadow"),
    IntSpec("dock_shadow_size", "Dock 阴影扩散大小", 52, 1, 120, "px", "dock_shadow"),
    IntSpec("dock_shadow_alpha", "Dock 阴影透明度", 140, 0, 200, "", "dock_shadow"),
    IntSpec("dock_shadow_y", "Dock 阴影 Y 偏移", 12, -40, 40, "px", "dock_shadow"),
    IntSpec("shadow_radius", "描边阴影半径", 8, 1, 40, "px", "stroke_shadow"),
    IntSpec("shadow_alpha", "描边阴影透明度", 70, 0, 200, "", "stroke_shadow"),
)

@Composable
private fun BetterDockSettings(activity: ComposeSettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    BackHandler(enabled = page != Page.Home) { page = Page.Home }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = page.title,
                subtitle = if (page == Page.Home) "HyperOS 3 Pad" else "",
                navigationIcon = {
                    if (page != Page.Home) TextButton(text = "返回", onClick = { page = Page.Home })
                },
                actions = {
                    TextButton(text = "重启桌面", onClick = activity::restartLauncher)
                },
            )
        },
    ) { padding ->
        when (page) {
            Page.Home -> HomePage(padding) { page = it }
            Page.Grid -> GridPage(padding, prefs)
            Page.Dock -> DockPage(padding, prefs)
            Page.Stroke -> StrokePage(padding, prefs)
            Page.Shadow -> ShadowPage(padding, prefs)
            Page.Data -> DataPage(padding, activity)
        }
    }
}

@Composable
private fun HomePage(padding: PaddingValues, open: (Page) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { SmallTitle("自定义") }
        item {
            SettingsCard {
                ArrowPreference("主屏幕布局", summary = "8×4 / 4×8、横竖屏边距与指示器", onClick = { open(Page.Grid) })
                ArrowPreference("Dock 尺寸与模糊", summary = "整体开关、尺寸、圆角、间距与位置", onClick = { open(Page.Dock) })
                ArrowPreference("描边与流光", summary = "底色、方圆曲线、动态灯光与线宽", onClick = { open(Page.Stroke) })
                ArrowPreference("阴影", summary = "整个 Dock 阴影与描边阴影", onClick = { open(Page.Shadow) })
            }
        }
        item { SmallTitle("配置") }
        item { SettingsCard { ArrowPreference("预设、导入与导出", summary = "iPad 预设、JSON 备份与重启桌面", onClick = { open(Page.Data) }) } }
    }
}

@Composable
private fun GridPage(padding: PaddingValues, prefs: SharedPreferences) {
    SettingsList(padding, "网格") {
        BooleanSetting(prefs, "home_grid_8x4", "启用 8×4 / 4×8 网格", true,
            "仅切换行列数；边距、间距和指示器偏移始终生效")
        gridSpecs.forEach { IntSetting(prefs, it, true) }
    }
}

@Composable
private fun DockPage(padding: PaddingValues, prefs: SharedPreferences) {
    var dockEnabled by remember { mutableStateOf(prefs.getBoolean("dock_customization", true)) }
    SettingsList(padding, "Dock") {
        BooleanSetting(prefs, "dock_customization", "自定义整个 Dock", true,
            "BetterDock Dock 功能总开关") { dockEnabled = it }
        StringDropdown(prefs, "light_mode", "灯光模式", "fixed",
            listOf("固定" to "fixed", "陀螺仪动态" to "dynamic", "关闭" to "none"), dockEnabled)
        dockSpecs.forEach { IntSetting(prefs, it, dockEnabled) }
    }
}

@Composable
private fun StrokePage(padding: PaddingValues, prefs: SharedPreferences) {
    val dockEnabled = prefs.getBoolean("dock_customization", true)
    var dockStroke by remember { mutableStateOf(prefs.getBoolean("dock_stroke", true)) }
    var squircle by remember { mutableStateOf(prefs.getBoolean("squircle", false)) }
    var fillDiff by remember { mutableStateOf(prefs.getBoolean("fill_diff", false)) }
    SettingsList(padding, "描边") {
        BooleanSetting(prefs, "dock_stroke", "显示完整描边", true, "控制 Dock 边框与灯光", dockEnabled) { dockStroke = it }
        BooleanSetting(prefs, "squircle", "方圆形连续曲线", false, "iPad 风格连续圆角", dockEnabled) { squircle = it }
        BooleanSetting(prefs, "fill_diff", "Fill-Diff 描边", false, "通过填充与挖空获得清晰抗锯齿", dockEnabled) { fillDiff = it }
        SmallTitle("描边背景色")
        strokeSpecs.take(4).forEach {
            IntSetting(prefs, it, dockEnabled && dockStroke)
        }
        SmallTitle("方圆形与线宽")
        strokeSpecs.drop(4).forEach {
            val enabled = when (it.dependency) {
                "dock_stroke" -> dockStroke
                "squircle" -> squircle
                "fill_diff" -> fillDiff
                else -> true
            }
            IntSetting(prefs, it, dockEnabled && enabled)
        }
    }
}

@Composable
private fun ShadowPage(padding: PaddingValues, prefs: SharedPreferences) {
    val dockEnabled = prefs.getBoolean("dock_customization", true)
    var dockShadow by remember { mutableStateOf(prefs.getBoolean("dock_shadow", true)) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean("stroke_shadow", false)) }
    SettingsList(padding, "阴影") {
        BooleanSetting(prefs, "dock_shadow", "整个 Dock 下方阴影", true, "跟随 Dock 长宽、高度和圆角", dockEnabled) { dockShadow = it }
        BooleanSetting(prefs, "stroke_shadow", "描边阴影", false, "描边下方的柔和阴影", dockEnabled) { strokeShadow = it }
        shadowSpecs.forEach {
            IntSetting(prefs, it, dockEnabled && when (it.dependency) {
                "dock_shadow" -> dockShadow
                "stroke_shadow" -> strokeShadow
                else -> true
            })
        }
    }
}

@Composable
private fun DataPage(padding: PaddingValues, activity: ComposeSettingsActivity) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { SmallTitle("预设") }
        item { SettingsCard { ArrowPreference("应用 iPad 预设", summary = "根据桌面资源和当前分辨率计算", onClick = { applyIpadPreset(activity) }) } }
        item { SmallTitle("备份与应用") }
        item {
            SettingsCard {
                ArrowPreference("导出当前参数", summary = "保存为 BetterDock JSON", onClick = activity::launchExport)
                ArrowPreference("导入参数", summary = "校验、写入并重启桌面", onClick = activity::launchImport)
            }
        }
    }
}

@Composable
private fun SettingsList(padding: PaddingValues, title: String, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { SmallTitle(title) }
        item { SettingsCard(content) }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(content = content)
    }
}

@Composable
private fun BooleanSetting(
    prefs: SharedPreferences, key: String, title: String, default: Boolean, summary: String? = null,
    enabled: Boolean = true, onChanged: (Boolean) -> Unit = {},
) {
    var value by remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    SwitchPreference(
        checked = value,
        onCheckedChange = { value = it; prefs.edit().putBoolean(key, it).apply(); onChanged(it) },
        title = title,
        summary = summary,
        enabled = enabled,
    )
}

@Composable
private fun IntSetting(prefs: SharedPreferences, spec: IntSpec, enabledOverride: Boolean? = null) {
    var value by remember(spec.key) { mutableIntStateOf(prefs.getInt(spec.key, spec.default)) }
    val enabled = enabledOverride ?: spec.dependency?.let { prefs.getBoolean(it, false) } ?: true
    SliderPreference(
        value = value.toFloat(),
        onValueChange = {
            val next = it.roundToInt().coerceIn(spec.min, spec.max)
            value = next
            prefs.edit().putInt(spec.key, next).apply()
        },
        title = spec.title,
        valueText = "$value${if (spec.unit.isBlank()) "" else " ${spec.unit}"}",
        enabled = enabled,
        valueRange = spec.min.toFloat()..spec.max.toFloat(),
        steps = (spec.max - spec.min - 1).coerceAtLeast(0),
        endActions = {
            Button(
                onClick = { value = spec.default; prefs.edit().putInt(spec.key, spec.default).apply() },
                enabled = enabled && value != spec.default,
                minWidth = 56.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("重置") }
        },
        insideMargin = PaddingValues(16.dp, 16.dp, 16.dp, 2.dp),
    )
}

@Composable
private fun StringDropdown(
    prefs: SharedPreferences, key: String, title: String, default: String,
    options: List<Pair<String, String>>, enabled: Boolean = true,
) {
    var value by remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    val index = options.indexOfFirst { it.second == value }.coerceAtLeast(0)
    ArrowPreference(
        title = title,
        summary = options[index].first,
        enabled = enabled,
        onClick = {
            if (!enabled) return@ArrowPreference
            val next = options[(index + 1) % options.size].second
            value = next
            prefs.edit().putString(key, next).apply()
        },
    )
}

private fun applyIpadPreset(activity: ComposeSettingsActivity) {
    val dm = activity.resources.displayMetrics
    val density = dm.density
    val shortSideDp = min(dm.widthPixels, dm.heightPixels) / density
    val displayScale = max(0.90f, min(1.20f, shortSideDp / 668f))
    var launcherRes: Resources? = null
    try {
        launcherRes = activity.createPackageContext("com.miui.home", Context.CONTEXT_IGNORE_SECURITY).resources
    } catch (_: PackageManager.NameNotFoundException) {}
    fun dimen(name: String, fallbackDp: Float): Int {
        val id = launcherRes?.getIdentifier(name, "dimen", "com.miui.home") ?: 0
        return if (id != 0) launcherRes!!.getDimensionPixelSize(id) else (fallbackDp * density).roundToInt()
    }
    val icon = dimen("config_hotseats_icon_content_default_height", 60f)
    val cell = dimen("hotseats_list_content_cell_width", 80f)
    val dockHeight = dimen("hotseats_height_land", 78f)
    val dockRadius = dimen("hotseats_list_content_background_radius", 21f)
    val sidePadding = dimen("hotseats_list_content_padding_side", 9.3f)
    val spacing = ((icon + 14f * density * displayScale - cell) / 2f).roundToInt()
    val heightOffset = icon + (20f * density * displayScale).roundToInt() - dockHeight
    val widthOffset = 2 * ((14f * density * displayScale).roundToInt() - sidePadding)
    val cornerOffset = (((22f * density * displayScale).roundToInt() - dockRadius) / density).roundToInt()
    val oneDp = max(1, (density * displayScale).roundToInt())
    PreferenceManager.getDefaultSharedPreferences(activity).edit()
        .putString("light_mode", "dynamic").putInt("blur_radius", 100)
        .putInt("height_offset", heightOffset).putInt("width_offset", widthOffset)
        .putBoolean("corners_dp", true)
        .putInt("corner_offset", cornerOffset).putInt("blur_corner_offset", -1)
        .putBoolean("home_grid_8x4", true)
        .putBoolean("grid_margins_dp", true).putBoolean("grid_margins_offset", true)
        .putInt("grid_landscape_margin_left", 0).putInt("grid_landscape_margin_right", 0)
        .putInt("grid_landscape_margin_top", 0).putInt("grid_landscape_margin_bottom", 0)
        .putInt("grid_portrait_margin_left", 0).putInt("grid_portrait_margin_right", 0)
        .putInt("grid_portrait_margin_top", 0).putInt("grid_portrait_margin_bottom", 0)
        .putInt("grid_landscape_row_gap", 0).putInt("grid_portrait_row_gap", 0)
        .putInt("indicator_landscape_y", 0).putInt("indicator_portrait_y", 0)
        .putBoolean("dock_customization", true).putBoolean("dock_stroke", true)
        .putInt("stroke_base_r", 255).putInt("stroke_base_g", 255)
        .putInt("stroke_base_b", 255).putInt("stroke_base_alpha", 255)
        .putBoolean("squircle", true).putInt("sq_stroke_w", oneDp)
        .putInt("sq_stroke_off", 0).putInt("sq_outer_cp", 65)
        .putBoolean("fill_diff", true).putInt("stroke_w", oneDp).putInt("std_stroke_w", oneDp)
        .putBoolean("dock_shadow", true)
        .putInt("dock_shadow_radius", (10f * density * displayScale).roundToInt())
        .putInt("dock_shadow_size", (13f * density * displayScale).roundToInt())
        .putInt("dock_shadow_alpha", 140).putInt("dock_shadow_y", (3f * density * displayScale).roundToInt())
        .putBoolean("stroke_shadow", false).putInt("shadow_radius", (3f * density * displayScale).roundToInt())
        .putInt("shadow_alpha", 70).putInt("dock_spacing", spacing)
        .putInt("dock_bottom_offset", (10f * density * displayScale).roundToInt()).commit()
    Toast.makeText(activity, "iPad 预设已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
}
