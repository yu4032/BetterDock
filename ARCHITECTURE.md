# LiquidDock architecture

This document describes the **current implementation on this branch**, not an aspirational target architecture.

The 2026-08-14 refactor completed **Phase 1: configuration convergence**. Later capture work moved freeform task ownership and the ordinary HOME/APP baseline into SystemUI/WMShell without pretending that the remaining large Launcher-side classes are fully modular. `MainHook`, `HomeGridHook`, and `DockLiquidGlassView` still own multiple responsibilities and remain explicit follow-up targets.

## Process and configuration boundaries

LiquidDock now has three relevant runtime boundaries in addition to the settings UI:

- The settings application owns local preferences, UI, import/export, presets, the exported broker service, and settings-side compatibility migration.
- The injected Launcher runtime executes inside `com.miui.home` and owns Launcher-only gesture/Overview/All Apps/workstation signals plus Dock rendering/capture orchestration.
- The injected SystemUI runtime executes inside `com.android.systemui` and observes existing WMShell task state for ordinary HOME/APP ownership and visible freeform task leashes.
- `FreeformLeashBrokerService` is only a Binder rendezvous. It stores the current SystemUI provider token and one Launcher provider watcher; it stores no task, HOME/APP, SurfaceControl, or capture state.
- API 101 Remote Preferences group `config` is the persisted cross-process configuration boundary.
- `ConfigReader` snapshots that boundary, and `LiquidDockConfig` converts the snapshot into immutable typed runtime configuration.

The normal settings flow is:

```text
Settings SharedPreferences
    -> ConfigMigration
    -> ConfigSchema / ConfigCodec / PresetManager
    -> API101 Remote Preferences
    -> ConfigReader (read-only snapshot)
    -> LiquidDockConfig (immutable typed snapshot)
    -> hooks / views / renderers
```

A separate one-time compatibility path exists for pre-API101 Launcher installs:

```text
ModuleMain.onPackageReady(com.miui.home)
    -> LegacyConfigMigration.migrateAtProcessStart()
       only when Remote Preferences are empty
    -> MainHook.install()
    -> WorkstationWallpaperOnlyHook.install()
```

`LegacyConfigMigration` probes the historical JSON locations and copies existing values into Remote Preferences before the first Launcher runtime snapshot is loaded. Failure is non-fatal. Ordinary `ConfigReader.load()` / `LiquidDockConfig.load()` are deliberately read-only and do not perform migration writes.

## Phase 1 configuration ownership

### `ConfigKey<T>`

Immutable metadata for one persisted setting:

- key name
- scalar type
- UI default
- runtime fallback
- historical export default
- integer bounds where applicable
- storage mode: `DIRECT` or `DP_TENTHS`
- export mode: `ALWAYS`, `IF_PRESENT`, or `NEVER`

### `ConfigSchema`

The authoritative registry for persisted configuration metadata. Current groups are:

- `Core`
- `Grid`
- `Dock`
- `Divider`
- `Glass`
- `Workstation`
- `Debug`

The three default values are intentionally separate. A current UI default, an absent-key runtime fallback, and an old JSON export fallback may describe different historical contracts and must not be normalized merely for cosmetic consistency.

Some conditional compatibility rules still belong in `LiquidDockConfig` instead of being flattened into one scalar schema value. Divider explicit/legacy behavior and context-dependent grid fallbacks are examples.

### `ConfigCodec`

Pure map-to-map import/export transformation. It owns:

- schema-based export inclusion
- scalar clamping
- `_tenths` round trips
- historical grid aliases
- old workstation All Apps aliases
- forced historical JSON representation for `dock_dimensions_dp` and `liquid_dimensions_dp`

`grid_widget_adaptation` now participates in export/import through the schema, fixing the omission that existed before Phase 1.

### `ConfigMigration`

Owns settings-side SharedPreferences migration, including:

- old merged/per-edge grid keys
- pixel-to-dp conversions
- offset semantics
- axis-distance keys
- Dock/Liquid dimension migration
- corner migration

It runs on the settings side, not as a side effect of ordinary runtime config loading.

### `PresetManager`

Owns the current default preset values and the dynamic iPad-style preset calculations. Device/resource-dependent preset calculations remain imperative because they depend on launcher resources and display metrics.

### `ConfigReader` and `LiquidDockConfig`

`ConfigReader` copies Remote Preferences into a process-local map snapshot. It accepts old scalar/string representations for compatibility and understands `<key>_tenths` when reading decimal values.

