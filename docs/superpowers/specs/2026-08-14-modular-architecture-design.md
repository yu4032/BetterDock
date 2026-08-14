# LiquidDock modular architecture refactor design

Date: 2026-08-14

Branch: `refactor/modular-architecture`

Baseline: `api101-migration` at `e2c21a59de3ba6a8aae5277422e601abb1438292`

## 1. Goal

Refactor LiquidDock into independently maintainable feature modules without changing user-visible behavior or configuration compatibility.

The refactor must make it possible to add, modify, disable, or remove a feature without needing to understand or modify unrelated modules. Configuration must have one authoritative schema, runtime configuration loading must be side-effect free, widget detection and span handling must be extensible, and `MainHook`, `HomeGridHook`, and `DockLiquidGlassView` must stop acting as multi-purpose ownership points.

This is an internal architecture refactor, not a feature redesign.

## 2. Compatibility contract

The refactor uses strict behavior preservation.

The following are compatibility contracts:

- Existing SharedPreferences keys remain valid and are not renamed.
- Existing exported JSON remains importable.
- Existing JSON field names remain stable.
- Existing defaults remain unchanged.
- Existing presets remain behaviorally unchanged.
- Existing GUI structure, visible defaults, and current feature behavior remain unchanged unless fixing an already identified defect.
- Existing 8x4 landscape / 4x8 portrait behavior remains unchanged.
- Existing rotation behavior and MIUI occupancy ownership remain unchanged.
- Existing Liquid Glass capture behavior, scene ordering, retry behavior, rotation recovery, and rendering output remain unchanged during extraction.

Explicitly allowed defect fixes are limited to:

- `grid_widget_adaptation` participating in configuration export/import;
- configuration loading no longer mutating widget runtime state;
- dead widget adaptation code being removed after equivalent active paths are covered by tests;
- the master switch preventing optional LiquidDock feature hooks from being installed.

Extensibility mechanisms must not silently enable new behavior for existing users. In particular, the initial widget span registry preserves the currently supported 1x1, 2x1, 2x2, and 4x2 behavior. Future span types are enabled by adding/activating registry policy, not by making every currently present unknown span active during this refactor.

## 3. Refactor strategy

Use layered, incremental replacement rather than a file-splitting rewrite or parallel full reimplementation.

For each subsystem:

1. establish tests that capture current behavior;
2. introduce the new abstraction beside the current code;
3. migrate one responsibility at a time;
4. keep temporary compatibility adapters when necessary;
5. run `testDebugUnitTest` and `assembleDebug` after every safe checkpoint;
6. remove the old path only after the new path is verified equivalent.

Every phase must be independently revertible. A phase must not proceed while CI is failing.

## 4. Execution phases

### Phase 1: configuration convergence

Goals:

- fix the missing `grid_widget_adaptation` JSON round trip;
- establish a single typed configuration schema;
- separate configuration migration, codec, presets, and runtime snapshots;
- make `LiquidDockConfig.load()` side-effect free.

Target structure:

```text
config/
  ConfigSchema
  ConfigKey<T>
  ConfigCodec
  ConfigMigration
  ConfigProvider
  LiquidDockConfig
  PresetManager
```

`ConfigSchema` is the authoritative definition for configurable values. A definition may include key, type, default, legal range, unit/encoding behavior, export/import participation, and legacy aliases where required.

The GUI still owns presentation: page placement, label, summary, and conditional enablement. It must reuse schema key/default/range instead of redefining them.

`LiquidDockConfig` remains an immutable typed runtime snapshot. Constructing or loading it may read, validate, and parse configuration only. It must not install hooks, modify views, mutate feature state, or call setters on other modules.

`ConfigProvider` is the runtime source of immutable `LiquidDockConfig` snapshots. It preserves the current distinction between process-start settings and settings that are refreshed while the launcher process remains alive, without allowing individual modules to invent independent configuration loading side effects.

`ConfigCodec` owns JSON serialization/deserialization. Existing JSON keys remain unchanged. `ConfigMigration` owns all legacy preference migration logic currently embedded in settings activities; migration behavior is moved before it is simplified.

Preset values remain unchanged. They can later be expressed through typed keys, but no preset behavior changes are allowed in this refactor.

