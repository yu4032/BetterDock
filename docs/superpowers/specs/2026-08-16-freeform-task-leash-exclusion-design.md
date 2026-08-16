# Freeform Task Leash Exclusion Design

Date: 2026-08-16
Branch: `fix/freeform-task-leash-exclusion`
Base: clean `api101-migration` (`10455c6bb10d90e01c9f1299d0af0a6754ac54ab`)

## 1. Goal

Replace the obsolete freeform exclusion path based on `ISurfaceComposer/getLayerDebugInfo()` and layer-name guessing with direct exclusion of the existing HyperOS freeform task `SurfaceControl` leash.

The intended behavior is:

- HOME + freeform: preserve the existing HOME/background source semantics, while excluding every visible freeform task leash whenever the selected capture source needs full-display exclusions.
- APP + freeform: keep FULL_DISPLAY live capture, excluding the Dock surface and every visible freeform task leash.
- If every visible freeform task leash cannot be obtained safely, fail closed to the existing WALLPAPER safety fallback.
- No second `TaskOrganizer`, no duplicate freeform task state machine, no SurfaceFlinger debug API, and no layer-name guessing for freeform tasks.

The diagnostic branch `dev/freeform-capture-diagnostic` is evidence only and must not be merged into this branch.

## 2. Evidence and chosen source of truth

The uploaded HyperOS SystemUI APK contains `com.android.wm.shell.freeform.FreeformTaskListener`. Direct DEX inspection of the target APK confirms that this class has final instance fields including:

- `android.content.Context mContext`;
- `com.android.wm.shell.ShellTaskOrganizer mShellTaskOrganizer`;
- `android.util.SparseArray mTasks`.

Its nested task state contains `android.view.SurfaceControl mLeash`. The APK also exposes multiple `findTaskSurface*` symbols, so the implementation must not depend on a particular synthetic method suffix.

The implementation will instead use the already-existing listener state read-only:

`FreeformTaskListener.mTasks[taskId] -> State.mLeash`

Both fields are treated as capability probes, not assumptions. They are resolved once and type-checked. If the class, field, field type, listener instance, task entry, task organizer executor, or leash is unavailable, the capability is unavailable and capture falls back safely. No state is modified.

Launcher-side transition/Recents leashes are not used because they are temporary animation surfaces and are removed after transitions.

## 3. Architecture

The design uses three small components.

### 3.1 SystemUI: `SystemUiFreeformLeashProvider`

The Xposed module scope is extended to include `com.android.systemui` in addition to `com.miui.home`.

`ModuleMain.onPackageReady()` dispatches by package:

- `com.miui.home`: existing Launcher hooks plus the Launcher-side leash client.
- `com.android.systemui`: only the freeform leash provider hook.

The SystemUI integration observes `com.android.wm.shell.freeform.FreeformTaskListener` constructors after the original constructor completes. From the successfully constructed listener it obtains:

- a `WeakReference<Object>` to the listener;
- the existing listener `mContext` reduced to application context for broker binding;
- the existing `mShellTaskOrganizer` only to obtain its callback executor.

No additional `SystemUIApplication.onCreate()` hook is required. No hidden `ActivityThread.currentApplication()` lookup is required.

The provider never changes constructor arguments, return values, task state, transactions, task listener registration, or WMShell behavior.

It exposes a private Binder provider implementing only a leash-query protocol. It does not keep a second task map. Each request reads the current HyperOS `mTasks` and `mLeash` in place on the same task-organizer executor used by WMShell callbacks.

### 3.2 Module app: `FreeformLeashBrokerService`

The module APK declares one exported bound service used only as a Binder rendezvous point.

The broker stores only:

- the currently registered SystemUI provider `IBinder`;
- a `DeathRecipient` for that provider.

It never stores `RunningTaskInfo`, task IDs, freeform state, or `SurfaceControl` objects.

SystemUI registers its provider Binder with the broker. Launcher obtains the provider Binder from the broker. Once Launcher has the provider Binder, leash queries are direct Launcher -> SystemUI Binder traffic; the broker is not in the capture data path.

Authorization is enforced at every Binder boundary using `Binder.getCallingUid()` plus `PackageManager.getPackagesForUid()`:

