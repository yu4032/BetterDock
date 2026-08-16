# One-Shot Freeform Capture Diagnostic — Design

## Goal

Add a one-shot, read-only diagnostic inside the injected Launcher process that captures enough evidence from one freeform/small-window reproduction to identify why an intended live full-display Dock capture is falling back to wallpaper.

The diagnostic must not change capture behavior.

## Failure path under investigation

The production pipeline can currently reach:

```text
visible freeform task detected
-> production FreeformLayerResolver has one or more freeform owner UIDs
-> SurfaceLayerNameResolver resolves zero layer names or throws
-> fullDisplayExclusions.safe = false
-> FULL_DISPLAY is downgraded to WALLPAPER
```

The production resolver historically catches its SurfaceFlinger failure and returns an empty layer list. Its per-layer `getOwnerUid()` and `getName()` helpers also collapse accessor failures to `null`. The device therefore cannot distinguish hidden-API failure, empty SF results, layer-metadata API mismatch, UID ownership mismatch, or a later exclusion/capture problem.

## Trigger

Run automatically at most once per Launcher process lifetime.

The trigger is the production freeform layer-resolution boundary:

```text
DockLiquidGlassView.resolveFullDisplayExclusions()
-> FreeformLayerResolver.resolveVisibleLayerNames()
-> production layer resolution completes or fails
-> one-shot diagnostic report
-> production caller computes the existing `safe` value unchanged
```

The report only claims the one-shot gate when either:

- production has identified at least one freeform owner UID; or
- an independent diagnostic task scan identifies a visible non-Launcher freeform task.

This lets the report still run when package-UID lookup itself is the failure.

No settings toggle or manual trigger is added.

## Architecture

### FreeformCaptureDiagnostic

A dedicated diagnostic-only component owns the one-shot gate and grouped logging.

It consumes immutable snapshots/facts and never participates in capture policy. It must not:

- modify `CaptureSceneState` or `CaptureSourcePolicy`;
- call `captureScreenAsync`, `captureLayerAsync`, `captureDisplay`, or any visual capture API;
- change capture source, visibility, native-background ownership, workstation state, or scheduling;
- cache information for later production decisions.

It emits one report using Android log tag `LiquidDockDiag` and one diagnostic id.

### FreeformLayerResolver

Production behavior remains the same:

```text
RunningTaskInfo -> visible freeform package -> package UID
-> resolveAllByOwnerUids(...)
-> cache resolved names, or empty list on failure
```

Diagnostic additions are isolated:

1. an independent `snapshotForDiagnostics()` task scan that does not read/write production caches;
2. a diagnostic-only `visibleFreeformDetected` fact so UID lookup failure can still trigger a report;
3. preservation of the actual `Throwable` caught during production `resolveAllByOwnerUids(...)`, passed only to the report before being discarded as before;
4. a single `FreeformCaptureDiagnostic.runOnce(...)` call at the resolver boundary.

The returned collection and cache semantics must remain unchanged.

The task snapshot records, where available:

- taskId;
- displayId;
- windowingMode;
- isVisible;
- topActivity;
- baseActivity;
- bounds;
- package name;
- package UID;
- whether the task is considered visible freeform;
- package-UID or snapshot error.

### SurfaceLayerNameResolver

Production `queryLayers()`, `resolveTopmostByOwnerUid()`, and `resolveAllByOwnerUids()` remain unchanged.

A separate `snapshotForDiagnostics(targetUids, keywords)` repeats the SurfaceFlinger query with explicit stage reporting:

```text
ServiceManager.getService("SurfaceFlinger")
-> ISurfaceComposer.Stub.asInterface(...)
-> getLayerDebugInfo method lookup
-> getLayerDebugInfo invocation
-> per-layer metadata access
-> candidate extraction
```

It reports:

- exact failing stage and underlying exception class/message;
- total returned layer count;
- first observed layer class;
- whether `getOwnerUid()` and `getName()` accessors are available;
- how many layer records returned readable owner UID/name values;
- the first layer-metadata accessor error;
- candidate layer names/owner UIDs and optional metadata.

Candidate layers are the union of:

1. `ownerUid` matching a detected/production freeform UID;
2. names containing freeform package/activity keywords;
3. names containing suspicious terms such as `freeform`, `miuifreeform`, `task`, `leash`, or `window`.

For candidates, optional reflected metadata such as id/parent/z is recorded when available; absence is represented as `-` and is not a failure.

