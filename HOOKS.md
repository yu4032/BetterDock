# LiquidDock 2.0 Hook 点总览

本文档记录 **当前 `main`（2.x）源码实际安装或尝试安装的主要 Hook 点**。它描述运行时事实，不描述 1.x 的历史架构，也不把普通反射调用误写成 Xposed Hook。

## 运行时边界

2.0 的 Xposed scope 只有：

```text
com.miui.home
```

`com.android.systemui` 已从 scope 删除，`ModuleMain` 也不会为 SystemUI / WMShell 安装任何 source/provider。

因此当前主线中不存在 1.x 的这些运行时链：

- SystemUI `ShellTaskOrganizer` task-state source；
- `MultiTaskingTaskRepository` HOME / APP ownership；
- `FreeformTaskListener` leash provider；
- SystemUI → Launcher transition / ownership Binder bridge；
- HOME / APP / RECENTS screenshot source selection；
- full-display screen capture / wallpaper capture fallback；
- capture freeze、capture cadence、black-frame retry 等截图状态机。

1.x 相关实现与文档语义应到 `archive/1.x` 查看，不应套用到 2.0。

---

## API 101 入口与安装顺序

入口为 `ModuleMain`。

`onPackageReady()` 只接受 `com.miui.home`，当前顺序是：

1. `LegacyConfigMigration.migrateAtProcessStart()`；
2. 读取一次 `LiquidDockConfig` runtime snapshot；
3. `new MainHook().install(classLoader)`；
4. `WorkspaceDropRuleHook.install(...)`，只有 master switch 与 custom grid 同时开启时才安装。

### 主开关边界

`MainHook.install()` 会在读取 `config.enabled` **之前**先安装 workstation mode guard。因此 master switch 关闭时，Launcher 主体功能不会继续安装，但 workstation mode guard 仍可能已经注册。

其余主要安装顺序为：

1. workstation mode guard；
2. `DockStrokeRenderer.installNativeHook()`；
3. workstation Dock icon / visible Dock geometry Hook；
4. Dock resize animation bypass（按配置）；
5. `DockDividerHook`；
6. `HomeGridHook`；
7. 如果开启 Liquid Glass，尝试 `Miuix307MaterialPipeline.install(...)`；
8. 307 material 安装成功后，`MainHook` 直接返回，不进入旧 renderer；
9. 如果 zero-copy material 不可用，则只在 Dock customization 开启时继续 native Dock customization 路径。

**2.0 没有 legacy glass fallback。**

---

## 307 Zero-copy Liquid Glass

### Material owner

`Miuix307MaterialPipeline` 识别两个当前支持的 vendor background：

```text
com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground
com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2
```

前者是当前 MiuiX material background；后者会在部分第三方主题/图标主题下成为实际 HotSeats background。

### 主要 Hook 点

| 目标 | Hook | 作用 |
|---|---|---|
| `com.miui.home.launcher.Launcher` | `setupViews()` | 取得 `mHotSeats` / `mWorkspace`，解析当前实际 background，并尝试把 zero-copy glass 绑定到真实 vendor hierarchy |
| `BlurUtilities` | `setBackgroundBlur(View, int, float[], int[][])` | 仅对受支持的 `BlurBackground2` material owner 把正的 vendor parent blur radius 压为 0，避免和 Prismal 重复合成 |
| `HotSeatsListContentMiuiXBlurBackground` | `setBackgroundWidth(int)` | 应用 307 Dock width offset，随后同步 glass size |
| 同上 | `setBackgroundHeight(int)` | 应用 height offset，随后同步 glass size |
| 同上 | `setBackgroundRadius(float)` | 应用 blur corner offset，并同步 glass geometry / optics |
| `HotSeatsListContentBlurBackground2` | `onAttachedToWindow()` | 主题背景实例 attach 时尝试重新绑定 glass |
| 同上 | `setBackgroundWidth(int)` | 同步主题背景 width 与 glass |
| 同上 | `setBackgroundHeight(int)` | 同步主题背景 height 与 glass |
| 同上 | `setBackgroundRadius(float)` | 同步主题背景 radius 与 glass |
| 同上及其父类 | 所有非 static `triggerMeasure(...)` overload | 主题背景真正完成测量/几何更新后重新同步 size / radius |

