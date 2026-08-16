# SystemUI HOME/APP Ownership Shadow Audit Design

Date: 2026-08-16

## Problem

LiquidDock currently derives the HOME/APP baseline in Launcher from a layered inference chain:

1. Launcher focus/lifecycle callbacks;
2. `ActivityManager.getRunningTasks(1)`;
3. reflective top-task windowing mode;
4. `LauncherSceneOwnershipPolicy`, which treats a top freeform task as still owned by HOME;
5. the result is written to `launcherResumed` and then consumed by `CaptureSceneState`.

This exists because a HyperOS freeform window can pause/defocus Launcher even though HOME remains visibly underneath it.

The already validated SystemUI injection now gives LiquidDock safe read-only access to WMShell-owned state. The target HyperOS SystemUI contains:

`com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository`

with HOME/focus state including:

- `mHomeTaskInfo`
- `mLastFullscreenFocusedTaskInfo`
- `mLastMultiWindowFocusedTaskInfo`
- `isHomeVisible()`
- `getHomeTask()`
- `getTopFullscreenTaskInfo(int displayId)`

The target ROM implementation of `isHomeVisible()` is conceptually direct: HOME exists and its `RunningTaskInfo.isVisible` state is true.

The next refactor should not immediately trust this state. First prove, on the real ROM, that it agrees with the current known-good Launcher inference at meaningful capture boundaries.

## Goal

Create a diagnostic-only shadow audit that compares:

- the **existing Launcher ownership decision**, which remains authoritative; and
- **SystemUI/WMShell HOME visibility**, which is observed only for evidence.

The diagnostic must have zero influence on capture source, `launcherResumed`, `CaptureSceneState`, gesture targets, Overview, All Apps, workstation state, or any other runtime behavior.

If the comparison demonstrates that SystemUI HOME visibility is reliable in stable scenes and at the relevant source-selection boundaries, a later formal refactor may replace `foregroundTaskWindowingMode()` and `LauncherSceneOwnershipPolicy`.

## Non-goals

This diagnostic does not:

- change HOME/APP capture behavior;
- remove `foregroundTaskWindowingMode()`;
- remove `LauncherSceneOwnershipPolicy`;
- change `CaptureSceneState`;
- replace gesture target events;
- replace Overview/All Apps/workstation state;
- register a second `TaskOrganizer`;
- maintain a second task repository;
- mutate `MultiTaskingTaskRepository`;
- alter any WMShell task lifecycle callback;
- become part of the formal production fix by merge or cherry-pick.

## Branch isolation

The diagnostic implementation must live on:

`dev/home-ownership-systemui-shadow`

created from the current device-validated `fix/freeform-task-leash-exclusion` checkpoint after this design is approved.

The diagnostic branch must never be merged or cherry-picked wholesale into `fix/freeform-task-leash-exclusion` or `api101-migration`.

The eventual production cleanup must be reimplemented independently from the evidence gathered by this branch.

The long-term deprecation inventory lives on the formal branch in `docs/DEPRECATED_SOURCE.md` and is not diagnostic code.

## Source of truth under test

### Existing Launcher decision

The current production path remains untouched and authoritative:

- `seedLauncherLifecycleState()`
- `onWindowFocusChanged(boolean)`
- lifecycle fallback `onPause()`
- `foregroundTaskWindowingMode()`
- `LauncherSceneOwnershipPolicy.launcherOwnsScene(...)`

The shadow probe observes the final boolean produced by this path. It does not recompute or replace it.

### Candidate SystemUI source

The candidate replacement source is the existing WMShell/Xiaomi `MultiTaskingTaskRepository` instance.

The diagnostic observes the existing instance with a weak reference. It must never construct its own repository or register task listeners to recreate repository state.

The minimum authoritative query is:

`isHomeVisible()`

Optional diagnostic context may also read:

- `getHomeTask()`
- `getTopFullscreenTaskInfo(displayId)`
- last-focused task fields when needed to explain mismatches.

These extra values are explanatory only and never affect the MATCH/MISMATCH decision, which compares the current Launcher HOME/APP baseline with `isHomeVisible()`.

## SystemUI observation architecture

### Repository discovery

Hook only the construction/availability boundary of the existing `MultiTaskingTaskRepository` implementation after the original constructor has completed successfully.

Store only a `WeakReference<Object>` to the existing repository.

Resolve required method/field structure once during install/first observation. If the target ROM structure does not match, disable the shadow capability for the current SystemUI process and log one concise capability failure.