- provider registration is accepted only when the caller UID maps to `com.android.systemui`;
- provider retrieval is accepted only when the caller UID maps to `com.miui.home`;
- the SystemUI provider itself accepts leash requests only from the Launcher UID mapping to `com.miui.home`.

A shared system UID may map to more than one package; therefore the check is membership of the expected package in the UID package list, not equality with a single package string. This is an availability/safety boundary, not a substitute for platform signing security.

### 3.3 Launcher: `FreeformTaskLeashResolver`

Launcher continues to use `RunningTaskInfo` as the source of visible freeform task IDs. The existing semantics for `WINDOWING_MODE_FREEFORM`, visibility, and display matching remain unchanged.

For all visible freeform tasks on the capture display, the resolver requests the corresponding leashes from the SystemUI provider and returns a single result:

- `SAFE(leashes[])` only if every currently visible freeform task has a valid leash;
- `UNAVAILABLE` if the provider is absent, any visible task lacks a valid leash, the request times out, Binder dies, or the bridge is disabled.

Partial success is not considered safe. If two visible freeform tasks exist and only one leash resolves, FULL_DISPLAY capture is blocked and the existing WALLPAPER fallback is used.

## 4. IPC protocol

### 4.1 Broker protocol

The broker protocol has only two operations:

- `REGISTER_PROVIDER(IBinder provider)` — SystemUI only.
- `GET_PROVIDER()` — Launcher only.

The provider Binder is cached by Launcher and monitored with `linkToDeath()`. Broker death only makes provider discovery temporarily unavailable; it does not invalidate an already-cached direct provider Binder.

Both SystemUI and Launcher use an explicit component plus `bindService(..., BIND_AUTO_CREATE)` to the module service. Connections are re-established after service disconnect with capped backoff. Reconnection is demand-driven so normal no-freeform operation does not create a permanent retry loop.

The broker itself never forwards a leash query and never unparcels a `SurfaceControl`.

### 4.2 Leash query protocol

The SystemUI provider accepts one batch request containing all visible freeform task IDs. Batch lookup preserves the old resolver's all-visible-task semantics while avoiding one Binder round-trip per task.

The request is asynchronous/one-way and includes an ephemeral Launcher callback Binder. The provider sends one one-way callback containing:

- request ID;
- requested task count;
- per-task task ID and success/failure status;
- each successful `SurfaceControl` copied through `Parcel`.

This prevents the capture worker from performing an unbounded synchronous Binder call.

The Launcher waits at most 25 ms for the callback. If the deadline expires, the current capture resolves as `UNAVAILABLE` and uses WALLPAPER. Late callbacks are accepted only for cleanup: any received `SurfaceControl` wrappers are released immediately and never reused for that expired capture.

No retry is performed inside the same capture request.

## 5. WMShell thread confinement

`FreeformTaskListener.mTasks` is a mutable `SparseArray` owned by WMShell. The provider must never read it directly on a SystemUI Binder pool thread.

The target `FreeformTaskListener` already owns `mShellTaskOrganizer`. Android `TaskOrganizer` dispatches organizer callbacks through its executor, and the runtime exposes `TaskOrganizer.getExecutor()` on the target framework family. The provider resolves this executor read-only from the existing `mShellTaskOrganizer` instance.

The request path is therefore:

```text
Launcher one-way Binder request
        ↓
SystemUI provider Binder thread
        ↓
validate caller + copy primitive task IDs only
        ↓
ShellTaskOrganizer.getExecutor().execute(...)
        ↓
WMShell task callback executor
        ↓
read listener.mTasks -> state.mLeash
        ↓
write copied SurfaceControls to one-way Launcher callback
```

The Binder thread never iterates `mTasks` and never blocks waiting for the shell executor.

If the task-organizer executor cannot be resolved or rejects execution, the bridge is unavailable/failing according to the error classification below. It must not fall back to reading `mTasks` from the Binder thread.

## 6. SystemUI crash isolation

SystemUI safety is the highest-priority invariant.

Every SystemUI integration boundary is wrapped so no LiquidDock exception can escape into `FreeformTaskListener`, WMShell, the WMShell executor, or a SystemUI Binder thread.