`Miuix307MaterialPipeline` 还通过 View attach/global-layout listener 观察 vendor hierarchy 被主题替换或 detach/reattach。这些是普通 Android listener，不是 Xposed Hook。

### `MiuixGlassHook` 的职责

`MiuixGlassHook` 本身不是新的场景状态机，它负责把当前 vendor background 转成 zero-copy glass 容器：

- vendor background 继续作为 Dock 几何 authority；
- vendor material body 被替换为透明 `GradientDrawable`；
- vendor parent GPU blur 被抑制；
- 在 vendor background 内加入 `DockLiquidGlassHostView`；
- host 内加入 `Miuix307PassBlurTextureView`；
- Dock radius / size 改变时同步 host、Prismal 参数和 foreground stroke；
- zero-copy 激活失败或验证超时只保持透明，不创建截图 renderer。

`DockLiquidGlassHostView` 只是轻量 geometry / clip host：最终按当前 Dock path 裁切 `TextureView`，不承担截图、RuntimeShader backdrop 或 CPU blur。

---

## SurfaceFlinger PassBlur：反射调用，不是 Hook

下面这些调用对 2.0 glass 很关键，但它们是对系统隐藏 API 的**反射调用**，不是 libxposed interceptor：

| 对象 | 调用 | 作用 |
|---|---|---|
| `View` / `ViewRootImpl` | `getViewRootImpl()` / `getSurfaceControl()` | 找到 Floating Dock / HotSeats 所在 root `SurfaceControl` |
| `SurfaceControl.Transaction` | `SetPassBlurSurface(SurfaceControl, Surface)` | 把 SurfaceFlinger PassBlur 输出绑定到 LiquidDock 提供的 producer `Surface` |
| 同上 | `setUpdateTextureFlag(SurfaceControl, boolean, float)` | 启用/关闭 PassBlur texture 更新 |
| 同上 | `setMiBlurWinExc(SurfaceControl, String[])` | 设置 compositor exclusion 名单，避免输出再次采样自身或系统栏层 |

当前 exclusion 包含 root surface 自身以及：

```text
NavigationBar
StatusBar
GestureStub
DockAssistantView
```

PassBlur producer 保持 full-resolution scale；最终 `TextureView` 位于已经排除的 Floating Dock root 内，因此不需要再做旧截图管线的 child-layer exclusion 管理。

---

## GPU 渲染阶段

`Miuix307PassBlurTextureView` 的渲染线程维护 EGL / OES / FBO 生命周期，当前数据流是：

### Stage A — OES → Dock-local RGBA

SurfaceFlinger PassBlur 写入 caller-owned `Surface`，对应 `SurfaceTexture` 作为 external OES texture 输入。

normalize shader 负责：

- OES texture matrix；
- producer surface / buffer geometry；
- config rotation；
- Dock 到 producer 的 Stage-B 坐标映射；
- FULL / PARTIAL / OUTSIDE coverage；
- partial coverage 的 mirror guard band，避免超出 producer 有效区域后留下透明黑边。

### Stage B — Gaussian blur

Dock-local RGBA 进入半分辨率 blur FBO：

```text
rawFramebuffer
  ↓ horizontal Gaussian
blurFramebufferH
  ↓ vertical Gaussian
blurFramebufferV
```

`BLUR_FBO_SCALE = 0.5f`。

### Stage C — Prismal

Prismal shader 只处理普通 2D texture，不直接理解 OES / SurfaceFlinger producer geometry。折射、色散、Fresnel、dome、specular、rim、caustics、vibrancy、tint 等参数由 `Miuix307PrismalMaterial` 统一映射。