Do not:

- hook `onTaskAppeared`/`onTaskVanished` to mirror state;
- write repository fields;
- replace return values;
- prevent original methods from running;
- hold task `SurfaceControl` objects for this audit;
- create transactions against task leashes.

### Thread ownership

Repository state must be read on the WMShell executor that owns the repository/task state.

Reuse an existing Shell/WMShell executor reachable from the repository or its surrounding controller/organizer context. Do not read mutable repository task state directly on an arbitrary Binder thread.

If a safe owning executor cannot be identified on the target build, the shadow capability is unavailable. Do not guess a thread or read mutable containers cross-thread just to obtain diagnostics.

### IPC reuse

Reuse the already established LiquidDock broker/SystemUI provider path rather than creating another exported service or another inter-process registry.

The diagnostic branch may add a new, reserved diagnostic transaction to the existing SystemUI provider Binder. It must use a distinct transaction code and payload from the production freeform snapshot protocol.

The request contains only:

- request ID;
- display ID;
- callback Binder.

The response contains only diagnostic metadata, not `SurfaceControl` handles:

- request ID;
- status;
- `homeVisible`;
- optional HOME task ID;
- optional top fullscreen task ID/windowing metadata when safely available;
- SystemUI sample timestamp.

No diagnostic Binder result is cached as authoritative application state.

## Launcher shadow probe

### Trigger points

Run a shadow probe only after the current production path has already computed an ownership decision at these boundaries:

1. initial `seedLauncherLifecycleState()`;
2. Launcher `onWindowFocusChanged(boolean)` after `launcherOwnsScene` is computed;
3. fallback lifecycle `onPause()` after `launcherResumed` is computed.

The diagnostic must not add a new source-selection trigger.

Gesture, Overview, All Apps, workstation and rotation events are recorded as context when available but do not independently change the HOME/APP shadow decision model.

### Asynchronous-only behavior

A shadow request must never block Launcher main/UI or capture threads.

Use a one-way Binder request and asynchronous callback. Do not wait with `CountDownLatch`, `Future.get`, polling or sleep in the production decision path.

A request failure, provider absence, SystemUI restart, timeout, malformed reply or diagnostic hook failure is logged as `UNAVAILABLE` and has no runtime consequence.

Maintain only a small bounded set of pending diagnostic contexts keyed by request ID. Expire old entries after a short diagnostic horizon so SystemUI death cannot grow an unbounded map.

## Comparison semantics

Map the current Launcher decision to an expected HOME visibility baseline:

- `launcherOwnsScene == true` -> expected `homeVisible == true`
- `launcherOwnsScene == false` -> expected `homeVisible == false`

This direct comparison is valid only for HOME/APP baseline ownership. Samples taken while a Launcher-owned overlay or special scene is active must be tagged so they are not interpreted as production replacement evidence.

Context tags include at least:

- event/reason (`seed`, `focus`, `fallback-pause`);
- display ID;
- Launcher focus value when applicable;
- top windowing mode from the current production inference;
- Launcher ownership result;
- current `CaptureSceneState` desired scene when available;
- Overview active;
- All Apps active;
- workstation mode;
- request and response timestamps / latency.

## MATCH/MISMATCH classification

### MATCH

A sample is `MATCH` when the existing Launcher baseline and SystemUI `homeVisible` agree.

Examples:

- Launcher HOME and SystemUI HOME visible;
- Launcher APP and SystemUI HOME not visible.

### MISMATCH

A first disagreement is not immediately treated as a failure of the SystemUI source because focus/lifecycle and WMShell task visibility can cross the same transition at slightly different times.

For an initial mismatch:

1. log the immediate mismatch with full context;
2. schedule exactly one asynchronous re-sample after a short bounded delay (target: about 120-200 ms; implementation plan selects one constant);
3. never change production state while waiting.

Classify the re-sample as:

- `TRANSIENT_MISMATCH` if it converges to the Launcher decision;
- `PERSISTENT_MISMATCH` if it remains different;
- `UNAVAILABLE_RECHECK` if the second sample cannot be obtained.

Do not create a repeating monitor or per-frame comparison loop.

## Special-scene interpretation

The audit must distinguish baseline evidence from scenes where HOME visibility alone should not drive LiquidDock capture source.

### Overview / Recents