### 6.1 Hook installation

SystemUI installation is best-effort:

```text
try install
  -> capability available
catch Throwable
  -> log once
  -> disable SystemUI bridge for this process
  -> return without affecting SystemUI
```

Missing classes, changed field layout, reflection errors, task-organizer executor lookup errors, broker binding errors, or unsupported ROM structure mean only `capability unavailable`.

### 6.2 Passive observation only

The constructor hook is an after-hook. The original constructor completes first. LiquidDock does not replace return values or stop original execution.

The listener reference is weak. LiquidDock does not keep a stale WMShell object alive.

The application context used for broker binding comes from the already-constructed listener's own `mContext`; LiquidDock does not add an Application lifecycle hook solely to acquire context.

### 6.3 Provider query guard

The provider's Binder entry, shell-executor runnable, and callback send are each independently guarded by `catch (Throwable)`. Query failures are translated into failure/unavailable responses when possible and never rethrown into WMShell.

The provider performs no `SurfaceControl.Transaction`, no reparent, no visibility change, no release of the HyperOS-owned source leash, and no mutation of `mTasks`.

## 7. Circuit breaker and error classes

Not every unavailable leash is an infrastructure failure.

### 7.1 Normal unavailability — no breaker count

Examples:

- SystemUI listener not created yet;
- visible task vanished between Launcher task scan and provider lookup;
- broker/provider is reconnecting;
- Binder died during a process restart;
- a request exceeded the 25 ms Launcher deadline;
- a late callback is discarded after its request expired.

These produce `UNAVAILABLE` for the current capture and may be retried on a later capture.

### 7.2 Infrastructure failure — breaker count

Examples:

- required `mTasks`, `mLeash`, `mShellTaskOrganizer`, or executor structure is incompatible;
- repeated unexpected reflection access failures after initialization;
- repeated task-organizer executor scheduling failures;
- repeated malformed Parcel/SurfaceControl transfer failures;
- repeated unexpected Binder protocol failures;
- unknown unexpected `Throwable` inside the bridge.

After three infrastructure failures in one process lifetime, that side of the bridge calls `disableForProcess()` and stops attempting the failing operation. Process restart resets the breaker. No persistent preference is written.

If the SystemUI side is disabled, SystemUI continues normally and Launcher sees provider capability unavailable. If the Launcher side is disabled, Launcher stops bridge requests and uses the existing fallback.

## 8. Binder death and reconnection

The broker links to the registered provider Binder. When SystemUI dies, the broker clears the provider immediately.

Launcher also links to the direct provider Binder. On provider death it clears the cached provider and marks the bridge unavailable until discovery succeeds again.

After SystemUI restarts:

1. a new `FreeformTaskListener` is constructed normally;
2. the after-hook captures the new listener/context/task-organizer capability;
3. the SystemUI provider reconnects to the broker and registers the new provider Binder;
4. Launcher rediscovers it without requiring a Launcher restart.

Launcher death has no effect on SystemUI because SystemUI keeps no persistent Launcher callback. Request callbacks are per-request only.

Broker process death clears rendezvous state but does not crash either hooked process. Both sides reconnect with capped backoff: approximately 250 ms, 500 ms, 1 s, 2 s, then 5 s maximum. Aggressive reconnect is only needed when SystemUI has a listener ready or Launcher currently sees a visible freeform task.

## 9. `SurfaceControl` ownership and cleanup

The HyperOS `mLeash` object is borrowed and owned by WMShell. LiquidDock must never call `release()` on that original SystemUI object.

When writing the leash to Parcel, the provider uses normal parcel flags (`0`). It must not use `Parcelable.PARCELABLE_WRITE_RETURN_VALUE`, because that flag can transfer/release ownership semantics for `SurfaceControl` and could invalidate the sender-side wrapper.

Launcher receives a new parcel-created Java wrapper referencing the same underlying layer. That received wrapper is owned by LiquidDock Launcher-side code and must be released exactly once after the capture request has been submitted or abandoned.

