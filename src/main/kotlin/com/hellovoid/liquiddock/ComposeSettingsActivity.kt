package com.hellovoid.liquiddock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.net.Uri
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
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
            MiuixTheme(controller = controller) { LiquidDockSettings(this) }
        }
    }
}

private enum class Page(val titleRes: Int) {
    Home(R.string.app_name), Grid(R.string.page_grid), Dock(R.string.page_dock),
    Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),
    Stroke(R.string.page_stroke), Shadow(R.string.page_shadow), Data(R.string.page_data),
    About(R.string.page_about)
}

// Ordinary UI writes are mirrored to API101 Remote Preferences by LiquidDockApp's
// SharedPreferences listener.  No per-control JSON/file/root synchronization exists.

private enum class IntSection { General, StrokeBackground, StrokeGeometry }

private data class IntSpec(
    val key: String, val title: String, val default: Int,
    val min: Int, val max: Int, val unit: String = "dp",
    val dependency: String? = null,
    val section: IntSection = IntSection.General,
    val summary: String = optionSummary(key),
)

private fun optionSummary(key: String): String = when (key) {
    "grid_landscape_horizontal_distance" -> "同时调整横屏布局左右两侧的水平距离"
    "grid_landscape_top_distance" -> "相对扣除 Dock 后的可用区域调整横屏顶部距离"
    "grid_landscape_bottom_distance" -> "相对扣除 Dock 后的可用区域调整横屏底部距离"
    "grid_portrait_horizontal_distance" -> "同时调整竖屏布局左右两侧的水平距离"
    "grid_portrait_top_distance" -> "相对扣除 Dock 后的可用区域调整竖屏顶部距离"
    "grid_portrait_bottom_distance" -> "相对扣除 Dock 后的可用区域调整竖屏底部距离"
    "grid_landscape_row_gap" -> "增减横屏图标行之间的垂直距离"
    "grid_portrait_row_gap" -> "增减竖屏图标行之间的垂直距离"
    "indicator_landscape_y" -> "调整横屏页面指示器的垂直位置"
    "indicator_portrait_y" -> "调整竖屏页面指示器的垂直位置"
    "blur_radius" -> "仅用于原生模糊模式；液态玻璃使用独立模糊参数"
    "height_offset" -> "相对默认高度增减 Dock 背景高度"
    "width_offset" -> "相对默认宽度增减 Dock 背景长度"
    "corner_offset" -> "相对默认值调整外部描边圆角"
    "blur_corner_offset" -> "单独调整内部模糊背景圆角"
    "dock_spacing" -> "增减相邻 Dock 图标之间的距离"
    "dock_bottom_offset" -> "调整 Dock 与屏幕底部的距离"
    "dock_divider_width_dp" -> "调整图标分隔竖线的宽度"
    "dock_divider_height_scale" -> "调整分隔竖线占图标高度的百分比"
    "dock_divider_y_offset" -> "上下偏移分隔竖线，正值下移负值上移"
    "dock_divider_color_r" -> "分隔竖线颜色 · 红，全为 0 使用系统默认"
    "dock_divider_color_g" -> "分隔竖线颜色 · 绿，全为 0 使用系统默认"
    "dock_divider_color_b" -> "分隔竖线颜色 · 蓝，全为 0 使用系统默认"
    "dock_divider_alpha" -> "分隔竖线不透明度，0 使用系统默认"
    "workstation_dock_width_offset" -> "相对系统工作台 Dock 的原始长度增减；不会改变位置或普通 Dock"
    "workstation_grid_horizontal_offset" -> "单独调整工作台 8 列图标区域的左右距离，不继承普通桌面偏移"
    "workstation_all_apps_landscape_horizontal_offset" -> "仅调整工作台所有应用横屏图标区的水平位置"
    "workstation_all_apps_landscape_vertical_offset" -> "仅调整工作台所有应用横屏图标区的垂直位置"
    "workstation_all_apps_portrait_horizontal_offset" -> "仅调整工作台所有应用竖屏图标区的水平位置"
    "workstation_all_apps_portrait_vertical_offset" -> "仅调整工作台所有应用竖屏图标区的垂直位置"
    "workstation_dock_icon_top_offset" -> "调整工作台 Dock 图标与容器顶部之间的距离"
    "workstation_dock_icon_bottom_offset" -> "调整工作台 Dock 图标与容器底部之间的距离"
    "liquid_blur" -> "液态玻璃对捕获背景的模糊范围"
    "liquid_native_blur_inset" -> "仅用于原生模糊与高级材质；让模糊区域向内收缩，为外圈高光留出清晰间距"
    "liquid_thickness" -> "影响折射路径的虚拟玻璃厚度"
    "liquid_ior" -> "折射率；越高，边缘弯曲越明显"
    "liquid_normal_strength" -> "表面法线起伏对折射的影响"
    "liquid_dome" -> "控制玻璃中心向外凸起的程度"
    "liquid_lens_refraction" -> "控制圆角边缘的透镜偏移距离"
    "liquid_chromatic" -> "边缘红蓝通道分离的强度"
    "liquid_tint_alpha" -> "玻璃底色覆盖背景的强度"
    "liquid_tint_r" -> "玻璃底色的红色通道"
    "liquid_tint_g" -> "玻璃底色的绿色通道"
    "liquid_tint_b" -> "玻璃底色的蓝色通道"
    "liquid_highlight_width" -> "调整外圈高光描边的粗细"
    "liquid_highlight_alpha" -> "调整外圈高光描边的明暗"
    "liquid_depth_effect" -> "增加从中心到边缘的深度变化"
    "liquid_brightness" -> "整体调整液态玻璃输出亮度"
    "liquid_specular_sharp" -> "越高，高光越集中且边缘更硬"
    "liquid_specular_strength" -> "控制表面镜面高光的亮度"
    "liquid_rim_light" -> "控制玻璃边缘受光强度"
    "liquid_caustics" -> "控制折射聚光形成的亮斑"
    "liquid_edge_band" -> "控制 Shader 边缘光带覆盖宽度"
    "liquid_capture_power_limit_fps" -> "动画和动态应用实时捕获的统一帧率上限"
    "liquid_dynamic_app_probe_fps" -> "静态应用中检测画面变化的低频帧率"
    "liquid_dynamic_motion_threshold" -> "越低越容易因亮度变化进入高频捕获"
    "liquid_dynamic_bit_threshold" -> "越低越容易因采样像素变化触发高频捕获"
    "liquid_dynamic_hold_ms" -> "检测到动态后维持高频捕获的时长"
    "liquid_black_threshold" -> "低于此平均亮度的捕获帧会被丢弃"
    "liquid_capture_scale" -> "SurfaceFlinger 捕获分辨率；越低越省电"
    "liquid_capture_stop_delay" -> "Dock 隐藏后继续允许捕获的缓冲时间"
    "liquid_recents_prearm_distance" -> "从底部上滑达到此距离时，提前启动多任务实时捕获"
    "liquid_capture_bleed_top" -> "在 Dock 上方多捕获的折射取样区域"
    "liquid_capture_bleed_bottom" -> "在 Dock 下方多捕获的折射取样区域"
    "stroke_base_r" -> "描边基础颜色的红色通道"
    "stroke_base_g" -> "描边基础颜色的绿色通道"
    "stroke_base_b" -> "描边基础颜色的蓝色通道"
    "stroke_base_alpha" -> "描边基础颜色的不透明度"
    "sq_stroke_w" -> "方圆形模式下的描边宽度"
    "sq_stroke_off" -> "方圆形描边相对 Dock 边界的内缩量"
    "sq_outer_cp" -> "控制方圆曲线从直边过渡到圆角的形状"
    "stroke_w" -> "Fill-Diff 外层与挖空层之间的宽度"
    "std_stroke_w" -> "普通路径描边模式使用的线宽"
    "dock_shadow_radius" -> "整个 Dock 阴影边缘的柔和程度"
    "dock_shadow_size" -> "整个 Dock 阴影向外扩散的最大距离"
    "dock_shadow_alpha" -> "整个 Dock 下方阴影的浓度"
    "dock_shadow_y" -> "整个 Dock 阴影的垂直偏移，可为负数"
    "shadow_radius" -> "仅描边阴影的柔化半径"
    "shadow_alpha" -> "仅描边阴影的不透明度"
    else -> "调整此功能的数值"
}

