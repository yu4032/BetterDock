# BetterDock

BetterDock is an LSPosed module for customizing the HyperOS 3 Pad launcher Dock (com.miui.home).

## Features

- Dock blur strength, width, height, bottom offset, and independent blur/stroke corner radii
- Round-rect and continuous squircle outlines
- Adjustable stroke thickness, base RGBA color, fill-diff rendering, and stroke toggle
- Adjustable icon spacing with matching Dock background width
- Independent whole-Dock shadow with softness, maximum spread, opacity, and signed Y offset
- HyperOS native Dock shadow suppression
- Saved default preset matching the recommended layout and glass parameters
- JSON parameter import and export

Development structure and extension rules are documented in [ARCHITECTURE.md](ARCHITECTURE.md).

## Disclaimer

This is an unofficial community project. It is **not affiliated with, endorsed by, or
related to Xiaomi Inc.** or HyperOS/MIUI. "HyperOS" and "MIUI" are trademarks of their
respective owners, referenced here only for compatibility description.

- For personal learning and research use only.
- Use at your own risk. The author is not responsible for any device damage, data loss,
  or warranty issues caused by this module.
- Commercial use is not permitted.

## Build

Requirements: Android SDK, JDK 17, and LSPosed API 82 (libs/api-82.jar).

    ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon

The release APK is generated under build/outputs/apk/release/.

- Compose Miuix 0.9.3 settings UI with HyperOS styling
- Hierarchical settings pages and per-value reset controls
