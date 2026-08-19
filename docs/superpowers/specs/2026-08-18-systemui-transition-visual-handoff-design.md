# SystemUI Transition Visual Handoff Design

## Goal

Replace the remaining Launcher-owned APP↔HOME backdrop handoff heuristics with one authoritative SystemUI/WMShell transition event chain while preserving the existing exact-Overview and pointer-cadence behavior.

## Motivation

HyperOS 3.0.307+ exposes a reliable WMShell transition lifecycle in SystemUI. Device logs show that the transition is visible in SystemUI before Launcher emits `GestureToHome`, and its finish boundary aligns with the end of the Launcher APP→HOME animation. Previous experiments that refreshed HOME ownership from Launcher focus caused repeated `UNKNOWN → APP/HOME` lifecycle churn and disrupted backdrop continuity.

The migration must therefore separate two concepts:

- **Scene ownership**: stable HOME/APP bootstrap state and exact Overview state.
- **Transition visual handoff**: a temporary APP→Launcher visual hold driven only by WMShell transition lifecycle.

SystemUI transition events must never directly call Launcher focus/lifecycle APIs or force `UNKNOWN` ownership.

## Authoritative State Model

### APP_TO_LAUNCHER

A WMShell transition is classified as `APP_TO_LAUNCHER` when its `TransitionInfo` contains all of the following on the same display:

1. A standard application task moving away from the foreground (`TO_BACK` / equivalent closing mode).
2. A HOME activity-type task moving to the foreground.
3. The HOME change carries wallpaper visibility (`SHOW_WALLPAPER`) or the same transition carries a wallpaper change moving to the foreground.

This is intentionally **not called APP_TO_HOME**. HyperOS uses the same Launcher/Recents surface handoff while the gesture may still resolve to Overview. The transition only says that the app is being handed to Launcher-owned surfaces.

### Final resolution

- `APP_TO_LAUNCHER_START` begins a visual hold of the last valid APP backdrop.
- If exact `EnterOverviewStateEvent` arrives while the hold is active, the visual state resolves to `RECENTS` immediately; the hold no longer blocks Recents capture.
- If the tracked transition finishes without exact Overview becoming active, it resolves to `HOME`; HOME capture is requested on the next Launcher main-loop turn.
- If the transition aborts or is superseded by a transition back to an application, the hold is released and APP remains authoritative.
- Transition merge transfers ownership from the merged token to the playing token so stale finish callbacks cannot release a newer hold.

## Architecture

### `SystemUiTransitionSource`

Runs only in the `com.android.systemui` process. It obtains the live WMShell `Transitions` object using the same SystemUI/Shell initialization environment already used by LiquidDock's SystemUI sources, registers a `Transitions.TransitionObserver`, and parses `TransitionInfo` into protocol events.

Responsibilities:

- Observe `onTransitionReady`, `onTransitionMerged`, and `onTransitionFinished`.
- Classify only transitions relevant to LiquidDock visual handoff.
- Track binder transition tokens and display IDs.
- Emit one-way events to the existing LiquidDock broker/provider transport.
- Fail open: if the observer cannot be installed on a vendor build, the Dock must remain functional and use the stable baseline/exact Overview behavior without adding Launcher heuristics back.

### `SystemUiTransitionProtocol`

Defines transport-neutral event types:

- `APP_TO_LAUNCHER_START`
- `TRANSITION_MERGED`
- `TRANSITION_FINISHED`
- `TRANSITION_ABORTED`

Every event carries a monotonically increasing generation, display ID, and a stable transition-token identity valid for the current SystemUI process lifetime. Binder delivery is one-way; there is no polling or synchronous Launcher wait.

### `SystemUiTransitionRuntime`

Runs in Launcher and is bound to the currently active `DockLiquidGlassView`.

Responsibilities:

- Ignore events for another display or an older generation.
- Begin/end the visual hold.
- Transfer token ownership on merge.
- Delegate exact Overview takeover to the existing `Miuix307RecentsInputHook`/`setOverviewActive` path.
- On a non-Overview finish, release the hold and request HOME capture on the next main-loop turn.

It must not call `HomeOwnershipRuntime.request()`, `setLauncherState()`, `onLauncherFocused()`, or `onLauncherFocusLost()` as part of transition handling.

### `DockLiquidGlassView` visual hold

The visual hold becomes an explicit internal state instead of an external hook around `requestStateCapture()`.

While active:

- The currently installed APP bitmap remains drawable.
- In-flight capture attempts from before the transition are invalidated by generation/revision ownership.
- New state-replacement captures that would replace the held APP bitmap are suppressed.
- Rendering itself remains active; the host/shell must not become transparent.
- Exact Overview is allowed to take over immediately.