private val gridSpecs = listOf(
    IntSpec("grid_landscape_horizontal_distance", "横屏水平距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_top_distance", "横屏顶部距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_bottom_distance", "横屏底部距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_horizontal_distance", "竖屏水平距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_top_distance", "竖屏顶部距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_portrait_bottom_distance", "竖屏底部距离偏移", 0, -600, 600, "dp"),
    IntSpec("grid_landscape_row_gap", "横屏图标纵向间距偏移", 0, -200, 400, "dp"),
    IntSpec("grid_portrait_row_gap", "竖屏图标纵向间距偏移", 0, -200, 400, "dp"),
    IntSpec("indicator_landscape_y", "横屏指示器 Y", 0, -160, 160, "dp"),
    IntSpec("indicator_portrait_y", "竖屏指示器 Y", 0, -160, 160, "dp"),
)
private val dockSpecs = listOf(
    IntSpec("blur_radius", "模糊强度", 100, 0, 400, ""),
    IntSpec("height_offset", "高度偏移", 0, -80, 80, "dp"),
    IntSpec("width_offset", "宽度偏移", 0, -80, 80, "dp"),
    IntSpec("corner_offset", "描边圆角偏移", -1, -50, 100, "dp"),
    IntSpec("blur_corner_offset", "内部模糊圆角偏移", 0, -50, 100, "dp"),
    IntSpec("dock_spacing", "Dock 图标间距", 0, -8, 12, "dp"),
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
    IntSpec("workstation_dock_width_offset", "工作台 Dock 长度偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_grid_horizontal_offset", "工作台桌面水平偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_all_apps_landscape_horizontal_offset", "所有应用 · 横屏水平偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_all_apps_landscape_vertical_offset", "所有应用 · 横屏垂直偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_all_apps_portrait_horizontal_offset", "所有应用 · 竖屏水平偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_all_apps_portrait_vertical_offset", "所有应用 · 竖屏垂直偏移", 0, -240, 240, "dp"),
    IntSpec("workstation_dock_icon_top_offset", "工作台图标上间距", 0, -48, 48, "dp"),
    IntSpec("workstation_dock_icon_bottom_offset", "工作台图标下间距", 0, -48, 48, "dp"),
)
private val liquidSpecs = listOf(
    IntSpec("liquid_blur", "玻璃模糊", 6, 0, 60, "dp"),
    IntSpec("liquid_native_blur_inset", "原生模糊内缩", 1, 0, 16, "dp"),
    IntSpec("liquid_thickness", "玻璃厚度", 18, 1, 60, "dp"),
    IntSpec("liquid_ior", "折射率 IOR", 155, 100, 200, "%"),
    IntSpec("liquid_normal_strength", "法线强度", 115, 0, 300, "%"),
    IntSpec("liquid_dome", "穹顶凸起", 100, 0, 200, "%"),
    IntSpec("liquid_lens_refraction", "透镜折射", 12, 0, 60, "dp"),
    IntSpec("liquid_chromatic", "色散强度", 8, 0, 40, "%"),
    IntSpec("liquid_tint_alpha", "玻璃底色透明度", 38, 0, 160, ""),
    IntSpec("liquid_tint_r", "底色 · 红", 238, 0, 255, ""),
    IntSpec("liquid_tint_g", "底色 · 绿", 244, 0, 255, ""),
    IntSpec("liquid_tint_b", "底色 · 蓝", 255, 0, 255, ""),
    IntSpec("liquid_highlight_width", "边缘高光厚度", 100, 20, 300, "%"),
    IntSpec("liquid_highlight_alpha", "高光不透明度", 100, 0, 200, "%"),
    IntSpec("liquid_depth_effect", "深度透镜效果", 8, 0, 50, "%"),
    IntSpec("liquid_brightness", "亮度", 108, 50, 200, "%"),
    IntSpec("liquid_specular_sharp", "高光锐度", 88, 1, 200, ""),
    IntSpec("liquid_specular_strength", "高光强度", 105, 0, 300, "%"),
    IntSpec("liquid_rim_light", "边缘光强度", 100, 0, 300, "%"),
    IntSpec("liquid_caustics", "焦散强度", 28, 0, 100, "%"),
    IntSpec("liquid_edge_band", "边缘光带宽度", 32, 5, 100, "‰"),
    IntSpec("liquid_capture_power_limit_fps", "实时捕获帧率上限", 20, 5, 60, "FPS"),
    IntSpec("liquid_dynamic_app_probe_fps", "静态画面探测帧率", 3, 1, 10, "FPS", "liquid_dynamic_app_capture"),
    IntSpec("liquid_dynamic_motion_threshold", "动态亮度变化阈值", 12, 1, 240, "", "liquid_dynamic_app_capture"),
    IntSpec("liquid_dynamic_bit_threshold", "动态像素位变化阈值", 18, 1, 64, "", "liquid_dynamic_app_capture"),
    IntSpec("liquid_dynamic_hold_ms", "高频捕获保持时间", 900, 0, 5000, "ms", "liquid_dynamic_app_capture"),
    IntSpec("liquid_black_threshold", "黑帧亮度阈值", 10, 0, 64, ""),
    IntSpec("liquid_home_settle_delay", "主页壁纸捕获延迟", 1200, 200, 3000, "ms"),
    IntSpec("liquid_capture_scale", "捕获分辨率", 50, 10, 100, "%"),
    IntSpec("liquid_capture_stop_delay", "捕获停止延迟", 150, 0, 10000, "ms"),
    IntSpec("liquid_recents_prearm_distance", "多任务捕获预触发距离", 8, 1, 48, "dp"),
    IntSpec("liquid_capture_bleed_top", "上额外捕获高度", 17, 0, 256, "dp"),
    IntSpec("liquid_capture_bleed_bottom", "下额外捕获高度", 6, 0, 256, "dp"),
)
private val strokeSpecs = listOf(
    IntSpec("stroke_base_r", "描边底色 · 红", 255, 0, 255, "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec("stroke_base_g", "描边底色 · 绿", 255, 0, 255, "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec("stroke_base_b", "描边底色 · 蓝", 255, 0, 255, "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec("stroke_base_alpha", "描边底色 · 透明度", 255, 0, 255, "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec("sq_stroke_w", "方圆形描边宽度", 1, 1, 10, "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec("sq_stroke_off", "方圆形描边内缩", 3, 0, 16, "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec("sq_outer_cp", "方圆曲线控制点", 58, 40, 80, "", "squircle", IntSection.StrokeGeometry),
    IntSpec("stroke_w", "Fill-Diff 宽度", 1, 1, 6, "dp", "fill_diff", IntSection.StrokeGeometry),
    IntSpec("std_stroke_w", "标准描边宽度", 1, 1, 10, "dp", null, IntSection.StrokeGeometry),
)
private val shadowSpecs = listOf(
    IntSpec("dock_shadow_radius", "Dock 阴影柔化", 15, 1, 40, "dp", "dock_shadow"),
    IntSpec("dock_shadow_size", "Dock 阴影扩散大小", 18, 1, 60, "dp", "dock_shadow"),
    IntSpec("dock_shadow_alpha", "Dock 阴影透明度", 140, 0, 200, "", "dock_shadow"),
    IntSpec("dock_shadow_y", "Dock 阴影 Y 偏移", 4, -24, 24, "dp", "dock_shadow"),
    IntSpec("shadow_radius", "描边阴影半径", 3, 1, 24, "dp", "stroke_shadow"),
    IntSpec("shadow_alpha", "描边阴影透明度", 70, 0, 200, "", "stroke_shadow"),
)

@Composable
private fun LiquidDockSettings(activity: ComposeSettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("liquiddock_enabled", true)) }
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    BackHandler(enabled = page != Page.Home) { page = Page.Home }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(page.titleRes),
                navigationIcon = {
                    if (page != Page.Home) TextButton(text = stringResource(R.string.action_back), onClick = { page = Page.Home })
                },
                actions = {
                    TextButton(text = stringResource(R.string.action_restart_launcher), onClick = { activity.restartLauncher() })
                },
            )
        },
    ) { padding ->
        // MIUI-style horizontal slide between pages: entering a submenu slides in from
        // the right (old page slides out left); going back reverses.  Uses AnimatedContent
        // so the page transition animates instead of snapping.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    // Forward (into a submenu): new page slides in from the right.
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    // Back: current page slides out to the right, home peeks in from left.
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "page",
        ) { target ->
            when (target) {
                Page.Home -> HomePage(padding, prefs, masterEnabled,
                    { masterEnabled = it }) { page = it }
                Page.Grid -> GridPage(padding, prefs, masterEnabled)
                Page.Dock -> DockPage(padding, prefs, masterEnabled)
                Page.Workstation -> WorkstationPage(padding, prefs, masterEnabled)
                Page.Liquid -> LiquidPage(padding, prefs, masterEnabled)
                Page.Stroke -> StrokePage(padding, prefs, masterEnabled)
                Page.Shadow -> ShadowPage(padding, prefs, masterEnabled)
                Page.Data -> DataPage(padding, activity)
                Page.About -> AboutPage(padding, activity, prefs)
            }
        }
    }
}