### Phase 2: widget isolation and extensibility

Goals:

- restore `WidgetGridSizing` to a stateless geometry helper;
- remove direct `itemType == 4 || itemType == 5 || itemType == 19` checks from core widget hooks;
- allow future widget type and span expansion without modifying the core hook;
- ensure disabling widget adaptation installs no widget adaptation behavior.

Target structure:

```text
grid/widget/
  WidgetClassifier
  WidgetTypeRegistry
  WidgetSpanRegistry
  WidgetSpanPolicy
  WidgetAdaptationHook
  WidgetGridSizing
```

`WidgetClassifier` returns a `WidgetClassification` describing whether the item is a widget and its span. Detection rules are registered through `WidgetTypeRegistry`.

Initial compatibility rules preserve current behavior:

1. prefer MIUI `ItemInfo.isWidget()` when available and true;
2. fall back to registered current MIUI widget item types 4, 5, and 19.

The numeric MIUI item types are compatibility constants, not user configuration. Future rules can be registered by type, class, method behavior, or another explicit compatibility predicate without changing `WidgetAdaptationHook`.

Span extensibility is registry/policy driven. The initial registry contains the currently supported 1x1, 2x1, 2x2, and 4x2 spans so this refactor does not change existing behavior. The core hook does not contain a fixed whitelist. Future 3x2, 4x1, 4x4, or generic bounded-span policies can be registered without changing the hook or geometry code.

`WidgetGridSizing` contains geometry calculation and bounds validation only. It owns no global enable flag and has no configuration side effects. Once a span has been accepted by `WidgetSpanPolicy`, geometry is computed generically from cell coordinates and X/Y pitch.

The existing two-stage adaptation behavior is preserved:

- adjust `CellLayout.setupLayoutParam()` results;
- reassert the exact final widget frame after MIUI `CellLayout.onLayout()`.

The obsolete `adaptTwoByOneWidget()` path and its associated logging state are removed only after equivalent behavior is covered by the active generalized path.

### Phase 3: grid decomposition

Goals:

- remove widget, rotation, indicator, folder, refresh, and workstation responsibilities from one large `HomeGridHook` implementation;
- centralize grid dimensions and compatibility mappings;
- move geometry calculations into testable pure functions.

Target structure:

```text
grid/
  HomeGridModule
  GridSpec
  GridMetrics
  GridGeometry
  GridGeometryHook
  GridRotationHook
  WorkspaceRefreshController
  PageIndicatorHook
  FolderAlignmentHook
```

`GridSpec` centralizes the current established grid:

```text
landscape: 8 x 4
portrait:  4 x 8
native pad compatibility count: 6
```

Existing scattered 6/8/4 mappings are replaced by named `GridSpec` behavior rather than moved as unexplained magic values.

`GridGeometry` is pure: it receives native grid metrics, configured insets/offsets, orientation, active `GridSpec`, and an external adjustment. It returns cell size, gaps, paddings, and coordinate arrays. It does not use reflection and does not modify launcher objects.

`GridGeometryHook` translates between MIUI reflection fields and `GridGeometry`.

`GridRotationHook` preserves the existing rotation contract. MIUI continues to own occupied matrices and native transforms. The refactor must not hook or replace `addOccupied()` or `transformToHVArray()` and must not reimplement occupancy storage.

`WorkspaceRefreshController` exclusively owns rotation/lazy-page refresh timing and readiness. Existing validated stabilization behavior is preserved before any timing optimization is considered.

`PageIndicatorHook` and `FolderAlignmentHook` own only their respective behaviors and can be removed independently.

`HomeGridModule` becomes a thin assembler that installs only the enabled grid submodules.

### Phase 4: workstation isolation

Goals:

- establish one owner for workstation mode state;
- remove workstation-specific state from `MainHook` and `HomeGridHook`;
- stop workstation logic from hooking Liquid Glass implementation methods;
- allow the workstation feature to be removed without modifying normal grid, dock, or glass internals.

Target structure:

```text
workstation/
  WorkstationModule
  WorkstationModeController
  WorkstationDockHook
  WorkstationGridAdjustment
  WorkstationAllAppsHook
  WorkstationWallpaperHook
  WorkstationLayoutStateStore
```

