# 大文件夹 / 小组件 Liquid Glass 可行性探索

分支：`explore/folder-widget-liquid-glass`

该探索基于当前 `main`，与 workstation Dock 恢复 PR 独立。

## 目标

分别提供两类独立开关：

- 大文件夹 Liquid Glass
- 小组件 Liquid Glass

目标效果若定义为“完整 LiquidDock Prismal”则包含实时 backdrop、Gaussian blur、折射、色散、Fresnel / specular / tint 等；若仅使用系统 `BackgroundBlurDrawable`，只能得到原生模糊/着色，不能等价于完整 Prismal 折射。

## 当前进度（2026-08-20）

已实现探索顺序中的步骤 1 和 2：

1. `FolderWidgetGlassProbe`：只读记录大文件夹以及 `LauncherAppWidgetHostView` / `MaMlHostView` 的 class、parent、attach/detach、尺寸、screen rect 和可解析的圆角信息，不改变视觉状态。
2. `LargeFolderNativeBlurPrototype`：对已识别的大文件夹在 stock plate 下方插入 `BackgroundBlurDrawable` 原型层；编辑模式、打开文件夹、detach 或 hidden-API 失败时恢复 stock plate。该 prototype 不创建新的 PassBlur producer。

步骤 1/2 目前仅完成代码与 CI 构建验证，尚未完成目标设备行为验证。拖拽态目前由 probe 观察 attach/detach 与几何变化，尚未单独给 `DragView` 建原生 blur；是否需要专门的 drag-preview fallback 由本轮设备日志决定。

## 1. 大文件夹

### 已确认的宿主结构

公开的当前 HyperCeiler 适配代码对 MIUI Home 大文件夹采用以下入口：

- `com.miui.home.launcher.FolderIcon`
- `com.miui.home.launcher.folder.FolderIcon2x2`
- Pad 变体包括 `FolderIcon4x4_16`、`FolderIcon3x3_9`
- 其他变体还包括 `FolderIcon2x2_4` / `FolderIcon2x2_9` / `FolderIcon1x2` 等

当前 steps 1/2 的 probe/prototype 实际尝试覆盖 `FolderIcon4x4_16`、`FolderIcon3x3_9`、`FolderIcon2x2_4`、`FolderIcon2x2_9` 和 `FolderIcon2x2`；缺失类按 fail-open 方式仅记录 unavailable，不影响 Launcher 启动。

在 `onFinishInflate()` 后可取得 `mIconImageView`，其 parent 是可插入背景层的 `FrameLayout`。现成做法会隐藏 stock `mIconImageView`，并在 parent index 0 插入自定义 background view。因此“大文件夹背景层”本身是高可行性的明确 hook 点。

需要同步的生命周期至少包括：

- Launcher 编辑模式
- openFolder / closeFolder
- Launcher state 切换
- DragView / 拖拽态
- attach / detach

建议 LiquidDock 不复制第三方实现，只复用上述已确认的 MIUI Home owner / lifecycle 事实，并自行实现最小 hook。

## 2. 小组件

### 两类宿主必须分别覆盖

当前 MIUI Home 至少存在两条 widget host 路径：

1. `com.miui.home.launcher.LauncherAppWidgetHostView`：标准 RemoteViews/AppWidget 路径。
2. `com.miui.home.launcher.maml.MaMlHostView`：MIUI/MAML 小组件路径。

公开代码显示两者都提供 `computeRoundedCornerRadius()`；这可以作为玻璃 clip/radius 的首选 authority，而不是硬编码圆角。

目标设备日志进一步确认 MAML 路径正在实际使用，并出现：

- `Launcher.BlurUtilities: setMaMlBlurIfSupported: isWidgetBlurSupported() = true supportBackgroundBlur = false`
- `MaMlHostView:init: supportBackgroundBlur = false`
- `MaMlHostView: applyPath: <width> <height>`

因此 MAML host 自己已经存在“是否支持背景模糊”和 path clip 概念。第一版 probe 应优先记录这些字段/方法，而不是直接覆盖 MAML 内容树。

### widget 的 UI 语义

不能默认删除所有 widget 自己的背景。推荐区分：

- glass-behind-content：在 widget 内容下方放玻璃，透明区域可见；风险最低。
- replace-known-host-background：只对已确认的 MIUI/MAML stock background 做替换。
- 不应对未知第三方 RemoteViews 强制清空其内部背景。

## 3. 当前 Dock PassBlur 不能按实例复制

这是本次 Spike 最重要的架构约束。

当前 `Miuix307PassBlurBridge.bind()` 从 material host 获取 `ViewRootImpl` / root `SurfaceControl`，然后调用：

