# HOME/APP Ownership Convergence — Pre-wrap Validation

**Date:** 2026-08-16  
**Implementation branch:** `fix/home-app-ownership-convergence`  
**Validated implementation HEAD before this record:** `f53f5210f178cdd3ce098873f57412dff4bb3053`  
**Formal base:** `bfc935048d8fe42440cff01342b29a25fa52996b`  
**Status:** Pre-wrap evidence record; not merged and not final acceptance.

## Scope

This record captures the evidence available after the production HOME/APP ownership convergence implementation was built, installed, rebooted, and exercised on the target HyperOS device. It does not convert unobserved scenarios into passes.

The production design under test is:

- SystemUI/WMShell is the sole ordinary HOME-versus-APP authority.
- `MultiTaskingTaskRepository` is sampled on the existing `ShellTaskOrganizer` executor.
- Launcher focus is a query trigger only, never ownership evidence.
- The Binder result exposes only `HOME`, `APP`, or `UNKNOWN`.
- Every query boundary fails closed to `UNKNOWN`; `UNKNOWN` maps to wallpaper.
- Initial `homeVisible + non-HOME top fullscreen` conflict gets exactly one bounded confirmation after 160 ms.
- No Launcher `getRunningTasks()` / foreground-windowing-mode fallback and no last-good HOME/APP capture fallback are allowed.
- Freeform leash exclusion remains an independent capability on the shared provider transport.

## Production startup evidence

After installing the implementation APK and rebooting both Launcher and SystemUI, the target device reported:

```text
[DC] API101 module loaded process=com.android.systemui framework=LSPosed api=102
[DC] API101 module loaded process=com.miui.home framework=LSPosed api=102
[DC] SystemUI task executor hook installed
[DC] SystemUI HOME ownership source hook installed
[DC] SystemUI freeform leash provider hook installed
[DC] SystemUI HOME ownership repository observed
```

This establishes that the production implementation, rather than the diagnostic shadow APK, was loaded and that the executor/source/repository/provider installation path became live.

No `[DC-SHADOW]` result was present in the captured production session.

## Ownership event summary

The captured production session contains 48 `HOME ownership baseline=` updates:

| Baseline | Count | Interpretation |
|---|---:|---|
| `HOME` | 15 | Confirmed ordinary HOME baseline |
| `APP` | 6 | Confirmed ordinary APP baseline |
| `UNKNOWN` | 27 | Fail-closed request/conflict/configuration intervals |

The 27 `UNKNOWN` results break down as:

| Reason | Count |
|---|---:|
| `focus-pending` | 20 |
| `focus-conflict` | 6 |
| `configuration-pending` | 1 |

No `timeout`, `provider-unavailable`, malformed callback, protocol-version failure, or persistent stable-scene `UNKNOWN` was observed in this session.

## HOME

Stable HOME requests repeatedly followed:

```text
UNKNOWN reason=focus-pending
HOME    reason=focus
```

Observed HOME query turnaround was normally a few milliseconds and remained bounded in the captured session. The ordinary HOME baseline therefore converged without Launcher task inference.

**Status: device-verified in this session.**

## HOME -> APP takeover

The important takeover case repeatedly showed the expected overlap:

```text
UNKNOWN reason=focus-pending
UNKNOWN reason=focus-conflict
APP     reason=focus-confirmed
```

During these samples WMShell already had a non-HOME fullscreen task while HOME could still report visible. The initial conflict therefore failed closed instead of incorrectly choosing HOME. The one bounded confirmation then resolved APP.

Six APP takeovers in the captured session followed this same pattern. Confirmation completed at roughly 172–176 ms after the initial conflict path began, consistent with the configured 160 ms recheck plus Binder/executor scheduling.

**Status: device-verified in this session.**

## APP -> HOME return

Repeated APP-to-HOME transitions returned to:

```text
UNKNOWN reason=focus-pending
HOME    reason=focus
```

No stale APP baseline or persistent conflict remained in the captured repeated-transition samples.

**Status: device-verified in this session.**

## Repeated HOME <-> APP transitions

The session contains multiple alternating HOME and APP transitions. APP takeover consistently used the bounded conflict-confirmation path; HOME return consistently resolved directly after the pending boundary.

No persistent ownership disagreement or transport outage appeared during these repetitions.

**Status: device-verified in this session.**

## APP + freeform

The session contains an APP baseline followed by creation of a second task in `windowingMode=5` / freeform while an ordinary fullscreen application remained underneath. WMShell later explicitly reported the underlying fullscreen application as `topFullScreenTask` while the freeform task was active.

The HOME/APP baseline did not get redefined by the freeform task itself. This is the intended architecture: ordinary ownership describes what is behind the floating task, while the independent freeform capability supplies task-leash exclusions to the final mode-1 capture gate.

The captured logging configuration did not emit a dedicated per-capture freeform-snapshot success line, so this record treats **ownership preservation under APP + freeform** as verified, while relying on the previously device-validated freeform branch for task-leash exclusion behavior itself.

**Status: production ownership behavior device-verified; final freeform-exclusion implementation remains regression-sensitive but no regression is evidenced here.**

## HOME + freeform

The session also contains a freeform task while the production ownership query resolves:

```text
UNKNOWN reason=focus-pending
HOME    reason=focus
```

A floating task over HOME therefore does not demote the ordinary baseline to APP. HOME remains wallpaper-backed as designed.

**Status: device-verified in this session.**

## Configuration / rotation boundary

One observed configuration boundary produced:

```text
UNKNOWN reason=configuration-pending
HOME    reason=configuration
```

The result recovered in about 40 ms and did not remain UNKNOWN.

This validates the production ownership query/recovery path across the observed configuration change. It is not a claim that every rotation/capture visual artifact has been exhaustively tested.

**Status: ownership-path device-verified for the observed configuration change.**

## Recents / Overview

The uploaded production log does not contain a conclusive `liquid overview active=...` sequence or another exact marker proving a complete Overview enter/exit validation run.

Static architecture remains unchanged: exact Overview state continues to outrank the ordinary SystemUI baseline in `CaptureSceneState`.

**Status: pending final device gate.**

## All Apps

The uploaded production log does not contain a conclusive normal All Apps enter/exit marker from which a full device pass can be established.

Static architecture remains unchanged: All Apps continues to outrank ordinary HOME/APP ownership. On exit, `DockLiquidGlassView` re-resolves against the currently stored SystemUI baseline rather than consulting Launcher top-task inference.

**Status: pending final device gate.**

## Workstation / laptop mode

Workstation remains outside the ordinary HOME/APP migration authority and is explicitly experimental in the repository architecture. No complete workstation regression matrix is established by this session.

**Status: optional / separate experimental gate; not a blocker for the ordinary supported HOME/APP baseline unless release policy changes.**

## Static implementation audit

The implementation branch was compared against formal base `bfc935048d8fe42440cff01342b29a25fa52996b` before this record:

- branch was 30 commits ahead and 0 behind;
- `MainHook.java` changed by +7/-131, primarily removing the old ownership path;
- `DockLiquidGlassView.java` was not modified by the convergence branch;
- `LauncherSceneOwnershipPolicy.java` and its test were deleted;
- no diagnostic `HomeOwnershipShadow*` production class or diagnostic R8 keep rule was copied wholesale.

`HomeOwnershipConvergenceContractTest` requires `MainHook.java` to contain none of:

```text
getRunningTasks(
foregroundTaskWindowingMode
LauncherSceneOwnershipPolicy
seedLauncherLifecycleState
launcherOwnsScene
launcherLifecycleKnown
launcherResumed
```

and requires the production `HomeOwnershipRuntime` bind/focus-query path.

The SystemUI source remains read-only: it keeps only a weak repository reference and the existing task executor, creates no `TaskOrganizer`, and maintains no duplicate task map.

## Compatibility seams deliberately left in place

`DockLiquidGlassView` still uses legacy internal names such as `launcherLifecycleKnown` / `launcherResumed` for the `(ownershipKnown, homeOwned)` input carried into `CaptureSceneState`. In this branch those values are written by `HomeOwnershipRuntime` from SystemUI results; they are not Launcher lifecycle authority.

This naming seam is intentionally retained because `DockLiquidGlassView.java` is a large renderer and the approved plan avoids a whole-file rewrite for cosmetic cleanup. It should be renamed when that file is safely patched locally.

The behavior-dead foreground app-layer probe and temporary freeform preflight compatibility shells are separately tracked in `docs/DEPRECATED_SOURCE.md`. They do not participate in HOME/APP ownership.

## Shared transport audit

HOME ownership and freeform snapshots share one Launcher `FreeformLeashBrokerClient` transport but retain separate request state and health behavior.

A pre-wrap audit specifically checked the transport demand path because a single shared `demanded` boolean could be unsafe if one capability actively disabled demand while the other still required reconnects. In the current active production path:

- `HomeOwnershipResolver` demands the provider for its lifetime;
- `FreeformLeashRuntime.install()` also enables provider demand;
- no active convergence path was found that disables provider demand;
- the old demand-toggle/readiness APIs are already marked compatibility-only in `docs/DEPRECATED_SOURCE.md` for deletion with the Dock preflight shell.

No demand-refcount/bitmask change was introduced without an actual failing path.

## Test/build evidence boundary

Pure Java classification/source-contract smoke checks were run during implementation and passed, including the convergence smoke used before device testing.

The assistant session has **not** received the terminal output of `./gradlew testDebugUnitTest`, so this record does not claim that unit-test task passed.

The production APK clearly compiled, installed, loaded in both injected processes, and executed on-device, as demonstrated by the production startup and runtime logs. That is not a substitute for preserving the Gradle unit-test output as a final gate.

## Remaining gates before wrap-up

Before final acceptance / merge evaluation, preserve fresh evidence for:

1. `./gradlew testDebugUnitTest` output;
2. `./gradlew assembleDebug` output for the exact final implementation HEAD;
3. normal Recents/Overview enter and exit;
4. normal All Apps enter and exit;
5. a brief visual regression pass confirming APP + freeform still excludes the freeform task correctly from the LiquidDock backdrop;
6. final branch compare/static contract check after any pre-wrap documentation or code adjustment.

Until those gates are closed, `fix/home-app-ownership-convergence` remains an isolated implementation branch and must not be merged into `fix/freeform-task-leash-exclusion`.
