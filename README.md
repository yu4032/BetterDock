# LiquidDock

![LiquidDock 效果](artwork/liquid-dock-screenshot.jpg)

LiquidDock 是一个 LSPosed 模块，为 HyperOS 3 Pad 的启动器 Dock 带来液态玻璃效果。

## 特性

- **液态玻璃 Dock 背景**：原生 blur（SurfaceFlinger 合成）叠加深度折射、高光、描边与色散边缘（Prismal 光学模型）
- Dock 模糊强度、宽度、高度、底部偏移、独立模糊/描边圆角
- 圆角矩形与连续方圆形（squircle）轮廓
- 可调描边厚度、基础 RGBA 颜色、Fill-Diff 渲染与描边开关
- 可调图标间距与匹配的 Dock 背景宽度
- 独立整体阴影：柔度、最大扩散、不透明度、Y 轴偏移
- HyperOS 原生 Dock 阴影抑制
- 匹配推荐布局与玻璃参数的默认预设
- JSON 参数导入与导出
- 静态桌面零捕获（壁纸条带缓存复用）；app 内实时屏幕捕获

开发结构与扩展规则见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 构建

要求：Android SDK、JDK 17、LSPosed API 82（libs/api-82.jar）。

    ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon

Release APK 生成于 build/outputs/apk/release/。

## 免责声明

非官方社区项目，与小米无关。"HyperOS" 与 "MIUI" 为其所有者商标，此处仅用于兼容性描述。

仅供学习与研究使用。自行承担使用风险；禁止商用。

## 感谢

感谢以下开源项目的作者，本项目在其基础上构建：

- **HyperCeiler** — 模块工程实践参考（模块结构、设置组织）
- **Prismal** — 液态玻璃光学模型与 Shader 参数设计参考
- **LSPosed** — 模块 Hook API 与加载框架
- **HyperLight** — 降采样与屏幕捕获思路启发

## 开源许可

本项目基于 [GPL-3.0](LICENSE) 许可开源。
