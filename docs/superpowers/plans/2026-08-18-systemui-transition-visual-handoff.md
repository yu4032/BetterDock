# SystemUI Transition Visual Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Launcher gesture/focus-driven APP↔HOME visual handoff with a SystemUI/WMShell transition push chain while preserving exact Overview and existing 307 capture protections.

**Architecture:** `SystemUiTransitionSource` observes WMShell transitions in SystemUI and pushes compact generation/token/display events through the existing provider/broker transport. `SystemUiTransitionRuntime` consumes those events in Launcher and drives an explicit internal visual hold in `DockLiquidGlassView`; exact Overview remains the only RECENTS authority. HOME/APP transition heuristics in Launcher are removed from the migrated path.

**Tech Stack:** Java, libxposed API 101, Android/WMShell reflection, Binder one-way transport, JUnit host-side contract/unit tests, Gradle `testDebugUnitTest` and `assembleDebug`.

**Spec:** `docs/superpowers/specs/2026-08-18-systemui-transition-visual-handoff-design.md`

## Global Constraints

- HyperOS 3.0.307 Floating Dock is `FLAG_NOT_FOCUSABLE`; never use Floating Dock `hasWindowFocus()` as an ownership gate.
- APP→Launcher transition events must never call `HomeOwnershipRuntime.request()`, `setLauncherState()`, `onLauncherFocused()`, or `onLauncherFocusLost()`.
- Exact `EnterOverviewStateEvent` / `ExitOverviewStateEvent` remains authoritative for RECENTS.
- No arbitrary transition timer replaces WMShell finish; HOME capture is posted to the next Launcher main-loop turn.
- Keep pointer cadence, APP wallpaper-frame rejection, workstation, SystemUI panel, freeform exclusions, and portrait drop behavior unchanged.
- Tests/build prove code correctness only; target-device visual behavior remains unconfirmed until device validation.

---

### Task 1: Lock the migration contract with RED tests

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipRuntimeContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307BackdropPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SystemUiTransitionPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SystemUiTransitionContractTest.java`

**Interfaces:**
- Consumes: current production source text and pure-Java policy classes.
- Produces: failing contracts for source installation, old-hook removal, push transport, transition policy, and visual-runtime boundaries.

- [ ] **Step 1: Replace old 307 backdrop assertions**

Require `Miuix307MaterialPipeline.java` to contain neither `installHomeGesturePrearm`, `GestureToHome` prearm nor `StateNotifyUtils` `toHome` handoff; require `MiuixGlassHook.java` not to expose `onHomeTransitionStart`.

- [ ] **Step 2: Add SystemUI source contract**

Require `ModuleMain` SystemUI process setup to install `SystemUiTransitionSource`, and require the source to reference WMShell `Transitions`, transition ready/merged/finished callbacks, and one-way broker delivery.

- [ ] **Step 3: Add pure policy tests**

Test that a normalized set of transition changes classifies APP→Launcher only when HOME comes front while an APP goes back and wallpaper participates. Verify unrelated HOME visibility or APP launches are ignored.

- [ ] **Step 4: Add runtime contract tests**

Require transition handling to call explicit visual-hold methods and prohibit ownership/focus methods. Require exact Overview integration and next-loop HOME release.

- [ ] **Step 5: Run tests and record expected RED**

Run `./gradlew testDebugUnitTest`. Expected: new migration tests fail because the SystemUI source/runtime do not yet exist and old hooks still remain.

- [ ] **Step 6: Commit RED tests**

Commit message: `test: define SystemUI transition handoff contract`.

---

### Task 2: Add pure transition classification and protocol

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTransitionPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTransitionProtocol.java`
- Test: `src/test/java/com/hellovoid/liquiddock/SystemUiTransitionPolicyTest.java`

**Interfaces:**
- Produces: `SystemUiTransitionPolicy.classify(...)` returning `NONE` or `APP_TO_LAUNCHER`; protocol event constants and immutable event payload fields `type`, `generation`, `tokenId`, `otherTokenId`, `displayId`, `aborted`.

