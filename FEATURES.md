# LiquidDock 功能手册

本文档按当前 `api101-migration` 的 `ConfigSchema` 与运行时实现整理。范围描述以实际代码为准；工作台部分虽然已有实验实现，但**仍未完成适配，不属于受支持功能**。

## 桌面网格 (Grid)

主开关 `home_grid_8x4` 启用后，桌面使用横屏 8×4 / 竖屏 4×8 的自定义网格。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Widget adaptation | 开/关 | 独立控制 Widget frame 适配；关闭时保留 MIUI Widget 几何 |
| 横屏水平距离偏移 | −600 ~ 600 dp | 同时调整横屏左右水平方向距离 |
| 横屏顶部距离偏移 | −600 ~ 600 dp | 调整横屏顶部距离 |
| 横屏底部距离偏移 | −600 ~ 600 dp | 调整横屏底部距离 |
| 竖屏水平距离偏移 | −600 ~ 600 dp | 同时调整竖屏左右水平方向距离 |
| 竖屏顶部距离偏移 | −600 ~ 600 dp | 调整竖屏顶部距离 |
| 竖屏底部距离偏移 | −600 ~ 600 dp | 调整竖屏底部距离 |
| 横屏行距偏移 | −200 ~ 400 dp | 调整横屏图标纵向行距 |
| 竖屏行距偏移 | −200 ~ 400 dp | 调整竖屏图标纵向行距 |
| 横屏页面指示器 Y | −160 ~ 160 dp | 横屏页面指示器垂直偏移 |
| 竖屏页面指示器 Y | −160 ~ 160 dp | 竖屏页面指示器垂直偏移 |

### Widget adaptation 当前边界

当前活动实现：

- 优先用 `ItemInfo.isWidget()` 判断；
- fallback item type：`4`、`5`、`19`；
- 当前支持 span：`1×1`、`2×1`、`2×2`、`4×2`；
- `setupLayoutParam()` 先写自定义 allocation；
- `onLayout()` 后再次断言 exact frame，抵消 MIUI 的 span-dependent 二次居中；
- 不修改 MIUI 的 occupancy/placement 算法。

Widget 类型与 span 目前仍是硬编码规则。后续计划改为 `WidgetClassifier` / `WidgetSpecRegistry`，但 registry **尚未实现**。

---

## Dock 外观

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Dock customization | 开/关 | 普通 Dock 几何/模糊自定义总开关 |
| Dock resize animation | 开/关 | 是否保留 MIUI 原生 resize 动画 |
| 平滑 resize animation | 开/关 | 原生 resize 被禁用时可使用 LiquidDock 自己的平滑过渡 |
| 宽度偏移 | −80 ~ 80 dp | 相对系统 Dock 背景宽度增减 |
| 高度偏移 | −80 ~ 80 dp | 相对系统 Dock 背景高度增减 |
| 图标间距 | −8 ~ 12 dp | 调整相邻 Dock 图标间距，并补偿背景宽度 |
| 底部偏移 | −30 ~ 40 dp | 调整 Dock 与屏幕底部距离 |
| 原生模糊强度 | 0 ~ 400 | 原生 blur Dock 的模糊量 |
| 描边圆角偏移 | −50 ~ 100 dp* | `corner_offset`；历史默认/兼容语义由 typed config 保留 |
| 内部模糊圆角偏移 | −50 ~ 100 dp | `blur_corner_offset` |
| 方圆形 | 开/关 | 使用 squircle 轮廓 |
| Fill-Diff | 开/关 | 使用 outer/inner 轮廓差形成描边 |

`*` `ConfigSchema` 当前 `corner_offset` 的 UI 默认值仍为 `-1`，并保持历史 absent-key 语义；不要仅按表格把旧配置重新归一化。

---

## 描边 (Stroke)

当前描边由 `DockStrokeRenderer` 直接安装到 native blur Dock 或 Liquid Glass View 的 foreground。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| 描边开关 | 开/关 | `dock_stroke` |
| 描边底色 R/G/B | 0 ~ 255 | RGB |
| 描边透明度 | 0 ~ 255 | 与 renderer 的历史视觉 alpha 共同计算实际 alpha |
| 方圆形控制点 | 40 ~ 80 | `sq_outer_cp` |
| 方圆形描边宽度 | 1 ~ 10 dp | squircle 模式宽度 |
| 方圆形外扩/内缩量 | 0 ~ 16 dp | `sq_stroke_off` |
| Fill-Diff 描边宽度 | 1 ~ 6 dp | `stroke_w` |
| 标准描边宽度 | 1 ~ 10 dp | `std_stroke_w` |