```text
SurfaceControl.Transaction.SetPassBlurSurface(rootSurface, producerSurface)
setUpdateTextureFlag(rootSurface, true, scale)
```

解绑则对同一个 root 调：

```text
SetPassBlurSurface(rootSurface, null)
```

也就是说当前 source 是以 Launcher root `SurfaceControl` 为绑定对象，不是以每个文件夹/小组件 View 为绑定对象。

因此禁止采用：

```text
Folder A -> 独立 PassBlur producer
Folder B -> 独立 PassBlur producer
Widget A -> 独立 PassBlur producer
Widget B -> 独立 PassBlur producer
```

这些实例共享 Launcher root，会竞争/覆盖同一个 PassBlur source；同时会显著放大 producer lifecycle / Binder 风险。

## 4. 推荐长期架构：Shared Backdrop Source + Multi Target

完整 Prismal 的正确方向是把当前 `Miuix307PassBlurTextureView` 中“backdrop source”和“单个输出 target”拆开。

### Shared source（每个 ViewRoot 一份）

概念类：`Miuix307SharedBackdropSource`

职责：

- 唯一 PassBlur producer `Surface`
- 唯一 input `SurfaceTexture` / OES texture
- root `SurfaceControl` bind/unbind/rebind
- root replacement lifecycle
- 一套 render thread / EGL context
- 新 OES frame 通知多个 target

### Target（每个可见玻璃对象一份）

概念类：`Miuix307GlassTarget`

职责：

- 目标 View / screen rect
- clip path / corner radius
- target geometry
- material parameters
- visibility / attach lifecycle
- output EGL surface 或共享 compositor 中的目标区域

一帧 backdrop 到达后，由共享 renderer 依次把同一 OES backdrop 按每个 target 的 screen rect 映射并渲染到对应 target。

### Dock 也必须最终成为同一个 source 的 target

如果 Dock 继续拥有当前独立 `SetPassBlurSurface(root, dockProducer)`，而 folder/widget 再启动第二个 shared producer，它们仍然会争同一个 Launcher root。

因此完整方案最终应为：

```text
Launcher root
    -> 1 x PassBlur producer / OES backdrop source
       -> Dock target
       -> Folder target(s)
       -> Widget target(s)
```

这是 full Prismal 与 Dock 共存时的必要结构。

## 5. 可选的低风险过渡方案

如果目标是先验证 UI / 生命周期而不是立刻做到完整折射，可先用 Launcher `ViewRootImpl.createBackgroundBlurDrawable()` / MIUI BlurUtilities 为 folder/widget 提供原生 background blur，再叠加 LiquidDock 的 tint / stroke / highlight 风格。

优点：

- 不新增 PassBlur producer
- 多实例天然成立
- 不与当前 Dock producer 竞争
- 可先把 folder/widget owner、clip、drag/edit/open lifecycle 验证完整

缺点：

- 不是完整 Prismal
- 没有真实 backdrop texture 输入到 LiquidDock shader，因此无法等价实现折射/色散

## 6. 推荐实现顺序

1. **Probe-only commit**：只 hook/记录大文件夹和两类 widget host 的 class、parent、size、radius、attach/detach、screen rect，不改视觉。 **已实现，待设备验证。**
2. **大文件夹 native-blur prototype**：仅一个背景 target，验证编辑/打开/拖拽生命周期。 **已实现基础 prototype，编辑/打开/失败 fallback 已编码；拖拽行为待设备验证。**
3. **widget native-blur prototype**：同时覆盖 `LauncherAppWidgetHostView` 和 `MaMlHostView`，默认 glass-behind-content。
4. **Shared backdrop source prototype**：先只注册一个 folder + 一个 widget target，验证一 source 多 target 的 geometry/OES mapping。
5. **Dock source migration**：把 Dock 改成同一 shared source 的 target；这一步完成后才允许 full Prismal folder/widget 与 Dock 同时启用。
6. 再加入设置项：大文件夹 / 小组件两个独立开关与各自 material 参数。

## 结论

- 大文件夹：**高可行性**，背景 owner / 插入层和 lifecycle 已有明确证据。
- 小组件：**高可行性但需要双宿主覆盖**，MAML 与标准 AppWidget 必须分别处理。
- 原生 blur 版本：可以低风险快速实现多实例。
- 完整 LiquidDock Prismal：**可行，但必须先把 PassBlur 改成 root-scoped shared source + multi-target renderer**。
- 不建议为每个 folder/widget 创建独立 `Miuix307PassBlurTextureView` / PassBlur producer。
