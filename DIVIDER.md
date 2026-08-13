# Dock 分隔竖线控制

## 独立配置

HyperOS 3 工作台模式 Dock 的图标分隔竖线由 `DockDividerHook` 独立控制。
它与普通 Dock 的尺寸、模糊、`dock_dimensions_dp` 和工作台 Dock 尺寸开关均无依赖。

Hook 点：`HotSeatsListContentAdapter$LineViewHolder.bindView()`。
系统完成 bind 后再覆盖分隔线 View，因此旋转、Dock 展开/收起都会重新应用。

## 参数

| key | 语义 |
|---|---|
| `dock_divider_enabled` | 独立总开关 |
| `dock_divider_width_dp` | 0–160，历史存储单位 0.1 dp，运行时规范化为 dp |
| `dock_divider_height_scale` | 0–100%，相对父容器高度 |
| `dock_divider_y_offset` | -80–80，历史存储单位 0.1 dp，正值下移 |
| `dock_divider_color_r/g/b` | 0–255 |
| `dock_divider_alpha` | 0–255 |

## 兼容规则

旧版本没有 `dock_divider_enabled`，并把数值 `0` 当作“保持系统默认”。升级后：

- 若存在任一旧 divider key 且没有新开关，自动进入 **legacy mode**，继续保持旧 sentinel 语义；
- 一旦 `dock_divider_enabled` 被明确写入，则进入 **explicit mode**，此时 `0` 是真正的可设置值；
- 新设置页在第一次主动开启 Divider 时会写入一组完整默认值，因此不会因为缺少字段而改变旧配置。

## 单位边界

Divider 的 dp 换算只在 `LiquidDockConfig.Divider` 与 `DockDividerHook` 内完成，绝不读取
`dock_dimensions_dp`。Dock 尺寸单位切换不会改变 Divider 的宽度或 Y 偏移。