`LiquidDockConfig` is the typed runtime boundary. Constructing/loading it no longer mutates Widget/Grid state. The current install path still explicitly forwards the widget-adaptation gate to `WidgetGridSizing`; removing that global flag is a later phase.

## Current runtime installation

`ModuleMain` is the libxposed API 101 entry point and deliberately has separate SystemUI and Launcher branches.

For `com.android.systemui`, `onPackageReady()` currently performs:

1. `SystemUiTaskExecutorSource.install(classLoader)`;
2. `SystemUiHomeOwnershipSource.install(classLoader)`;
3. `SystemUiFreeformLeashProvider.install(classLoader)`;
4. returns before all Launcher hooks.

The first two components only observe existing WMShell objects. They never instantiate a `TaskOrganizer`, never register a second organizer, and never mirror the task map.

For `com.miui.home`, `onPackageReady()` currently performs:

1. `LegacyConfigMigration.migrateAtProcessStart()`;
2. `new MainHook().install(classLoader)`;
3. `WorkstationWallpaperOnlyHook.install(classLoader)`.

`MainHook.install()` currently performs several Launcher-side module-install responsibilities itself, including:

- workstation mode guard/state installation
- top-level config snapshot loading
- native foreground stroke installation
- recents haptic adapter installation
- workstation Dock hooks
- Dock resize behavior
- Divider hook installation
- `HomeGridHook` installation/configuration
- Liquid Glass scene/capture hooks
- Dock geometry/background/shadow hooks

It no longer infers ordinary HOME/APP ownership from Launcher lifecycle, window focus, `getRunningTasks()`, or foreground windowing mode. Launcher focus/configuration changes only request a fresh SystemUI baseline.

This is the **current implementation**, not the desired end state.

### Master-switch caveat

The current master switch is not yet a true zero-hook gate:

- the SystemUI observer hooks are installed at package-ready independently of Launcher config;
- `MainHook` installs the workstation mode guard before testing `config.enabled`;
- `WorkstationWallpaperOnlyHook` is invoked independently from `ModuleMain`.

A later phase should move top-level config gating to the composition boundary so disabled optional modules install no unnecessary hooks.

## Runtime components

- `ModuleMain` — API 101 process entry; splits SystemUI and Launcher installation paths.
- `Api101Bridge` — process-local libxposed API bridge for module access, logging and Remote Preferences.
- `HookUtil` — unified reflection/hook compatibility layer; superclass-aware exact-method lookup is used for HyperOS variation.
- `MainHook` — current large Launcher composition point; still contains Dock, workstation and special-scene hook installation, but no longer owns ordinary HOME/APP inference.
- `SystemUiTaskExecutorSource` — passively publishes the existing `ShellTaskOrganizer` executor used by WMShell task callbacks.
- `SystemUiHomeOwnershipSource` — read-only observer of the existing Xiaomi `MultiTaskingTaskRepository`; classifies only HOME / APP / UNKNOWN on the task-state executor.
- `SystemUiTaskStateProvider` — one shared SystemUI Binder provider that multiplexes HOME ownership and freeform snapshot transactions without owning task state.
- `HomeOwnershipPolicy` / `HomeOwnershipProtocol` — pure classification and versioned production wire contract.
- `HomeOwnershipResolver` / `HomeOwnershipRuntime` — asynchronous Launcher consumer of SystemUI HOME/APP/UNKNOWN; never blocks capture and never falls back to Launcher task inference.
- `FreeformLeashBrokerService` / `FreeformLeashBrokerClient` — package-checked provider-token rendezvous and recovery transport; provider recovery is event-driven through a Launcher watcher.
- `SystemUiFreeformLeashProvider` — read-only `FreeformTaskListener.mTasks` snapshot handler; freeform health remains independent from HOME ownership health.
- `FreeformCaptureLeashHook` — final mode-1 safety gate that merges the SystemUI-provided visible freeform `SurfaceControl[]` into capture exclusions and falls back safely when the snapshot is unavailable.
- `HomeGridHook` — current 8×4/4×8 count, geometry, rotation, widget sizing, indicator and folder alignment implementation.
- `WidgetGridSizing` — shared widget allocation geometry. It is not yet pure because it still owns a static adaptation flag.
- `DockDividerHook` — independent workstation Divider view adaptation.
- `DockStrokeRenderer` — shared configurable border renderer; native blur Dock uses it directly, Liquid Glass hosts it on the sharp overlay.
- `LiquidGlassFactory` — central construction/configuration of the Liquid Glass body.
- `DockLiquidGlassHostView` — exact Dock-sized composition/final-clip boundary for Liquid Glass.
- `DockLiquidGlassView` — capture lifecycle + recovery + dynamic detection + refraction body; selects Shader or MIUI self-blur at runtime.
- `DockStrokeOverlayView` — crisp Canvas highlight + `DockStrokeRenderer` layer above the glass body.
- `MiBlurBridge` / `LiquidBlurBackendPolicy` — cached MIUI `View.setMi*` self-blur bridge and fail-closed runtime backend policy.
- `CaptureSceneState` — pure `UNKNOWN` / HOME / APP / RECENTS / ALL_APPS scene and stale-frame revision policy. Gesture, exact Overview, and All Apps remain Launcher-owned overlays on the ordinary SystemUI baseline.
- `CaptureCadence` — pure capture cadence/power policy.
- `LiveScreenCapture` — hidden SurfaceFlinger compatibility layer.
- `RecentsHapticHook` — version-tolerant semantic adapter for recents haptic entry.
- `WorkstationWallpaperOnlyHook` — experimental workstation snapshot/All Apps/Recents handling.

