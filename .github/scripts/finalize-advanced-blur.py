from pathlib import Path


def rep(path, old, new):
    p = Path(path)
    s = p.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{path}: expected one match, got {n}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1))


# Lifecycle: a View can detach/re-attach when the Floating Dock window is rebuilt.
rep(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    '''    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        captureGeneration++;''',
    '''    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL
                && !advancedMaterialUnavailableForProcess
                && !advancedMaterialActive) {
            updateBlurBackend();
        }
        captureGeneration++;''',
)
rep(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    '''    @Override protected void onDetachedFromWindow() {
        attached = false;
        MiBlurBridge.clearContentBlur(this);
        cancelPendingCaptureWork();''',
    '''    @Override protected void onDetachedFromWindow() {
        attached = false;
        MiBlurBridge.clearContentBlur(this);
        advancedMaterialActive = false;
        activeBlurBackend = LiquidBlurMode.SHADER;
        cancelPendingCaptureWork();''',
)

# README
rep(
    "README.md",
    "- **液态玻璃 Dock**：RuntimeShader 实现模糊、折射、色散、穹顶、高光与焦散",
    "- **液态玻璃 Dock**：可选标准 RuntimeShader 模糊或 HyperOS SurfaceFlinger 高级材质 self-blur；折射、色散、穹顶与光学效果继续由 LiquidDock 渲染",
)
rep(
    "README.md",
    "- **前景描边**：`DockStrokeRenderer` 直接安装到宿主 View foreground，不再创建独立描边 overlay / RenderNode",
    "- **前景描边**：原生 blur Dock 继续使用 foreground `DockStrokeRenderer`；Liquid Glass 使用独立锐利 overlay，使描边/Canvas 高光不进入 self-blur",
)
rep(
    "README.md",
    "功能参数见 [FEATURES.md](FEATURES.md)，当前实际 Hook 点见 [HOOKS.md](HOOKS.md)，配置与模块边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。\n\n## 配置架构",
    """功能参数见 [FEATURES.md](FEATURES.md)，当前实际 Hook 点见 [HOOKS.md](HOOKS.md)，配置与模块边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。

### Liquid Glass 模糊后端

`liquid_blur_mode` 提供 `shader`（兼容默认）和 `advanced_material` 两种后端。高级材质模式直接反射 `View.setMiSelfBlur(...)` / `setPassTextureScale(...)` 驱动 HyperOS SurfaceFlinger self-blur；运行时能力检测失败时只在当前 Launcher 进程回退到 Shader，不改写用户保存的模式。

高级模式把玻璃主体、锐利高光/描边和最终形状裁剪分层：`DockLiquidGlassView` 保持矩形 RenderNode 进入 self-blur，`DockLiquidGlassHostView` 在合成后裁回 Dock 圆角/方圆轮廓，`DockStrokeOverlayView` 在最上层绘制 Canvas 高光和可配置描边。这样避免 self-blur 前的圆角裁剪造成角落采样缺失，同时不让描边和高光被内容模糊。

## 配置架构""",
)

# FEATURES
rep(
    "FEATURES.md",
    "当前描边由 `DockStrokeRenderer` 直接安装到 native blur Dock 或 Liquid Glass View 的 foreground。",
    "native blur Dock 的描边仍由 `DockStrokeRenderer` 直接安装到 foreground；Liquid Glass 则把 `DockStrokeRenderer` 放到独立的 `DockStrokeOverlayView` foreground，以避免高级材质 self-blur 模糊描边。",
)
rep(
    "FEATURES.md",
    "| 玻璃模糊 | 0 ~ 60 dp | 捕获背景的模糊范围 |",
    """| 模糊方式 | Shader / 高级材质 | `liquid_blur_mode`；默认 `shader`，高级材质不可用时运行时安全回退 |
| 玻璃模糊 | 0 ~ 60 dp | Shader 模式为采样模糊范围；高级材质模式映射为 MIUI self-blur 半径 |""",
)
rep(
    "FEATURES.md",
    "### 颜色、高光与边缘",
    """### 模糊后端与分层

- **标准 Shader 模糊**：保留原 40-sample kernel，是旧配置/新安装的兼容默认。
- **高级材质模糊**：直接调用 `View.setMiSelfBlur`、`setPassTextureScale(0.5)` 和 self-blur enhance flag，由 SurfaceFlinger 执行内容模糊；不依赖 `HyperMaterialUtils.isEnable()`。
- 高级材质调用失败时，当前 Launcher 进程回退到标准 Shader；`liquid_blur_mode` 不会被运行时代码改写。
- `DockLiquidGlassHostView` 在 self-blur 合成后做最终 round/squircle clip；self-blurred `DockLiquidGlassView` 不先裁圆角，避免左上等圆角区域因输入像素被提前裁掉而出现未模糊缺口。
- Canvas 高光和可配置 Dock stroke 位于 `DockStrokeOverlayView`，不参与 self-blur，因此保持锐利。

### 颜色、高光与边缘""",
)

