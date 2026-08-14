# Contributing

## 构建

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

要求：Android SDK、JDK 17、LSPosed API 101（`libs/api-101.jar`）。

Debug/CI 验证基线：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

## 当前开发分支

API 101 迁移与模块化重构仍在 `api101-migration` 推进。需要修改当前实现时，从该分支创建独立特性/修复分支，不要从已经过时的旧实现假定 Hook 或配置边界。

## 配置修改规则

Phase 1 后，新增/修改配置必须遵守以下顺序：

1. 先在 `ConfigSchema` 定义/修改 persisted key、类型、default、range、storage/export metadata。
2. 若涉及历史偏好升级，放入 `ConfigMigration`；不要把 migration 重新塞回 Activity 或 runtime config load。
3. 若涉及 JSON 形状/legacy alias，修改 `ConfigCodec` 并加 round-trip 单测。
4. 若涉及预设，修改 `PresetManager`。
5. Runtime 通过 `LiquidDockConfig` 读取 typed config；Hook/renderer 不直接读取 raw preference key。
6. 保持现有 SharedPreferences key、历史 JSON field、`_tenths` 和 legacy fallback 兼容，除非另有明确 breaking migration 设计。

特别注意：`uiDefault`、`runtimeFallback`、`exportDefault` 可能故意不同，不能为了“统一默认值”把它们合并。

## Hook / 模块规则

- 系统类兼容与反射适配放在 `*Hook` / `HookUtil` 边界。
- 纯策略应尽量保持 Android/Xposed-free 并配单元测试。
- 不要继续向 `MainHook` 增加新的全局可变状态；当前文件已经是待拆分热点。
- 不要让 `LiquidDockConfig.load()` 产生跨模块副作用。
- 不要重新 Hook `addOccupied()` / `transformToHVArray()` 猜测 MIUI occupied matrix 方向。
- 网格旋转/位置修复必须保持 MIUI 对 placement/occupancy 的所有权。
- Widget adaptation 当前活动规格只有 1×1、2×1、2×2、4×2；不要把 legacy `adaptTwoByOneWidget(...)` 重新接回活动路径。
- Widget 类型/span 泛化应走后续 `WidgetClassifier` / `WidgetSpecRegistry` 方案，而不是继续在核心 Hook 增加 `itemType == ...` 分支。

## 工作台规则

工作台 / Laptop 目前**尚未完成适配**。已有 `WorkstationWallpaperOnlyHook`、Laptop 状态检测、Dock/Grid/All Apps offset、Divider、snapshot 等代码只能视为实验性兼容实现。

在没有完整真机回归之前：

- 不要在 README/CHANGELOG 中宣称“工作台已支持”；
- 修改工作台路径时必须单独验证普通模式没有回归；
- 工作台完成标准至少覆盖 Dock、All Apps、Recents、横竖屏、进入/退出、位置 backup/restore、捕获与 native snapshot。

## 描边规则

当前描边使用 `DockStrokeRenderer` foreground Drawable。不要恢复旧独立 overlay/RenderNode 方案，除非有新的架构设计与回归证据。

历史 `stroke_shadow` / `shadow_radius` / `shadow_alpha` key 仍需保持配置兼容，但当前 foreground renderer 不实现旧描边阴影。若未来重新实现描边阴影，应作为新 renderer 能力设计，而不是假定旧代码仍有效。

## 目录/职责速查

| 文件/目录 | 当前职责 |
|------|------|
| `ModuleMain.java` | API 101 入口；process-start legacy migration；调用 MainHook 和 workstation experimental hook |
| `config/ConfigKey.java` | 单个 persisted setting 的 typed metadata |
| `config/ConfigSchema.java` | persisted config schema registry |
| `config/ConfigCodec.java` | JSON import/export pure transform |
| `config/ConfigMigration.java` | settings-side legacy preference migration |
| `config/PresetManager.java` | preset ownership |
| `ConfigReader.java` | read-only API101 Remote Preferences snapshot |
| `LiquidDockConfig.java` | immutable typed runtime config |
| `MainHook.java` | 当前大型 runtime composition/状态中心，待继续拆分 |
| `HomeGridHook.java` | 8×4/4×8、geometry、rotation、widget、indicator、folder；待继续拆分 |
| `WidgetGridSizing.java` | Widget allocation geometry + 当前临时 static gate |
| `DockStrokeRenderer.java` | native/Liquid foreground 描边 |
| `DockDividerHook.java` | Divider Hook |
| `DockLiquidGlassView.java` | View + capture + recovery + dynamic detection + shader；待继续拆分 |
| `CaptureSceneState.java` | 纯 scene/revision state |
| `CaptureCadence.java` | 纯 capture cadence policy |
| `LiveScreenCapture.java` | SurfaceFlinger compatibility |
| `WorkstationWallpaperOnlyHook.java` | 工作台实验性 snapshot/All Apps/Recents 兼容路径 |
| `HOOKS.md` | 当前实际 Hook 点权威说明 |
| `ARCHITECTURE.md` | 当前架构状态和后续模块化边界 |

## 提交前

至少完成：

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

涉及配置时，还应覆盖 schema/codec/migration/preset 的 focused tests；涉及 Grid/Widget/Workstation/Capture 时必须补对应回归测试或真机验证记录。

## 许可

本项目基于 [GPL-3.0](LICENSE) 许可。提交代码即表示同意在该许可下分发。