### DockLiquidGlassView

No diagnostic code is added to this large class.

Its existing production flow remains authoritative and unchanged. The resolver-boundary report runs transitively while `resolveFullDisplayExclusions()` is evaluating the freeform exclusion list, before the existing `safe -> WALLPAPER` decision.

## Report

One reproduction emits a bounded report:

```text
LiquidDockDiag: BEGIN id=...
LiquidDockDiag: PROCESS ...
LiquidDockDiag: TASKS ...
LiquidDockDiag: TASK ...
LiquidDockDiag: SF ...
LiquidDockDiag: LAYER ...
LiquidDockDiag: RESOLVER ...
LiquidDockDiag: DECISION ...
LiquidDockDiag: END id=...
```

### TASKS / TASK

Shows the independent `RunningTaskInfo` view, including which task is considered freeform and whether package UID resolution succeeded.

### SF / LAYER

Shows SurfaceFlinger API availability, total layer count, first layer class, metadata getter availability/readability, metadata errors, candidate layers, owner UIDs, keyword matches, suspicious system layers, and optional id/parent/z metadata.

### RESOLVER

Shows the actual production facts used by the failing resolver call:

```text
productionOwnerUids
resolvedLayers
productionResolutionError
productionSafe = ownerUids empty OR resolvedLayers non-empty
```

This directly mirrors the current downstream safety condition without modifying it.

### DECISION

Reports whether current production facts imply the existing safety fallback:

```text
wouldSafetyFallback = productionOwnerUids non-empty AND resolvedLayers empty
```

The diagnostic classifies the evidence as one of:

```text
TASK_DETECTION_FAILED
SURFACEFLINGER_API_FAILED
SURFACEFLINGER_EMPTY_RESULT
LAYER_METADATA_API_FAILED
UID_MATCH_FAILED
PRODUCTION_RESOLVER_FAILED
POST_RESOLUTION_CAPTURE_PATH
UNKNOWN
```

Classification is informational only.

Interpretation examples:

```text
SURFACEFLINGER_API_FAILED
-> service/interface/getLayerDebugInfo lookup or invocation failed.

SURFACEFLINGER_EMPTY_RESULT
-> getLayerDebugInfo succeeded but returned no layers.

LAYER_METADATA_API_FAILED
-> layer list exists but getOwnerUid/getName cannot be read on this framework build.

UID_MATCH_FAILED
-> SF metadata is readable but no layer is owned by the freeform App UID;
   inspect suspicious task/leash/system-owned candidates.

PRODUCTION_RESOLVER_FAILED
-> independent SF diagnostic works, but the actual production resolver threw.

POST_RESOLUTION_CAPTURE_PATH
-> production resolver already returned layer names; investigate mode-1 exclusion/capture later.
```

## One-shot and error handling

`AtomicBoolean.compareAndSet(false, true)` claims the report only after a freeform condition is established. Once claimed, it remains claimed even if diagnostics throw, preventing repeated expensive scans.

All report errors are caught internally. A claimed report always attempts to emit `END`. The diagnostic never rethrows into production capture.

## Testing

Source-contract tests verify:

- an `AtomicBoolean` one-shot gate exists;
- diagnostic code contains no visual capture calls;
- both resolvers expose diagnostic snapshot APIs;
- SF metadata accessor/readability evidence is retained;
- `FreeformLayerResolver` invokes the diagnostic at its production resolution boundary;
- the actual production resolution `Throwable` is captured for logging instead of changing control flow;
- `DockLiquidGlassView` still contains its existing `FULL_DISPLAY && !safe -> WALLPAPER` fallback;
- `DockLiquidGlassView` itself contains no diagnostic integration.

## Scope constraints

Do not change the current freeform capture result in this diagnostic build.

Specifically, do not:

- remove or weaken `fullDisplayExclusions.safe`;
- change HOME/APP/RECENTS scene ownership;
- change workstation All Apps or Recents behavior;
- change `CaptureSourcePolicy`;
- change `LiveScreenCapture` capture modes/exclusion semantics;
- introduce an underlay resolver;
- save screenshots or binary artifacts;
- add settings UI.

Keep the diagnostic isolated so it can be deleted cleanly once the root cause is confirmed.

All commits remain on `dev/freeform-capture-diagnostic` and use `[skip ci]` unless a build is explicitly requested.