# HOOKS
rep(
    "HOOKS.md",
    "| `com.miui.home.launcher.Launcher` | `setupViews()` | Dock 视图树完成后创建/绑定 Liquid Glass 与可选整体 shadow；保存原生背景引用 |",
    "| `com.miui.home.launcher.Launcher` | `setupViews()` | Dock 视图树完成后通过共享 assembly 创建 `DockLiquidGlassHostView`（glass body + sharp overlay）与可选整体 shadow；保存原生背景引用 |",
)
rep(
    "HOOKS.md",
    """## 描边：当前 foreground 实现

`DockStrokeRenderer` 已取代旧的独立描边 overlay：

- Hook `HotSeatsListContentBlurBackground2.setBackgroundRadius(float)`；
- native blur Dock 和 `DockLiquidGlassView` 复用同一 foreground renderer；
- `StrokeDrawable` 构造 outer/inner path；
- 通过 `clipPath(outer)` + `clipOutPath(inner)` 从几何上排除 Dock 中心；
- 不使用独立 overlay View / RenderNode；
- 不把可配置 Dock border 画成普通 `Paint.Style.STROKE`。
""",
    """## 描边与 Liquid Glass 分层

`DockStrokeRenderer` 仍是唯一的可配置边框 renderer，但宿主因渲染后端而不同：

- native blur Dock：继续安装到系统背景 View foreground；
- Liquid Glass：安装到独立 `DockStrokeOverlayView` foreground，overlay 与 Canvas 高光位于 self-blurred glass body 之上；
- `StrokeDrawable` 仍构造 outer/inner path，并用 `clipPath(outer)` + `clipOutPath(inner)` 从几何上排除 Dock 中心；
- 不恢复旧的独立 RenderNode/布局动画描边实现；Liquid Glass overlay 只承担锐利视觉层，不改变 Dock/icon LayoutParams；
- 不把可配置 Dock border 退化为普通 `Paint.Style.STROKE`。

### Liquid Glass 高级材质模糊

`liquid_blur_mode=advanced_material` 时，`MiBlurBridge` 缓存并直接反射 `View.setMiSelfBlur(int, ArrayList)`、`setPassTextureScale(float)` 与 `setMiSelfBlurEnhanceFlag(int,int)`。成功后 `DockLiquidGlassView` 把 `shaderBlurEnabled=0`，原 `blurred()` 直接返回 `source()`，由 SurfaceFlinger self-blur 接管模糊；任一能力调用失败则 active backend 回到 Shader，但持久化模式不变。

最终裁剪由 `DockLiquidGlassHostView.dispatchDraw()` 完成。高级模式下 glass child 自身保持矩形、不使用 `clipToOutline`，使圆角外但仍位于矩形 RenderNode 内的像素可以参与 self-blur，再由 host clip 回 round/squircle 形状。这是对实验中左上角模糊缺口的结构性修复。
""",
)