To make ownership explicit, the resolver returns a closeable result object containing only the remotely received freeform wrappers. The capture preflight merges those wrappers with existing non-owned exclusions such as the Dock `SurfaceControl` for request submission. A `finally` block closes only the remote wrappers after `captureScreenAsync()` has submitted the Binder capture request. Existing Dock-owned surfaces are never released by the resolver.

Late callback results after a timeout are immediately released in the callback handler.

No `SurfaceControl` is cached across capture requests. The provider Binder is cached; task leashes are queried fresh for each safe FULL_DISPLAY preflight so task replacement cannot leave a stale remote wrapper in Launcher state.

## 10. Capture integration

`LiveScreenCapture` already supports `CaptureArgs.Builder.setExcludeLayers(SurfaceControl[])`; this remains the only freeform exclusion mechanism after the migration.

The capture preflight becomes:

```text
visible freeform tasks?
  no -> preserve existing scene/source behavior
  yes
    -> resolve all task leashes
       all valid -> FULL_DISPLAY is allowed with exclusions:
                    existing Dock SurfaceControl(s) + all freeform task leashes
       unavailable/partial/timeout/error -> FULL_DISPLAY is not safe
                                            -> existing WALLPAPER fallback
```

The scene ownership policy is not modified by this fix.

The old `SurfaceLayerNameResolver` / UID-to-SurfaceFlinger layer-name chain is removed from the freeform capture decision path. It may be deleted if no other production caller remains after implementation. No replacement path may call `ISurfaceComposer`, `getLayerDebugInfo()`, or guess freeform layers by string name.

The existing `Floating Dock` name exclusion used by HyperOS mode 1 is not expanded into a new freeform name scheme. Freeform exclusion is exclusively by `SurfaceControl` task leash.

## 11. Multiple freeform windows

All visible `WINDOWING_MODE_FREEFORM` tasks on the relevant display are considered. The provider request is batched by task ID.

Safety is all-or-nothing:

- 0 visible freeform tasks: no leash requirement.
- N visible freeform tasks and N valid leashes: safe FULL_DISPLAY exclusion.
- N visible freeform tasks and fewer than N valid leashes: unsafe, use WALLPAPER.

Duplicates are removed by task ID before the Binder request. Returned surfaces are associated with requested task IDs so a malformed or reordered response cannot be mistaken for complete coverage.

If a task vanishes after Launcher scanned it but before WMShell lookup, the batch is incomplete and the current capture falls back to WALLPAPER. The next capture rescans tasks and may recover immediately. This race is normal unavailability, not an infrastructure failure.

## 12. Context acquisition and service binding

SystemUI uses the `Context` already stored by the successfully constructed `FreeformTaskListener` (`mContext`), reduced to application context. Direct DEX inspection of the uploaded target APK confirms that this field is present and final.

Launcher uses an existing Launcher/Dock context and immediately reduces it to application context before constructing the broker client.

Failure to obtain either context disables only bridge availability for that process.

No `ActivityThread.currentApplication()` reflection is part of the normal path.

## 13. Required production changes

Expected files/components:

- `META-INF/xposed/scope.list`: add `com.android.systemui`.
- `ModuleMain.java`: package-specific dispatch with fully isolated SystemUI initialization.
- `FreeformLeashBrokerService.java`: module-app rendezvous service.
- `AndroidManifest.xml`: exported broker service declaration.
- `SystemUiFreeformLeashProvider.java`: passive `FreeformTaskListener` constructor observation, read-only state/executor capability resolution, and provider Binder.
- `FreeformLeashBrokerClient.java`: small process-local connection/discovery helper.
- `FreeformTaskLeashResolver.java`: Launcher visible-task scan + timed batch leash resolution + closeable ownership result.
- `DockLiquidGlassView.java`: replace freeform layer-name preflight with leash result and merge the returned surfaces into existing `SurfaceControl[]` capture exclusions.
- `FreeformLayerResolver.java` / `SurfaceLayerNameResolver.java`: remove from freeform production path; delete only when confirmed unused.

No SystemUI source is patched. All behavior is injected by the LiquidDock module at runtime.

## 14. Tests and verification gates

### 14.1 Pure/unit tests

Add tests for:

