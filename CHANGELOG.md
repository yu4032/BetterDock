# Changelog

## v1.0.3 (2026-08-12)

- **Dock 分隔竖线控制**：工作台模式图标间竖线支持宽度、高度比例、垂直偏移、颜色和透明度独立调节
- **工作台捕获修复**：强制 wallpaper-only 捕获，避免全屏捕获将自身采样进背景
- **HookUtil 反射修复**：`hookMethod(Class,...)` 改为父类链查找
- **per-frame scene 检测**：`onPreDraw` 帧触发替代轮循
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
