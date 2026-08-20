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
import com.hellovoid.liquiddock.config.ConfigKey
import com.hellovoid.liquiddock.config.ConfigSchema
import com.hellovoid.liquiddock.config.PresetManager
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
    Divider(R.string.page_divider), Workstation(R.string.page_workstation), Liquid(R.string.page_liquid),
    Stroke(R.string.page_stroke), Shadow(R.string.page_shadow), Data(R.string.page_data),
    About(R.string.page_about)
}

// Ordinary UI writes are mirrored to API101 Remote Preferences by LiquidDockApp's
// SharedPreferences listener. No per-control JSON/file/root synchronization exists.

private enum class IntSection { General, StrokeBackground, StrokeGeometry }

private data class IntSpec(
    val config: ConfigKey<Int>,
    val title: String,
    val unit: String = "dp",
    val dependency: String? = null,
    val section: IntSection = IntSection.General,
    val summary: String = optionSummary(config.name()),
) {
    val key: String get() = config.name()
    val default: Int get() = config.uiDefault()
    val min: Int get() = requireNotNull(config.minInt())
    val max: Int get() = requireNotNull(config.maxInt())
}

private fun optionSummary(key: String): String = when (key) {
    "grid_landscape_horizontal_distance" -> "Edge Offset 只调整左右边缘，不改变纵向位置"
    "grid_landscape_top_distance" -> "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算"
    "grid_landscape_bottom_distance" -> "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算"
    "grid_portrait_horizontal_distance" -> "Edge Offset 只调整左右边缘，不改变纵向位置"
    "grid_portrait_top_distance" -> "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算"
    "grid_portrait_bottom_distance" -> "兼容旧配置；当前普通桌面纵向区域由状态栏与 Dock 自动计算"
    "grid_landscape_row_gap" -> "Margin 是图标单元实际间距；0 表示自动使用屏幕宽度的 0.9%"
    "grid_portrait_row_gap" -> "Margin 是图标单元实际间距；0 表示自动使用屏幕宽度的 0.9%"
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
    "dock_divider_color_r" -> "分隔竖线颜色 · 红"
    "dock_divider_color_g" -> "分隔竖线颜色 · 绿"
    "dock_divider_color_b" -> "分隔竖线颜色 · 蓝"
    "dock_divider_alpha" -> "分隔竖线不透明度"
    "workstation_dock_width_offset" -> "相对系统工作台 Dock 的原始长度增减；不会改变位置或普通 Dock"
    "workstation_grid_horizontal_offset" -> "单独调整工作台 8 列图标区域的左右距离，不继承普通桌面偏移"
    "workstation_all_apps_landscape_horizontal_offset" -> "直接设置工作台所有应用横屏图标区左右间距；不叠加系统默认位置"
    "workstation_all_apps_landscape_top_spacing" -> "直接设置工作台所有应用横屏图标区上间距；不叠加系统默认位置"
    "workstation_all_apps_landscape_bottom_spacing" -> "直接设置工作台所有应用横屏图标区下间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_horizontal_offset" -> "直接设置工作台所有应用竖屏图标区左右间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_top_spacing" -> "直接设置工作台所有应用竖屏图标区上间距；不叠加系统默认位置"
    "workstation_all_apps_portrait_bottom_spacing" -> "直接设置工作台所有应用竖屏图标区下间距；不叠加系统默认位置"
    "workstation_dock_icon_top_offset" -> "调整工作台 Dock 图标与容器顶部之间的距离"
    "workstation_dock_icon_bottom_offset" -> "调整工作台 Dock 图标与容器底部之间的距离"
    "liquid_blur" -> "液态玻璃对实时 OES 背景的模糊范围"
    "liquid_thickness" -> "影响 Snell 折射路径的虚拟玻璃厚度"
    "liquid_ior" -> "折射率；越高，边缘弯曲越明显"
    "liquid_normal_strength" -> "Prismal 表面高度场对法线的影响"
    "liquid_dome" -> "控制玻璃穹顶/meniscus 的凸起程度"
    "liquid_lens_refraction" -> "兼容旧配置：作为 Prismal 边缘透镜距离倍率"
    "liquid_chromatic" -> "Prismal 色散总强度"
    "liquid_tint_alpha" -> "玻璃颜色乘色强度"
    "liquid_tint_r" -> "玻璃颜色 · 红"
    "liquid_tint_g" -> "玻璃颜色 · 绿"
    "liquid_tint_b" -> "玻璃颜色 · 蓝"
    "liquid_highlight_width" -> "兼容控制 Prismal 边缘高光带宽"
    "liquid_highlight_alpha" -> "LiquidDock 对 Prismal 高光总强度的兼容倍率"
    "liquid_depth_effect" -> "兼容控制 Prismal lens-depth 倍率"
    "liquid_brightness" -> "Prismal 整体输出亮度"
    "liquid_specular_sharp" -> "Prismal 镜面高光锐度"
    "liquid_specular_strength" -> "Prismal 双镜面高光强度"
    "liquid_rim_light" -> "Prismal 边缘光强度"
    "liquid_caustics" -> "Prismal 焦散强度"
    "liquid_edge_band" -> "兼容调整 Prismal rim band 宽度"
    "liquid_prismal_refraction_inset" -> "Prismal 折射遮罩向内收缩的距离"
    "liquid_prismal_displacement_scale" -> "Prismal 折射与视差位移总倍率"
    "liquid_prismal_height_transition_width" -> "Prismal 高度场从边缘过渡到中心的宽度"
    "liquid_prismal_smin_smoothing" -> "Prismal 圆角 SDF 的多项式平滑尺度"
    "liquid_prismal_edge_refraction_falloff" -> "边缘折射向内部衰减的曲线强度"
    "liquid_prismal_fresnel_reflect" -> "Fresnel 背景反射与 sky haze 强度"
    "liquid_prismal_dispersion_r" -> "红色通道相对色散倍率"
    "liquid_prismal_dispersion_b" -> "蓝色通道相对色散倍率"
    "liquid_prismal_vibrancy" -> "Prismal 折射背景的色彩鲜艳度"
    "liquid_prismal_plain_highlight" -> "Prismal 基础边缘高光"
    "liquid_prismal_light_dir_x" -> "Prismal 光源水平方向"
    "liquid_prismal_light_dir_y" -> "Prismal 光源垂直方向"
    "liquid_prismal_shadow_r" -> "Prismal 内阴影 · 红"
    "liquid_prismal_shadow_g" -> "Prismal 内阴影 · 绿"
    "liquid_prismal_shadow_b" -> "Prismal 内阴影 · 蓝"
    "liquid_prismal_shadow_alpha" -> "Prismal 内阴影透明度"
    "liquid_prismal_shadow_softness" -> "Prismal 内阴影扩散柔和度"
    "liquid_prismal_transmittance" -> "Prismal 玻璃最终透射/Alpha"
    "liquid_prismal_backdrop_scale_x" -> "Prismal 背景取样水平缩放"
    "liquid_prismal_backdrop_scale_y" -> "Prismal 背景取样垂直缩放"
    "liquid_prismal_parallax_scale" -> "Prismal 表面视差倍率"
    "liquid_capture_power_limit_fps" -> "动画和动态应用实时捕获的统一帧率上限"
    "liquid_dynamic_app_probe_fps" -> "静态应用中检测画面变化的低频帧率"
    "liquid_dynamic_motion_threshold" -> "越低越容易因亮度变化进入高频捕获"
    "liquid_dynamic_bit_threshold" -> "越低越容易因采样像素变化触发高频捕获"
    "liquid_dynamic_hold_ms" -> "检测到动态后维持高频捕获的时长"
    "liquid_black_threshold" -> "低于此平均亮度的捕获帧会被丢弃"
    "liquid_capture_scale" -> "SurfaceFlinger 捕获分辨率；越低越省电"
    "liquid_capture_stop_delay" -> "Dock 隐藏后继续允许捕获的缓冲时间"
    "liquid_recents_prearm_distance" -> "从底部上滑达到此距离时，提前启动多任务实时捕获"
    "liquid_capture_bleed_top" -> "在 Dock 上方扩展 zero-copy 折射背景的采样区域"
    "liquid_capture_bleed_bottom" -> "在 Dock 下方扩展 zero-copy 折射背景的采样区域"
    "liquid_capture_bleed_left" -> "在默认 32dp 左侧采样之外继续扩展的 zero-copy 区域"
    "liquid_capture_bleed_right" -> "在默认 32dp 右侧采样之外继续扩展的 zero-copy 区域"
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
    IntSpec(ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE, "横屏 Edge Offset"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_ROW_GAP, "横屏 Margin"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE, "竖屏 Edge Offset"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_ROW_GAP, "竖屏 Margin"),
    IntSpec(ConfigSchema.Grid.LANDSCAPE_INDICATOR_Y, "横屏指示器 Y"),
    IntSpec(ConfigSchema.Grid.PORTRAIT_INDICATOR_Y, "竖屏指示器 Y"),
)
private val dockSpecs = listOf(
    IntSpec(ConfigSchema.Dock.BLUR_RADIUS, "模糊强度", ""),
    IntSpec(ConfigSchema.Dock.HEIGHT_OFFSET, "高度偏移"),
    IntSpec(ConfigSchema.Dock.WIDTH_OFFSET, "宽度偏移"),
    IntSpec(ConfigSchema.Dock.CORNER_OFFSET, "描边圆角偏移"),
    IntSpec(ConfigSchema.Dock.BLUR_CORNER_OFFSET, "内部模糊圆角偏移"),
    IntSpec(ConfigSchema.Dock.SPACING, "Dock 图标间距"),
    IntSpec(ConfigSchema.Dock.BOTTOM_OFFSET, "Dock 底部偏移"),
)
private val dividerSpecs = listOf(
    IntSpec(ConfigSchema.Divider.WIDTH_DP, "分隔线宽度", "dp×10"),
    IntSpec(ConfigSchema.Divider.HEIGHT_SCALE, "分隔线高度比例", "%"),
    IntSpec(ConfigSchema.Divider.Y_OFFSET_DP, "分隔线垂直偏移", "dp×10"),
    IntSpec(ConfigSchema.Divider.COLOR_RED, "分隔线颜色 · 红", ""),
    IntSpec(ConfigSchema.Divider.COLOR_GREEN, "分隔线颜色 · 绿", ""),
    IntSpec(ConfigSchema.Divider.COLOR_BLUE, "分隔线颜色 · 蓝", ""),
    IntSpec(ConfigSchema.Divider.ALPHA, "分隔线透明度", ""),
)
private val dividerKeys = dividerSpecs.map { it.key }
private fun hasLegacyDividerConfig(prefs: SharedPreferences): Boolean = dividerKeys.any(prefs::contains)
private fun ensureDividerDefaults(prefs: SharedPreferences) {
    val e = prefs.edit()
    dividerSpecs.forEach { if (!prefs.contains(it.key)) e.putInt(it.key, it.default) }
    e.apply()
}
private val workstationSpecs = listOf(
    IntSpec(ConfigSchema.Workstation.DOCK_WIDTH_OFFSET, "工作台 Dock 长度偏移"),
    IntSpec(ConfigSchema.Workstation.GRID_HORIZONTAL_OFFSET, "工作台桌面水平偏移"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_HORIZONTAL_OFFSET, "所有应用 · 横屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_TOP_SPACING, "所有应用 · 横屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_LANDSCAPE_BOTTOM_SPACING, "所有应用 · 横屏下间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_HORIZONTAL_OFFSET, "所有应用 · 竖屏水平间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_TOP_SPACING, "所有应用 · 竖屏上间距"),
    IntSpec(ConfigSchema.Workstation.ALL_APPS_PORTRAIT_BOTTOM_SPACING, "所有应用 · 竖屏下间距"),
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET, "工作台 Dock 图标上间距"),
    IntSpec(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET, "工作台 Dock 图标下间距"),
)
private val liquidSpecs = listOf(
    IntSpec(ConfigSchema.Glass.BLUR, "玻璃模糊"),
    IntSpec(ConfigSchema.Glass.THICKNESS, "玻璃厚度"),
    IntSpec(ConfigSchema.Glass.IOR, "折射率 IOR", "%"),
    IntSpec(ConfigSchema.Glass.NORMAL_STRENGTH, "法线强度", "%"),
    IntSpec(ConfigSchema.Glass.DOME, "穹顶凸起", "%"),
    IntSpec(ConfigSchema.Glass.LENS_REFRACTION, "透镜折射"),
    IntSpec(ConfigSchema.Glass.CHROMATIC, "色散强度", ""),
    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_TOP, "上额外采样高度", "px"),
    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM, "下额外采样高度", "px"),
    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_LEFT, "左额外采样宽度", "px"),
    IntSpec(ConfigSchema.Glass.CAPTURE_BLEED_RIGHT, "右额外采样宽度", "px"),
    IntSpec(ConfigSchema.Glass.TINT_ALPHA, "玻璃底色透明度", ""),
    IntSpec(ConfigSchema.Glass.TINT_RED, "底色 · 红", ""),
    IntSpec(ConfigSchema.Glass.TINT_GREEN, "底色 · 绿", ""),
    IntSpec(ConfigSchema.Glass.TINT_BLUE, "底色 · 蓝", ""),
    IntSpec(ConfigSchema.Glass.HIGHLIGHT_WIDTH, "边缘高光厚度", "%"),
    IntSpec(ConfigSchema.Glass.BRIGHTNESS, "亮度", "%"),
    IntSpec(ConfigSchema.Glass.SPECULAR_SHARPNESS, "高光锐度", ""),
    IntSpec(ConfigSchema.Glass.SPECULAR_STRENGTH, "高光强度", "%"),
    IntSpec(ConfigSchema.Glass.RIM_LIGHT, "边缘光强度", "%"),
    IntSpec(ConfigSchema.Glass.CAUSTICS, "焦散强度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_REFRACTION_INSET, "Prismal · 折射内缩"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPLACEMENT_SCALE, "Prismal · 位移倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_HEIGHT_TRANSITION_WIDTH, "Prismal · 高度过渡"),
    IntSpec(ConfigSchema.Glass.PRISMAL_SMIN_SMOOTHING, "Prismal · SDF 平滑"),
    IntSpec(ConfigSchema.Glass.PRISMAL_EDGE_REFRACTION_FALLOFF, "Prismal · 边缘折射衰减", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_FRESNEL_REFLECT, "Prismal · Fresnel 反射", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_R, "Prismal · 红色散倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_DISPERSION_B, "Prismal · 蓝色散倍率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_VIBRANCY, "Prismal · 鲜艳度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_PLAIN_HIGHLIGHT, "Prismal · 基础高光", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_X, "Prismal · 光源 X", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_LIGHT_DIR_Y, "Prismal · 光源 Y", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_RED, "Prismal · 内阴影红", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_GREEN, "Prismal · 内阴影绿", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_BLUE, "Prismal · 内阴影蓝", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_ALPHA, "Prismal · 内阴影透明度", ""),
    IntSpec(ConfigSchema.Glass.PRISMAL_SHADOW_SOFTNESS, "Prismal · 内阴影柔和度", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_TRANSMITTANCE, "Prismal · 透射率", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_X, "Prismal · 背景缩放 X", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_BACKDROP_SCALE_Y, "Prismal · 背景缩放 Y", "%"),
    IntSpec(ConfigSchema.Glass.PRISMAL_PARALLAX_SCALE, "Prismal · 视差倍率", "%"),
)
private val strokeSpecs = listOf(
    IntSpec(ConfigSchema.Dock.STROKE_RED, "描边底色 · 红", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_GREEN, "描边底色 · 绿", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_BLUE, "描边底色 · 蓝", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.STROKE_ALPHA, "描边底色 · 透明度", "", "dock_stroke", IntSection.StrokeBackground),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH, "方圆形描边宽度", "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_STROKE_OFFSET, "方圆形描边内缩", "dp", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.SQUIRCLE_CONTROL_POINT, "方圆曲线控制点", "", "squircle", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH, "Fill-Diff 宽度", "dp", "fill_diff", IntSection.StrokeGeometry),
    IntSpec(ConfigSchema.Dock.STANDARD_STROKE_WIDTH, "标准描边宽度", "dp", null, IntSection.StrokeGeometry),
)
private val shadowSpecs = listOf(
    IntSpec(ConfigSchema.Dock.SHADOW_RADIUS, "Dock 阴影柔化", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_SIZE, "Dock 阴影扩散大小", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_ALPHA, "Dock 阴影透明度", "", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.SHADOW_Y, "Dock 阴影 Y 偏移", "dp", "dock_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_RADIUS, "描边阴影半径", "dp", "stroke_shadow"),
    IntSpec(ConfigSchema.Dock.STROKE_SHADOW_ALPHA, "描边阴影透明度", "", "stroke_shadow"),
)

@Composable
private fun LiquidDockSettings(activity: ComposeSettingsActivity) {
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
    var masterEnabled by remember {
        mutableStateOf(prefs.getBoolean(ConfigSchema.Core.ENABLED.name(), ConfigSchema.Core.ENABLED.uiDefault()))
    }
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
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                            (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "page",
        ) { target ->
            when (target) {
                Page.Home -> HomePage(padding, prefs, masterEnabled, { masterEnabled = it }) { page = it }
                Page.Grid -> GridPage(padding, prefs, masterEnabled)
                Page.Dock -> DockPage(padding, prefs, masterEnabled)
                Page.Divider -> DividerPage(padding, prefs, masterEnabled)
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
        item { SettingsCard { BooleanSetting(prefs, ConfigSchema.Core.ENABLED, stringResource(R.string.enable_liquiddock), stringResource(R.string.enable_liquiddock_summary)) { onMasterChanged(it) } } }
        item { SmallTitle(stringResource(R.string.category_customization)) }
        item {
            SettingsCard {
                ArrowPreference(stringResource(R.string.page_grid), summary = stringResource(R.string.home_grid_summary), onClick = { open(Page.Grid) })
                ArrowPreference(stringResource(R.string.page_dock), summary = stringResource(R.string.home_dock_summary), onClick = { open(Page.Dock) })
                ArrowPreference(stringResource(R.string.page_divider), summary = stringResource(R.string.home_divider_summary), onClick = { open(Page.Divider) })
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
    var customGrid by remember { mutableStateOf(prefs.getBoolean(
        ConfigSchema.Grid.ENABLED.name(), ConfigSchema.Grid.ENABLED.uiDefault())) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
        item { PageHeader(stringResource(R.string.page_grid), stringResource(R.string.grid_header_summary)) }
        item { SmallTitle(stringResource(R.string.category_grid)) }
        item {
            SettingsCard {
                BooleanSetting(
                    prefs, ConfigSchema.Grid.ENABLED,
                    stringResource(R.string.enable_extended_grid),
                    stringResource(R.string.enable_extended_grid_summary), masterEnabled
                ) { customGrid = it }
                StringDropdown(
                    prefs = prefs,
                    key = ConfigSchema.Grid.PROFILE.name(),
                    title = stringResource(R.string.grid_profile),
                    default = ConfigSchema.Grid.PROFILE.uiDefault(),
                    options = listOf(
                        "8×4 / 4×8" to "8x4",
                        "10×6 / 6×10" to "10x6",
                    ),
                    enabled = masterEnabled && customGrid,
                )
                BooleanSetting(
                    prefs, ConfigSchema.Grid.WIDGET_ADAPTATION,
                    stringResource(R.string.enable_widget_adaptation),
                    stringResource(R.string.enable_widget_adaptation_summary),
                    masterEnabled && customGrid
                )
            }
        }
        item { SmallTitle(stringResource(R.string.category_landscape)) }
        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_landscape") || it.key == "indicator_landscape_y" }.forEach { IntSetting(prefs, it, masterEnabled && customGrid) } } }
        item { SmallTitle(stringResource(R.string.category_portrait)) }
        item { SettingsCard { gridSpecs.filter { it.key.startsWith("grid_portrait") || it.key == "indicator_portrait_y" }.forEach { IntSetting(prefs, it, masterEnabled && customGrid) } } }
    }
}

@Composable
private fun DockPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockEnabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())) }
    var resizeAnimation by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.RESIZE_ANIMATION.name(), ConfigSchema.Dock.RESIZE_ANIMATION.uiDefault())) }
    var smoothResize by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.name(), ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION.uiDefault())) }
    SettingsList(padding, stringResource(R.string.page_dock)) {
        BooleanSetting(prefs, ConfigSchema.Dock.ENABLED, stringResource(R.string.dock_customization), stringResource(R.string.dock_customization_summary), masterEnabled) { dockEnabled = it }
        BooleanSetting(prefs, ConfigSchema.Dock.RESIZE_ANIMATION, stringResource(R.string.dock_resize_animation), stringResource(R.string.dock_resize_animation_summary), masterEnabled && dockEnabled) { resizeAnimation = it }
        BooleanSetting(prefs, ConfigSchema.Dock.SMOOTH_RESIZE_ANIMATION, stringResource(R.string.dock_smooth_resize_animation), stringResource(R.string.dock_smooth_resize_animation_summary), masterEnabled && dockEnabled && !resizeAnimation) { smoothResize = it }
        dockSpecs.forEach { IntSetting(prefs, it, masterEnabled && dockEnabled) }
    }
}