- all-visible-task coverage policy: full coverage is safe, partial coverage is unsafe;
- task ID deduplication and display filtering;
- normal unavailability does not trip the breaker;
- three infrastructure failures disable the bridge for the process;
- timed-out/late response state rejects the result and requires cleanup;
- provider death clears the cached provider;
- broker authorization policy accepts only expected package membership for each operation;
- batch response task IDs must exactly cover the requested set before the result is safe.

### 14.2 Source/contract tests

Add contract tests asserting:

- Xposed scope contains both `com.miui.home` and `com.android.systemui`;
- `ModuleMain` isolates SystemUI initialization in `try/catch(Throwable)` and does not run Launcher hooks in SystemUI;
- SystemUI only after-hooks `FreeformTaskListener` construction and does not register a `TaskOrganizer`;
- SystemUI lookup posts to the existing task-organizer executor before reading `mTasks`/`mLeash`;
- there is no Binder-thread fallback that directly reads `mTasks`;
- production code contains no freeform call to `ISurfaceComposer` or `getLayerDebugInfo()`;
- `SurfaceControl` is written with flags `0`, not `PARCELABLE_WRITE_RETURN_VALUE`;
- partial leash resolution cannot allow FULL_DISPLAY;
- existing WALLPAPER fallback remains present when freeform exclusion is unsafe.

### 14.3 Device verification

Device testing must cover at least:

1. APP with no freeform: unchanged live capture.
2. APP + one freeform: underlying APP remains live; freeform is absent from Dock glass.
3. APP + multiple visible freeforms if HyperOS permits it: all are excluded.
4. HOME + freeform: existing HOME semantics remain; freeform is excluded whenever full-display capture is selected.
5. Open/close/move freeform repeatedly: no stale surface crash and no permanent wallpaper state after the freeform disappears.
6. Restart SystemUI while Launcher remains alive: no SystemUI crash; temporary WALLPAPER fallback; automatic recovery after provider re-registration.
7. Kill/restart module app broker: no SystemUI/Launcher crash; temporary fallback; recovery after rebind.
8. Deliberately make the provider unavailable: no capture recursion; fallback remains WALLPAPER.
9. Force or simulate a leash response delay beyond 25 ms: current frame falls back without hanging capture; later frames recover.
10. Rotation with freeform present: no regression to the previously fixed black-frame/rotation behavior.
11. Workstation, All Apps, Recents, and normal Dock capture regressions remain unchanged.

SystemUI stability is a release gate: any uncaught LiquidDock exception in SystemUI, ANR attributable to the bridge, Binder-thread read of mutable WMShell task state, or WMShell state mutation fails the fix even if visual capture is correct.

## 15. Non-goals

This fix does not:

- alter HOME/APP/RECENTS ownership rules;
- modify HyperOS freeform behavior;
- register another `TaskOrganizer`;
- maintain a duplicate task lifecycle map;
- patch SystemUI APK files;
- repair or rename `ISurfaceComposer` APIs;
- revive `getLayerDebugInfo()`;
- infer freeform surfaces from package UID or layer names;
- change workstation or All Apps semantics;
- add a permanent diagnostic logger;
- cache task `SurfaceControl` wrappers across capture requests.

## 16. Acceptance criteria

The fix is acceptable only when all of the following are true:

1. With a visible freeform task above a fullscreen app, LiquidDock uses FULL_DISPLAY only when every visible freeform task has a valid direct task leash exclusion.
2. The freeform window itself does not appear inside the Dock glass.
3. Failure of SystemUI injection, reflection, broker binding, Binder transport, executor scheduling, timeout, or leash lookup cannot crash SystemUI or Launcher and results in the existing WALLPAPER safety fallback.
4. No second organizer or duplicate freeform state machine exists.
5. The obsolete UID -> SurfaceFlinger debug layer-name chain is no longer part of production freeform exclusion.
6. Received remote `SurfaceControl` wrappers are deterministically released without releasing WMShell-owned originals.
7. SystemUI/broker process death produces temporary degradation only and recovers without requiring a full device reboot.
8. Mutable WMShell `mTasks` state is read only on the existing task-organizer callback executor, never directly from a Binder thread.