@Composable
private fun HomePage(
    padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean,
    onMasterChanged: (Boolean) -> Unit, open: (Page) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader(stringResource(R.string.app_name)) }
        item { SmallTitle(stringResource(R.string.category_master)) }
        item { SettingsCard { BooleanSetting(prefs, "liquiddock_enabled", stringResource(R.string.enable_liquiddock), true,
            stringResource(R.string.enable_liquiddock_summary)) { onMasterChanged(it) } } }
        item { SmallTitle(stringResource(R.string.category_customization)) }
        item {
            SettingsCard {
                ArrowPreference(stringResource(R.string.page_grid), summary = stringResource(R.string.home_grid_summary), onClick = { open(Page.Grid) })
                ArrowPreference(stringResource(R.string.page_dock), summary = stringResource(R.string.home_dock_summary), onClick = { open(Page.Dock) })
                ArrowPreference(stringResource(R.string.page_workstation), summary = stringResource(R.string.home_workstation_summary), onClick = { open(Page.Workstation) })
                ArrowPreference(stringResource(R.string.page_liquid), summary = stringResource(R.string.home_liquid_summary), onClick = { open(Page.Liquid) })
                ArrowPreference(stringResource(R.string.page_stroke), summary = stringResource(R.string.home_stroke_summary), onClick = { open(Page.Stroke) })
                ArrowPreference(stringResource(R.string.page_shadow), summary = stringResource(R.string.home_shadow_summary), onClick = { open(Page.Shadow) })
            }
        }
        item { SmallTitle(stringResource(R.string.category_configuration)) }
        item { SettingsCard { ArrowPreference(stringResource(R.string.home_data_title), summary = stringResource(R.string.home_data_summary), onClick = { open(Page.Data) }) } }
        item { SmallTitle(stringResource(R.string.category_about)) }
        item { SettingsCard { ArrowPreference(stringResource(R.string.home_about_title), summary = stringResource(R.string.home_about_summary), onClick = { open(Page.About) }) } }
    }
}

