# HOME/APP Ownership Convergence Design

**Date:** 2026-08-16  
**Status:** Approved for implementation  
**Target branch:** `fix/freeform-task-leash-exclusion`  
**Implementation branch:** `fix/home-app-ownership-convergence`

## Goal

Make SystemUI/WMShell the single authoritative source for the ordinary HOME-versus-APP baseline used by LiquidDock capture. Remove Launcher-side foreground-task inference (`ActivityManager.getRunningTasks(1)`, foreground windowing-mode inspection, and `LauncherSceneOwnershipPolicy`) from the production ownership path.

The migration must preserve the already device-verified freeform leash exclusion path and all Launcher-owned special-scene signals (gesture target, Recents/Overview, All Apps, workstation state, Dock interaction, and capture revision/generation).

## Evidence from the diagnostic shadow

The diagnostic branch `dev/home-ownership-systemui-shadow` compared the current Launcher decision against `MultiTaskingTaskRepository` without changing capture behavior.

The decisive device run showed:

- Normal HOME: `launcherHome=true`, `rawHomeVisible=true`, no non-HOME fullscreen task -> combined baseline HOME.
- APP launch while HOME remained visible: `launcherHome=false`, `rawHomeVisible=true`, `topFullscreenTaskId != homeTaskId` -> combined baseline APP. This fixes the false HOME result produced by `isHomeVisible()` alone.
- APP + freeform and freeform exit: combined baseline remained APP.
- HOME + freeform: combined baseline remained HOME.
- One reverse-transition sample briefly had `rawHomeVisible=true` and a stale non-HOME fullscreen task while Launcher was already HOME. A single recheck about 164 ms later had no non-HOME fullscreen task and resolved HOME.
- No persistent mismatch and no UNKNOWN result occurred in the final eligible run.

Therefore `isHomeVisible()` is necessary but insufficient. `getTopFullscreenTaskInfo(displayId)` supplies the missing APP takeover evidence, while the conflicting overlap state requires one bounded confirmation rather than a permanent priority rule.

## Non-goals

This migration does not move the following authorities into SystemUI:

- GestureToHome / GestureToApp / GestureToRecent target events.
- Exact Overview entry/exit.
- All Apps overlay state.
- Workstation/laptop mode.
- Dock drag, animation, visibility, geometry, and interaction state.
- Notification/control-center expansion handling.
- `CaptureSceneState` revision/generation freshness guards.
- Freeform task leash ownership itself; that remains the existing `FreeformTaskListener` snapshot capability.

It also does not add focused-task or last-focused-task signals. The final shadow run did not produce a persistent mismatch requiring those additional private APIs.

## SystemUI architecture

### 1. Shared SystemUI provider transport

Introduce a small process-local `SystemUiTaskStateProvider` that owns only the Binder provider token and broker registration. It owns no task map and no HOME/APP state.

The provider Binder multiplexes independent capabilities:

1. existing visible-freeform-leash snapshot requests;
2. HOME/APP baseline requests.

`SystemUiFreeformLeashProvider` and the new HOME source become transaction handlers rather than separately owning provider registration.

`SystemUiTaskStateProvider.attachContext(Context)` may be called by either a constructed `FreeformTaskListener` or a constructed `MultiTaskingTaskRepository`. The first valid context creates the single existing `FreeformLeashBrokerClient(Role.SYSTEM_UI)` and registers the shared Binder. This removes the current accidental dependency that Launcher can discover a provider only after `FreeformTaskListener` has been observed.

Freeform and HOME capabilities keep independent structural health. A HOME-source failure must not trip the freeform circuit breaker; a freeform failure must not disable HOME ownership.

### 2. Shell task-state executor source

Introduce `SystemUiTaskExecutorSource` which passively hooks existing `com.android.wm.shell.ShellTaskOrganizer` construction and reads its existing `getExecutor()` result after construction.

It must never instantiate `ShellTaskOrganizer`, register another organizer, or maintain a task collection.

The executor is required because the target ROM dispatches `TaskOrganizer` task callbacks through the `ShellTaskOrganizer` executor, and Xiaomi's multitasking listener updates `MultiTaskingTaskRepository` directly from those callbacks. HOME repository reads therefore run on the same executor.

Failure to obtain the executor makes HOME sampling unavailable; it does not fall back to another thread or another state source.

### 3. HOME ownership source

Introduce `SystemUiHomeOwnershipSource` as a read-only observer of the existing:

`com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository`

At install time it resolves, once:

- `mContext`;
- `isHomeVisible()`;
- `getHomeTask()`;
- `getTopFullscreenTaskInfo(int displayId)`.

Each constructor is hooked after the original constructor completes. The source stores only a weak reference to the current repository and the resolved methods. It does not mirror `mTasks`, task lifecycles, or task IDs between callbacks.

