# One-Shot Freeform Capture Diagnostic — Design

## Goal

Add a one-shot, read-only diagnostic path that runs inside the injected Launcher process when a visible freeform/small-window task exists and the Dock is about to start its next capture. The diagnostic must gather enough evidence in one reproduction to identify why live full-display capture is being downgraded to wallpaper capture.

The diagnostic must not change capture behavior.

## Current failure path

Current capture behavior can reach this sequence:

```text
visible freeform task detected
-> FreeformLayerResolver resolves zero SurfaceFlinger layer names
-> fullDisplayExclusions.safe = false
-> requested FULL_DISPLAY is downgraded to WALLPAPER
```

The current implementation silently absorbs most resolver failures, so device logs do not reveal whether the failure is task detection, SurfaceFlinger access, UID matching, layer naming, or a later exclusion/capture step.

## Trigger

Run automatically once per Launcher process lifetime when all conditions are true:

```text
freeform diagnostic not yet attempted
AND at least one visible freeform task is detected
AND DockLiquidGlassView is entering capture preflight
```

The diagnostic is marked attempted even if it throws. This prevents repeated SurfaceFlinger scans and log spam.

No settings toggle or manual trigger is added.

## Architecture

### FreeformCaptureDiagnostic

Add a dedicated diagnostic component whose only responsibility is to snapshot and log the capture decision and freeform-resolution environment.

It must not:

- modify `CaptureSceneState`;
- modify `CaptureSourcePolicy` output;
- call `captureScreenAsync`, `captureLayerAsync`, or any visual capture API;
- change `sourceDirty`, `capturing`, visibility, native-background ownership, or workstation state;
- cache data used later by production capture decisions.

The component emits one grouped report using the log tag/prefix `LiquidDockDiag` and a single diagnostic id.

### FreeformLayerResolver

Expose a diagnostic snapshot API that reports the same RunningTaskInfo facts used by production detection without changing production resolution semantics.

The snapshot should include, where available:

- taskId;
- displayId;
- windowingMode;
- isVisible;
- topActivity package/class;
- baseActivity package/class;
- task bounds;
- package UID;
- whether `FreeformCapturePolicy.shouldExclude(...)` considers the task a visible freeform task.

The diagnostic should inspect the first 16–32 running tasks so task ordering and underlay relationships are visible.

### SurfaceLayerNameResolver

Expose a diagnostic snapshot API around `ISurfaceComposer.getLayerDebugInfo()`.

It should report:

- whether SurfaceFlinger service lookup succeeded;
- whether `ISurfaceComposer.Stub.asInterface(...)` succeeded;
- whether `getLayerDebugInfo()` exists and invocation succeeded;
- the exact exception class and message on failure;
- returned layer count on success;
- ownerUid/name pairs for candidate layers.

Candidate layer groups should include:

1. layers whose ownerUid equals a detected freeform app UID;
2. layers whose names contain the freeform package/activity keywords;
3. suspicious system layers whose names contain case-insensitive terms such as `freeform`, `task`, `leash`, `window`, or `miuifreeform`.

If extra fields such as parent/id/z are available through reflection, include them; otherwise log them as unavailable rather than treating that as a diagnostic failure.

### DockLiquidGlassView

Add only a small preflight call that passes current capture facts to `FreeformCaptureDiagnostic.runOnce(...)` before the existing safety fallback is applied.

The invocation must not branch production behavior.

## Report contents

A single report should contain these sections under one diagnostic id:

```text
[LiquidDockDiag] BEGIN id=...
```

### Capture preflight

- workstationMode;
- fullscreenCapture;
- effective useFullscreen;
- current CaptureScene;
- selected/requested CaptureSourcePolicy.Source;
- launcher lifecycle/focus facts available to DockLiquidGlassView;
- displayId;
- dockWindowLayerName;
- whether the Dock SurfaceControl is currently considered valid;
- current dragLayerName if available.

### Running tasks

For each inspected task, print the fields listed above and explicitly identify tasks treated as visible freeform tasks.

### SurfaceFlinger layer enumeration

Print the API availability/failure evidence and candidate layers. Do not dump every SurfaceFlinger layer unconditionally; candidate filtering keeps the report bounded while still exposing likely freeform task/leash layers.

### Existing resolver result

Print:

- `freeformActive`;
- freeform app UIDs detected from tasks;
- `resolvedFreeformLayers` from the existing production resolver;
- merged exclusion layer names;
- resulting `fullDisplayExclusions.safe` value.

### Decision consequence

Print:

- originally selected source;
- whether the existing safety rule will downgrade FULL_DISPLAY to WALLPAPER;
- the exact reason category inferred from the snapshot when possible.

The diagnostic may classify the observation as one of:

```text
TASK_DETECTION_FAILED
SURFACEFLINGER_API_FAILED
UID_MATCH_FAILED
LAYER_RESOLUTION_SUCCEEDED
POST_RESOLUTION_CAPTURE_PATH
UNKNOWN
```

Classification is informational only and must not drive production behavior.

Finish with:

```text
[LiquidDockDiag] END id=...
```

## Error handling

Diagnostic errors must never affect capture. `runOnce(...)` catches all diagnostic exceptions, logs the exact exception class/message, emits END, and leaves the existing production flow unchanged.

The diagnostic attempt flag is set before expensive work starts so a failure cannot cause repeated scans every frame.

## Testing

Add focused tests/source contracts that verify:

- the diagnostic is one-shot;
- it does not call any capture method;
- it does not write capture scene/source state;
- production `safe -> WALLPAPER` fallback remains unchanged;
- current freeform production resolver behavior remains unchanged;
- diagnostic task/layer snapshot helpers surface exceptions instead of silently swallowing them;
- `DockLiquidGlassView` only invokes the diagnostic from capture preflight.

## Scope constraints

Do not change the current freeform capture behavior in this diagnostic build.

Specifically, do not:

- remove or weaken `fullDisplayExclusions.safe`;
- change HOME/APP/RECENTS scene ownership;
- change workstation All Apps or Recents behavior;
- change `CaptureSourcePolicy`;
- change `LiveScreenCapture` capture mode or exclusion semantics;
- introduce a new underlay resolver;
- save screenshots or other binary diagnostics;
- add settings UI.

Keep the diagnostic code isolated so it can be removed cleanly after the root cause is confirmed.

Commits use `[skip ci]` unless a build is explicitly requested.