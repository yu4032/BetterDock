# HOME Ownership Shadow Plan Corrections

Date: 2026-08-16

This note records target-ROM facts discovered while implementing the diagnostic-only `dev/home-ownership-systemui-shadow` branch. It corrects two implementation details in `2026-08-16-home-ownership-systemui-shadow.md` without changing the approved diagnostic goal or production behavior.

## 1. Repository state must not be sampled on `mBgExecutor`

The original implementation plan tentatively selected `MultiTaskingTaskRepository.mBgExecutor` as the repository read executor. Direct DEX inspection of the target `MiuiSystemUI.apk` showed that this assumption is wrong.

Observed call chain on the target ROM:

```text
ShellTaskOrganizer.onTaskAppeared / onTaskInfoChanged / onTaskVanished
  -> MultiTaskingControllerStub.getMultiTaskingListener()
  -> MultiTaskingTaskListener
  -> MultiTaskingTaskRepository.onTaskAppeared / onTaskInfoChanged / onTaskVanished
  -> direct writes to mTasks / mHomeTaskInfo / focused-task fields
```

`mBgExecutor` is used by selected auxiliary repository operations and is not the general owner of these task-state writes.

The same target DEX shows `ShellTaskOrganizer` passes its `ShellExecutor` to the `android.window.TaskOrganizer(..., Executor)` superclass constructor. The already device-validated production freeform provider obtains this TaskOrganizer executor through `mShellTaskOrganizer.getExecutor()`.

Therefore the diagnostic implementation must sample `MultiTaskingTaskRepository.isHomeVisible()` on the **ShellTaskOrganizer / TaskOrganizer executor**, not `mBgExecutor`.

Diagnostic implementation rule:

```text
SystemUiFreeformLeashProvider.taskStateExecutorForDiagnostics()
  -> existing ListenerState.executor
  -> executor.execute(shadow repository sample)
```

This accessor is read-only. Shadow failures do not touch `FreeformBridgePolicy.CircuitBreaker`, production task state, or freeform capture behavior.

## 2. Do not modify `MainHook` for diagnostic sampling

The original plan proposed inserting shadow calls after ownership decisions inside `MainHook`. Because `MainHook.java` is large and already device-validated, the diagnostic implementation uses a stricter zero-behavior-change boundary instead:

```text
HomeOwnershipShadowLauncherHook
  -> hooks existing Launcher setup/focus boundaries
  -> calls original hook stack first
  -> posts one main-loop read
  -> reads MainHook's already-computed ownership fields/method for diagnostics only
  -> sends asynchronous shadow sample
```

`MainHook.java`, `DockLiquidGlassView.java`, `CaptureSceneState.java`, `CaptureSourcePolicy.java`, and `LauncherSceneOwnershipPolicy.java` remain byte-for-byte unchanged on the diagnostic branch relative to its formal-branch base.

Because debug builds also run through R8 optimization, the diagnostic branch adds narrow keep rules only for the private MainHook/Dock members read reflectively by `HomeOwnershipShadowLauncherHook`. These keep rules are diagnostic-only and are not part of a future formal migration.

## 3. APP + freeform evidence trigger

Opening a freeform task while a fullscreen app already owns the scene may not generate a Launcher focus transition. To avoid missing this required evidence case, the diagnostic branch reuses the existing production freeform snapshot result:

- only a **safe** snapshot is considered;
- a shadow sample is requested only when safe freeform presence changes between absent/present;
- unsafe/provider-unavailable results produce no freeform-presence evidence;
- no extra SystemUI task lifecycle hook, polling loop, or per-frame shadow Binder request is introduced.

This is evidence plumbing only. It does not modify the freeform snapshot result or capture fallback decision.

## Unchanged safety contract

The approved rules remain unchanged:

- current Launcher ownership logic stays authoritative;
- shadow results never write `launcherResumed` or `CaptureSceneState`;
- no new TaskOrganizer or task-state mirror;
- shadow IPC is one-way/asynchronous;
- one immediate mismatch gets at most one delayed recheck;
- Overview, All Apps, and workstation samples are not production migration evidence;
- diagnostic branch must never be merged or cherry-picked wholesale into the formal branch;
- a later production cleanup is designed and implemented independently after device evidence is reviewed.