Every request is scheduled on the executor published by `SystemUiTaskExecutorSource`.

## HOME/APP classification

The SystemUI source owns all interpretation of task repository data. Launcher never receives `RunningTaskInfo`, task IDs, package names, or windowing modes.

For an ordinary request:

1. If repository, executor, home task structure, or required method result is unavailable -> `UNKNOWN`.
2. If `isHomeVisible() == false` -> `APP`.
3. If HOME is visible and there is no non-HOME top fullscreen task -> `HOME`.
4. If HOME is visible and a non-HOME top fullscreen task exists -> conflict/transition ambiguity.

For the first conflict request, SystemUI responds `UNKNOWN` with `retryRecommended=true`. Launcher immediately becomes baseline UNKNOWN and therefore wallpaper-backed, then schedules exactly one confirmation request after 160 ms.

For a confirmation request:

- if the conflict has cleared -> return the now-observed HOME or APP result;
- if HOME remains visible and the same class of non-HOME fullscreen evidence remains -> return `APP`;
- if structure or transport is unavailable -> `UNKNOWN`.

There is no repeating timer, polling loop, debounce state machine, or last-known-result fallback.

The 160 ms confirmation is deliberately bounded to one retry. It is justified by the device shadow evidence: the one reverse-transition conflict resolved after about 164 ms, whereas true APP takeovers repeatedly retained the non-HOME fullscreen evidence.

## Production protocol

Create a production `HomeOwnershipProtocol` separate from the diagnostic protocol.

Use a fresh provider transaction code rather than reusing the diagnostic shadow transaction. The diagnostic code is treated as a tombstone so mixed diagnostic/production process generations cannot parse each other's payloads accidentally.

Request payload:

- provider interface token;
- protocol version;
- request ID;
- display ID;
- `confirmation` flag;
- callback Binder.

Response payload:

- callback interface token;
- protocol version;
- request ID;
- status;
- baseline (`HOME`, `APP`, or `UNKNOWN`);
- `retryRecommended` flag.

No task metadata crosses into Launcher.

Mixed-version behavior is fail-closed. A rejected transaction, malformed version, provider death, timeout, or unknown status returns UNKNOWN and must not poison the freeform capability breaker.

## Launcher architecture

### 1. HomeOwnershipResolver

Add an asynchronous Launcher-only `HomeOwnershipResolver`.

It obtains the existing SystemUI provider through the broker transport. It never calls ActivityManager and never blocks the main or capture thread.

Requests are one-way Binder calls with bounded pending correlation. Callback parsing produces only `HOME`, `APP`, or `UNKNOWN` plus the retry recommendation.

The resolver tracks no task state. It may track only request IDs, pending callback correlation, and one scheduled confirmation for the current conflict request.

Provider unavailability or death emits UNKNOWN immediately. Provider recovery triggers a fresh baseline request.

### 2. HomeOwnershipRuntime

Add a thin process-local `HomeOwnershipRuntime` that binds the current `DockLiquidGlassView` and the resolver.

Responsibilities:

- set the initial baseline to UNKNOWN;
- request a baseline when the glass view is created;
- use Launcher `onWindowFocusChanged` only as an event trigger for a SystemUI query, never as ownership evidence;
- apply HOME/APP/UNKNOWN results to the glass scene input;
- on an APP transition, invoke the existing APP backdrop pre-arm boundary;
- on HOME return, invoke the existing HOME-return boundary;
- on UNKNOWN, immediately select the fail-closed unknown baseline.

The runtime must not cache a last-good HOME/APP value for fallback.

### 3. Existing broker transport

Do not create a second task-state authority or a second SystemUI task repository.

The current broker service remains a token rendezvous only. It never stores HOME/APP state.

The ownership resolver may share the existing provider transport implementation, but its pending requests, timeout handling, and health must remain independent from freeform leash resolution.

## Capture scene integration

Add `UNKNOWN` to `CaptureScene` and make `CaptureSceneState` start in UNKNOWN instead of APP.

Baseline resolution becomes:

- active gesture target -> target scene (unchanged Launcher authority);
- confirmed Recents -> RECENTS;
- active All Apps -> ALL_APPS;
- known SystemUI baseline HOME -> HOME;
- known SystemUI baseline APP -> APP;
- SystemUI baseline UNKNOWN -> UNKNOWN.

`CaptureSourcePolicy` maps UNKNOWN to WALLPAPER.

This satisfies the approved fail-closed rule:

> If SystemUI HOME/APP ownership is unavailable or structurally ambiguous, LiquidDock does not use Launcher foreground-task inference and does not preserve a last-known APP/HOME decision. The ordinary baseline is wallpaper-backed until a valid SystemUI result arrives.

Gesture targets remain a separate Launcher-owned predictive transition signal, as already approved. Recents, All Apps, and workstation special paths keep their current authority and source rules.

