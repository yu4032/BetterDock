# HOME/APP Ownership Convergence — Final Acceptance

**Date:** 2026-08-16  
**Implementation branch:** `fix/home-app-ownership-convergence`  
**Integration target:** `api101-migration`  
**Formal implementation checkpoint:** `d241753eab191d847182e70c0fa475e67c40f388`  
**Status:** Accepted for merge after CI and target-device regression validation.

## Scope

This record closes the HOME/APP ownership-convergence work. The supported ordinary-scene contract is now:

- SystemUI/WMShell is the sole ordinary HOME-versus-APP authority.
- `MultiTaskingTaskRepository` is sampled on the existing `ShellTaskOrganizer` executor.
- Launcher focus is a query/refresh boundary only, never ownership evidence.
- The Binder result exposes only `HOME`, `APP`, or `UNKNOWN`.
- Every request boundary fails closed to `UNKNOWN`; `UNKNOWN` maps to wallpaper.
- Initial `homeVisible + non-HOME top fullscreen` conflict gets one bounded confirmation after about 160 ms.
- No Launcher `getRunningTasks()` / top-task ownership inference and no last-good ordinary HOME/APP fallback are allowed.
- Freeform task-leash exclusion remains an independent SystemUI capability.
- Recents/Overview and All Apps continue to outrank the ordinary HOME/APP baseline in `CaptureSceneState`.

Workstation/laptop mode remains an experimental, separate scene and is not part of the ordinary HOME/APP authority migration.

## Production startup and provider recovery

Target-device logs verified that the production module loads in both SystemUI and Launcher under LSPosed API 102 and installs the expected SystemUI hooks:

```text
[DC] SystemUI task executor hook installed
[DC] SystemUI HOME ownership source hook installed
[DC] SystemUI freeform leash provider hook installed
[DC] SystemUI HOME ownership repository observed
```

Launcher startup correctly fails closed while the provider is unavailable and then converges after the SystemUI provider becomes ready. A later Launcher process restart repeated the same recovery sequence and reattached successfully without relying on stale Launcher lifecycle ownership state.

**Status: device-verified.**

## HOME and APP convergence

Stable HOME refreshes repeatedly follow:

```text
UNKNOWN reason=focus-pending
HOME    reason=focus
```

HOME-to-APP takeovers repeatedly follow:

```text
UNKNOWN reason=focus-pending
UNKNOWN reason=focus-conflict
APP     reason=focus-confirmed
```

The confirmed APP result arrives roughly 170–180 ms after the initial conflict in observed samples, consistent with the single bounded confirmation policy. APP-to-HOME returns then resolve directly through the normal pending boundary without stale APP ownership.

Repeated HOME <-> APP transitions remained stable, including after Launcher restart.

**Status: device-verified.**

## Recents / Overview

A dedicated target-device regression run entered and exited the normal Recents/Overview UI. The MIUI task-view path was active and produced normal task thumbnail binding/update activity. No HOME/APP ownership regression, persistent `UNKNOWN`, or scene-transition failure was observed. The user confirmed the visual behavior is correct.

The architecture remains unchanged: exact Overview state outranks the ordinary SystemUI HOME/APP baseline in `CaptureSceneState`.

**Status: device-verified and visually accepted.**

## All Apps

A dedicated target-device All Apps enter/exit run was performed. Ownership refreshes around the interaction repeatedly converged through:

```text
UNKNOWN reason=focus-pending
HOME    reason=focus
```

No false APP takeover was observed while using All Apps, and the user confirmed the final visual behavior is correct.

All Apps therefore continues to outrank the ordinary HOME/APP baseline while active and returns to the stored SystemUI baseline on exit.

**Status: device-verified and visually accepted.**

## Freeform exclusion

Both HOME + freeform and APP + freeform were exercised on the target device. The production logs show an actual `windowingMode=5` freeform task while the ordinary ownership baseline remains independently resolved. The user confirmed that the LiquidDock backdrop visually excludes the freeform task as intended.

This closes both parts of the freeform regression gate:

1. a floating task does not redefine ordinary HOME/APP ownership;
2. the independent SystemUI task-leash exclusion prevents the freeform task from contaminating the captured backdrop.

**Status: device-verified and visually accepted.**

## Configuration / rotation recovery

Observed configuration/rotation boundaries fail closed to `UNKNOWN` while the request is pending and then recover to the correct baseline. No persistent ownership failure remained after the observed transitions.

**Status: device-verified for the tested configuration/rotation path.**

## Static implementation audit

The convergence implementation removes Launcher-side ordinary ownership inference from `MainHook` and routes ownership through `HomeOwnershipRuntime` to the SystemUI source. The SystemUI source keeps only a weak repository reference and the existing task executor; it does not create a duplicate `TaskOrganizer` or maintain a parallel task map.

`HomeOwnershipConvergenceContractTest` enforces that the focus hook requests the SystemUI baseline and does not restore legacy Launcher lifecycle ownership evidence.

Legacy internal field names such as `launcherLifecycleKnown` / `launcherResumed` remain inside `DockLiquidGlassView` only as a compatibility naming seam for `(ownershipKnown, homeOwned)`. They are written from SystemUI ownership results and are not Launcher lifecycle authority. This deliberate naming cleanup remains outside the convergence scope.

The behavior-dead foreground app-layer probe and temporary freeform preflight compatibility shells remain separately tracked in `docs/DEPRECATED_SOURCE.md`.

## CI evidence

GitHub Actions run `31948422333` / job `95168017682` validated the implementation branch after CI enablement.

Unit-test gate:

```text
./gradlew testDebugUnitTest --stacktrace
> Task :testDebugUnitTest
BUILD SUCCESSFUL in 1m 54s
24 actionable tasks: 24 executed
```

Debug-build gate:

```text
./gradlew assembleDebug --stacktrace
> Task :assembleDebug
BUILD SUCCESSFUL in 1m 2s
35 actionable tasks: 16 executed, 19 up-to-date
```

The run uploaded exactly one `LiquidDock-api101-debug` artifact. No production Java/Kotlin implementation changed after the validated implementation checkpoint; subsequent changes before this final record were CI/documentation-only.

A fresh CI run is required for the exact final pre-merge documentation HEAD because this final acceptance record itself is committed without `[skip ci]`.

## Acceptance matrix

| Gate | Result |
|---|---|
| CI `testDebugUnitTest` | PASS |
| CI `assembleDebug` | PASS |
| Production hooks load in SystemUI + Launcher | PASS |
| HOME | PASS |
| HOME -> APP | PASS |
| APP -> HOME | PASS |
| Repeated HOME <-> APP | PASS |
| Launcher restart/provider recovery | PASS |
| Recents/Overview enter/exit | PASS |
| All Apps enter/exit | PASS |
| HOME + freeform | PASS |
| APP + freeform ownership | PASS |
| Freeform visual exclusion | PASS |
| Observed configuration/rotation recovery | PASS |

## Merge decision

The feature branch is based on `api101-migration`. Final integration must be performed only after the exact final feature HEAD receives a green CI run and a final compare confirms that the feature branch is not behind `api101-migration`.

With those two repository-side checks green, the HOME/APP ownership convergence work is accepted for fast-forward integration into `api101-migration`.