On HOME finish:

- Clear the hold.
- Select HOME through the authoritative stable scene state.
- Post one HOME capture to the next main-loop turn; do not use `homeSettleDelayMs` or an arbitrary transition timer.

## Legacy Logic Removal

### HyperOS 3.0.307 material path

Remove from `Miuix307MaterialPipeline`:

- `installHomeGesturePrearm()`.
- `GestureToHome` constructors used for wallpaper prearm.
- `StateNotifyUtils.sendStateBroadcast(..., "toHome", ...)` backdrop hook.

Remove from `MiuixGlassHook`:

- `onHomeTransitionStart()`.
- Any immediate `setGestureCaptureTarget("HOME")` transition prearm.

Delete the dead experimental `Miuix307HomeTransitionFreezeHook` source after its behavior is fully replaced by the internal visual hold.

### Generic legacy capture path

For HOME/APP transition ownership, remove:

- `Launcher.onWindowFocusChanged → HomeOwnershipRuntime.request("focus")`.
- `GestureToHome` and `GestureToApp` as capture scene authorities.

`GestureToRecent` must not be used as a 307 scene authority. Exact Overview remains authoritative. Generic legacy behavior may retain compatibility hooks only where required for non-307 versions, but no hook may drive HOME/APP backdrop handoff when the SystemUI transition source is installed.

### `HomeOwnershipRuntime`

Retain only as a cold-bind/provider-recovery bootstrap snapshot source. A normal live APP↔Launcher transition must not trigger ownership refreshes.

The runtime may initialize a newly bound glass to `UNKNOWN` until the first bootstrap result, but transition events themselves must not introduce `UNKNOWN` churn.

### `CaptureSceneState`

Remove HOME/APP transition dependence on the 1.5-second gesture-target lease. Keep explicit state for:

- stable HOME/APP baseline,
- exact RECENTS,
- ALL_APPS,
- workstation suspension.

Any remaining gesture hint must not outrank SystemUI transition/exact Overview for HOME/APP resolution.

## Existing Behavior That Must Remain

The migration must not regress:

- 60 FPS pointer interaction cadence.
- Floating Dock root touch observation.
- Launcher `dispatchTouchEvent` fallback.
- Exact `EnterOverviewStateEvent` / `ExitOverviewStateEvent` behavior.
- 307 APP wallpaper-like frame rejection during pointer interaction.
- SystemUI panel expansion capture gate.
- Workstation capture ownership/exclusions.
- Freeform leash exclusions.
- Portrait workspace drop-boundary bypass.

## Error Handling

- Failure to install the SystemUI transition observer is logged once and fails open.
- Malformed or unrecognized `TransitionInfo` is ignored.
- Stale/unknown finish or merge tokens are ignored.
- Provider restart increments generation so pre-restart events cannot affect the new Launcher binding.
- Binder disconnect never forces `UNKNOWN` during an active visual hold; the last valid visual remains until a local authoritative boundary (exact Overview or new stable bootstrap) resolves it.

## Testing Strategy

Add contract/unit coverage requiring:

1. No 307 `GestureToHome` or `StateNotifyUtils("toHome")` backdrop hook remains.
2. No Launcher-focus ownership refresh is used for the migrated transition path.
3. `MiuixGlassHook.onHomeTransitionStart()` is gone.
4. The experimental freeze hook is deleted/not installed.
5. SystemUI transition source is installed from the SystemUI process entry point.
6. Transport is push/one-way and has no polling/wait loop.
7. `APP_TO_LAUNCHER_START` enters visual hold without `setLauncherState()`/ownership refresh.
8. Exact Overview releases/transfers the hold to RECENTS.
9. Finish without Overview resolves HOME and schedules capture on the next main-loop turn.
10. Merge/abort behavior is token/generation safe.
11. Launcher→APP convergence does not depend on focus.
12. Existing Recents cadence, backdrop rejection, workstation, and workspace-drop regression tests continue to pass.

## Device Validation

After JVM tests and `assembleDebug` pass, device validation must separately verify:

1. APP→HOME no longer loses the Dock background.
2. The original one-frame wallpaper flash is absent or reduced by the SystemUI visual hold.
3. Slow APP→Recents remains APP-backed until exact Overview and does not become sustained wallpaper.
4. Aborted HOME/Recents gestures return to APP without a stale hold.
5. Rapid repeated APP↔HOME transitions do not generate `UNKNOWN` ownership churn.

CI/build success is not device proof; each visual result remains unconfirmed until tested on the target HyperOS device.
