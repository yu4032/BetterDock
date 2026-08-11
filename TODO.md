# BetterDock TODO

- Properly adapt Dock, home-grid geometry, live capture, and liquid-glass rendering to
  HyperOS workstation/PC mode. Until implemented, BetterDock deliberately bypasses all
  modifications while `LauncherModeController.isLaptopMode()` is true (legacy Mingou builds
  fall back to `DeviceConfig.isMingouLaptopPcModeEnabled()`).
