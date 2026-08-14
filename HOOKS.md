# Hook 点总览

本文档记录 `api101-migration` 当前源码实际安装或尝试安装的主要 Hook 点，以及这些 Hook 在运行时承担的职责。LiquidDock 注入 `com.miui.home`，使用 libxposed API 101 原生 interceptor；反射调用但没有安装 Hook 的系统方法会单独注明。

> 当前状态：2026-08-14 的 Phase 1 主要重构了配置层。`MainHook` / `HomeGridHook` / `DockLiquidGlassView` 尚未完成后续模块拆分，因此本文件按**实际调用路径**描述，而不是按目标架构描述。

## 入口、配置迁移与安装顺序

API 101 入口是 `ModuleMain`。

`onPackageReady()` 只处理 `com.miui.home`，当前顺序为：

1. `LegacyConfigMigration.migrateAtProcessStart()`
2. `new MainHook().install(classLoader)`
3. `WorkstationWallpaperOnlyHook.install(classLoader)`

`LegacyConfigMigration` 只在 Remote Preferences 为空时尝试读取 pre-API101 JSON，并在第一次运行时边界同步迁移。普通 `ConfigReader.load()` / `LiquidDockConfig.load()` 是只读 snapshot，不会再因为加载配置而写 Remote Preferences。

### 主开关的当前边界

当前主开关还不是严格的“零 Hook”：

- `MainHook.install()` 会先安装工作台模式 guard，再读取 `LiquidDockConfig`；
- `config.enabled == false` 后主体 Hook 才停止继续安装；
- `WorkstationWallpaperOnlyHook.install()` 仍由 `ModuleMain` 独立调用。

因此“主开关关闭 = 完全不安装任何 Hook”是后续重构目标，不是当前事实。

## `MainHook.install()` 当前职责

配置开启后，`MainHook` 仍是一个较大的安装/状态中心，当前会负责或触发：

- `DockStrokeRenderer.installNativeHook()`
- `RecentsHapticHook.install()`
- 工作台 Dock / 模式相关 Hook
- Dock resize 动画控制
- `DockDividerHook.install()`
- `HomeGridHook.install(...)`
- `HomeGridHook` 工作台偏移配置
- Liquid Glass 场景/捕获 Hook
- Dock 几何、背景、spacing、blur、shadow 等 Hook

这也是后续模块化要拆掉的主要耦合点。

## 场景判定与捕获触发

HOME / APP / RECENTS 由 `CaptureSceneState` 维护。事件 Hook 用来更早预置场景，`DockLiquidGlassView.onPreDraw()` 再根据可见性/生命周期/Recents 状态刷新实际目标。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.Launcher` | `onWindowFocusChanged(boolean)` | HOME / APP 的关键边界信号；Launcher 失焦时进入 APP 预热路径，重新聚焦后安排 HOME settle |
| `GestureToHome` | 所有构造函数 | 预置 HOME gesture target |
| `GestureToApp` | 所有构造函数 | 预置 APP gesture target，并允许短时 APP backdrop pre-arm |
| `GestureToRecent` | 所有构造函数 | 预置 RECENTS gesture target |
| `EnterOverviewStateEvent` | 所有构造函数 | 标记 Overview / RECENTS 进入 |
| `ExitOverviewStateEvent` | 所有构造函数 | 标记 Overview / RECENTS 退出 |
| `com.miui.home.launcher.Launcher` | `showOrHideRecent()` | 普通路径提供 Recents 边界；工作台实验路径还用它刷新 native snapshot |
| `com.miui.home.launcher.DeviceConfig` | `setControlPanelExpanded(boolean)` | 控制中心/通知区域展开时关闭不合适的捕获 |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged(Configuration)` | 旋转/配置变化后打开 capture stabilization；网格路径也等待新方向 bounds 稳定 |
| `android.app.Activity` | `onWindowVisibilityChanged(int)` | 仅 Launcher 实例作为捕获触发/可见性补充，不作为唯一场景判定 |
| `Launcher` | `onResume/onPause/onStart/onStop` | 生命周期记录与 fallback 边界 |

`CaptureSceneState` 带 revision。异步 SurfaceFlinger 回调只有在 scene/revision/attempt token 仍匹配时才能安装，避免旧帧覆盖新场景。

### 捕获模式不是固定的 scene→mode 表

