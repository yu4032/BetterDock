# Hook 点总览

LiquidDock 是一个注入 `com.miui.home` 的 LSPosed 模块，当前 `api101-migration` 分支使用 libxposed API 101 原生 Hook 接口。本文档只记录**当前源码实际安装的 Hook 点**及其职责；反射调用但未被 Hook 的系统方法会单独说明。

> 说明：同一个系统方法可能被多个功能模块分别 Hook，例如 `Launcher.setupViews()`、`Launcher.onConfigurationChanged()`、`CellLayout.onLayout()` 和 `Launcher.showOrHideRecent()`。下表按职责拆分，而不是假定每个方法只有一个拦截器。

## 入口与安装顺序

`ModuleMain` 是 API 101 入口。`onPackageReady()` 只处理 `com.miui.home`，随后依次调用：

1. `new MainHook().install(classLoader)`：普通 Dock、液态玻璃、网格、工作台状态等主体 Hook；
2. `WorkstationWallpaperOnlyHook.install(classLoader)`：工作台原生壁纸快照锁定与 All Apps / Recents 补充 Hook。

`MainHook.install()` 会先安装工作台模式状态监听，再读取 `LiquidDockConfig`。主开关关闭时主体 Hook 不再继续安装；`WorkstationWallpaperOnlyHook` 仍由 `ModuleMain` 独立调用。

## 场景判定与捕获触发

HOME / APP / RECENTS 由 `CaptureSceneState` 维护。当前实现不是只靠 Activity 生命周期推断，而是结合 Launcher 焦点、Dock 手势语义事件、Overview 事件和最近任务可见性。`DockLiquidGlassView.onPreDraw()` 负责刷新观察结果和 `updateDesiredScene()`，事件 Hook 则用于更早锁定第一帧目标场景。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.Launcher` | `onWindowFocusChanged(boolean)` | HOME / APP 的核心边界信号。失焦时先解析前台应用层并预热 APP 背景；重新聚焦时回到 Launcher 状态 |
| `com.miui.home.launcher.dock.v3.GestureToHome` | 所有构造函数 | 把手势目标预置为 HOME，早于后续焦点/可见性变化 |
| `com.miui.home.launcher.dock.v3.GestureToApp` | 所有构造函数 | 把手势目标预置为 APP，并为隐藏状态下的 APP 捕获重新开短暂预热窗口 |
| `com.miui.home.launcher.dock.v3.GestureToRecent` | 所有构造函数 | 把手势目标预置为 RECENTS |
| `com.miui.home.recents.event.EnterOverviewStateEvent` | 所有构造函数 | 标记 Overview/RECENTS 进入 |
| `com.miui.home.recents.event.ExitOverviewStateEvent` | 所有构造函数 | 标记 Overview/RECENTS 退出 |
| `com.miui.home.launcher.Launcher` | `showOrHideRecent()` | 工作台专用最近任务按钮的精确边界；`MainHook` 在原方法前通知玻璃层，工作台壁纸模块还会在前后刷新原生快照 |
| `com.miui.home.launcher.DeviceConfig` | `setControlPanelExpanded(boolean)` | 控制中心展开状态写入捕获门控，展开期间禁止不合适的捕获 |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged(Configuration)` | 旋转/配置变化后触发玻璃捕获稳定流程；网格模块也在同一方法上等待新方向尺寸稳定后重排 |
| `android.app.Activity` | `onWindowVisibilityChanged(int)` | 仅对 `Launcher` 实例生效，作为捕获触发信号；**不作为 HOME / APP 判定依据** |
| `com.miui.home.launcher.Launcher` | `onResume()` / `onPause()` / `onStart()` / `onStop()` | 当前直接 Hook 主要用于生命周期日志；焦点/可见性负责实际决策 |
| `android.app.Activity` | `onResume()` / `onPause()` | 仅当 Launcher 直接生命周期 Hook 安装失败时作为 fallback，并且只处理 `Launcher` 实例 |

`CaptureSceneState` 还维护 revision。异步捕获请求会记录场景与 revision，回调到达时若状态已变化则丢弃旧帧，避免 APP/HOME/RECENTS 的过期结果覆盖当前背景。

## 壁纸 offset / zoom 监听

这里与旧文档最大的差异是：**LiquidDock 当前不会阻止 WallpaperManager 的滚动、位移或缩放调用。** 三个 Hook 都先执行系统原方法，再把变化通知给 `DockLiquidGlassView`，用于更新捕获/映射状态。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `android.app.WallpaperManager` | `setWallpaperOffsets(IBinder, float, float)` | 原调用照常执行；通知标准化 wallpaper offset |
| `android.app.WallpaperManager` | `setDisplayOffset(IBinder, int, int)` | 原调用照常执行；通知原始显示偏移 |
| `android.app.WallpaperManager` | `setWallpaperZoomOut(IBinder, float)` | 原调用照常执行；通知壁纸缩放变化 |