- [ ] **Step 1: Implement normalized change model**

Expose a package-private pure-Java `Change` value with booleans for HOME task, APP task, wallpaper, front/back, and `showWallpaper`, plus display ID.

- [ ] **Step 2: Implement classifier**

Return `APP_TO_LAUNCHER` only when the same display contains a HOME-front signal, an APP-back signal, and wallpaper participation.

- [ ] **Step 3: Implement protocol event model**

Define `APP_TO_LAUNCHER_START`, `TRANSITION_MERGED`, `TRANSITION_FINISHED`, `TRANSITION_ABORTED`; reject negative generations/token IDs in constructors only where they are truly invalid.

- [ ] **Step 4: Run policy tests**

Run the focused `SystemUiTransitionPolicyTest`, then full `testDebugUnitTest` to ensure only integration contracts remain RED.

- [ ] **Step 5: Commit**

Commit message: `feat: classify SystemUI launcher transitions`.

---

### Task 3: Install SystemUI WMShell transition observer and push transport

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTransitionSource.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify existing SystemUI provider/broker files that currently carry HOME ownership/freeform state; exact paths discovered from repository source before editing.
- Test: `src/test/java/com/hellovoid/liquiddock/SystemUiTransitionContractTest.java`

**Interfaces:**
- Consumes: WMShell `Transitions` instance/reflection and existing LiquidDock binder provider.
- Produces: one-way event push to registered Launcher consumer; generation increments on SystemUI/provider lifetime and token mapping survives merge.

- [ ] **Step 1: Discover current provider callback extension point**

Reuse the existing provider registration/listener mechanism rather than introduce a second Binder service.

- [ ] **Step 2: Add observer registration**

Register one observer in SystemUI. Parse `TransitionInfo.getChanges()` reflectively, using `RunningTaskInfo.getActivityType()`/task metadata and transition mode/flags to build normalized policy changes.

- [ ] **Step 3: Track tokens**

Assign stable process-local long IDs to transition binder tokens. On merge, transfer active APP→Launcher tracking from merged token to playing token. Ignore stale finishes.

- [ ] **Step 4: Push protocol events**

Deliver callback events with `FLAG_ONEWAY` or the existing asynchronous listener abstraction. Do not poll and do not block Launcher waiting for Shell.

- [ ] **Step 5: Fail open**

If WMShell internals/signatures are unavailable, log one concise diagnostic and leave the rest of LiquidDock active.

- [ ] **Step 6: Run integration contract tests**

Expected: SystemUI source/transport tests GREEN; Launcher-runtime and old-hook-removal tests may remain RED.

- [ ] **Step 7: Commit**

Commit message: `feat: push WMShell transition events from SystemUI`.

---

### Task 4: Add Launcher transition runtime and internal visual hold

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTransitionRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307RecentsInputHook.java`
- Modify current broker consumer registration file discovered in Task 3.
- Create/Modify focused runtime tests or source contracts.

**Interfaces:**
- Produces on `DockLiquidGlassView` package-private methods:
  - `beginAppToLauncherVisualHold(long generation, long tokenId)`
  - `resolveVisualHoldToOverview(long generation)`
  - `finishAppToLauncherVisualHold(long generation, long tokenId, boolean aborted)`
- `SystemUiTransitionRuntime.bind(DockLiquidGlassView)` binds the current glass and routes protocol events on Launcher main.

- [ ] **Step 1: Implement visual-hold state**

Store active generation/token and a boolean hold. Beginning a hold invalidates queued/in-flight replacement capture work while preserving the installed bitmap/shader.

- [ ] **Step 2: Gate replacement capture internally**

During the hold, suppress state-replacement requests/candidate installs that would replace the held APP backdrop. Do not hide/alpha the host or vendor shell.

- [ ] **Step 3: Integrate exact Overview**

When `EnterOverviewStateEvent` becomes active, resolve hold to Overview before requesting the Recents capture. `ExitOverviewStateEvent` continues to use exact scene state.

- [ ] **Step 4: Finish/abort semantics**

For a matching finish without Overview, clear hold and post one HOME scene capture to the next main-loop turn. For abort/supersede, clear hold and retain APP scene/capture.

- [ ] **Step 5: Bind runtime on glass creation/rebind**

Bind at every MiuiX/legacy glass creation point that uses the SystemUI transition chain; stale view refs must be weak/replaced.

- [ ] **Step 6: Run focused + full tests**

Expected: visual runtime tests GREEN; old-hook-removal tests still RED until Task 5.

- [ ] **Step 7: Commit**

Commit message: `feat: drive backdrop handoff from SystemUI transitions`.

---

### Task 5: Remove old Launcher-owned transition authorities

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/Miuix307HomeTransitionFreezeHook.java`
- Tests: migration contract tests plus existing scene tests.

