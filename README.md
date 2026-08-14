# LiquidDock

![LiquidDock 效果](artwork/liquid-dock-screenshot.jpg)

LiquidDock 是一个面向 HyperOS 3 Pad 启动器的 LSPosed/libxposed API 101 模块，用于扩展系统 Dock 的液态玻璃、Dock 外观、桌面网格等行为。

当前 `api101-migration` 已完成第一阶段配置架构重构：设置、导入/导出、迁移、预设和运行时读取开始共享同一套类型化配置契约，而不是在多个界面和 Hook 中分别维护 key、默认值与范围。

## 当前能力

- **液态玻璃 Dock**：RuntimeShader 实现模糊、折射、色散、穹顶、高光与焦散
- **Dock 几何**：宽高、底部偏移、图标间距、模糊、圆角和方圆形轮廓
- **前景描边**：`DockStrokeRenderer` 直接安装到宿主 View foreground，不再创建独立描边 overlay / RenderNode
- **独立 Dock 阴影**：与描边分离，并抑制 HyperOS 原生 Dock 阴影避免重复
- **捕获管线**：HOME / APP / RECENTS 场景状态、动态画面探针、黑帧保护、旋转稳定与捕获节流
- **桌面网格**：横屏 8×4、竖屏 4×8，自定义边距、行距与页面指示器偏移
- **Widget adaptation**：可独立开关；当前活动实现只覆盖已验证的 1×1、2×1、2×2、4×2 span
- **配置管理**：Schema 驱动的 JSON 导入/导出、默认预设、历史配置迁移和 API 101 Remote Preferences
- **工作台相关实验代码**：包含模式检测、独立 Dock 参数、All Apps/Grid 偏移、Divider 和 wallpaper snapshot 处理，但**工作台整体仍未完成适配，不属于当前受支持功能**

功能参数见 [FEATURES.md](FEATURES.md)，当前实际 Hook 点见 [HOOKS.md](HOOKS.md)，配置与模块边界见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 配置架构

设置进程与 Launcher 进程通过 API 101 Remote Preferences 解耦：

```text
Settings SharedPreferences
    -> ConfigMigration
    -> ConfigSchema / ConfigCodec / PresetManager
    -> API101 Remote Preferences
    -> ConfigReader
    -> LiquidDockConfig immutable snapshot
    -> Hook / renderer
```

核心职责：

- `ConfigSchema`：持久化 key、类型、范围、存储模式和导出策略的统一登记处
- `ConfigCodec`：JSON 导入/导出的纯转换层
- `ConfigMigration`：设置进程中的历史 SharedPreferences 升级
- `PresetManager`：默认预设和 iPad 风格预设写入
- `ConfigReader`：Launcher 进程只读 Remote Preferences snapshot
- `LiquidDockConfig`：把 snapshot 转成不可变、类型化的运行时配置
- `LegacyConfigMigration`：仅在 `ModuleMain.onPackageReady()` 的显式兼容边界尝试把 pre-API101 JSON 迁移到空的 Remote Preferences；普通 `LiquidDockConfig.load()` 不再执行写入

`ConfigSchema` 有意区分 UI 默认值、运行时缺省值和历史导出默认值。历史 `_tenths`、legacy alias、Divider sentinel 等兼容语义不能为了“统一默认值”而被抹平。

## 当前架构状态

这次完成的是 **Phase 1：configuration convergence**，不是整个项目的模块化终点。当前仍有几个明确的后续重构目标：

- `MainHook` 仍承担过多 Dock / Glass / Workstation / Grid 组装与状态职责
- `HomeGridHook` 仍同时负责网格数量、几何、旋转、Widget、指示器和文件夹对齐
- `WidgetGridSizing` 仍使用静态 feature flag，Widget 类型与 span 仍写死在活动路径中
- `DockLiquidGlassView` 仍同时承载 View、捕获控制、失败恢复、动态检测和 Shader 渲染
- 工作台存在实验实现，但仍未完成设备级适配与回归

后续重构必须保持当前 SharedPreferences key、历史 JSON、默认值、预设和已验证 8×4/4×8 行为兼容。

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

## 已知限制

- **工作台 / Laptop 模式尚未完成适配**。现有 Workstation Hook 只表示已有实验实现，不能视为功能完成；出现布局、Dock、捕获或切换异常仍属于已知范围
- **旧描边阴影已失效**。描边迁移到 foreground `DockStrokeRenderer` 后，历史 `stroke_shadow` / `shadow_radius` / `shadow_alpha` key 仍为配置兼容保留，但当前描边 renderer 不实现旧阴影效果
- Widget adaptation 当前只支持 1×1、2×1、2×2、4×2；Widget 类型识别与 span registry 尚未完成下一阶段泛化
- 分屏、小窗和不同 HyperOS Launcher 版本的 SurfaceFlinger/布局行为仍可能需要单独兼容

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
