# Hook 点总览

LiquidDock 是一个注入 `com.miui.home` 的 LSPosed 模块，通过 libxposed API 101 原生语法在运行时调整桌面与 Dock 的外观和行为。本文档按功能域列出模块所 hook 的全部类与方法，以及每个 hook 的作用。Hook 的具体实现以源码为准。

## 场景判定与生命周期

桌面与应用场景的区分是模块所有行为的起点：壁纸捕获、动态捕获、玻璃渲染都依赖这一判定链。

| 目标类 | 方法 | 作用 |
|--------|------|------|
| `com.miui.home.launcher.Launcher` | `setupViews` | Dock 视图树初始化完成点，在此注入玻璃与描边 overlay |
| `com.miui.home.launcher.Launcher` | `onWindowFocusChanged(boolean)` | 窗口焦点变化——桌面聚焦/失焦的核心信号，驱动场景切换 |
| `com.miui.home.launcher.Launcher` | `onResume` / `onPause` | 前台状态跟踪（`launcherResumed`），参与场景判定 |
| `com.miui.home.launcher.Launcher` | `onStart` / `onStop` | 窗口可见性跟踪（launcher 是否被全屏应用覆盖） |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged` | 横竖屏与配置变化，触发几何参数重算 |
| `android.app.Activity` | `onWindowVisibilityChanged(int)` | 通用窗口可见性（与 launcher 可见性交叉验证） |
| `android.app.Activity` | `onResume` / `onPause` | 前台应用判定（非 launcher 的 Activity 生命周期） |

场景判定通过 `CaptureSceneState` 状态机实现（HOME / APP / RECENTS），`onPreDraw` 每帧触发
`updateDesiredScene()` 检测场景转变，不依赖轮循。进入多任务（RECENTS）通过触觉/手势预触发
（`prearmRecentsCapture`），返回桌面（RECENTS→HOME）通过 `scene-settle-home` 立即捕获。

## Dock 外观

Dock 背景的尺寸、圆角、模糊与阴影均通过 Hook 调整，描边 overlay 以 Hook 到的背景几何为基准绘制。

| 目标类 | 方法 | 作用 |
|--------|------|------|
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 读取/校正 Dock 背景宽度（描边对齐基准） |
| `HotSeatsListContentBlurBackground2` | `setBackgroundHeight(int)` | 背景高度 |
| `HotSeatsListContentBlurBackground2` | `setBackgroundRadius(float)` | 背景圆角（描边圆角 = 系统圆角 + 偏移） |
| `HotSeatsListContentBlurBackground2` | `setBackgroundBlur(View, int, float[], int[][])` | 原生模糊参数（radius 版本） |
| `HotSeatsListContentBlurBackground2` 的 `LayoutManager` | `updateBackgroundView` | 背景视图更新时机，同步 overlay 几何 |
| `HotSeats` | `setBackgroundWidth/Height/Radius` | 桌面 Dock 主背景的尺寸与圆角 |
| `BlurUtilities` 类 | `setBackgroundBlur(...)` | 原生模糊另一入口（float 版本） |
| 阴影工具类 | `applyViewShadow(View, int, ...)` | Dock 与描边的投影参数（柔化/扩散/透明度/偏移） |
| 设备配置类 | `getHotSeatsMarginBottom` | 读取底部边距，参与 Dock 几何计算 |
| 设备配置类 | `setControlPanelExpanded` | 控制中心展开状态（影响捕获门控） |

## 壁纸行为

桌面壁纸保持静止：滚动与缩放被拦截，使捕获到的壁纸条带长期可复用。

| 目标类 | 方法 | 作用 |
|--------|------|------|
| `android.app.WallpaperManager` | `setWallpaperOffsets` | 拦截壁纸横向滚动 |
| `android.app.WallpaperManager` | `setDisplayOffset` | 拦截壁纸位移 |
| `android.app.WallpaperManager` | `setWallpaperZoomOut` | 拦截壁纸缩放 |

## 拖拽与多任务

| 目标类 | 方法 | 作用 |
|--------|------|------|
| `DockContainer` | `startDrag` / `endDrag` | 拖拽会话跟踪——Dock 几何运动期间激活高频动态采样。通过 `installDockDragHooks` 进程级一次性安装，回调内动态读取 `liquidGlassView` 避免 View 泄漏 |
| 设备状态类 | `onLaptopModeChanged` | 笔记本/平板模式切换（工作站场景） |
| `RecentsView` 相关类 | `performEnterRecent(View)` | 进入最近任务时的触觉反馈行为。通过 `HookUtil.findMethodExact` 沿父类查找，兼容 HyperOS 继承链 |
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | 工作台 Dock 图标分隔竖线的属性调整（宽度、高度、颜色、透明度、偏移） |

## 桌面网格

| 目标类 | 方法 | 作用 |
|--------|------|------|
| `com.miui.home.launcher.Launcher` | `setupViews`（网格路径） | 网格初始化入口，缓存 Workspace 弱引用并触发全页刷新 |
| `Folder` | `onMeasure` | 文件夹测量（网格尺寸参与） |
| `CellLayout` | `calculateXsAndYs` | 图标行列坐标计算——8x4/4x8 布局的核心，前后各应用一次自定义几何 |
| `CellLayout` | `onLayout` | 布局阶段，应用行列偏移 |
| `com.miui.home.launcher.Launcher` | `onConfigurationChanged` | 配置变化时网格重算；等待 Workspace 尺寸与方向匹配后刷新 |
| `Workspace`（screenView） | `updateIndicatorPositions` | 指示器位置 |
| `LayoutTransformRuleGridChanged` | 构造函数 | 8x4/4x8 旋转变换规则：注入竖屏/横屏屏幕坐标映射与 totalBlocks |
| `LayoutTransformRuleGridChanged` | `checkCellCount` | 行列数校验（8x4/4x8 直接放行，绕过原生校验） |
| 网格配置类 | 行列数 getter/setter | 把 6 列改写成 8 列（`hookGridCountSetter/Getter`） |
| 设备兼容类 | `getCellCountX/YMin/Def` | 旋转时返回方向相关行列数：竖屏 4x8 / 横屏 8x4（`hookAxis`） |

> 旋转图标偏移修复（v1.1.0）：旋转后 Workspace 尺寸延迟更新，早前 `getCellLayout(int)`
> 在此 HyperOS 构建返回 null 导致错误几何未被覆盖。现改为 `sizeMatchesOrientation`
> 等待尺寸与方向匹配，再经 `collectWorkspaceCellLayouts` 递归遍历真实 CellLayout 后代重算。
> 已删除 `addOccupied` / `transformToHVArray` hook（8x4/4x8 矩阵转置方向不安全）。

## 反射工具层

所有反射调用统一收归 `HookUtil`，不再使用 `XposedHelpers`：

- `HookUtil.findMethodExact(Class, String, Class...)` — 沿父类链查找方法（替代 `getDeclaredMethod` 的不完整查找），确保 HyperOS 中继承而来的方法也能被 hook
- `HookUtil.invoke(Object, String, Object...)` — 实例方法调用
- `HookUtil.invokeStatic(String className, String, Object...)` — 静态方法调用
- `HookUtil.getField/setField` — 字段访问

`Api101Bridge` 提供进程内 libxposed 桥接（日志、配置、资源）。

## 捕获与渲染架构（非 Hook）

背景捕获与玻璃渲染不依赖 Hook，而是通过 SurfaceControl/Display 捕获与 RuntimeShader 完成：

- **捕获管线**：`LiveScreenCapture` 负责屏幕/壁纸层捕获（桌面壁纸条带缓存复用；应用/多任务前台时全屏捕获），`CaptureCadence` 控制采样节奏（高频动态采样与静态低频探针），`CaptureSceneState` 维护场景状态机（桌面 / 应用 / 最近任务）。捕获模式由 `CaptureScene` 单一判定：HOME → mode 2（壁纸），APP/RECENTS → mode 1（全屏+Dock 排除）
- **场景检测**：`onPreDraw` 每帧触发 `updateDesiredScene()`（不轮循），RECENTS→HOME 立即 `scene-settle-home` 捕获。进入多任务时 `prearmRecentsCapture` 强制取消进行中的捕获以确保场景切换不被管线合并丢失
- **渲染**：`DockLiquidGlassView` 用 RuntimeShader 实现液态玻璃光学（折射、高光、色散、穹顶），Shader 内高斯模糊叠加在捕获壁纸上
- **配置**：设置界面通过 LSPosed Remote Preferences (Binder IPC) 同步，模块侧 `LiquidDockConfig` 读取。不再使用 `su`/JSON 文件方案

各 hook 的触发时机与参数细节请直接阅读 `MainHook`、`HomeGridHook`、`RecentsHapticHook`、`DockDividerHook`、`WorkstationWallpaperOnlyHook`、`HookUtil`、`Api101Bridge` 文件。
