# LiquidDock TODO

当前 `api101-migration` 已完成 **Phase 1：配置收敛**。以下事项仍未完成，按风险从低到高推进。

## 1. Widget detection / span extensibility

- 移除 `WidgetGridSizing` 的 static `widgetAdaptationEnabled` 全局状态；由安装模块持有 immutable config。
- 引入 `WidgetClassifier`：集中处理 `ItemInfo.isWidget()`、item type fallback 和未来 HyperOS 变体。
- 引入 `WidgetSpecRegistry`：当前先只登记 1×1、2×1、2×2、4×2，后续新增 span 不修改核心 Hook。
- 删除未接入活动路径的 legacy `HomeGridHook.adaptTwoByOneWidget(...)` 和对应日志缓存。
- 保持 Widget adaptation 只修改像素 allocation/frame，不接管 MIUI placement/occupancy。

## 2. `HomeGridHook` 拆分

按低风险到高风险逐步拆出：

1. Widget adaptation；
2. Page indicator；
3. Folder alignment；
4. Cell geometry；
5. Grid rotation / refresh（最后拆）。

必须保持：

- 横屏 8×4 / 竖屏 4×8 当前行为；
- lazy/off-screen page 几何准备；
- `LayoutTransformRuleGridChanged` 现有 metadata；
- 不 Hook `addOccupied()` / `transformToHVArray()`；
- 不改变 MIUI occupied matrix / placement 所有权。

## 3. 工作台 / Laptop 完整适配

**当前仍未适配完成。** 已存在的 Laptop/Workstation Hook、Dock/Grid/All Apps offset、Divider、wallpaper snapshot、Recents capture 等只视为实验性实现。

完成标准至少包括真机验证：

- 进入/退出工作台；
- 普通桌面位置 backup/restore；
- Dock 宽度、图标 spacing/offset；
- All Apps 横竖屏；
- Recents 打开/关闭；
- 旋转；
- native wallpaper snapshot/live blur 切换；
- Liquid Glass suspension/recovery；
- 普通模式无回归。

架构上应继续拆出 `WorkstationModeController` 与独立 Workstation module，其他模块只依赖小的状态接口，不依赖 `MainHook` static state。

## 4. `MainHook` 收缩为真正 composition root

- 把 Dock/Grid/Glass/Workstation 安装拆成独立模块。
- 把 mutable static 状态移到对应 controller/view 生命周期所有者。
- 顶层先读取 immutable config，再按 feature 安装模块。
- 实现真正的 master-switch zero-hook：主开关关闭时不提前安装 workstation guard，也不独立安装 `WorkstationWallpaperOnlyHook`。

## 5. `DockLiquidGlassView` 拆分

优先提取纯策略/状态：

- Dynamic motion detector；
- Capture failure/retry policy；
- Capture controller；
- Renderer/shader ownership。

保持现有 scene revision、attempt token、black-frame guard、APP pre-arm、Recents continuation、rotation stabilization 和视觉参数不变。

## 6. 描边阴影后续决定

foreground `DockStrokeRenderer` 已替代旧描边 overlay，因此历史 `stroke_shadow` / `shadow_radius` / `shadow_alpha` 目前不产生旧描边阴影效果。

后续二选一：

- 设计适配 foreground renderer 的新描边阴影实现；或
- 正式标记该视觉能力 deprecated，但继续保留历史配置 key 的读入/导入兼容。

在决定前不要删除旧 key，也不要把旧 overlay 实现重新接回去。

## 7. 清理与架构 gate

- 去除过时 compatibility facade / dead helper。
- 尽量让 pure policy 不依赖 Android/Xposed。
- 增加 schema/codec/architecture regression tests。
- 每个阶段都运行 `testDebugUnitTest` + `assembleDebug`，高风险 Grid/Capture/Workstation 改动再做真机回归。