@Composable
private fun GridPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var grid8x4 by remember { mutableStateOf(prefs.getBoolean("home_grid_8x4", false)) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader(stringResource(R.string.page_grid), stringResource(R.string.grid_header_summary)) }
        item { SmallTitle(stringResource(R.string.category_grid)) }
        item { SettingsCard { BooleanSetting(prefs, "home_grid_8x4", stringResource(R.string.enable_grid_8x4), false,
            stringResource(R.string.enable_grid_8x4_summary), masterEnabled) { grid8x4 = it } } }
        item { SmallTitle(stringResource(R.string.category_landscape)) }
        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y" }
            .forEach { IntSetting(prefs, it, masterEnabled && grid8x4) } } }
        item { SmallTitle(stringResource(R.string.category_portrait)) }
        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y" }
            .forEach { IntSetting(prefs, it, masterEnabled && grid8x4) } } }
    }
}

@Composable
private fun DockPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockEnabled by remember { mutableStateOf(prefs.getBoolean("dock_customization", true)) }
    var resizeAnimation by remember { mutableStateOf(prefs.getBoolean("dock_resize_animation", false)) }
    var smoothResize by remember { mutableStateOf(prefs.getBoolean("dock_smooth_resize_animation", true)) }
    SettingsList(padding, stringResource(R.string.page_dock)) {
        BooleanSetting(prefs, "dock_customization", stringResource(R.string.dock_customization), true,
            stringResource(R.string.dock_customization_summary), masterEnabled) { dockEnabled = it }
        BooleanSetting(prefs, "dock_resize_animation", stringResource(R.string.dock_resize_animation), false,
            stringResource(R.string.dock_resize_animation_summary), masterEnabled && dockEnabled) { resizeAnimation = it }
        BooleanSetting(prefs, "dock_smooth_resize_animation", stringResource(R.string.dock_smooth_resize_animation), true,
            stringResource(R.string.dock_smooth_resize_animation_summary), masterEnabled && dockEnabled && !resizeAnimation) { smoothResize = it }
        dockSpecs.forEach { IntSetting(prefs, it, masterEnabled && dockEnabled) }
    }
}