### 旧描边阴影状态

历史配置仍保留：

- `stroke_shadow`
- `shadow_radius`
- `shadow_alpha`

但描边切换到 foreground renderer 后，**当前 `DockStrokeRenderer` 不实现旧描边阴影效果**。这些 key 目前主要为配置兼容存在，不应把“描边没有旧阴影”当作当前 renderer 的回归。

---

## 整体 Dock 阴影 (Dock Shadow)

这和上面的旧“描边阴影”不是同一功能。

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| Dock 阴影开关 | 开/关 | 独立整个 Dock shadow |
| 阴影柔和度 | 1 ~ 40 dp | `dock_shadow_radius` |
| 阴影扩散 | 1 ~ 60 dp | `dock_shadow_size` |
| 阴影浓度 | 0 ~ 200 | `dock_shadow_alpha` |
| 阴影 Y 偏移 | −24 ~ 24 dp | `dock_shadow_y` |

LiquidDock 会识别并抑制 HyperOS 原生 Dock shadow target，避免自绘 shadow 与系统 shadow 重叠。

---

## Liquid Glass

主开关 `liquid_glass` 控制液态玻璃视图和捕获路径。

### 核心光学

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| 玻璃模糊 | 0 ~ 60 dp | 捕获背景的模糊范围 |
| 玻璃厚度 | 1 ~ 60 dp | 虚拟玻璃厚度 |
| 折射率 IOR | 100 ~ 200 % | 运行时除以 100 |
| 法线强度 | 0 ~ 300 % | 运行时除以 100 |
| 穹顶凸起 | 0 ~ 200 % | 运行时除以 100 |
| 透镜折射 | 0 ~ 60 dp | 边缘折射偏移 |
| 色散强度 | 0 ~ 40 % | RGB 分离强度 |
| 深度透镜效果 | 0 ~ 50 % | `liquid_depth_effect` |
| 亮度 | 50 ~ 200 % | `liquid_brightness` |

### 颜色、高光与边缘

| 参数 | 当前范围 | 说明 |
|------|------:|------|
| 玻璃底色透明度 | 0 ~ 160 | tint alpha |
| Tint R/G/B | 0 ~ 255 | 玻璃 tint 颜色 |
| 边缘高光宽度 | 20 ~ 300 % | `liquid_highlight_width` |
| 高光不透明度 | 0 ~ 200 % | `liquid_highlight_alpha` |
| 镜面锐度 | 1 ~ 200 | `liquid_specular_sharp` |
| 镜面强度 | 0 ~ 300 % | `liquid_specular_strength` |
| Rim light | 0 ~ 300 % | `liquid_rim_light` |
| 焦散 | 0 ~ 100 % | `liquid_caustics` |
| Edge band | 5 ~ 100 | `liquid_edge_band` |

---

## 捕获 (Capture)

| 参数 | 当前 Schema 范围 | 说明 |
|------|------:|------|
| 捕获帧率上限 | 5 ~ 60 fps | `liquid_capture_power_limit_fps` |
| 捕获停止延迟 | 0 ~ 10000 ms | Dock 隐藏后的 grace period |
| 捕获分辨率 | 10 ~ 100 % | SurfaceFlinger capture scale |
| 动态 APP 捕获 | 开/关 | 是否使用探针/活动 cadence |
| 静态探针帧率 | 1 ~ 10 fps | `liquid_dynamic_app_probe_fps` |
| 动态运动阈值 | 1 ~ 240 | `liquid_dynamic_motion_threshold` |
| 动态 bit/pixel 阈值 | 1 ~ 64 | `liquid_dynamic_bit_threshold` |
| 高频保持时间 | 0 ~ 5000 ms | 动态后 active cadence 保持时间 |
| 黑帧阈值 | 0 ~ 64 | 低亮度异常帧保护 |
| HOME settle delay | 200 ~ 3000 ms | APP→HOME 后等待壁纸稳定 |
| Recents 预触发距离 | 1 ~ 48 dp | 底部手势达到阈值时预热 |
| 上额外捕获高度 | 0 ~ 256 dp | 折射采样 bleed top |
| 下额外捕获高度 | 0 ~ 256 dp | 折射采样 bleed bottom |

