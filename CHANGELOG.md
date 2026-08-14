# Changelog

## Unreleased (2026-08-14)

### API101 configuration convergence

- 新增类型化 `ConfigKey` / `ConfigSchema`，集中登记 persisted key、类型、UI default、runtime fallback、export default、范围、存储模式和导出策略
- 新增纯 `ConfigCodec`，接管 JSON 导入/导出与 legacy alias 转换，移除 `SettingsActivity` 中的大量手写 key 列表
- `grid_widget_adaptation` 现已正确参与配置导入/导出
- 新增 `ConfigMigration`，把设置进程历史 SharedPreferences 升级逻辑从 Activity 中分离
- 新增 `PresetManager`，统一默认预设与动态 iPad 风格预设写入
- Compose 设置页开始绑定 schema key/default/range，减少 UI 与运行时配置漂移
- `LiquidDockConfig.load()` 改为 side-effect-free snapshot；Widget adaptation 的运行时 gate 暂时显式迁到 `MainHook.install()`
- pre-API101 JSON 迁移移到 `ModuleMain.onPackageReady()` 的 `LegacyConfigMigration` compatibility boundary；普通 runtime config load 不再写 Remote Preferences
- 保留 `liquid_home_settle_delay` 历史 `_tenths` round-trip
- Divider width/Y 明确保留历史 DIRECT raw-tenths JSON 语义，避免错误通用 `DP_TENTHS` sidecar 与 clamp 漂移
- 增加 forced Dock/Liquid dimension、absent-default、legacy migration、storage compatibility 等回归测试

### Liquid Glass advanced material blur

- 新增 `liquid_blur_mode`：默认 `shader` 保持现有行为，可选 `advanced_material` 使用 HyperOS/MIUI SurfaceFlinger self-blur
- 新增缓存反射 `MiBlurBridge`，直接调用 `View.setMiSelfBlur`、`setPassTextureScale` 与 self-blur enhance flag；能力失败仅回退当前运行时 backend，不改写用户配置
- RuntimeShader 增加 `shaderBlurEnabled`，高级材质实际生效时绕过原 40-sample blur kernel；Shader 模式和 fallback 继续使用原 kernel
- Liquid Glass 拆为 `DockLiquidGlassHostView` + `DockLiquidGlassView` + `DockStrokeOverlayView`：glass body 负责折射/模糊，overlay 保持 Canvas 高光和可配置描边锐利
- 最终 round/squircle clip 移到 host 合成层；高级模式下 self-blurred child 不预先裁圆角，修复实验中左上圆角区域没有模糊的问题
- 两条 `Launcher.setupViews()` 路径统一使用同一 Liquid Glass layer assembly；workstation 仍保持未完成适配状态

### Documentation / known status

- 更新 README、ARCHITECTURE、HOOKS、FEATURES、DIVIDER、CONTRIBUTING、TODO 以匹配当前 API101 实现
- 明确当前只完成配置架构 Phase 1；`MainHook`、`HomeGridHook`、`DockLiquidGlassView` 的后续模块拆分尚未完成
- 明确 Widget detection/span registry 尚未实现；当前仍固定支持 1×1、2×1、2×2、4×2
- 明确工作台虽已有实验性 Hook/参数/快照逻辑，但**整体仍未完成适配，不属于受支持功能**
- 明确 foreground `DockStrokeRenderer` 替代旧描边 overlay 后，历史描边阴影效果已失效；相关 key 暂为配置兼容保留

## v1.0.3 (2026-08-12)

- **Dock 分隔竖线控制**：工作台模式图标间竖线支持宽度、高度比例、垂直偏移、颜色和透明度独立调节
- **工作台捕获实验修复**：加入 wallpaper-only/native snapshot 方向的兼容尝试；工作台整体适配仍未完成
- **HookUtil 反射修复**：`hookMethod(Class,...)` 改为父类链查找
- **per-frame scene 检测**：`onPreDraw` 帧触发替代轮询
- **RECENTS→HOME 立即捕获**：scene 转变时立即 `scene-settle-home`
- **Haptic 预触发取消**：`prearmRecentsCapture` 强制取消进行中捕获

## v1.0.2 (2026-08-05)

- 旋转黑帧过滤：阈值钳位 > 0
- HOME settle barrier：延迟方案，GUI 滑块可配
- 壁纸条带缓存短路修复
- 旋转稳定收敛：3s 窗口，连续两帧签名一致停止

## v1.0.1 (2026-07-28)

- 配置同步迁移至 LSPosed Remote Preferences（不再依赖 su）
- 导出/导入包含 `homeSettleDelay`
- DragController hooks 进程级一次性安装

## v1.0.0 (2026-07-20)

- 首个签名发布版
- libxposed API 101 迁移完成
- 统一反射层 HookUtil
- 液态玻璃光学模型（Prismal 参考实现）