因此不要再把这组三个 Hook 描述成“固定壁纸”或“拦截壁纸移动”。

## 普通 Dock 几何、模糊与阴影

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.Launcher` | `setupViews()` | Dock 视图树完成后创建/绑定 `DockLiquidGlassView` 与独立 Dock shadow View，并保存原生背景引用；液态玻璃-only 路径也从这里初始化 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 普通模式应用宽度偏移；之后同步玻璃/阴影几何。工作台尺寸模块也可在同一入口应用独立宽度偏移 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundHeight(int)` | 普通模式应用高度偏移并同步玻璃/阴影几何 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundRadius(float)` | 普通模式调整原生模糊圆角并同步玻璃；`DockStrokeRenderer` 也在该入口更新 native blur Dock 的 foreground 描边 |
| `HotSeatsListContentBlurBackground2` | `updateBackgroundSize(int, int, float)` | 当禁用 MIUI 原生 Dock resize 动画时结束系统 animator；可选择由 LiquidDock 用 180 ms 动画从旧几何平滑到新几何 |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(Rect, View, RecyclerView, RecyclerView.State)` | 普通模式调整图标水平 spacing；工作台模式可独立调整图标 top/bottom offset |
| `HotSeatsListContentLayoutManager` | `updateBackgroundView(FrameLayout, int, int, float)` | spacing 开启时把新增 item 间距计入 Dock 背景宽度 |
| `com.miui.home.launcher.DeviceConfig` | `getHotSeatsMarginBottom()` | `dock_bottom_offset` 非 0 时调整普通 Dock 底部边距 |
| `com.miui.home.launcher.common.BlurUtilities` | `setBackgroundBlur(View, int, float[], int[][])` | 普通模式按配置覆盖原生 blur radius |
| `com.miui.home.launcher.hotseats.HotSeats` | `getMingouStaticDockBlurShadowTarget()` | 记录真正的原生 Dock 阴影目标 View |
| `com.miui.home.launcher.common.MiShadowUtils` | `applyViewShadow(View, int, float, float, float, float)` | 仅对已识别的原生 Dock 阴影目标清空系统 shadow，避免与 LiquidDock 自绘阴影重复 |

旧文档中的 `HotSeatsListContentBlurBackground2.setBackgroundBlur(...)` 和 `HotSeats.setBackgroundWidth/Height/Radius` 已不是当前 Hook 点。

### 描边实现

描边已经从“独立 overlay View”迁移到 `DockStrokeRenderer`：

- `DockStrokeRenderer.installNativeHook()` Hook `HotSeatsListContentBlurBackground2.setBackgroundRadius(float)`；
- 描边以 `StrokeDrawable` 安装到宿主 View 的 **foreground**，Liquid Glass View 也复用同一 renderer；
- renderer 用 outer path + inner path，并通过 `clipOutPath(inner)` 排除 Dock 中心，不再创建单独的描边 overlay / RenderNode。

因此 `Launcher.setupViews()` 中的额外 View 现在是玻璃层和可选 shadow，不应再把描边写成 overlay View。