像素数据全程留在 GPU buffer 中；当前 zero-copy glass 不调用 `captureScreenAsync`、Bitmap readback 或 `glReadPixels`。

---

## 307 Glass 的 fail-closed 行为

当前失败路径有意保持简单：

```text
supported vendor material
        ↓
PassBlur/OES activation succeeds ──→ Prismal glass
        │
        └─ fails / times out ──────→ transparent glass
```

不会变成：

```text
PassBlur fails → screenshot renderer
```

如果 `Miuix307MaterialPipeline` 连受支持的 background class 都找不到，`MainHook` 会把 Liquid Glass 视为不可用；如果普通 Dock customization 仍开启，则可以继续安装 native Dock 自定义 Hook，但不会恢复旧 glass renderer。

---

## 普通 / Native Dock 自定义 Hook

以下路径主要用于未由 307 zero-copy material 接管的 native Dock customization。

| 目标类 | 方法 | 当前作用 |
|---|---|---|
| `HotSeatsListContentBlurBackground2` | `setBackgroundWidth(int)` | 普通 Dock width offset，并同步 shadow geometry |
| 同上 | `setBackgroundHeight(int)` | height offset，并同步 shadow geometry |
| 同上 | `setBackgroundRadius(float)` | blur radius basis / stroke radius / squircle geometry 同步 |
| 同上 | `updateBackgroundSize(int,int,float)` | 按配置结束 vendor resize animator；可由 LiquidDock 重新做短时 smooth geometry animation |
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | 普通 Dock icon spacing；workstation 开启时也可叠加 workstation top/bottom offset |
| `HotSeatsListContentLayoutManager` | `updateBackgroundView(FrameLayout,int,int,float)` | spacing 改变后补偿 Dock background width |
| `DeviceConfig` | `getHotSeatsMarginBottom()` | 普通 Dock bottom offset |
| `BlurUtilities` | `setBackgroundBlur(...)` | native 路径覆盖系统 Dock blur radius |
| `HotSeats` | `getMingouStaticDockBlurShadowTarget()` | 记录系统原生 Dock shadow target |
| `MiShadowUtils` | `applyViewShadow(...)` | 只对已确认的原生 Dock shadow target 清除 vendor shadow，避免和 LiquidDock 独立 shadow 重叠 |
| `Launcher` | `setupViews()` | native 路径初始化 background 引用和可选独立 Dock shadow |

### Dock stroke

`DockStrokeRenderer.installNativeHook()` Hook：

```text
HotSeatsListContentBlurBackground2.setBackgroundRadius(float)
```

native blur Dock 直接使用 background foreground 绘制 border。307 in-place glass 则由 material/geometry 同步路径直接调用 `configureReplacingForeground(...)`，不需要再创建历史上的独立 stroke overlay renderer。

边框绘制使用 outer path + inner path：先 clip 到 outer，再 `clipOutPath(inner)` 排除 Dock 中心。内部 contour 在动画瞬间无效时宁可跳过该帧，也不会降级成整块填充。

---

## Workstation / Laptop 模式 Hook

工作台状态完全在 Launcher 进程内判断，不依赖 SystemUI。

### 状态 authority

优先读取：

```text
LauncherModeController.isLaptopMode()
```

并 Hook：

```text
LaptopStateManager.onLaptopModeChanged(boolean)
```

如果当前 API 不存在，则回退到 Launcher 内的旧接口：

```text
DeviceConfig.isMingouLaptopPcModeEnabled()
DeviceConfig.setMingouLaptopPcModeEnabled(boolean)   // Hook
```

workstation mode 改变后会同步 `HomeGridHook` 与 `WorkstationDockGeometryHook`，并在进入/退出时备份或恢复普通桌面 item position。

### Workstation Dock

| 目标 | Hook | 作用 |
|---|---|---|
| `HotSeatsListContentLayoutManager$OffsetDecoration` | `getItemOffsets(...)` | workstation 模式下增加 Dock icon top / bottom offset |
| `HotSeatsListContentAdapter$LineViewHolder` | `bindView()` | `WorkstationDockGeometryHook` 从 divider anchor 向父级寻找实际 `DockContainer`，对可见 laptop Dock 应用独立 width offset |