# ARCHITECTURE
rep(
    "ARCHITECTURE.md",
    """- `DockStrokeRenderer` — shared foreground border renderer for native blur Dock and Liquid Glass.
- `LiquidGlassFactory` — central construction/configuration of `DockLiquidGlassView`.
- `DockLiquidGlassView` — current View + capture lifecycle + recovery + dynamic detection + shader rendering owner.""",
    """- `DockStrokeRenderer` — shared configurable border renderer; native blur Dock uses it directly, Liquid Glass hosts it on the sharp overlay.
- `LiquidGlassFactory` — central construction/configuration of the Liquid Glass body.
- `DockLiquidGlassHostView` — exact Dock-sized composition/final-clip boundary for Liquid Glass.
- `DockLiquidGlassView` — capture lifecycle + recovery + dynamic detection + refraction body; selects Shader or MIUI self-blur at runtime.
- `DockStrokeOverlayView` — crisp Canvas highlight + `DockStrokeRenderer` layer above the glass body.
- `MiBlurBridge` / `LiquidBlurBackendPolicy` — cached MIUI `View.setMi*` self-blur bridge and fail-closed runtime backend policy.""",
)
rep(
    "ARCHITECTURE.md",
    "## Capture architecture\n\nScene state is expressed as HOME / APP / RECENTS, but capture mode is not a simple fixed scene-to-mode table.",
    """## Liquid Glass rendering architecture

`liquid_blur_mode` is a persisted user-intent setting with `shader` as the compatibility default and `advanced_material` as the optional HyperOS SurfaceFlinger backend. `MiBlurBridge` resolves the MIUI `View.setMi*` methods once and never owns preferences; if advanced material cannot be applied, `LiquidBlurBackendPolicy` keeps the active backend on Shader without rewriting the saved choice.

The Liquid Glass view hierarchy is layered:

```text
DockLiquidGlassHostView    <- exact Dock size; final round/squircle clip
  ├─ DockLiquidGlassView   <- capture/refraction/tint; self-blurred rectangular RenderNode
  └─ DockStrokeOverlayView <- sharp Canvas highlight + DockStrokeRenderer foreground
```

In active advanced mode the AGSL `blurred()` function bypasses its 40-sample kernel and samples the source directly. The glass child is deliberately not pre-clipped to the rounded outline: SurfaceFlinger receives corner pixels first, then the host clips the composed result. Standard mode and advanced-mode runtime fallback retain the existing Shader kernel.

## Capture architecture

Scene state is expressed as HOME / APP / RECENTS, but capture mode is not a simple fixed scene-to-mode table.""",
)
rep(
    "ARCHITECTURE.md",
    "`DockStrokeRenderer` replaced the old independent stroke overlay. It installs a `StrokeDrawable` in the host foreground, builds validated outer/inner paths and excludes the Dock center with `clipOutPath(inner)`.",
    "`DockStrokeRenderer` replaced the old layout-coupled stroke overlay. Native blur Dock installs its `StrokeDrawable` directly in the host foreground; Liquid Glass now installs the same renderer on `DockStrokeOverlayView` so the border remains outside the self-blur RenderNode. The renderer still builds validated outer/inner paths and excludes the Dock center with `clipOutPath(inner)`. ",
)

# CHANGELOG
rep(
    "CHANGELOG.md",
    "### Documentation / known status",
    """### Liquid Glass advanced material blur

- 新增 `liquid_blur_mode`：默认 `shader` 保持现有行为，可选 `advanced_material` 使用 HyperOS/MIUI SurfaceFlinger self-blur
- 新增缓存反射 `MiBlurBridge`，直接调用 `View.setMiSelfBlur`、`setPassTextureScale` 与 self-blur enhance flag；能力失败仅回退当前运行时 backend，不改写用户配置
- RuntimeShader 增加 `shaderBlurEnabled`，高级材质实际生效时绕过原 40-sample blur kernel；Shader 模式和 fallback 继续使用原 kernel
- Liquid Glass 拆为 `DockLiquidGlassHostView` + `DockLiquidGlassView` + `DockStrokeOverlayView`：glass body 负责折射/模糊，overlay 保持 Canvas 高光和可配置描边锐利
- 最终 round/squircle clip 移到 host 合成层；高级模式下 self-blurred child 不预先裁圆角，修复实验中左上圆角区域没有模糊的问题
- 两条 `Launcher.setupViews()` 路径统一使用同一 Liquid Glass layer assembly；workstation 仍保持未完成适配状态
- Floating Dock View detach 时清理 MIUI self-blur 状态，重新 attach 后按保存的 advanced 请求自动重施

### Documentation / known status""",
)

# Final contract sanity.
glass = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java").read_text()
for needle in (
    "advancedMaterialActive = false;\n        activeBlurBackend = LiquidBlurMode.SHADER;",
    "requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL\n                && !advancedMaterialUnavailableForProcess\n                && !advancedMaterialActive",
):
    if needle not in glass:
        raise SystemExit("missing lifecycle contract: " + needle)