@Composable
private fun DividerPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    val legacyDefault = remember { hasLegacyDividerConfig(prefs) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Divider.ENABLED.name(), legacyDefault)) }
    SettingsList(padding, stringResource(R.string.page_divider)) {
        BooleanSetting(prefs, ConfigSchema.Divider.ENABLED, "自定义 Dock 分隔线", "独立于 Dock 尺寸、模糊和单位开关；宽度与偏移固定使用 dp", masterEnabled, default = legacyDefault) {
            enabled = it
            if (it) ensureDividerDefaults(prefs)
        }
        dividerSpecs.forEach { IntSetting(prefs, it, masterEnabled && enabled) }
    }
}

@Composable
private fun WorkstationPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var enabled by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Workstation.DOCK_CUSTOMIZATION.name(), ConfigSchema.Workstation.DOCK_CUSTOMIZATION.uiDefault())) }
    SettingsList(padding, stringResource(R.string.page_workstation)) {
        BooleanSetting(prefs, ConfigSchema.Workstation.DOCK_CUSTOMIZATION, stringResource(R.string.workstation_customization), stringResource(R.string.workstation_customization_summary), masterEnabled) { enabled = it }
        workstationSpecs.forEach { IntSetting(prefs, it, masterEnabled && enabled) }
    }
}

