# Dock 分隔竖线控制

## 当前状态

HyperOS 3 工作台模式 Dock 的图标分隔竖线由 `DockDividerHook` 独立控制。

需要注意：**工作台整体仍未完成适配**。Divider Hook 本身存在并可配置，不代表 Workstation/Laptop 模式已经进入受支持状态。

## Hook 点

`HotSeatsListContentAdapter$LineViewHolder.bindView()`。

系统完成 bind 后，LiquidDock 再按配置覆盖 Divider View，因此旋转、Dock 展开/收起或重新 bind 时会重新应用。

## 与普通 Dock 的边界

Divider 与以下普通 Dock 配置无依赖：

- `dock_dimensions_dp`
- 普通 Dock width/height offset
- 普通 Dock blur
- 工作台 Dock customization 总开关

Divider 的单位兼容只在 `ConfigSchema.Divider`、`LiquidDockConfig.Divider` 和 `DockDividerHook` 边界处理。

## 参数

| key | 持久化范围 | 运行时语义 |
|---|---:|---|
| `dock_divider_enabled` | boolean | 独立总开关；存在该 key 时进入 explicit mode |
| `dock_divider_width_dp` | 0–160 | 历史 raw `0.1 dp` 整数；运行时除以 10 得真实 dp |
| `dock_divider_height_scale` | 0–100 | 相对父容器高度百分比 |
| `dock_divider_y_offset` | −80–80 | 历史 raw `0.1 dp` 整数；正值下移，运行时除以 10 |
| `dock_divider_color_r/g/b` | 0–255 | RGB |
| `dock_divider_alpha` | 0–255 | Alpha |

## 为什么 width / Y 不使用通用 `DP_TENTHS`

Phase 1 配置重构后，`ConfigSchema` 支持通用 `DP_TENTHS` sidecar（`<key>_tenths`），但 Divider width/Y 是历史例外。

旧 JSON 已经把：

- `dock_divider_width_dp`
- `dock_divider_y_offset`

本身定义成 raw tenths-of-dp integer，并带有自己的历史 import clamp。

因此 `ConfigSchema.Divider.WIDTH_DP` 和 `Y_OFFSET_DP` **故意使用 `DIRECT`**：

- 不生成额外 `<key>_tenths` sidecar；
- 保留旧 JSON 数值表示；
- 保留旧 import clamp；
- 运行时只在 `LiquidDockConfig.Divider` 中 `/ 10f` 规范化为真实 dp。

把它们改成通用 `DP_TENTHS` 会造成 10 倍单位漂移和错误 round-trip，这正是 Phase 1 final compatibility fix 要避免的问题。

## explicit / legacy 兼容规则

旧版本没有 `dock_divider_enabled`，并把数值 `0` 当作“保持系统默认”。当前继续区分两种模式。

### explicit mode

只要存在 `dock_divider_enabled`：

- 开关值决定是否启用 Divider override；
- width / height / Y / RGBA 中的数值 `0` 都是合法显式配置；
- 缺省值按当前 explicit 默认处理。

### legacy mode

如果没有 `dock_divider_enabled`，但存在任一旧 Divider 数值 key：

- 自动视为旧配置；
- 继续保留 `0 = 不覆盖系统默认值` 的 sentinel 语义；
- 缺失字段保持 0，不用新版本默认值补齐。

这样旧用户升级不会因为新增独立总开关而改变已有 Divider 行为。

## 配置架构位置

Phase 1 后：

```text
ConfigSchema.Divider
    -> ConfigCodec import/export
    -> API101 Remote Preferences
    -> ConfigReader snapshot
    -> LiquidDockConfig.Divider
    -> DockDividerHook
```

`DockDividerHook` 不直接读取 SharedPreferences key，也不读取 `dock_dimensions_dp`。