当前隐藏兼容项 `liquid_capture_fullscreen` 默认开启：

- 开启时优先走 full-display SurfaceFlinger 捕获；
- APP/RECENTS 等非 HOME 普通路径会在需要时排除 Dock/drag layer；
- 关闭时才退回 vendor wallpaper `captureMode(2)`；
- 工作台模式还有独立 suspension/native snapshot 实验逻辑。

因此不要再把实现简单描述为“HOME 永远 mode 2，APP/RECENTS 永远 mode 1”。

## 壁纸 offset / zoom 监听

LiquidDock 不阻止系统壁纸滚动/缩放调用，而是在原调用后读取变化并更新捕获映射状态。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `android.app.WallpaperManager` | `setWallpaperOffsets(IBinder, float, float)` | 原调用继续执行；记录标准化 wallpaper offset |
| `android.app.WallpaperManager` | `setDisplayOffset(IBinder, int, int)` | 原调用继续执行；记录 display offset |
| `android.app.WallpaperManager` | `setWallpaperZoomOut(IBinder, float)` | 原调用继续执行；记录 wallpaper zoom |

## 普通 Dock 几何、模糊与阴影

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.Launcher` | `setupViews()` | Dock 视图树完成后创建/绑定 Liquid Glass 与可选整体 shadow；保存原生背景引用 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 普通模式宽度偏移；同步 Glass / shadow 几何；工作台实验路径也会在同入口应用独立宽度偏移 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundHeight(int)` | 普通 Dock 高度偏移并同步几何 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundRadius(float)` | 原生模糊圆角更新；同时是 `DockStrokeRenderer` 的 native Dock 描边刷新入口 |
| `HotSeatsListContentBlurBackground2` | `updateBackgroundSize(int, int, float)` | 控制系统 resize animator，必要时由 LiquidDock 使用平滑 resize 动画 |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 普通 Dock 图标 spacing；工作台实验路径可独立应用 top/bottom offset |
| `HotSeatsListContentLayoutManager` | `updateBackgroundView(FrameLayout, int, int, float)` | spacing 开启时补偿 Dock 背景长度 |
| `com.miui.home.launcher.DeviceConfig` | `getHotSeatsMarginBottom()` | 应用普通 Dock bottom offset |
| `com.miui.home.launcher.common.BlurUtilities` | `setBackgroundBlur(View, int, float[], int[][])` | 按配置覆盖原生 blur radius |
| `com.miui.home.launcher.hotseats.HotSeats` | `getMingouStaticDockBlurShadowTarget()` | 记录 HyperOS 原生 Dock shadow target |
| `com.miui.home.launcher.common.MiShadowUtils` | `applyViewShadow(...)` | 只对已识别的 Dock 原生 shadow target 清空系统 shadow，避免重复阴影 |

旧文档中的 `HotSeatsListContentBlurBackground2.setBackgroundBlur(...)`、`HotSeats.setBackgroundWidth/Height/Radius` 已不是当前 Hook 点。

## 描边：当前 foreground 实现

`DockStrokeRenderer` 已取代旧的独立描边 overlay：

- Hook `HotSeatsListContentBlurBackground2.setBackgroundRadius(float)`；
- native blur Dock 和 `DockLiquidGlassView` 复用同一 foreground renderer；
- `StrokeDrawable` 构造 outer/inner path；
- 通过 `clipPath(outer)` + `clipOutPath(inner)` 从几何上排除 Dock 中心；
- 不使用独立 overlay View / RenderNode；
- 不把可配置 Dock border 画成普通 `Paint.Style.STROKE`。

### 旧描边阴影

历史 `stroke_shadow`、`shadow_radius`、`shadow_alpha` key 仍在 `ConfigSchema` / `LiquidDockConfig` 中以保持配置兼容，但**当前 `DockStrokeRenderer` 不消费这些值，旧描边阴影效果已经失效**。

这和独立的整个 Dock shadow (`dock_shadow*`) 是两套功能。测试新描边时不应把“没有旧描边阴影”判为回归。

## 拖拽与 Recents 触觉

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.DragController` | `startDrag(...)` | 标记 Dock 拖拽开始，并尝试解析 drag layer 供捕获排除/调度使用 |
| `com.miui.home.launcher.DragController` | `endDrag()` | 清除拖拽状态与 drag layer |
| `HapticFeedbackCompatLinear` | `performEnterRecent(View)` | Recents 语义预触发 |
| `HapticFeedbackCompatV2` | `performEnterRecent(View)` | 同上，兼容另一 HyperOS 实现 |
| `HapticFeedbackCompatNormal` | `performEnterRecent(View)` | 同上，兼容另一 HyperOS 实现 |

