# LiquidDock

![LiquidDock 效果](artwork/liquid-dock-screenshot.jpg)

LiquidDock 是一个面向 HyperOS 3 Pad 启动器的 LSPosed/libxposed API 101 模块，用于扩展系统 Dock 的液态玻璃、Dock 外观、桌面网格等行为。

## 功能

- **液态玻璃 Dock**：可选标准 RuntimeShader 模糊或 HyperOS SurfaceFlinger 高级材质 self-blur；折射、色散、穹顶与光学效果继续由 LiquidDock 渲染
- **动态高光**：RuntimeShader 实时逐像素计算镜面/边缘光/焦散，配合 self-blur 保持锐利，GUI 滑条即时生效
- **Dock 几何**：宽高、底部偏移、图标间距、模糊、圆角和方圆形轮廓
- **前景描边**：原生 blur Dock 使用 foreground `DockStrokeRenderer`；Liquid Glass 使用独立锐利 overlay，使描边/高光不进入 self-blur
- **独立 Dock 阴影**：与描边分离，并抑制 HyperOS 原生 Dock 阴影避免重复
- **捕获管线**：HOME / APP / RECENTS 场景状态、动态画面探针、黑帧保护、旋转稳定与捕获节流
- **桌面网格**：横屏 8×4、竖屏 4×8，自定义边距、行距与页面指示器偏移
- **Widget adaptation**：可独立开关
- **Dock 分隔线**：图标间竖线的宽度、高度比例、垂直偏移、颜色和透明度独立调节
- **配置管理**：Schema 驱动的 JSON 导入/导出、默认预设、历史配置迁移和 API 101 Remote Preferences

功能参数见 [FEATURES.md](FEATURES.md)，当前实际 Hook 点见 [HOOKS.md](HOOKS.md)，配置与模块边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。

### Liquid Glass 模糊后端

`liquid_blur_mode` 提供两种后端：`shader`（兼容默认）和 `advanced_material`。高级材质模式反射 `View.setMiSelfBlur(...)` / `setPassTextureScale(...)` 驱动 HyperOS SurfaceFlinger self-blur。

高级模式分层：`DockLiquidGlassView` 保持矩形 RenderNode 进入 self-blur，`DockLiquidGlassHostView` 在合成后裁回 Dock 圆角/方圆轮廓，`DockStrokeOverlayView` 在最上层绘制高光和可配置描边。

## 构建

要求：Android SDK、JDK 17、LSPosed API 101（`libs/api-101.jar`）。

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

Debug/CI 基线：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Release 构建启用 R8 代码与资源裁剪，产物位于 `build/outputs/apk/release/`。

## 免责声明

本项目为非官方社区项目，与小米公司无关。"HyperOS" 与 "MIUI" 为其所有者商标，此处仅用于兼容性描述。

本项目仅供学习与研究使用。使用者自行承担使用风险；本项目禁止商用。

## 感谢

- **HyperCeiler** — 模块工程实践参考
- **Prismal** — 液态玻璃光学模型与 Shader 参数设计参考
- **LSPosed** — Hook API 与加载框架

降采样与屏幕捕获设计思路受 HyperLight 启发。

## 开源许可

本项目基于 [GPL-3.0](LICENSE) 许可开源。