@Composable
private fun WorkstationPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var enabled by remember { mutableStateOf(prefs.getBoolean("workstation_dock_customization", false)) }
    SettingsList(padding, stringResource(R.string.page_workstation)) {
        BooleanSetting(prefs, "workstation_dock_customization", stringResource(R.string.workstation_customization), false,
            stringResource(R.string.workstation_customization_summary),
            masterEnabled) { enabled = it }
        workstationSpecs.forEach { IntSetting(prefs, it, masterEnabled && enabled) }
    }
}

@Composable
private fun LiquidPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var liquidGlass by remember { mutableStateOf(prefs.getBoolean("liquid_glass", false)) }
    var dynamicAppCapture by remember { mutableStateOf(prefs.getBoolean("liquid_dynamic_app_capture", true)) }
    SettingsList(padding, stringResource(R.string.page_liquid)) {
        BooleanSetting(prefs, "liquid_glass", stringResource(R.string.liquid_enable), false,
            stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        StringDropdown(prefs, "liquid_blur_method", stringResource(R.string.liquid_blur_method), "shader",
            listOf(
                stringResource(R.string.liquid_shader_blur) to "shader",
                stringResource(R.string.liquid_native_blur) to "native",
                stringResource(R.string.liquid_material_blur) to "material",
            ), masterEnabled && liquidGlass)
        BooleanSetting(prefs, "liquid_dynamic_app_capture", stringResource(R.string.liquid_dynamic_capture), true,
            stringResource(R.string.liquid_dynamic_capture_summary), masterEnabled && liquidGlass) { dynamicAppCapture = it }
        liquidSpecs.forEach {
            IntSetting(prefs, it, masterEnabled && liquidGlass && (it.dependency != "liquid_dynamic_app_capture" || dynamicAppCapture))
        }
    }
}

