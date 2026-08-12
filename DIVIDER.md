# Dock 分隔竖线控制

## 概览

HyperOS 3 工作台模式 Dock 的图标分隔竖线由独立 Hook 控制。  
Hook 点：`HotSeatsListContentAdapter$LineViewHolder.bindView()`  
位置：`DockDividerHook.java`

系统每次 `bindView()` 后覆盖以下参数，不影响普通桌面模式。

## 参数

所有参数在 **Dock 设置页** 调整。

### 宽度

| key | 范围 | 说明 |
|-----|------|------|
| `dock_divider_width_dp` | 0–160 (0–16 dp) | 竖线的绝对宽度。0 使用系统默认 |

### 高度比例

| key | 范围 | 说明 |
|-----|------|------|
| `dock_divider_height_scale` | 0–100 (%) | 竖线占图标高度的百分比。0 使用系统默认 |

### 垂直偏移

| key | 范围 | 说明 |
|-----|------|------|
| `dock_divider_y_offset` | −80–80 (dp×10) | 竖线上下偏移。正值下移，负值上移。不受高度比例影响（可独立控制位置） |

### 颜色与透明度

| key | 范围 | 说明 |
|-----|------|------|
| `dock_divider_color_r` | 0–255 | 红色通道。全 0 使用系统默认色 |
| `dock_divider_color_g` | 0–255 | 绿色通道 |
| `dock_divider_color_b` | 0–255 | 蓝色通道 |
| `dock_divider_alpha` | 0–255 | 不透明度。0 使用系统默认 |

RGB 全 0 且 alpha 为 0 → 不干预，系统原样渲染。  
任一通道非 0 或 alpha 非 0 → 覆盖颜色。

## 实现原理

```
HotSeatsList$AdapterItem.asDivLine()
    ↓ viewType=64
HotSeatsListContentAdapter.onCreateViewHolder(parent, 64)
    ↓
LineViewHolder.bindView()
    ↓ after（系统算完尺寸后覆盖）
    ↓
getContent() → View (竖线本体)
    ↓
LayoutParams.width     → 宽度
LayoutParams.height    → 高度
LayoutParams.topMargin → 居中位置 + 偏移
setBackgroundColor()   → 颜色 / 透明度
```

Hook 位于 RecyclerView 的 bind 阶段，旋转、Dock 展开/收起均会重新触发。
