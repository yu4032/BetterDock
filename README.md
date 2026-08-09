# BetterDock

BetterDock is an LSPosed module for customizing the HyperOS 3 Pad launcher Dock (com.miui.home).

## Features

- Dock blur strength, width, height, bottom offset, and independent blur/stroke corner radii
- Round-rect and continuous squircle outlines
- Adjustable stroke thickness, base RGBA color, fill-diff rendering, and stroke toggle
- Fixed or gyroscope-driven highlight
- Adjustable icon spacing with matching Dock background width
- Independent whole-Dock shadow with softness, maximum spread, opacity, and signed Y offset
- HyperOS native Dock shadow suppression
- Resolution-aware iPad-style preset
- JSON parameter import and export

## Build

Requirements: Android SDK, JDK 17, and LSPosed API 82 (libs/api-82.jar).

    ANDROID_HOME=/path/to/Android ./gradlew assembleRelease --no-daemon

The release APK is generated under build/outputs/apk/release/.
