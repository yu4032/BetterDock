# SystemUI State-Source Convergence Design

Date: 2026-08-16
Branch: `fix/freeform-task-leash-exclusion`

## Goal

Reduce LiquidDock's remaining Launcher-side system-state inference now that the module already injects safely into SystemUI/WMShell.

This phase deliberately has a narrow scope:

1. Make SystemUI's existing `FreeformTaskListener.mTasks` the single source of truth for visible freeform-task presence, task enumeration, and task leashes.
2. Delete the dead foreground-app SurfaceFlinger layer-name path.

This phase does **not** change HOME/APP ownership, gesture-target logic, Overview/Recents state, All Apps state, Workstation/Laptop state, notification-shade state, capture revisioning, or capture cadence.

## Current Redundancy

The current freeform path is only partially converged:

- SystemUI already provides canonical task leashes from `FreeformTaskListener`.
- Launcher still calls `ActivityManager.getRunningTasks()` in `FreeformLayerResolver` to infer freeform presence.
- Launcher calls `ActivityManager.getRunningTasks()` again in `FreeformTaskLeashResolver` to enumerate task IDs.
- Both Launcher-side paths reflect hidden `displayId`, `getWindowingMode()`, and `isVisible` state.
- `DockLiquidGlassView` still contains the old foreground-app layer-name fields and refresh path even though `SurfaceLayerNameResolver` is now a no-op and the resulting name is not consumed by capture policy.

This leaves two state authorities for one concept and keeps hidden-API inference in Launcher unnecessarily.

## Target Architecture

### SystemUI is the only freeform-task authority

`SystemUiFreeformLeashProvider` already holds a weak reference to the existing HyperOS `FreeformTaskListener` and executes reads on the existing `ShellTaskOrganizer` executor.

The provider will expose one all-or-nothing operation:

```text
requestVisibleFreeformLeashes(displayId)
```

On the Shell executor it will:

1. Read `FreeformTaskListener.mTasks`.
2. Iterate the existing task states. `mTasks` is already the freeform listener's task set, so no Launcher-side `windowingMode == FREEFORM` test is required.
3. Read each state's `mTaskInfo` and `mLeash`.
4. Skip tasks that are definitely invisible.
5. Skip tasks that are definitely on a different display.
6. Treat unknown visibility as potentially visible.
7. Treat unknown display ID as belonging to the candidate set rather than risking a missed exclusion.
8. Require every selected task to have a non-null, valid leash.
9. Return the complete SurfaceControl list in one callback.

If enumeration, metadata access, or leash validation fails in a way that could make the snapshot incomplete, the provider returns infrastructure failure rather than a partial list.

### Launcher no longer enumerates system tasks

`FreeformTaskLeashResolver` will no longer call `ActivityManager.getRunningTasks()` and will no longer inspect `RunningTaskInfo`.

Its job becomes only:

```text
current displayId
    -> provider request
    -> wait up to 25 ms
    -> validate returned SurfaceControl wrappers
    -> Resolution
```

The current display ID comes from the local `Display`, which is public API. Hidden task metadata remains confined to the SystemUI provider.

### Capture gate remains the final safety boundary

`FreeformCaptureLeashHook` remains the only authority immediately before a mode-1 FULL_DISPLAY capture is submitted.

For mode 1:

```text
SystemUI snapshot succeeds, count = 0
    -> FULL_DISPLAY with existing Dock exclusions

SystemUI snapshot succeeds, count > 0
    -> FULL_DISPLAY + every returned freeform leash

provider unavailable / timeout / malformed response / incomplete snapshot / invalid leash
    -> rewrite this capture to mode 2 WALLPAPER
```

For non-mode-1 captures, no SystemUI freeform query is needed and provider demand can be released.

This keeps the proven fail-closed behavior while eliminating the earlier preflight task scan.

## Protocol Simplification

The existing protocol currently sends Launcher-enumerated task IDs to SystemUI and receives per-task results. That structure exists only because Launcher was acting as the task authority.

