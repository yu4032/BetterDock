# LiquidDock architecture

LiquidDock has two processes and one configuration boundary:

- The settings application owns preferences, import/export, presets, and writes to LSPosed Remote Preferences.
- The LSPosed entry point runs inside `com.miui.home` and installs API 101 hooks (libxposed native).
- `LiquidDockConfig` is the immutable, typed boundary between Remote Preferences and runtime code.

## Configuration flow

The settings process owns configuration storage and compatibility conversion. Its Phase 1
data path is:

```text
local SharedPreferences -> ConfigMigration -> ConfigSchema/ConfigCodec
                                                \
                                                 -> API101 Remote Preferences -> ConfigReader -> LiquidDockConfig snapshot
```

`ConfigMigration` performs legacy preference upgrades before the settings UI or codec uses
the values. `ConfigSchema` is the single definition of persisted keys, types, bounds, and
compatibility metadata; `ConfigCodec` uses it for import and export. The settings app then
publishes the resulting values through API 101 Remote Preferences. In the launcher process,
`ConfigReader` reads that boundary and `LiquidDockConfig` converts it into an immutable,
side-effect-free runtime snapshot.

Schema metadata intentionally distinguishes three meanings of a default:

- `uiDefault` is the current UI/storage default used when presenting or persisting settings.
- `runtimeFallback` preserves the injected runtime behavior for absent legacy preferences.
- `exportDefault` preserves the historic exported representation where compatibility requires it.

Those values may differ. They must not be normalized merely because a current UI default,
a legacy runtime fallback, and an export fallback happen to describe the same feature.

## Runtime modules

- `ModuleMain`: libxposed API 101 entry point. Calls `MainHook.install(CL)` + `HomeGridHook.install(CL)` + `RecentsHapticHook.install(CL)`.
- `Api101Bridge`: process-local bridge to libxposed API 101 (log, config, resource access).
- `HookUtil`: unified reflection layer. `findMethodExact` walks superclass chain for HyperOS version tolerance. All `invoke`/`invokeStatic`/field access flows through this class.
- `MainHook`: composition root. It decides which modules are enabled and connects them.
- `HomeGridHook`: home-grid, rotation mapping, widget alignment, and indicator positioning.
- `RecentsHapticHook`: version-tolerant adapter for the launcher recents haptic event (uses `HookUtil.findMethodExact` for superclass-aware hooking).
- `LiquidGlassFactory`: the only place that assembles and configures a glass view.
- `DockLiquidGlassView`: Android view and capture lifecycle owner.
- `CaptureSceneState`: pure HOME/APP/RECENTS state machine and stale-frame revisioning.
- `CaptureCadence`: pure capture-rate and power-limit policy.
- `LiveScreenCapture`: hidden SurfaceFlinger API compatibility layer.

Pure policy classes must not depend on Android views or Xposed. They have local unit tests.
Compatibility hooks should translate launcher events into calls on runtime controllers; they
must not own rendering state.

## Capture mode

Capture mode is driven by `CaptureScene` alone:

| Scene   | Mode | Description |
|---------|------|-------------|
| HOME    | 2    | Wallpaper-only (`Wallpaper BBQ wrapper`), Dock/icons inherently excluded |
| APP     | 1    | Full-display with Dock + drag layers excluded, real-time content |
| RECENTS | 1    | Full-display with Dock excluded, real-time multitasking |

Scene detection runs per-frame in `onPreDraw` (not polled). RECENTS→HOME transitions
trigger an immediate `scene-settle-home` capture without waiting for observation cadence.
Haptic/gesture-triggered recents entry (`prearmRecentsCapture`) force-cancels in-flight
capture work to ensure the scene switch is not coalesced away.

## Adding a feature

1. Add its key, type, and compatibility defaults to the appropriate `ConfigSchema` section.
2. Add one settings specification and translated label/summary.
3. Put launcher reflection in a dedicated `*Hook` adapter.
4. Put deterministic policy in a small Android-free class and add a unit test.
5. Connect the module from `MainHook`, then run `testDebugUnitTest assembleDebug`.

Do not read config keys directly from hooks or rendering code. Do not add new mutable global
state to `MainHook`; state belongs to the controller/view instance that owns its lifecycle.