**Interfaces:**
- Home ownership remains bootstrap/provider-recovery only.
- No normal APP↔Launcher transition calls `HomeOwnershipRuntime.request`.

- [ ] **Step 1: Remove 307 toHome/GestureToHome prearm hooks**

Delete the StateNotifyUtils and GestureToHome installation code from `Miuix307MaterialPipeline`; delete `MiuixGlassHook.onHomeTransitionStart`.

- [ ] **Step 2: Remove Launcher focus ownership refresh**

Delete `Launcher.onWindowFocusChanged → HomeOwnershipRuntime.request("focus")` from the migrated runtime path.

- [ ] **Step 3: Remove HOME/APP gesture target authority**

Stop installing `GestureToHome`/`GestureToApp` capture-target hooks for paths using SystemUI transition events. Keep exact Overview and any non-conflicting old-version compatibility path only if tests/source prove it is still needed.

- [ ] **Step 4: Simplify bootstrap runtime/scene state**

Keep `HomeOwnershipRuntime.bind` bootstrap and provider-recovery refresh, but remove transition side effects that are now owned by SystemUI transition runtime. Remove HOME-specific gesture-target cleanup from scene state if no remaining caller needs it.

- [ ] **Step 5: Delete failed freeze experiment source**

Remove `Miuix307HomeTransitionFreezeHook.java` entirely and ensure no production/test reference installs it.

- [ ] **Step 6: Run full tests**

Expected: all migration tests and pre-existing tests GREEN.

- [ ] **Step 7: Commit**

Commit message: `refactor: remove Launcher transition heuristics`.

---

### Task 6: Verify build, inspect diff, and package device APK

**Files:**
- No functional source changes unless verification exposes a defect.
- Update PR #7 description with the new architecture and explicit device-validation status.

**Interfaces:**
- Produces: CI/test evidence and a debug APK artifact.

- [ ] **Step 1: Run fresh verification**

Run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: all host JVM tests pass and debug APK assembles.

- [ ] **Step 2: Review migration diff**

Search production source for:

```text
installHomeGesturePrearm
MiuixGlassHook.onHomeTransitionStart
HomeOwnershipRuntime.request("focus")
miuix307-focus
StateNotifyUtils + toHome backdrop hook
Miuix307HomeTransitionFreezeHook.install
```

Expected: no active migrated-path references.

- [ ] **Step 3: Confirm preserved paths**

Verify source/tests still cover pointer cadence, exact Overview, wallpaper-like APP frame rejection, SystemUI panel, workstation, freeform exclusion, and portrait drop rule.

- [ ] **Step 4: Update PR #7**

Document the transition source, removal of old heuristics, test/build results, and explicitly mark APP→HOME flash/background continuity as pending target-device validation.

- [ ] **Step 5: Provide APK**

Download/copy the CI artifact into `/mnt/data` if available and report SHA-256. Do not claim device success.