普通 HotSeats background 在 workstation 模式下会隐藏；可见 capsule 由独立 laptop `DockContainer` 管理，因此 workstation width 不通过普通 HotSeats blur background 强行实现。

---

## Dock Divider Hook

`DockDividerHook` Hook：

```text
HotSeatsListContentAdapter$LineViewHolder.bindView()
```

从 holder 取得 divider content View，并按 Remote Preferences 应用：

- width；
- 相对父容器高度的 height percent；
- Y offset；
- RGBA color。

首个 RecyclerView bind 发生在父容器还没有有效高度时，会注册一次普通 `OnLayoutChangeListener` 延迟补几何；这个 listener 不是 Xposed Hook。

---

## 8×4 / 4×8 Home Grid

`HomeGridHook` 只有在 custom grid 开启时安装布局 Hook；关闭时保持 MIUI stock grid，不改 CellLayout / indicator / folder measurement。

主要 Hook 范围包括：

- `LauncherCellCountCompatPadDevice` 的 X/Y min/default cell count；
- `GridConfig` count X/Y getter / setter；
- `CellLayout.calculateXsAndYs()`；
- `CellLayout.setupLayoutParam(...)`；
- `CellLayout.onLayout(...)`；
- `FolderIcon1x1.onMeasure(...)`；
- orientation transform、workspace refresh、页面 indicator 和旋转后的 geometry refresh 相关 Launcher 方法。

关键职责包括：

- 横屏 8×4 / 竖屏 4×8 count；
- 横竖屏独立 padding / row gap；
- 重新构建 `mXs` / `mYs`；
- widget span 和最终 frame 适配；
- lazy/off-screen page 首次得到有效 bounds 时补 geometry；
- 小文件夹在新 cell size 下重新对齐；
- workstation 桌面 / All Apps 独立偏移。

### Workspace drop rule

`WorkspaceDropRuleHook` 仅在 master switch + custom grid 都开启时 Hook：

```text
com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces
    .isLegalXY(int, int, int, int)
```

返回 `true` 的目的只是移除 MIUI stock 6-column swap-placement pattern 限制。

它**不会**替换 `GridOccupancyController`：边界、occupied cells、vacancy search 和实际 placement 仍由 Launcher 原逻辑负责。

---

## 当前不应再加入 HOOKS.md 的 1.x 内容

维护 2.0 文档时，下列概念除非明确标成历史说明，否则不应重新写回“当前 Hook 点”：

- `com.android.systemui` scope；
- `ShellTaskOrganizer` / WMShell transition observer；
- HOME / APP ownership provider；
- freeform leash snapshot；
- `CaptureSceneState`；
- wallpaper / full-display capture source policy；
- `LiveScreenCapture`；
- Bitmap / HardwareBuffer screenshot renderer；
- Recents pre-arm / capture hold / capture cadence；
- SystemUI 控制中心展开对 screenshot capture 的 gating。

2.0 glass 的判断原则应保持为：

> **只维护“GPU 管线现在能否正确绑定并绘制”的状态，不维护“现在应该截什么”的状态。**

---

## 源码事实来源

Hook 行为发生变化时，优先以这些文件为准：

```text
src/main/resources/META-INF/xposed/scope.list
src/main/java/com/hellovoid/liquiddock/ModuleMain.java
src/main/java/com/hellovoid/liquiddock/MainHook.java
src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java
src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java
src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java
src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java
src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java
src/main/java/com/hellovoid/liquiddock/HomeGridHook.java
src/main/java/com/hellovoid/liquiddock/WorkspaceDropRuleHook.java
src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java
src/main/java/com/hellovoid/liquiddock/DockDividerHook.java
src/main/java/com/hellovoid/liquiddock/WorkstationDockGeometryHook.java
```
