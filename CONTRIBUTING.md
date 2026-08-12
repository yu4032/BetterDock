# Contributing

## 构建

```bash
ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon
```

要求：Android SDK、JDK 17、LSPosed API 101 (`libs/api-101.jar`)。

## 开发流程

1. Fork 仓库，基于 `main` 创建特性分支
2. 钩子放 `*Hook.java`，策略放纯 Java 类并加单测
3. 配置 key → `LiquidDockConfig`，GUI → `ComposeSettingsActivity.kt`
4. CI 通过 `testDebugUnitTest assembleDebug` 后提 PR

## 目录结构

| 文件 | 职责 |
|------|------|
| `ModuleMain.java` | API 101 入口 |
| `MainHook.java` | 组装所有模块 |
| `HookUtil.java` | 统一反射层 |
| `DockLiquidGlassView.java` | 捕获管线 + 渲染 |
| `HomeGridHook.java` | 桌面网格 |
| `DockDividerHook.java` | 分隔竖线 |
| `*Hook.java` | 各功能钩子适配器 |

## 许可

本项目基于 [GPL-3.0](LICENSE) 许可。提交代码即表示同意在该许可下分发。