## MainHook cleanup

Remove HOME/APP ownership responsibilities from `MainHook`:

- remove `foregroundTaskWindowingMode()`;
- remove every `ActivityManager.getRunningTasks(1)` call used by HOME/APP ownership;
- remove every call to `LauncherSceneOwnershipPolicy`;
- remove lifecycle/focus code that writes `launcherResumed` / `launcherLifecycleKnown` as ownership state;
- remove `seedLauncherLifecycleState()` ownership inference;
- remove the lifecycle fallback that converts `onPause` to an ownership decision;
- change `onWindowFocusChanged` to query `HomeOwnershipRuntime` only;
- initial glass creation binds/requests the SystemUI ownership runtime rather than seeding from Launcher lifecycle.

Direct Launcher lifecycle hooks that exist only to log the old ownership path should be deleted rather than retained as dead diagnostics.

Window visibility remains a capture trigger, not an ownership signal.

## Legacy foreground app-layer cleanup

`DockLiquidGlassView.refreshForegroundAppLayer()` and `resolveAppLayerByUid()` are already behavior-dead for final capture submission: mode-1 capture is governed by the final task-leash exclusion hook, not by guessed foreground layer names.

The migration must remove calls from the ownership path. If tooling permits a safe exact edit of `DockLiquidGlassView`, remove the dead `getRunningTasks()` app-layer probe and its cached fields as part of this convergence so Launcher has no remaining task-query side channel. If the giant file cannot be safely patched with the available repository tool, runtime ownership convergence must not be blocked; the dead source must remain explicitly listed in `docs/DEPRECATED_SOURCE.md` for physical deletion from a real local checkout. It must not participate in any decision.

## Error handling and safety

- No LiquidDock exception may escape into SystemUI.
- HOME source structural failure disables only the HOME capability for the SystemUI process.
- Freeform breaker remains independent.
- Provider registration failure is transport failure; both capabilities remain fail-closed until rediscovered.
- Launcher request rejection, timeout, provider death, malformed callback, or version mismatch -> UNKNOWN.
- UNKNOWN never invokes Launcher `getRunningTasks`, Launcher focus ownership, or last-good-state fallback.
- All Binder callbacks are one-way.
- No request blocks the Launcher main thread or capture worker.

## Removal boundary

After the migration, `LauncherSceneOwnershipPolicy.java` is deleted. Its tests are replaced by tests of SystemUI baseline classification and `CaptureSceneState` UNKNOWN behavior.

The diagnostic-only classes from `dev/home-ownership-systemui-shadow` are not merged wholesale. The production implementation is written from this spec and uses a fresh protocol transaction code.

## Tests

Pure Java / contract tests must cover at least:

1. HOME visible + no non-HOME fullscreen -> HOME.
2. HOME not visible -> APP.
3. HOME visible + non-HOME fullscreen on initial sample -> UNKNOWN + retry.
4. Same conflict on confirmation -> APP.
5. Missing home task -> UNKNOWN.
6. Missing repository or executor -> UNKNOWN.
7. `CaptureSceneState` defaults to UNKNOWN.
8. UNKNOWN baseline -> `CaptureScene.UNKNOWN` -> wallpaper source.
9. Gesture target still overrides ordinary baseline.
10. Recents and All Apps still override ordinary baseline.
11. Source contract: `MainHook` contains no `getRunningTasks`, `foregroundTaskWindowingMode`, or `LauncherSceneOwnershipPolicy` reference.
12. Deleted `LauncherSceneOwnershipPolicy` has no remaining references.
13. HOME provider failure cannot trip the freeform breaker.
14. Freeform provider failure cannot disable HOME source state.
15. Protocol version / malformed response fails closed.

## Device validation

After unit/static validation, test on the target HyperOS device after rebooting both Launcher and SystemUI:

- HOME;
- APP;
- HOME -> APP;
- APP -> HOME;
- APP + freeform;
- HOME + freeform;
- freeform close back to underlying APP/HOME;
- repeated HOME <-> APP transitions;
- Recents;
- All Apps;
- rotation;
- workstation if available.

Acceptance criteria:

- no Launcher ownership `getRunningTasks` path executes;
- normal HOME is wallpaper-backed;
- normal APP is full-display backed;
- freeform above HOME does not demote HOME to APP;
- freeform above APP remains APP with task-leash exclusion;
- transient conflict is wallpaper-backed until the one confirmation resolves;
- no persistent UNKNOWN in stable HOME/APP under normal provider health;
- freeform capture behavior remains device-equivalent to the already verified branch;
- no SystemUI crash or escaped exception.

## Rollback boundary

The implementation is isolated on `fix/home-app-ownership-convergence`. The diagnostic branch is not merged. The formal branch receives only the approved spec/plan until implementation validation is complete.