## 拖拽、手势与最近任务触觉

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.DragController` | `startDrag(Drawable, boolean, ItemInfo, int, int, float, DragSource, int)` | 标记 Dock 拖拽开始，并尝试解析 drag View 的 SurfaceControl layer name，供动态捕获排除/调度使用 |
| `com.miui.home.launcher.DragController` | `endDrag()` | 清除拖拽状态与 drag layer |
| `HapticFeedbackCompatLinear` | `performEnterRecent(View)` | 进入最近任务的语义触觉事件；触发 RECENTS 预热后继续执行原方法 |
| `HapticFeedbackCompatV2` | `performEnterRecent(View)` | 同上，兼容另一套 HyperOS 实现 |
| `HapticFeedbackCompatNormal` | `performEnterRecent(View)` | 同上，兼容另一套 HyperOS 实现 |

三个触觉类的完整包名均为 `com.miui.home.launcher.common.*`。`RecentsHapticHook` 会逐个尝试存在的实现，`HookUtil.findMethodExact()` 沿父类链解析 `performEnterRecent(View)`。

旧文档中的 `DockContainer.startDrag/endDrag` 和“RecentsView 相关类”描述已过时。

## 工作台 / Laptop 模式

工作台模式不再只是普通 Dock 的一个尺寸分支。它有独立状态监听、原生 wallpaper snapshot 锁定、All Apps/Recents 刷新和独立 Dock 参数。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `com.miui.home.launcher.laptop.LaptopStateManager` | `onLaptopModeChanged(boolean)` | 当前首选的工作台模式切换 Hook；进入前备份普通桌面 item 位置，退出后恢复并刷新网格 |
| `com.miui.home.launcher.DeviceConfig` | `setMingouLaptopPcModeEnabled(boolean)` | 仅当前 `LauncherModeController` / `LaptopStateManager` API 不可用时使用的 legacy fallback |
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | `workstation_dock_customization` 开启时应用工作台专用 Dock 宽度偏移 |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 工作台专用图标上/下偏移 |
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | 工作台 Dock 分隔竖线独立配置；系统 bind 后覆盖宽度、高度比例、Y 偏移和 RGBA |
| `DockLiquidGlassView` | `onWorkstationRecentsButton()` | `WorkstationWallpaperOnlyHook` 在工作台模式拦截该内部边界，阻止 Liquid full-display Recents 路径接管原生快照 |
| `com.miui.home.launcher.hotseats.HotSeats` | `setMingouStaticDockLiveBlurVisible(boolean)` | 工作台模式强制禁止切回 native live blur |
| `com.miui.home.launcher.hotseats.HotSeats` | `setMingouStaticDockSnapshotMode(boolean)` | 工作台模式强制保持 snapshot mode |
| `com.miui.home.launcher.laptop.AllAppsController` | `showAllApps(boolean)` | All Apps 切换前后刷新原生 wallpaper snapshot，并在稳定后再补一次 |
| `com.miui.home.launcher.laptop.AllAppsController` | `showWindow(boolean)` | 同上，覆盖另一条 All Apps 窗口切换路径 |
| `com.miui.home.launcher.Launcher` | `showOrHideRecent()` | Recents 切换前后刷新工作台原生 wallpaper snapshot |
| `com.miui.home.launcher.CellLayout` | `onLayout(boolean, int, int, int, int)` | 工作台 All Apps 原生布局完成后补齐被 `GridConfig` top/bottom slack clamp 掉的纵向偏移 |

工作台状态初值通过反射调用 `LauncherModeController.isLaptopMode()`；旧系统 fallback 读取 `DeviceConfig.isMingouLaptopPcModeEnabled()`。这两个 getter **只是调用，不是 Hook 点**。

工作台 wallpaper 刷新还会反射调用以下原生方法，它们同样不是额外 Hook：

- `requestMingouStaticDockBlurSnapshotIfNeeded(false)`
- `showMingouStaticDockBlurOverlayIfPossible()`
- `setMingouStaticDockSnapshotMode(true)`
- `setMingouStaticDockLiveBlurVisible(false)`

### Divider 独立配置

`DockDividerHook` 已与普通 Dock geometry 配置拆分。`LiquidDockConfig.Divider` 在配置层把历史 `0.1 dp` 存储单位规范化为真实 dp，并区分：

- **explicit mode**：存在 `dock_divider_enabled`，数值 `0` 也是有效配置；
- **legacy mode**：旧配置没有显式开关，沿用 `0 = 不覆盖系统默认值` 的旧语义。

Divider 不读取 `dock_dimensions_dp`，不会随普通 Dock 尺寸单位切换而变化。更详细参数见 `DIVIDER.md`。

## 桌面 8×4 / 4×8 网格

`HomeGridHook` 只有在 `home_grid_8x4` 开启时才安装以下网格 Hook；关闭时直接保留 MIUI 原生布局。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `LauncherCellCountCompatPadDevice` | `getCellCountXMin(Context)` / `getCellCountXDef(Context)` | 横屏返回 8 列，竖屏返回 4 列 |
| `LauncherCellCountCompatPadDevice` | `getCellCountYMin(Context)` / `getCellCountYDef(Context)` | 横屏返回 4 行，竖屏返回 8 行 |
| `com.miui.home.launcher.grid.GridConfig` | `setCountX(int)` / `setCountY(int)` | 把系统原本的 6 扩展为 8 |
| `com.miui.home.launcher.grid.GridConfig` | `getCountX()` / `getCountY()` | 读取时同样把 6 规范化为 8 |
| `com.miui.home.launcher.compat.LayoutTransformRuleGridChanged` | 所有构造函数 | 对 8×4 / 4×8 填入 8 个 2×2 block 的横竖屏坐标，并设置 `totalBlocks = 8` |
| `LayoutTransformRuleGridChanged` | `checkCellCount()` | 允许 8×4 / 4×8 通过系统网格校验 |
| `com.miui.home.launcher.CellLayout` | `calculateXsAndYs()` | 原方法前后应用自定义边距/间距并重建最终 `mXs/mYs`；避免 MIUI 在内部重新覆盖竖屏几何 |
| `com.miui.home.launcher.CellLayout` | `onLayout(boolean, int, int, int, int)` | 首次获得有效 bounds 时准备页面几何；原生布局后适配特定 2×1 widget |
| `com.miui.home.launcher.folder.FolderIcon1x1` | `onMeasure(int, int)` | 临时用 CellLayout 的真实 cell 高度重基准小文件夹测量，修正 8×4 下的垂直偏移 |
| `com.miui.home.launcher.Launcher` | `setupViews()` | 保存 `mWorkspace` 弱引用，并对所有 CellLayout 页面安排 0/180/500 ms 刷新 |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged(Configuration)` | 等待 Workspace 新方向尺寸连续稳定后刷新所有页面，避免使用旧方向 bounds 写入新网格 |
| `com.miui.home.launcher.ScreenView` | `updateIndicatorPositions(int, boolean)` | 只在实例实际为 `Workspace` 时保存系统基准 translation，再应用横/竖屏指示器 Y 偏移 |

