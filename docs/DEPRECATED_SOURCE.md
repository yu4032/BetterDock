# Deprecated Source Inventory

This document tracks source that is already obsolete in behavior but still exists for compatibility or because removing it safely requires a normal local patch of a large file.

Use search anchors rather than absolute line numbers. Line numbers in `DockLiquidGlassView.java` change frequently and are not a stable maintenance reference.

## Status legend

- **Dead / remove** — no longer contributes to runtime decisions and should be deleted when the containing file can be safely patched.
- **Compatibility shell / remove** — retained only because an old call site or signature still exists; it must not regain state authority.
- **Protocol tombstone / keep** — intentionally retained to prevent old/new process generations from reusing incompatible Binder transaction codes.
- **Active / do not remove** — still has real runtime responsibility.

## Freeform state-source retirement

The device-validated freeform path now uses SystemUI/WMShell `FreeformTaskListener.mTasks` as the only freeform task authority. Launcher no longer enumerates `RunningTaskInfo` to discover freeform tasks, windowing mode, visibility, or display ownership.

### `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

#### Search anchors: `surfaceLayerNameResolver`, `appLayerName`, `appLayerPkg`

Status: **Dead / remove**.

Obsolete members and methods:

- `surfaceLayerNameResolver`
- `appLayerName`
- `appLayerPkg`
- `refreshForegroundAppLayer()`
- `resolveAppLayerByUid(String)`
- attach-time `refreshForegroundAppLayer()` call and `Liquid foreground app layer` log

Why obsolete:

The historical foreground-app SurfaceFlinger layer-name path no longer participates in capture-source selection, layer exclusion, or safety decisions. `SurfaceLayerNameResolver` is already a no-op adapter. `appLayerName` is only assigned/logged and has no consumer in the capture pipeline.

Removal precondition:

Patch `DockLiquidGlassView.java` through a normal local checkout/worktree or another edit path that can produce a small reviewed diff. Do not replace the whole large file merely to remove these symbols.

#### Search anchors: `freeformLayerResolver`, `resolveFullDisplayExclusions`

Status: **Compatibility shell / remove**.

Obsolete members/calls:

- `freeformLayerResolver`
- constructor creation of `FreeformLayerResolver`
- `freeformLayerResolver.invalidate()`
- freeform-related branches inside `resolveFullDisplayExclusions()`

Why obsolete:

The final mode-1 submission gate now obtains the complete display-scoped freeform `SurfaceControl[]` snapshot from SystemUI and fails closed to wallpaper if the snapshot is unavailable. The old Dock preflight no longer owns freeform presence or safety.

Removal precondition:

Keep the existing Dock/drag exclusion behavior intact. Removing the compatibility shell must not remove the final `FreeformCaptureLeashHook` safety gate or the Dock's own exclusion handle/name path.

#### Search anchor: `liveHomeBehindFreeform`

Status: **Dead / remove**.

Why obsolete:

HOME is now unconditionally wallpaper-backed by `CaptureSourcePolicy`; visible freeform windows no longer change the HOME source to full-display capture. The value therefore has no semantic effect.

Removal precondition:

Remove the associated compatibility parameter/overload from `CaptureSourcePolicy` in the same cleanup so the API does not continue advertising a dead behavior.

### `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`

Status: **Compatibility shell / remove entire file**.

Current role:

- installs `FreeformTaskLeashResolver` into `FreeformLeashRuntime`;
- returns no Launcher-derived freeform state;
- returns no guessed freeform layer names.

Why obsolete:

The class no longer reads `ActivityManager`, `RunningTaskInfo`, display ID, visibility, windowing mode, package UID, or SurfaceFlinger state. Its remaining construction side effect should move to normal Launcher capture-hook initialization, after which the class can be deleted.

### `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`

Status: **Compatibility shell / remove entire file**.

Why obsolete:

The old `ISurfaceComposer/getLayerDebugInfo()` implementation was retired because the target HyperOS generation no longer exposes that debug API. The current class intentionally returns `null`/empty collections and must never regain layer-guessing behavior.

Removal precondition:

Delete the remaining `DockLiquidGlassView` foreground-app layer-name path first.

### `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`

#### Search anchor: `homeLiveBackdrop`