@Composable
private fun LiquidPage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var liquidGlass by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Glass.ENABLED.name(), ConfigSchema.Glass.ENABLED.uiDefault())) }
    SettingsList(padding, stringResource(R.string.page_liquid)) {
        BooleanSetting(prefs, ConfigSchema.Glass.ENABLED, stringResource(R.string.liquid_enable), stringResource(R.string.liquid_enable_summary), masterEnabled) { liquidGlass = it }
        BooleanSetting(prefs, ConfigSchema.Glass.PRISMAL_SHOW_NORMALS,
            "Prismal · 显示法线",
            "调试：用 RGB 直接显示当前 Prismal 表面法线",
            masterEnabled && liquidGlass)
        liquidSpecs.forEach { IntSetting(prefs, it, masterEnabled && liquidGlass) }
    }
}

@Composable
private fun StrokePage(padding: PaddingValues, prefs: SharedPreferences, masterEnabled: Boolean) {
    var dockStroke by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_ENABLED.name(), ConfigSchema.Dock.STROKE_ENABLED.uiDefault())) }
    var squircle by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SQUIRCLE.name(), ConfigSchema.Dock.SQUIRCLE.uiDefault())) }
    var fillDiff by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.FILL_DIFF.name(), ConfigSchema.Dock.FILL_DIFF.uiDefault())) }
    SettingsList(padding, "描边") {
        BooleanSetting(prefs, ConfigSchema.Dock.STROKE_ENABLED, "显示完整描边", "控制 Dock 边框与灯光", masterEnabled) { dockStroke = it }
        BooleanSetting(prefs, ConfigSchema.Dock.SQUIRCLE, "方圆形连续曲线", "iPad 风格连续圆角", masterEnabled) { squircle = it }
        BooleanSetting(prefs, ConfigSchema.Dock.FILL_DIFF, "Fill-Diff 描边", "通过填充与挖空获得清晰抗锯齿", masterEnabled) { fillDiff = it }
        SmallTitle("描边背景色")
        strokeSpecs.filter { it.section == IntSection.StrokeBackground }.forEach { IntSetting(prefs, it, masterEnabled && dockStroke) }
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
    val dockEnabled = prefs.getBoolean(ConfigSchema.Dock.ENABLED.name(), ConfigSchema.Dock.ENABLED.uiDefault())
    var dockShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.SHADOW_ENABLED.name(), ConfigSchema.Dock.SHADOW_ENABLED.uiDefault())) }
    var strokeShadow by remember { mutableStateOf(prefs.getBoolean(ConfigSchema.Dock.STROKE_SHADOW.name(), ConfigSchema.Dock.STROKE_SHADOW.uiDefault())) }
    SettingsList(padding, "阴影") {
        BooleanSetting(prefs, ConfigSchema.Dock.SHADOW_ENABLED, "整个 Dock 下方阴影", "跟随 Dock 长宽、高度和圆角", masterEnabled && dockEnabled) { dockShadow = it }
        BooleanSetting(prefs, ConfigSchema.Dock.STROKE_SHADOW, "描边阴影", "描边下方的柔和阴影", masterEnabled && dockEnabled) { strokeShadow = it }
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
                BooleanSetting(prefs, ConfigSchema.Debug.LOGGING, "调试日志", "输出诊断日志到 Download/liquiddock.log，重启桌面生效")
            }
        }
        item { SmallTitle("界面与运行框架") }
        item {
            SettingsCard {
                ArrowPreference("Compose Miuix", summary = "MIUIX Compose 界面框架 · Apache-2.0", onClick = { openUrl(activity, "https://github.com/compose-miuix-ui/miuix") })
                ArrowPreference("AndroidX / Jetpack", summary = "Activity、Preference、AppCompat · Apache-2.0", onClick = { openUrl(activity, "https://source.android.com/docs/setup/about/licenses") })
                ArrowPreference("LSPosed API", summary = "模块 Hook API · GPL-3.0", onClick = { openUrl(activity, "https://github.com/LSPosed/LSPosed") })
            }
        }
        item { SmallTitle("实现参考") }
        item {
            SettingsCard {
                ArrowPreference("HyperCeiler", summary = "设置分层、交互方式与模块工程实践参考 · GPL-3.0", onClick = { openUrl(activity, "https://github.com/ReChronoRain/HyperCeiler") })
                ArrowPreference("Prismal", summary = "液态玻璃光学模型与 Shader 参数设计参考 · MIT", onClick = { openUrl(activity, "https://github.com/styropyr0/Prismal") })
                ArrowPreference("HyperLight", summary = "降采样与屏幕捕获思路启发", onClick = {})
            }
        }
        item { SmallTitle("许可说明") }
        item {
            SettingsCard {
                ArrowPreference("第三方开源声明", summary = "完整依赖版本、用途与许可证文本链接", onClick = { openUrl(activity, "https://github.com/yu4032/LiquidDock/blob/main/THIRD_PARTY_NOTICES.md") })
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
        if (!summary.isNullOrBlank()) Text(summary, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { Column(content = content) }
}

@Composable
private fun BooleanSetting(
    prefs: SharedPreferences, config: ConfigKey<Boolean>, title: String, summary: String? = null,
    enabled: Boolean = true, default: Boolean = config.uiDefault(), onChanged: (Boolean) -> Unit = {},
) {
    val key = config.name()
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
    val displayValue = if (decimalDp) String.format(java.util.Locale.ROOT, "%.1f", value) else value.roundToInt().toString()
    SliderPreference(
        value = value,
        onValueChange = { save(it) },
        title = spec.title,
        summary = spec.summary,
        valueText = "",
        enabled = enabled,
        valueRange = spec.min.toFloat()..spec.max.toFloat(),
        steps = if (decimalDp) ((spec.max - spec.min) * 10 - 1).coerceAtLeast(0) else (spec.max - spec.min - 1).coerceAtLeast(0),
        endActions = {
            Button(
                onClick = {
                    val input = EditText(context).apply {
                        setText(displayValue)
                        selectAll()
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or
                                if (decimalDp) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0
                    }
                    android.app.AlertDialog.Builder(context)
                        .setTitle(spec.title)
                        .setView(input)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确定") { _, _ -> input.text.toString().toFloatOrNull()?.let(::save) }
                        .show()
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
    val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
    PresetManager.applyDefault(prefs.edit())
    Toast.makeText(activity, "默认预设已应用", Toast.LENGTH_LONG).show()
    activity.restartLauncher()
}