### 隐藏兼容项

`liquid_capture_fullscreen` 当前默认 true，且不作为普通 JSON 导出字段。开启时优先 full-display capture；关闭时才退回 vendor wallpaper capture mode。实际 capture 还受 scene、Dock 可见性、Recents、drag、SystemUI、屏幕交互状态、attempt token 和 rotation stabilization 共同控制。

因此不要再把 capture 描述为固定的“HOME=wallpaper、APP/RECENTS=full display”映射。

---

## 工作台 / Laptop（未完成适配）

**工作台目前仍是未完成适配状态。**

源码已经有实验性实现，包括：

- Laptop/Workstation 状态检测
- 工作台 Dock width offset
- 工作台 Dock icon top/bottom offset
- 工作台 Grid horizontal offset
- All Apps 横/竖屏独立 horizontal/vertical offset
- Divider 自定义
- native wallpaper snapshot refresh/locking
- Recents capture/suspension 边界
- 普通布局位置 backup/restore

相关参数：

| 参数 | 当前范围 |
|------|------:|
| 工作台 Dock 长度偏移 | −240 ~ 240 dp |
| 工作台桌面水平偏移 | −240 ~ 240 dp |
| All Apps 横屏水平偏移 | −240 ~ 240 dp |
| All Apps 横屏垂直偏移 | −240 ~ 240 dp |
| All Apps 竖屏水平偏移 | −240 ~ 240 dp |
| All Apps 竖屏垂直偏移 | −240 ~ 240 dp |
| 工作台 Dock icon top offset | −48 ~ 48 dp |
| 工作台 Dock icon bottom offset | −48 ~ 48 dp |

这些参数存在不代表工作台已经可用。当前仍需要完整真机回归覆盖 Dock、All Apps、Recents、旋转、进入/退出、位置恢复和捕获。

---

## 工作台 Divider

`DockDividerHook` 与普通 Dock dimension unit 完全解耦。

| 参数 | 持久化范围 | 运行时语义 |
|------|------:|------|
| Divider 开关 | 开/关 | 有显式开关时进入 explicit mode |
| width | 0 ~ 160 | 历史 raw `0.1 dp` 整数；运行时除以 10 |
| height scale | 0 ~ 100 | 相对父容器高度百分比 |
| Y offset | −80 ~ 80 | 历史 raw `0.1 dp` 整数；运行时除以 10 |
| R/G/B | 0 ~ 255 | Divider 颜色 |
| Alpha | 0 ~ 255 | Divider alpha |

旧配置没有 `dock_divider_enabled` 时沿用 legacy sentinel：数值 0 表示“不覆盖系统默认”。显式开关存在后，0 就是实际可设置值。

详见 [DIVIDER.md](DIVIDER.md)。

---

## 配置管理

Phase 1 后的配置职责：

- `ConfigSchema`：统一登记 persisted key/type/default/range/storage/export metadata
- `ConfigCodec`：JSON 导入/导出
- `ConfigMigration`：设置进程历史偏好升级
- `PresetManager`：预设写入
- `ConfigReader`：只读 Remote Preferences snapshot
- `LiquidDockConfig`：不可变运行时 typed config
- `LegacyConfigMigration`：仅 Launcher package-ready 时尝试迁移 pre-API101 JSON

当前兼容规则包括：

- SharedPreferences key 不改名；
- 历史 JSON 继续可导入；
- `grid_widget_adaptation` 已加入导入/导出；
- decimal dp 使用 `<key>_tenths` 保留精度；
- `liquid_home_settle_delay` 继续保留历史 `_tenths` round-trip；
- Divider width/Y 保持历史 DIRECT raw-tenths JSON 语义，不改成通用 `DP_TENTHS` sidecar；
- `dock_dimensions_dp` 与 `liquid_dimensions_dp` 的历史导出表示继续保持兼容。