`WorkstationModeController` is the single workstation-mode state owner. It encapsulates current and legacy HyperOS detection APIs and exposes state/listener behavior through a small interface.

No other module may call `MainHook.isWorkstationMode()` or maintain a duplicate workstation boolean.

Grid receives an abstract `GridAdjustmentProvider`, not Mingou/laptop implementation details. The normal provider returns no adjustment; `WorkstationGridAdjustment` provides requested workstation home or All Apps translations.

`WorkstationWallpaperHook` owns native snapshot mode, live blur suppression, and snapshot refresh boundaries. It does not hook `DockLiquidGlassView` methods. Launcher/recents events are routed through shared lifecycle/state and each active feature decides whether it handles the event.

`WorkstationAllAppsHook` owns the current post-layout vertical correction and related transition handling. Wallpaper behavior and All Apps geometry do not remain coupled.

Normal home item backup/restore state moves from `MainHook` to `WorkstationLayoutStateStore`. Entering workstation backs up the normal layout; leaving restores it and requests workspace refresh. No other module owns this data.

### Phase 5: MainHook reduction and runtime wiring

Goals:

- make `MainHook` a composition root;
- move dock implementation state out of `MainHook`;
- move launcher lifecycle state out of `MainHook`;
- make master enablement an outer gate.

Target structure:

```text
runtime/
  FeatureContext
  LauncherState
  LauncherLifecycleHook
  LiquidDockLogger

dock/
  DockModule
  DockGeometryHook
  DockResizeController
  DockShadowController
  DockViewBinding
```

`FeatureContext` carries only shared runtime dependencies: class loader, `ConfigProvider`, workstation state abstraction, launcher state, and logger. It is not a service locator and does not hold feature business state.

Modules take an immutable `LiquidDockConfig` snapshot when installing process-start-only hooks. Modules that already support live refresh request a new immutable snapshot through `ConfigProvider` at the same effective cadence/boundary as the current implementation. No module mutates configuration or another module while reading it.

`LauncherState` owns shared launcher lifecycle observations such as lifecycle-known/resumed, SystemUI panel expansion, and recents state. `LauncherLifecycleHook` translates HyperOS callbacks into this state. Glass and other consumers react to state rather than installing overlapping lifecycle ownership.

Dock responsibilities move as follows:

- width/height/spacing/bottom/radius behavior -> `DockGeometryHook`;
- resize animation bypass/replacement and animator ownership -> `DockResizeController`;
- shadow view/state/render geometry -> `DockShadowController`;
- references to native dock background/workspace binding -> `DockViewBinding`.

Logging is provided through a logger abstraction instead of feature code depending on `MainHook.log()`.

The master switch is checked before any optional feature module is installed. API101 module loading may still occur, but disabled LiquidDock leaves optional launcher behavior untouched.

Hook installation order is explicit and documented. Dependencies are provided through constructors/small interfaces rather than implied static state or accidental call order.

### Phase 6: Liquid Glass decomposition

Goals:

- break `DockLiquidGlassView` into view, rendering, capture orchestration, motion policy, and failure policy components;
- preserve capture and rendering behavior exactly during extraction;
- make Liquid Glass removable without disabling Dock/Grid/Workstation-native behavior.

Target structure:

```text
glass/
  LiquidGlassModule
  DockLiquidGlassView
  LiquidGlassController
  GlassRenderer
  GlassShaderSource
  GlassRuntimeState

glass/capture/
  CaptureController
  CaptureRequest
  CaptureResult
  CaptureFailurePolicy
  DynamicMotionDetector
  CaptureCadence
  CaptureScene
  CaptureSceneState
  LiveScreenCapture
```

Extraction order is intentionally conservative:

1. move shader source without changing shader text;
2. extract renderer without changing uniforms/rendering math;
3. extract dynamic motion detection as pure policy;
4. extract capture failure/backoff policy as pure policy;
5. establish `CaptureController` initially delegating equivalent behavior;
6. move scene/cadence orchestration under capture controller;
7. switch launcher lifecycle consumption to shared `LauncherState`;
8. remove capture ownership from the view only after equivalence is established.

`DockLiquidGlassView` ultimately owns Android View lifecycle and drawing entry points only.