@Composable
private fun StrokePage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockStroke by remember { mutableStateOf(prefs.getBoolean("dock_stroke", true)) }
    var squircle by remember { mutableStateOf(prefs.getBoolean("squircle", false)) }
    var fillDiff by remember { mutableStateOf(prefs.getBoolean("fill_diff", false)) }
    SettingsList(padding, "描边") {
        BooleanSetting(prefs, "dock_stroke", "显示完整描边", true, "控制 Dock 边框与灯光", masterEnabled) { dockStroke = it }
        BooleanSetting(prefs, "squircle", "方圆形连续曲线", false, "iPad 风格连续圆角", masterEnabled) { squircle = it }
        BooleanSetting(prefs, "fill_diff", "Fill-Diff 描边", false, "通过填充与挖空获得清晰抗锯齿", masterEnabled) { fillDiff = it }
        SmallTitle("描边背景色")
        strokeSpecs.filter { it.section == IntSection.StrokeBackground }.forEach {
            IntSetting(prefs, it, masterEnabled && dockStroke)
        }
        SmallTitle("方圆形与线宽")
        strokeSpecs.filter { it.section == IntSection.StrokeGeometry }.forEach {
            val enabled = when (it.dependency) {
                "dock_stroke" -> dockStroke
                "squircle" -> squircle
                "fill_diff" -> fillDiff
                else -> true
            }
            IntSetting(prefs, it, masterEnabled && enabled)
        }
    }
}