## Widget adaptation status

Widget adaptation is **not yet registry-driven**.

Current active behavior:

- feature gate: `home_grid_8x4 && grid_widget_adaptation`;
- widget detection first calls `ItemInfo.isWidget()`, then falls back to item types `4`, `5`, and `19`;
- supported spans are hard-coded in `WidgetGridSizing.isSupportedSpec()` as `1×1`, `2×1`, `2×2`, and `4×2`;
- `CellLayout.setupLayoutParam()` applies the custom allocation;
- `CellLayout.onLayout()` reasserts exact frames after MIUI's span-dependent centering pass;
- placement/occupancy ownership remains with MIUI.

`HomeGridHook.adaptTwoByOneWidget(...)` is legacy/inert code and is not the active sizing path. A later phase should remove it after characterization tests.

The planned extensibility boundary is a `WidgetClassifier` plus immutable `WidgetSpecRegistry`, allowing both widget type rules and supported spans to grow without editing the core geometry hook. That phase is not implemented yet.

## Grid invariants

The custom layout remains fixed at:

- landscape: 8 columns × 4 rows
- portrait: 4 columns × 8 rows

Important invariants:

- do not replace or reinterpret MIUI occupancy ownership;
- do not hook `addOccupied()` or `transformToHVArray()` to guess matrix orientation;
- keep `LayoutTransformRuleGridChanged` metadata consistent with the current 8-block transform;
- widget adaptation changes pixel allocation/frame geometry only, not placement semantics;
- lazy/off-screen pages must derive geometry from their own valid orientation bounds before layout.

## Liquid Glass rendering architecture

`liquid_blur_mode` is a persisted user-intent setting with `shader` as the compatibility default and `advanced_material` as the optional HyperOS SurfaceFlinger backend. `MiBlurBridge` resolves the MIUI `View.setMi*` methods once and never owns preferences; if advanced material cannot be applied, `LiquidBlurBackendPolicy` keeps the active backend on Shader without rewriting the saved choice.

The Liquid Glass view hierarchy is layered:

```text
DockLiquidGlassHostView    <- exact Dock size; final round/squircle clip
  ├─ DockLiquidGlassView   <- capture/refraction/tint; self-blurred rectangular RenderNode
  └─ DockStrokeOverlayView <- sharp Canvas highlight + DockStrokeRenderer foreground
```

In active advanced mode the AGSL `blurred()` function bypasses its 40-sample kernel and samples the source directly. The glass child is deliberately not pre-clipped to the rounded outline: SurfaceFlinger receives corner pixels first, then the host clips the composed result. Standard mode and advanced-mode runtime fallback retain the existing Shader kernel.

## Capture architecture

### Ordinary HOME/APP authority

Ordinary HOME versus APP ownership has one production authority: the existing WMShell `MultiTaskingTaskRepository` observed inside SystemUI.

The classification is intentionally small:

```text
missing repository/executor/home-task structure -> UNKNOWN
homeVisible == false                         -> APP
homeVisible + no non-HOME top fullscreen     -> HOME
homeVisible + non-HOME top fullscreen        -> conflict
```

An initial conflict returns `UNKNOWN` and recommends exactly one confirmation after 160 ms. If the same conflict persists on that confirmation it resolves APP; structural/transport failure remains UNKNOWN. There is no recurring polling, Launcher task-query fallback, or last-good HOME/APP capture fallback.