`GlassRenderer` owns RuntimeShader/Paint/frame and rendering uniforms, but does not decide when or what to capture.

`CaptureController` is the unique owner of capture request lifecycle, in-flight/coalescing state, generation/revision validation, retry scheduling, capture client reset, rotation stabilization, and accepting/rejecting async frames.

Existing `CaptureSceneState`, `CaptureCadence`, and `LiveScreenCapture` are retained as existing useful boundaries and are not rewritten merely for style.

`CaptureFailurePolicy` expresses current timeout, black-frame, reset, and backoff decisions. Current proven recovery behavior, including discarding/rebuilding a stuck live capture client before the corresponding retry path, must be preserved.

`DynamicMotionDetector` answers whether application content is active/static. `CaptureCadence` determines requested cadence. `CaptureController` decides whether a capture is actually issued.

Rotation capture behavior is treated as a first-class state. Old-generation/revision frames must never overwrite post-rotation frames.

Algorithm implementation constants such as shader kernel weights remain internal named constants. User policy parameters such as blur, IOR, thickness, capture scale, brightness, and FPS remain configuration-driven. The goal is to eliminate unexplained magic values and duplicated configuration definitions, not to expose every algorithm coefficient in JSON.

### Phase 7: final convergence

Goals:

- remove transitional adapters and dead legacy paths;
- update architecture/hook documentation;
- verify module removal boundaries and configuration compatibility;
- prepare for device regression testing before any merge into `api101-migration`.

No merge back to `api101-migration` is implied by completion of code refactoring alone. Device regression verification is a separate acceptance gate.

## 5. Module boundary rules

Every feature unit has one clear owner for mutable state and one narrow public capability boundary.

Rules:

- configuration snapshots do not mutate feature state;
- hooks translate vendor callbacks into module/controller calls but do not own unrelated rendering or business state;
- pure policy classes do not depend on Android views or Xposed;
- modules depend on capabilities/interfaces, not large concrete modules;
- no feature hooks another LiquidDock feature's implementation class to coordinate behavior;
- disabling a feature prevents its feature-specific hooks from being installed;
- deleting a feature requires removing its module registration and its own files/config/UI, not editing unrelated feature internals;
- vendor compatibility constants remain centralized and named, but are not necessarily user configuration.

Examples of narrow capabilities:

```text
WorkspaceRefresh.refreshAllPages()
GridAdjustmentProvider.current()
DockBoundsProvider.dockBounds()
LauncherState snapshot/listener
```

## 6. Widget extensibility contract

Widget extensibility covers both detection and span while preserving current behavior by default.

Detection:

- current MIUI `isWidget()` behavior remains primary;
- current item type fallbacks remain registered compatibility rules;
- future rules can be added to `WidgetTypeRegistry` without modifying the core adaptation hook.

Span:

- the core hook and geometry code contain no built-in size-specific branching;
- the initial `WidgetSpanRegistry` enables only the currently supported 1x1, 2x1, 2x2, and 4x2 specs;
- future specs or a generic bounded-span policy can be registered without modifying the hook;
- every accepted span still passes positive-size and placement-bound validation;
- `WidgetGridSizing` computes geometry from X/Y coordinate boundaries and pitch and therefore supports independent horizontal and vertical pitch for any accepted span.

Current tiling invariants remain regression contracts:

- two adjacent 1x1 allocations tile the same horizontal footprint as one 2x1;
- two stacked 2x1 allocations tile the same footprint as one 2x2;
- four 1x1 allocations tile the same footprint as one 2x2.

## 7. Configuration ownership rules

Configurable values fall into three categories:

1. user policy parameters -> `ConfigSchema`;
2. HyperOS/vendor compatibility constants -> named compatibility classes/registries;
3. algorithm implementation constants -> named constants/resources inside their implementation module.

Do not move vendor class names, item type codes, shader kernel weights, or internal capture API modes into user JSON merely to avoid hardcoded literals.

Do move user-controlled defaults, bounds, units, serialization rules, and feature enablement into the authoritative schema.

## 8. Error handling and compatibility fallback

Vendor reflection/hook failures remain feature-local and fail soft where the existing implementation already fails soft.

A failed optional compatibility hook must:

- log through the shared logger;
- avoid corrupting shared state;
- avoid installing a partial replacement that violates current behavior;
- leave unrelated modules operational.

Compatibility fallback selection, such as current vs legacy workstation state APIs or optimized vs launcher wallpaper capture fallback, remains explicit and tested where possible.

No refactor phase intentionally hides a failure that currently produces actionable debug logging.

## 9. Testing strategy

Every implementation phase begins by establishing or extending tests for the behavior being moved.

### Configuration tests

- all schema keys are unique;
- defaults are legal and parseable;
- numeric defaults fall within declared bounds;
- export -> import -> export preserves supported values;
- legacy JSON fixtures import correctly;
- `grid_widget_adaptation` defaults to false and round-trips;
- loading a runtime config snapshot does not mutate widget/grid/glass global state.

### Widget/grid tests

- widget classifier uses `isWidget()` correctly;
- fallback type registry preserves current types;
- unknown item types do not become widgets without a matching rule;
- registry can add a new type without modifying the core hook;
- initial span registry preserves only 1x1, 2x1, 2x2, and 4x2 behavior;
- a test-only registered 3x2 or 4x4 policy proves span extensibility without changing default production behavior;
- invalid/out-of-bounds spans are rejected;
- grid orientation mapping and native compatibility mapping remain correct;
- grid geometry is tested in portrait/landscape with independent X/Y pitch and configured offsets;
- existing tiling invariants remain valid;
- widget adaptation disabled policy installs no widget adaptation behavior.

### Workstation/runtime tests

- workstation enter/leave state and listener notification;
- layout backup/restore ownership;
- normal grid calculation does not require the workstation module;
- Liquid Glass absence does not break Dock;
- master disabled installs no optional feature modules;
- launcher state transitions are deterministic at the policy level.

### Liquid Glass tests

- capture failure/backoff/reset policy;
- motion detection threshold/hold behavior;
- capture coalescing;
- stale scene revision rejection;
- stale capture generation rejection;
- workstation suspension;
- scene transitions;
- rotation-old-frame rejection;
- renderer config/geometry to shader-uniform mapping where practical.

Existing `CaptureSceneStateTest`, `CaptureCadenceTest`, and widget geometry tests remain regression assets and are extended rather than discarded.

## 10. Verification gates

Each phase must pass:

```text
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

The phase diff is inspected for accidental behavior changes before moving on.

For high-risk Glass phases, automated tests are necessary but not sufficient. Device regression is required before merging the refactor back into the stable migration branch.

Critical device behaviors to preserve include:

- 8x4/4x8 rotation stability and lazy page initialization;
- icon/widget placement and no occupancy regression;
- widget adaptation off by default;
- Dock geometry/stroke/shadow behavior;
- normal/workstation mode transition behavior;
- HOME/APP/RECENTS capture selection;
- rotation capture recovery;
- no stale/black frame reintroduction.

## 11. Definition of done

The refactor is complete when all of the following are true:

- configuration has one authoritative typed schema for user-configurable behavior;
- configuration loading is side-effect free;
- current and legacy configuration formats remain compatible;
- widget type rules and span policy are independently extensible without enabling unverified span behavior by default;
- widget adaptation is an independent optional module;
- grid geometry, rotation, indicator, folder alignment, and workspace refresh have separate owners;
- workstation state has one owner and does not invade normal feature implementations;
- `MainHook`/entry code is primarily composition and enablement;
- Dock implementation state no longer lives in `MainHook`;
- Liquid Glass view, renderer, capture controller, motion policy, and failure policy are separate responsibilities;
- optional feature removal does not require changes to unrelated module internals;
- each phase's unit tests and debug build pass;
- architecture and hook documentation describe the new ownership model;
- device regression passes before merge into `api101-migration`.

## 12. Non-goals

This refactor does not:

- redesign the UI;
- change visual tuning or presets;
- rename public/current preference keys;
- change the 8x4/4x8 product behavior;
- reimplement MIUI occupancy storage;
- replace the proven SurfaceFlinger compatibility implementation solely for style;
- expose all implementation constants to configuration;
- introduce a dependency injection framework or generalized plugin framework.

The architecture remains explicit, small, and appropriate for an Xposed module that must tolerate HyperOS private API changes.
