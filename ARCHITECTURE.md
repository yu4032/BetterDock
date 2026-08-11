# BetterDock architecture

BetterDock has two processes and one configuration boundary:

- The settings application owns preferences, import/export, presets, and writes the JSON file.
- The LSPosed entry point runs inside `com.miui.home` and installs small compatibility hooks.
- `BetterDockConfig` is the immutable, typed boundary between JSON and runtime code.

## Runtime modules

- `MainHook`: composition root. It decides which modules are enabled and connects them.
- `HomeGridHook`: home-grid, rotation mapping, widget alignment, and indicator positioning.
- `RecentsHapticHook`: version-tolerant adapter for the launcher recents haptic event.
- `LiquidGlassFactory`: the only place that assembles and configures a glass view.
- `DockLiquidGlassView`: Android view and capture lifecycle owner.
- `CaptureSceneState`: pure HOME/APP/RECENTS state machine and stale-frame revisioning.
- `CaptureCadence`: pure capture-rate and power-limit policy.
- `LiveScreenCapture`: hidden SurfaceFlinger API compatibility layer.

Pure policy classes must not depend on Android views or Xposed. They have local unit tests.
Compatibility hooks should translate launcher events into calls on runtime controllers; they
must not own rendering state.

## Adding a feature

1. Add its key and default to the appropriate section of `BetterDockConfig`.
2. Add one settings specification and translated label/summary.
3. Put launcher reflection in a dedicated `*Hook` adapter.
4. Put deterministic policy in a small Android-free class and add a unit test.
5. Connect the module from `MainHook`, then run `testDebugUnitTest lintRelease assembleRelease`.

Do not read JSON keys directly from hooks or rendering code. Do not add new mutable global
state to `MainHook`; state belongs to the controller/view instance that owns its lifecycle.