三个 haptic 类位于 `com.miui.home.launcher.common.*`；`RecentsHapticHook` 使用 superclass-aware 的精确方法查找。

## 桌面 8×4 / 4×8 网格

`MainHook` 总会调用 `HomeGridHook.install(...)`，但 `HomeGridHook` 在 `home_grid_8x4 == false` 时会立即返回，不安装布局自定义 Hook。

开启后主要 Hook：

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `LauncherCellCountCompatPadDevice` | `getCellCountXMin/Def(Context)` | 横屏 8 列、竖屏 4 列 |
| `LauncherCellCountCompatPadDevice` | `getCellCountYMin/Def(Context)` | 横屏 4 行、竖屏 8 行 |
| `com.miui.home.launcher.grid.GridConfig` | `setCountX/setCountY` | 把系统 6 规范化/扩展为 8 |
| `com.miui.home.launcher.grid.GridConfig` | `getCountX/getCountY` | 读取时同样规范化 6→8 |
| `LayoutTransformRuleGridChanged` | 所有构造函数 | 写入当前 8 个 2×2 block 的横竖屏 transform metadata |
| `LayoutTransformRuleGridChanged` | `checkCellCount()` | 允许 8×4 / 4×8 通过系统校验 |
| `com.miui.home.launcher.CellLayout` | `calculateXsAndYs()` | 应用自定义 margin/gap，并在原方法后重建最终 `mXs/mYs` |
| `com.miui.home.launcher.CellLayout` | `setupLayoutParam(...)` | Widget adaptation 开启时应用支持 span 的自定义 allocation |
| `com.miui.home.launcher.CellLayout` | `onLayout(...)` | lazy page 首次有效 bounds 几何准备；原生布局后重新断言 Widget exact frame |
| `com.miui.home.launcher.folder.FolderIcon1x1` | `onMeasure(int, int)` | 用真实 CellLayout cell 高度重基准小文件夹测量 |
| `com.miui.home.launcher.Launcher` | `setupViews()` | 保存 Workspace 弱引用并安排多次页面刷新 |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged(Configuration)` | 等待新方向 Workspace bounds 稳定后刷新页面 |
| `com.miui.home.launcher.ScreenView` | `updateIndicatorPositions(int, boolean)` | 对实际 Workspace 保存基准 translation，再应用方向独立 Y offset |

### 占位/位置 invariants

当前实现刻意**不 Hook** `addOccupied()` 和 `transformToHVArray()`。MIUI 继续拥有 occupied matrix 与 placement/transform 语义，LiquidDock 只扩展 grid metadata 和像素几何。

不要重新引入“猜测 occupied matrix 是 `[x][y]` 还是 `[y][x]`”的单 item 修补逻辑，这类修改历史上会导致重叠、丢图标和旋转越界。

## Widget adaptation：当前真实路径

开关：`grid_widget_adaptation`。只有 `home_grid_8x4 && grid_widget_adaptation` 时 `WidgetGridSizing.gridRect()` 才返回有效自定义矩形。

当前活动检测逻辑：

1. 优先调用 `ItemInfo.isWidget()`；
2. fallback 到 `itemType == 4 || 5 || 19`；
3. `WidgetGridSizing.isSupportedSpec()` 只允许：
   - 1×1
   - 2×1
   - 2×2
   - 4×2

活动尺寸路径是 `CellLayout.setupLayoutParam()` + `CellLayout.onLayout()` 的 exact-frame enforcement。

`HomeGridHook.adaptTwoByOneWidget(...)` 仍存在于源码，但当前没有接到活动布局链，属于待删除 legacy/inert helper；不要把它重新接回去当作当前 2×1 修复方案。

下一阶段计划引入 `WidgetClassifier` / `WidgetSpecRegistry`，把类型和 span 从核心 Hook 中移出；**该 registry 尚未实现**。

## Divider

`DockDividerHook` 独立 Hook：

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | 系统 bind 完成后按配置覆盖 Divider 宽度、高度比例、Y offset 和 RGBA |

Divider 与 `dock_dimensions_dp` 解耦。历史 width/Y JSON 数值是 raw `0.1 dp` 整数，`ConfigSchema.Divider` 故意使用 `DIRECT` 保存旧导入 clamp/导出表示；运行时由 `LiquidDockConfig.Divider` 除以 10 变成真实 dp。

存在 `dock_divider_enabled` 时是 explicit mode；旧配置缺少开关时继续使用 `0 = 不覆盖系统默认` 的 legacy sentinel。

详见 [DIVIDER.md](DIVIDER.md)。

## 工作台 / Laptop：实验实现，不是完成适配

源码中已有多条 Workstation Hook，但这只能说明存在实验性兼容路径，**不能说明工作台已经适配完成**。

主要实验 Hook/入口包括：

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.laptop.LaptopStateManager` | `onLaptopModeChanged(boolean)` | 首选工作台状态变化入口；触发状态切换与普通布局 backup/restore |
| `com.miui.home.launcher.DeviceConfig` | `setMingouLaptopPcModeEnabled(boolean)` | 旧系统 fallback |
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 可应用工作台专用 Dock width offset |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 可应用工作台 Dock icon top/bottom offset |
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | 工作台 Divider 实验配置 |
| `com.miui.home.launcher.hotseats.HotSeats` | `setMingouStaticDockLiveBlurVisible(boolean)` | 实验性强制 native snapshot/live blur 状态 |
| `com.miui.home.launcher.hotseats.HotSeats` | `setMingouStaticDockSnapshotMode(boolean)` | 实验性 snapshot mode 维持 |
| `com.miui.home.launcher.laptop.AllAppsController` | `showAllApps(boolean)` | 切换前后刷新原生 wallpaper snapshot |
| `com.miui.home.launcher.laptop.AllAppsController` | `showWindow(boolean)` | 同上，覆盖另一条 All Apps 路径 |
| `com.miui.home.launcher.Launcher` | `showOrHideRecent()` | 工作台 Recents snapshot/capture 边界 |
| `com.miui.home.launcher.CellLayout` | `onLayout(...)` | 实验性修正 Laptop All Apps/Grid 纵向 offset |