Launcher Overview enter/exit remains authoritative. HOME visibility may stay true underneath Recents and therefore does not by itself represent the desired `RECENTS` capture scene.

Samples while Overview is active are tagged `SPECIAL_OVERVIEW` and excluded from the decision to replace Launcher HOME/APP baseline logic.

### All Apps

All Apps is Launcher-owned. HOME task visibility may remain true while the capture scene is `ALL_APPS`.

Samples while All Apps is active are tagged `SPECIAL_ALL_APPS` and excluded from baseline replacement evidence.

### Workstation

Workstation has separate Dock/capture semantics. Samples while workstation mode is active are tagged `SPECIAL_WORKSTATION` and excluded from baseline replacement evidence.

### Gesture transitions

Gesture target events remain earlier, Launcher-owned transition intent. A temporary mismatch while a valid HOME/APP gesture target is still active is diagnostic timing evidence, not grounds to remove gesture handling.

## Logging

Use concise, machine-grep-friendly records. Suggested shape:

`[DC-SHADOW] home-ownership result=MATCH reason=focus request=42 launcherHome=true systemUiHome=true focus=true topMode=1 scene=HOME overview=false allApps=false workstation=false latencyMs=4`

For mismatches include both phases:

`result=MISMATCH phase=immediate`

followed by one of:

- `result=TRANSIENT_MISMATCH phase=recheck`
- `result=PERSISTENT_MISMATCH phase=recheck`
- `result=UNAVAILABLE_RECHECK phase=recheck`

Rate-limit repeated identical `UNAVAILABLE` infrastructure messages. MATCH records may remain enabled in the diagnostic build because the purpose of the branch is evidence gathering, but there must be no per-frame sampling.

## Fail-safe requirements

The SystemUI safety rules from the freeform leash bridge remain mandatory:

- every diagnostic hook boundary catches `Throwable`;
- after-constructor observation only;
- no original return/argument mutation;
- no WMShell lifecycle takeover;
- no second task map;
- no second organizer/listener registration;
- all mutable WMShell state reads happen on its owning executor;
- Binder caller authentication remains enforced;
- normal process death is not counted as structural corruption;
- structural mismatch disables only the diagnostic capability for that process;
- production freeform leash capability must continue working even if the HOME shadow probe is disabled.

The shadow diagnostic must be architecturally separable from the production freeform bridge so a failure in its reflection, repository discovery or logging cannot trip the production freeform capture circuit breaker.

## Evidence matrix

The device audit must exercise at least:

1. stable HOME;
2. stable fullscreen APP;
3. HOME + one freeform window;
4. APP + one freeform window;
5. APP -> HOME;
6. HOME -> APP;
7. enter/exit Recents;
8. enter/exit normal All Apps;
9. workstation mode boundaries relevant to capture;
10. rotation while HOME;
11. rotation while APP;
12. SystemUI restart/reconnect.

For HOME/APP migration, the load-bearing evidence is stable HOME/APP and the settled state after transitions, especially HOME/APP with a freeform task present.

## Acceptance criteria for a later production migration

SystemUI HOME visibility may replace the current Launcher windowing-mode inference only if the device logs show:

1. stable HOME and stable APP always agree with the current known-good baseline;
2. HOME + freeform and APP + freeform settle to the correct distinct results;
3. transition mismatches, if any, are short-lived and converge within the single bounded recheck window;
4. no persistent mismatch occurs at a capture-source decision boundary in ordinary HOME/APP operation;
5. SystemUI restart/unavailability degrades only the diagnostic and does not disturb capture;
6. special scenes are explainable by their existing Launcher-owned state rather than requiring a new SystemUI scene state machine.

If these criteria are not met, keep the existing ownership logic and use the logs to identify a narrower SystemUI source or event boundary. Do not force a migration merely to reduce code size.

## Expected production cleanup if the audit passes

A later, separately designed formal refactor may then remove or simplify:

- `MainHook.foregroundTaskWindowingMode()`;
- `LauncherSceneOwnershipPolicy`;
- its unit test;
- repeated `ActivityManager.getRunningTasks(1)` calls used only for HOME/freeform ownership correction;
- parts of lifecycle fallback whose only purpose is compensating for freeform focus loss.

It must keep:

- `CaptureSceneState` revision/stale-frame protection;
- gesture target events;
- Overview state;
- All Apps state;
- workstation state;
- local visibility/power/capture gates.

No production deletion is authorized by this shadow spec alone. The audit produces evidence for the next design cycle.