Launcher owns only higher-level/special scene signals that are earlier or more precise than ordinary task ownership: gesture targets, exact Overview/Recents state, All Apps, workstation state, Dock interaction, and capture revision/generation. Launcher `onWindowFocusChanged` and `onConfigurationChanged` are refresh triggers for the SystemUI query, not ownership evidence.

Provider death immediately clears ordinary ownership to UNKNOWN. The broker's provider watcher pushes SystemUI provider replacement/death events so a restarted SystemUI can trigger a fresh `provider-ready` ownership request even if the user remains in the same stable scene.

### Scene/source policy

`CaptureSceneState` carries `UNKNOWN`, HOME, APP, RECENTS, and ALL_APPS with revision tokens. Gesture targets, exact Recents, and All Apps can temporarily outrank the ordinary SystemUI baseline; stale asynchronous frames are rejected when scene/revision/attempt tokens no longer match.

The normal source policy is fail-closed:

- `UNKNOWN` -> wallpaper;
- HOME -> wallpaper;
- APP -> full-display capture;
- RECENTS -> wallpaper until exact Overview lifecycle confirms live Recents, then full display;
- normal All Apps -> wallpaper.

The hidden `liquid_capture_fullscreen` compatibility setting and lower-level capture/backend availability can still force a safer vendor-wallpaper fallback. Workstation mode has separate experimental source/suspension rules.

For APP full-display capture, `FreeformCaptureLeashHook` requests a display-scoped snapshot of visible freeform task leashes from SystemUI immediately before mode-1 submission. A safe snapshot is merged into the existing exclusion array; unavailable/unsafe/incomplete snapshot fails the affected capture to wallpaper. HOME never becomes a live full-display source merely because a freeform task is visible.

Capture safety additionally includes:

- scene revision / attempt tokens to reject stale asynchronous frames
- black-frame validation
- bounded APP pre-arm captures
- Recents-specific continuation while the Floating Dock window is hidden
- visibility and display-interactive gates
- dynamic APP probing/active cadence
- rotation stabilization and signature convergence

## Workstation / Laptop status

Workstation support is **experimental and incomplete**.

The source already contains:

- `LauncherModeController` / `LaptopStateManager` detection paths
- normal-layout backup/restore logic
- workstation Dock width/icon offsets
- grid and All Apps offsets
- independent Divider configuration
- native wallpaper snapshot locking/refresh
- Recents-specific capture/suspension logic

These implementations are compatibility experiments, not evidence that workstation mode is fully adapted. The mode remains outside the supported regression baseline until device-level layout, Dock, capture, All Apps, Recents, rotation and enter/exit behavior are all validated.

Documentation should therefore say **"experimental implementation exists; workstation is not yet adapted"**, not "workstation support complete".

## Stroke and shadow boundary

`DockStrokeRenderer` replaced the old layout-coupled stroke overlay. Native blur Dock installs its `StrokeDrawable` directly in the host foreground; Liquid Glass now installs the same renderer on `DockStrokeOverlayView` so the border remains outside the self-blur RenderNode. The renderer still builds validated outer/inner paths and excludes the Dock center with `clipOutPath(inner)`.

The old stroke-shadow preference keys remain part of the compatibility contract, but the current foreground renderer does not implement the previous stroke-shadow visual. Do not treat the missing old stroke shadow as a regression in the current renderer. The separate whole-Dock shadow remains a different feature.

## Adding or changing a setting

1. Define/update the persisted contract in `ConfigSchema` first.
2. Preserve historical key names, export shape and aliases unless an explicit migration is designed.
3. Use `ConfigCodec` for JSON transformation; do not reintroduce hand-written export/import key lists in activities.
4. Put settings-side data upgrades in `ConfigMigration`.
5. Put preset ownership in `PresetManager`.
6. Read runtime values through `LiquidDockConfig`; hooks/renderers must not read raw preference keys directly.
7. Add unit tests for schema/default/storage/codec behavior.
8. Run `testDebugUnitTest` and `assembleDebug`.

## Remaining modularization phases

The next safe sequence remains:

1. remove widget global state and introduce classifier/spec registry;
2. split `HomeGridHook` by responsibility, with rotation extraction last;
3. extract workstation state/controller interfaces while keeping the feature experimental;
4. reduce `MainHook` to a real composition root and implement a true zero-hook master switch;
5. split capture controller/dynamic detector/failure policy/rendering ownership out of `DockLiquidGlassView`;
6. remove compatibility facades and dead code only after characterization tests and device regression.

The historical design/implementation documents under `docs/superpowers/` are planning records. This file and `HOOKS.md` are the authoritative current-state references.