状态初值还会**反射调用** `LauncherModeController.isLaptopMode()`；旧系统 fallback 会反射读取 `DeviceConfig.isMingouLaptopPcModeEnabled()`。这些 getter 只是调用，不是额外 Hook 点。

当前工作台仍可能在 Dock、All Apps、Recents、横竖屏、布局恢复或捕获上出现异常，因此应保持“未适配/不支持”的产品状态，直到完成真机回归。

## API 101 Hook / 反射工具层

所有 Hook/反射统一经过 `HookUtil`，不再依赖旧 `XposedHelpers` shim：

- `HookUtil.hook(...)`：安装 API 101 interceptor
- `HookUtil.hookMethod(...)`：按精确签名查找并 Hook
- `HookUtil.findMethodExact(...)`：沿父类链查找精确方法
- `HookUtil.invoke/invokeStatic(...)`：反射调用
- `HookUtil.getField/setField` 及基本类型 helper：沿父类链访问字段

`Api101Bridge` 保存进程内 `XposedModule` 实例，并提供日志、Remote Preferences 等 API 101 能力。

## 当前配置边界（非 Hook）

Phase 1 后：

- `ConfigSchema`：key/type/default/range/storage/export metadata
- `ConfigCodec`：JSON import/export
- `ConfigMigration`：设置进程历史 SharedPreferences 升级
- `PresetManager`：预设
- `ConfigReader`：只读 Remote Preferences snapshot
- `LiquidDockConfig`：不可变运行时配置
- `LegacyConfigMigration`：仅 package-ready 的 pre-API101 JSON compatibility bridge

Hook 和 renderer 不应重新直接读取 raw preference key。新增配置应先进入 schema，再由 typed config 传入运行时。

实现细节以当前 `ModuleMain`、`MainHook`、`HomeGridHook`、`WidgetGridSizing`、`DockLiquidGlassView`、`LiveScreenCapture`、`CaptureSceneState`、`CaptureCadence`、`DockStrokeRenderer`、`DockDividerHook`、`RecentsHapticHook`、`WorkstationWallpaperOnlyHook`、`HookUtil`、`ConfigSchema`、`ConfigCodec`、`ConfigMigration`、`ConfigReader` 和 `LiquidDockConfig` 为准。