### 不再 Hook 的占位方法

当前实现**刻意不 Hook** `addOccupied()` 和 `transformToHVArray()`。MIUI 自己维护 occupied matrix 的存储方向，8×4 / 4×8 下可能出现转置；在单 item 层猜测 `[x][y]` / `[y][x]` 会造成图标重叠或越界。

现在只扩展 `LayoutTransformRuleGridChanged` 的规则元数据，让 MIUI 原生 transform 继续负责占位矩阵。这也是旧 `HOOKS.md` 中最需要删除的一项。

## API 101 Hook / 反射工具层

所有 Hook 与反射统一经过 `HookUtil`，不再使用 `XposedHelpers` / `XposedBridge` shim：

- `HookUtil.hook(Method/Constructor, Hooker)`：调用 `Api101Bridge.module().hook(...).intercept(...)` 安装 API 101 interceptor；
- `HookUtil.hookMethod(...)`：按精确签名查找并 Hook；ClassLoader 版本支持类名/参数类名解析；
- `HookUtil.findMethodExact(...)`：沿父类链寻找精确签名，兼容方法声明在父类的 HyperOS 变体；
- `HookUtil.invoke(...)` / `invokeStatic(...)`：按参数 best-match 调用实例/静态方法；
- `HookUtil.getField/setField` 及 int/long/boolean 变体：沿父类链访问字段。

`Api101Bridge` 保存进程内 `XposedModule` 实例，并承接日志、Remote Preferences 等 API 101 桥接能力。

## 捕获与渲染架构（非 Hook）

Hook 负责提供“何时场景变化、何时需要重新捕获”的边界；真正的屏幕读取和玻璃绘制在普通 View/SurfaceControl 管线中完成。

- **场景状态**：`CaptureSceneState` 维护 HOME / APP / RECENTS、短时 gesture target 和 revision；`onPreDraw()` 只在观察变化时把 source 标脏，捕获请求是 one-shot + coalesced，而不是每个 pre-draw 都强制抓一帧。
- **捕获模式**：当前默认 `liquid_capture_fullscreen = true`。实际捕获首先由这个开关决定 full-display 还是 vendor wallpaper `captureMode(2)`，**不再是固定的“HOME → mode 2、APP/RECENTS → mode 1”映射**。full-display 且目标场景不是 HOME、并且不在 workstation 时，才要求排除 Dock window layer。关闭 fullscreen 开关时才退回 wallpaper mode 2。
- **动态捕获**：`LiveScreenCapture` 执行 SurfaceControl/Display readback；`CaptureCadence` 管理动态采样/探针节奏；black-frame guard、capture attempt token 与 scene revision 共同阻止黑帧或旧异步帧污染当前背景。
- **工作台**：Workstation 模式优先使用 HyperOS 原生 wallpaper snapshot，并阻止 LiquidDock 的工作台 Recents full-display 路径接管该背景。
- **玻璃渲染**：`DockLiquidGlassView` 使用 `RuntimeShader` 完成折射、模糊、色散、穹顶、高光等光学效果；`DockStrokeRenderer` 通过 foreground Drawable 绘制边框。
- **配置**：设置界面通过 LSPosed Remote Preferences（Binder IPC）同步；运行时由 `ConfigReader` / `LiquidDockConfig` 读取并统一处理默认值、兼容值和单位语义，不再使用旧的 `su` + JSON 文件方案。

实现细节以 `ModuleMain`、`MainHook`、`DockLiquidGlassView`、`LiveScreenCapture`、`CaptureSceneState`、`CaptureCadence`、`DockStrokeRenderer`、`HomeGridHook`、`RecentsHapticHook`、`DockDividerHook`、`WorkstationWallpaperOnlyHook`、`HookUtil` 和 `Api101Bridge` 为准。