@Composable
private fun ShadowPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val dockEnabled = prefs.getBoolean("dock_customization", true)
    var dockShadow by remember { mutableStateOf(prefs.getBoolean("dock_shadow", true)) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean("stroke_shadow", false)) }
    SettingsList(padding, "阴影") {
        BooleanSetting(prefs, "dock_shadow", "整个 Dock 下方阴影", true, "跟随 Dock 长宽、高度和圆角", masterEnabled && dockEnabled) { dockShadow = it }
        BooleanSetting(prefs, "stroke_shadow", "描边阴影", false, "描边下方的柔和阴影", masterEnabled && dockEnabled) { strokeShadow = it }
        shadowSpecs.forEach {
            IntSetting(prefs, it, masterEnabled && dockEnabled && when (it.dependency) {
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
        item { PageHeader("预设与数据", "保存、恢复或迁移 LiquidDock 配置") }
        item { SmallTitle("预设") }
        item { SettingsCard { ArrowPreference("应用默认预设", summary = "恢复当前保存的布局与液态玻璃参数", onClick = { applyDefaultPreset(activity) }) } }
        item { SmallTitle("备份与应用") }
        item {
            SettingsCard {
                ArrowPreference("导出当前参数", summary = "保存为 LiquidDock JSON", onClick = activity::launchExport)
                ArrowPreference("导入参数", summary = "校验、写入并重启桌面", onClick = activity::launchImport)
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun AboutPage(padding: PaddingValues, activity: ComposeSettingsActivity, prefs: SharedPreferences) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader("引用与许可", "LiquidDock 使用的框架与实现参考") }
        item {
            SettingsCard {
                BooleanSetting(prefs, "liquiddock_debug_log", "调试日志",
                    false, "输出诊断日志到 Download/liquiddock.log，重启桌面生效")
            }
        }
        item { SmallTitle("界面与运行框架") }
        item {
            SettingsCard {
                ArrowPreference("Compose Miuix", summary = "MIUIX Compose 界面框架 · Apache-2.0",
                    onClick = { openUrl(activity, "https://github.com/compose-miuix-ui/miuix") })
                ArrowPreference("AndroidX / Jetpack", summary = "Activity、Preference、AppCompat · Apache-2.0",
                    onClick = { openUrl(activity, "https://source.android.com/docs/setup/about/licenses") })
                ArrowPreference("LSPosed API", summary = "模块 Hook API · GPL-3.0",
                    onClick = { openUrl(activity, "https://github.com/LSPosed/LSPosed") })
            }
        }
        item { SmallTitle("实现参考") }
        item {
            SettingsCard {
                ArrowPreference("HyperCeiler", summary = "设置分层、交互方式与模块工程实践参考 · GPL-3.0",
                    onClick = { openUrl(activity, "https://github.com/ReChronoRain/HyperCeiler") })
                ArrowPreference("Prismal", summary = "液态玻璃光学模型与 Shader 参数设计参考 · MIT",
                    onClick = { openUrl(activity, "https://github.com/styropyr0/Prismal") })
                ArrowPreference("HyperLight", summary = "降采样与屏幕捕获思路启发",
                    onClick = {})
            }
        }
        item { SmallTitle("许可说明") }
        item {
            SettingsCard {
                ArrowPreference("第三方开源声明", summary = "完整依赖版本、用途与许可证文本链接",
                    onClick = { openUrl(activity, "https://github.com/yu4032/LiquidDock/blob/main/THIRD_PARTY_NOTICES.md") })
            }
        }
    }
}

@Composable
private fun SettingsList(padding: PaddingValues, title: String, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader(title) }
        item { SettingsCard(content) }
    }
}

@Composable
private fun PageHeader(title: String, summary: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (!summary.isNullOrBlank()) {
            Text(summary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        }
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
    val decimalDp = spec.unit == "dp"
    val initial = if (decimalDp && prefs.contains("${spec.key}_tenths"))
        prefs.getInt("${spec.key}_tenths", spec.default * 10) / 10f
    else prefs.getInt(spec.key, spec.default).toFloat()
    var value by remember(spec.key) { mutableStateOf(initial) }
    val enabled = enabledOverride ?: spec.dependency?.let { prefs.getBoolean(it, false) } ?: true
    val context = LocalContext.current
    fun save(nextValue: Float) {
        val next = if (decimalDp) (nextValue * 10f).roundToInt() / 10f else nextValue.roundToInt().toFloat()
        value = next.coerceIn(spec.min.toFloat(), spec.max.toFloat())
        val editor = prefs.edit().putInt(spec.key, value.roundToInt())
        if (decimalDp) editor.putInt("${spec.key}_tenths", (value * 10f).roundToInt())
        editor.apply()
    }
    val displayValue = if (decimalDp) String.format(java.util.Locale.ROOT, "%.1f", value)
        else value.roundToInt().toString()
    SliderPreference(
        value = value.toFloat(),
        onValueChange = { save(it) },
        title = spec.title,
        summary = spec.summary,
        valueText = "",
        enabled = enabled,
        valueRange = spec.min.toFloat()..spec.max.toFloat(),
        steps = if (decimalDp) ((spec.max - spec.min) * 10 - 1).coerceAtLeast(0)
            else (spec.max - spec.min - 1).coerceAtLeast(0),
        endActions = {
            Button(
                onClick = {
                    val input = EditText(context).apply {
                        setText(displayValue)
                        selectAll()
                        inputType = InputType.TYPE_CLASS_NUMBER or
                                InputType.TYPE_NUMBER_FLAG_SIGNED or
                                if (decimalDp) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                    }
                    android.app.AlertDialog.Builder(context)
                        .setTitle(spec.title)
                        .setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定") { _, _ ->
                            input.text.toString().toFloatOrNull()?.let(::save)
                        }.show()
                },
                enabled = enabled,
                minWidth = 72.dp,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("$displayValue${if (spec.unit.isBlank()) "" else " ${spec.unit}"}") }
            Button(
                onClick = { save(spec.default.toFloat()) },
                enabled = enabled && value != spec.default.toFloat(),
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

private fun applyDefaultPreset(activity: ComposeSettingsActivity) {
    val editor = PreferenceManager.getDefaultSharedPreferences(activity).edit()
    fun dp(key: String, value: Float) {
        editor.putInt(key, value.roundToInt()).putInt("${key}_tenths", (value * 10f).roundToInt())
    }
    editor
        .putBoolean("liquiddock_enabled", true)
        .putBoolean("home_grid_8x4", false)
        .putBoolean("grid_margins_dp", true).putBoolean("grid_margins_offset", true)
        .putInt("grid_landscape_margin_left", 0).putInt("grid_landscape_margin_right", 0)
        .putInt("grid_landscape_margin_top", 0).putInt("grid_landscape_margin_bottom", 0)
        .putInt("grid_portrait_margin_left", 0).putInt("grid_portrait_margin_right", 0)
        .putInt("grid_portrait_margin_top", 0).putInt("grid_portrait_margin_bottom", 0)
        .putInt("grid_landscape_row_gap", 0).putInt("grid_portrait_row_gap", 0)
        .putBoolean("dock_customization", true).putBoolean("dock_dimensions_dp", true)
        .putBoolean("dock_resize_animation", false)
        .putBoolean("dock_smooth_resize_animation", true)
        .putBoolean("workstation_dock_customization", false)
        .putInt("blur_radius", 100)
        .putBoolean("liquid_glass", true).putBoolean("liquid_dimensions_dp", true)
        .putString("liquid_blur_method", "native")
        .putInt("liquid_ior", 170).putInt("liquid_normal_strength", 115)
        .putInt("liquid_dome", 100).putInt("liquid_chromatic", 8)
        .putInt("liquid_tint_alpha", 64).putInt("liquid_tint_r", 119)
        .putInt("liquid_tint_g", 122).putInt("liquid_tint_b", 122)
        .putInt("liquid_highlight_width", 100).putInt("liquid_highlight_alpha", 100)
        .putInt("liquid_depth_effect", 8).putInt("liquid_brightness", 108)
        .putInt("liquid_specular_sharp", 88).putInt("liquid_specular_strength", 105)
        .putInt("liquid_rim_light", 100).putInt("liquid_caustics", 28)
        .putInt("liquid_edge_band", 32).putInt("liquid_capture_power_limit_fps", 30)
        .putBoolean("liquid_dynamic_app_capture", true)
        .putInt("liquid_dynamic_app_probe_fps", 3).putInt("liquid_dynamic_motion_threshold", 12)
        .putInt("liquid_dynamic_bit_threshold", 18).putInt("liquid_dynamic_hold_ms", 900)
        .putInt("liquid_black_threshold", 10).putInt("liquid_capture_scale", 100)
        .putInt("liquid_capture_stop_delay", 150)
        .putBoolean("corners_dp", true).putBoolean("dock_stroke", true)
        .putInt("stroke_base_r", 180).putInt("stroke_base_g", 180)
        .putInt("stroke_base_b", 180).putInt("stroke_base_alpha", 119)
        .putBoolean("squircle", true).putInt("sq_outer_cp", 65)
        .putBoolean("fill_diff", true)
        .putBoolean("dock_shadow", true)
        .putInt("dock_shadow_alpha", 64)
        .putBoolean("stroke_shadow", false).putInt("shadow_alpha", 70)
    dp("grid_landscape_margin_left", 30f); dp("grid_landscape_margin_right", 30f)
    dp("grid_landscape_margin_top", -35f); dp("grid_landscape_margin_bottom", 20f)
    dp("grid_portrait_margin_left", 0f); dp("grid_portrait_margin_right", 0f)
    dp("grid_portrait_margin_top", 0f); dp("grid_portrait_margin_bottom", 100f)
    dp("grid_landscape_horizontal_distance", 30f)
    dp("grid_landscape_top_distance", 0f); dp("grid_landscape_bottom_distance", 20f)
    dp("grid_portrait_horizontal_distance", 0f)
    dp("grid_portrait_top_distance", 10.3f); dp("grid_portrait_bottom_distance", 0f)
    dp("grid_landscape_row_gap", 0f); dp("grid_portrait_row_gap", -16f)
    dp("indicator_landscape_y", -8.8f); dp("indicator_portrait_y", 11.8f)
    dp("liquid_blur", 0f); dp("liquid_native_blur_inset", 0f)
    dp("liquid_thickness", 18f)
    dp("liquid_lens_refraction", 12f); dp("liquid_capture_bleed_top", 17f)
    dp("liquid_capture_bleed_bottom", 16f)
    dp("liquid_recents_prearm_distance", 8f)
    dp("height_offset", 2.2f); dp("width_offset", 0f)
    dp("corner_offset", 1f); dp("blur_corner_offset", -1f)
    dp("sq_stroke_w", 1f); dp("sq_stroke_off", 0f)
    dp("stroke_w", 1f); dp("std_stroke_w", 1f)
    dp("dock_shadow_radius", 10f); dp("dock_shadow_size", 4.7f); dp("dock_shadow_y", 0f)
    dp("shadow_radius", 3f); dp("dock_spacing", 0f); dp("dock_bottom_offset", -2f)
    dp("workstation_dock_width_offset", 0f)
    dp("workstation_grid_horizontal_offset", 0f)
    dp("workstation_dock_icon_top_offset", 0f); dp("workstation_dock_icon_bottom_offset", 0f)
    editor.commit()
    Toast.makeText(activity, "默认预设已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
}