Status: **Dead compatibility API / remove**.

Why obsolete:

HOME always resolves to `WALLPAPER`, including HOME with a visible freeform task. The parameter is no longer a policy input.

Removal precondition:

Update all source-policy tests to assert the simpler signature directly.

### `src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java`

#### Search anchors: `demandProvider`, `isProviderReady`

Status: **Compatibility API / remove after Dock preflight removal**.

Why obsolete:

These methods exist for the former Dock preflight/readiness path. The final capture gate only needs a resolver installation point and `resolveForCapture(displayId)`.

Do not remove yet if any compatibility-shell call remains.

### `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java`

#### Search anchors: `setProviderDemanded`, `isProviderReady`

Status: **Compatibility API / remove after runtime preflight removal**.

Why obsolete:

The resolver should ultimately expose one operation: resolve the current display-scoped SystemUI snapshot for a capture request. Provider readiness should remain an internal transport concern, not a scene-state input.

### Tests

#### `FreeformCaptureExclusionTest.java`
#### `FreeformTaskLeashBridgeContractTest.java`

Status: **Compatibility assertions / rewrite during cleanup**.

Replace assertions that require the temporary Dock preflight shell with assertions that:

- `DockLiquidGlassView` no longer references `FreeformLayerResolver`;
- Launcher freeform resolution contains no `ActivityManager`/`RunningTaskInfo` enumeration;
- mode-1 capture still merges SystemUI-provided task leashes;
- unavailable/incomplete snapshot still falls back to wallpaper;
- no production source references `ISurfaceComposer` or `getLayerDebugInfo`.

## Binder protocol tombstones

### `src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java`

#### Search anchors: `TRANSACTION_REQUEST_LEASHES`, `TRANSACTION_LEASH_RESULT`

Status: **Protocol tombstone / keep**.

Do **not** delete or reuse these transaction numbers simply because the old per-task protocol is gone. Launcher and SystemUI can restart independently and briefly run different module generations. Reusing an old transaction code with a new payload could make mixed generations parse incompatible Parcel layouts.

A later protocol-major cleanup may remove them only if the bridge gains an explicit version negotiation mechanism that makes numeric reuse safe.

## HOME/APP ownership inference slated for shadow audit

The following code is **active today** and must not be deleted until the SystemUI shadow audit proves a replacement is reliable at capture decision boundaries.

### `src/main/java/com/hellovoid/liquiddock/MainHook.java`

Search anchors:

- `seedLauncherLifecycleState`
- `foregroundTaskWindowingMode`
- `onWindowFocusChanged`
- `LauncherSceneOwnershipPolicy.launcherOwnsScene`
- `liquid lifecycle fallback: onPause`

Current inference chain:

1. Launcher lifecycle/focus callbacks indicate whether Launcher appears resumed/focused.
2. `foregroundTaskWindowingMode()` calls `ActivityManager.getRunningTasks(1)`.
3. The top task's windowing mode is read reflectively.
4. `LauncherSceneOwnershipPolicy` treats freeform top tasks as still owned by HOME.
5. The result updates `launcherResumed`, which becomes the HOME/APP baseline for `CaptureSceneState`.

This chain remains active until a diagnostic-only comparison proves that SystemUI's existing home-task state is a safe baseline replacement.

### `src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java`

Status: **Active pending shadow audit**.

The class is intentionally simple, but it exists only to compensate for Launcher focus/lifecycle being disturbed by freeform tasks. It becomes a deletion candidate if SystemUI home visibility is proven reliable.

### `src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java`

Status: **Active pending shadow audit**.

Delete or replace only in the formal HOME/APP ownership migration, not in the diagnostic branch.

## Explicit non-candidates

The following state remains Launcher-owned and is not part of the SystemUI ownership cleanup:

- `GestureToHome`, `GestureToApp`, `GestureToRecent` target events;
- confirmed Overview enter/exit state;
- normal and workstation All Apps state;
- Dock drag/animation state;
- capture generation/revision and stale-frame rejection;
- local View/window visibility needed for rendering/capture gating;
- wallpaper offsets/zoom;
- workstation layout and Dock geometry state.

SystemUI may provide related task information, but replacing these would either lose earlier Launcher transition intent or create an unnecessary mirrored state machine.