The new request carries only:

```text
requestId
displayId
callbackBinder
```

The callback carries:

```text
requestId
overallStatus
count
SurfaceControl[count]
```

No task-ID coverage comparison is needed because SystemUI creates the snapshot itself.

The provider caps the number of returned freeform tasks at `MAX_TASKS` (currently 32). If the authoritative task set exceeds the cap, the whole request fails closed instead of truncating.

A new transaction code is used for the converged request. This is intentional process-restart compatibility protection: if Launcher and SystemUI temporarily run different module generations, an unsupported transaction fails to wallpaper rather than being interpreted under the wrong wire format.

`SurfaceControl` ownership rules remain unchanged:

- SystemUI never releases or mutates the canonical WMShell leash.
- SystemUI writes the leash with parcel flags `0`, never `PARCELABLE_WRITE_RETURN_VALUE`.
- Launcher owns only the parcel-created wrappers it receives.
- Launcher releases all wrappers on success completion, malformed replies, timeouts, partial failures, and stale callbacks.

## Removing FreeformLayerResolver

After the capture gate becomes the only freeform safety boundary, `DockLiquidGlassView` no longer needs a separate freeform-presence preflight.

Therefore:

- remove the `FreeformLayerResolver` field and construction;
- remove its invalidation calls;
- remove freeform layer-name merging from `resolveFullDisplayExclusions()`;
- remove the now-meaningless `liveHomeBehindFreeform` calculation;
- keep HOME source semantics unchanged: HOME is always WALLPAPER;
- keep APP source semantics unchanged: APP is FULL_DISPLAY, subject to the capture gate.

`FreeformLayerResolver.java` can then be deleted entirely.

`FreeformCapturePolicy.java` can also be deleted if it has no remaining consumers after Launcher task enumeration is removed.

## Removing Dead Foreground-App Layer State

`DockLiquidGlassView` still contains an obsolete chain:

```text
ActivityManager.getRunningTasks(1)
    -> topActivity package
    -> package UID
    -> SurfaceLayerNameResolver.resolveTopmostByOwnerUid()
    -> appLayerName
```

The production `SurfaceLayerNameResolver` is already a no-op, and `appLayerName` is no longer consumed by capture source selection or exclusion.

Delete:

- `surfaceLayerNameResolver` field;
- `appLayerName` field;
- `appLayerPkg` field;
- `refreshForegroundAppLayer()`;
- `resolveAppLayerByUid()`;
- constructor initialization of `SurfaceLayerNameResolver`;
- attach/focus refresh calls and logs that only serve this state.

If no consumers remain, delete `SurfaceLayerNameResolver.java` itself.

This deletion is independent of the new SystemUI path; dead state should not be replaced with another remote query.

## Failure and Crash Isolation

All existing SystemUI safety rules remain mandatory.

- Only an after-constructor observation hook captures `FreeformTaskListener`.
- No second `TaskOrganizer` is registered.
- No second task map is maintained by LiquidDock.
- `mTasks` is read only on the existing Shell executor.
- No `SurfaceControl.Transaction` is submitted by the provider.
- No canonical leash is released.
- Any install-time structural mismatch disables the SystemUI bridge for that process.
- Runtime infrastructure failures use the existing circuit breaker.
- Launcher death, SystemUI death, broker death, provider absence, timeout, and normal task disappearance are treated as availability failures and fall back to wallpaper.
- No exception from LiquidDock may propagate into SystemUI/WMShell.

### Hidden metadata fallback

Because `RunningTaskInfo.displayId` and some visibility metadata are hidden from the public SDK, SystemUI-side access is reflective.

Fail-closed rules are:

- known different display -> skip;
- known invisible -> skip;
- unknown display -> include candidate;
- unknown visibility -> include candidate;
- candidate with missing/invalid leash -> fail the whole snapshot.

Including an uncertain candidate may exclude an extra surface that is not part of the captured display; missing a real freeform candidate would be unsafe, so uncertainty biases toward inclusion.

## State That Explicitly Stays in Launcher

This phase must not migrate these sources to SystemUI:

- `GestureToHome`, `GestureToApp`, `GestureToRecent` target events;
- `EnterOverviewStateEvent` / `ExitOverviewStateEvent` and the authoritative `overviewActive` latch;
- All Apps state;
- Launcher-owned Workstation/Laptop callbacks;
- Dock drag and animation state;
- local View/window visibility;
- capture generation, scene revision, timeout breaker, and stale-frame rejection.

These are either Launcher-owned UI state or local asynchronous-capture correctness state, not global task-state inference.

## Deferred Follow-up: HOME/APP Ownership Audit

The current Launcher baseline still combines lifecycle/focus with `getRunningTasks(1)` and `LauncherSceneOwnershipPolicy` to distinguish a real fullscreen APP takeover from Launcher remaining underneath a freeform window.

SystemUI/WMShell has cleaner candidates such as `ShellTaskOrganizer` focused-task state and Xiaomi's multitasking task repository/home visibility state. However replacing the baseline scene authority affects more transitions than freeform exclusion.

That work is explicitly deferred to a separate diagnostic phase after this convergence is device-verified. The diagnostic should compare current Launcher-derived HOME/APP baseline against SystemUI home/focused-task state without changing behavior. Only after equivalence is established should `foregroundTaskWindowingMode()` and `LauncherSceneOwnershipPolicy` be considered for removal.

## Verification

### Static/contract checks

Tests should lock down that:

1. `FreeformTaskLeashResolver` contains no `ActivityManager`, `getRunningTasks`, `RunningTaskInfo`, `getWindowingMode`, task `displayId` reflection, or task visibility reflection.
2. `SystemUiFreeformLeashProvider` enumerates `mTasks` only on the Shell executor.
3. The provider owns all display/visibility filtering and uses fail-closed metadata rules.
4. The wire request no longer contains Launcher-supplied task IDs.
5. The callback is all-or-nothing and capped by `MAX_TASKS`.
6. `FreeformCaptureLeashHook` still rewrites unsafe mode-1 requests to mode 2.
7. `DockLiquidGlassView` no longer references `FreeformLayerResolver`, `SurfaceLayerNameResolver`, `appLayerName`, `appLayerPkg`, or `refreshForegroundAppLayer`.
8. HOME remains WALLPAPER and APP remains FULL_DISPLAY in `CaptureSourcePolicy` tests.
9. Recents, All Apps, Workstation, and scene-state tests remain unchanged except for compilation adjustments caused by dead-code removal.

### Device matrix

At minimum verify:

- APP without freeform -> live FULL_DISPLAY;
- APP with one freeform -> live FULL_DISPLAY excluding the freeform;
- APP with multiple freeforms -> all freeforms excluded;
- close freeform during capture -> no crash, no stale-handle leak;
- HOME with/without freeform -> WALLPAPER;
- APP -> HOME -> APP with a freeform present -> correct source switches;
- Recents entry/exit unchanged;
- Workstation paths unchanged;
- SystemUI restart while Launcher stays alive -> temporary WALLPAPER fallback, then recovery;
- Launcher restart while SystemUI stays alive -> provider rediscovery and recovery;
- provider unavailable or protocol-version mismatch -> WALLPAPER, never unexcluded FULL_DISPLAY.

## Success Criteria

This phase is complete when:

- SystemUI/WMShell is the only authority enumerating freeform tasks;
- Launcher no longer inspects `RunningTaskInfo` for freeform exclusion;
- the dead foreground-app layer-name state is removed rather than replaced;
- no behavior changes outside freeform exclusion and removal of dead code;
- all unsafe or unavailable states still fail to WALLPAPER;
- device testing confirms no SystemUI or Launcher crash and no freeform self-capture regression.
