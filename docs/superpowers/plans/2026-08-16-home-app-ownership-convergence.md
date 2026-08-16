# HOME/APP Ownership Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Launcher-side HOME/APP foreground-task inference with a single SystemUI/WMShell baseline source that fails closed to wallpaper when unavailable.

**Architecture:** SystemUI passively observes the existing `ShellTaskOrganizer` executor and Xiaomi `MultiTaskingTaskRepository`, classifies HOME/APP internally, and exposes only a versioned HOME/APP/UNKNOWN result through the existing broker rendezvous. Launcher uses focus only to trigger asynchronous queries; `CaptureSceneState` gains UNKNOWN so transport/structure ambiguity can never fall back to APP or Launcher task inference.

**Tech Stack:** Java, Android Binder/Parcel, libxposed API 101 hooks, JUnit 4 source/contract tests.

## Global Constraints

- Implementation branch: `fix/home-app-ownership-convergence` from the current `fix/freeform-task-leash-exclusion` HEAD containing this plan.
- Do not merge or cherry-pick the diagnostic branch wholesale.
- No `ActivityManager.getRunningTasks(1)` or foreground windowing-mode inference may remain in the production HOME/APP ownership path.
- UNKNOWN must map to wallpaper and must never fall back to Launcher focus/task state or a last-good HOME/APP value.
- Gesture target, Recents, All Apps, workstation state, Dock state, and capture revision/generation remain Launcher-local.
- HOME-source failures must not trip the freeform breaker; freeform failures must not disable HOME ownership.
- No LiquidDock exception may escape into SystemUI.
- Binder requests/callbacks are one-way; Launcher main/capture threads must not block.
- Keep all commits `[skip ci]` unless explicit CI is requested.

---

### Task 1: Pure HOME/APP classification and production protocol

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipPolicy.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipProtocol.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipPolicyTest.java`

**Interfaces:**
- Produces `HomeOwnershipPolicy.Baseline { HOME, APP, UNKNOWN }`.
- Produces `HomeOwnershipPolicy.Result classify(boolean homeVisible, int homeTaskId, int topFullscreenTaskId, boolean confirmation)` with fields `baseline` and `retryRecommended`.
- Produces protocol constants for version, request/result transaction codes, status values, baseline wire values, timeout, recheck delay (160 ms), and max pending requests.

- [ ] **Step 1: Write failing classification tests**

Cover exactly:

```java
assertEquals(HOME, classify(true, 10, -1, false).baseline);
assertEquals(APP, classify(false, 10, -1, false).baseline);
assertEquals(UNKNOWN, classify(true, 10, 20, false).baseline);
assertTrue(classify(true, 10, 20, false).retryRecommended);
assertEquals(APP, classify(true, 10, 20, true).baseline);
assertEquals(UNKNOWN, classify(true, -1, 20, false).baseline);
```

- [ ] **Step 2: Verify RED**

Run `./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipPolicyTest` when Gradle is available. Expected: compilation/test failure because production types do not exist.

- [ ] **Step 3: Implement minimal pure policy and fresh protocol**

Use a fresh provider request transaction code; do not reuse the diagnostic shadow code. Keep task IDs internal to the pure classifier only; protocol responses expose only HOME/APP/UNKNOWN and retry recommendation.

- [ ] **Step 4: Verify GREEN**

Run the same JUnit target. Also compile/run an equivalent pure `javac/java` smoke harness if Android Gradle is unavailable.

- [ ] **Step 5: Commit**

`git commit -m "feat: define SystemUI home ownership policy [skip ci]"`

---

### Task 2: Fail-closed UNKNOWN capture scene

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureScene.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/CaptureSceneStateTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java`

**Interfaces:**
- `CaptureScene.UNKNOWN` represents unavailable ordinary HOME/APP baseline.
- Existing `CaptureSceneState.resolve(... lifecycleKnown, launcherResumed)` temporarily keeps its signature so the giant Dock file need not be rewritten; semantically `lifecycleKnown=false` now resolves UNKNOWN and `launcherResumed=true/false` represents SystemUI HOME/APP only.

- [ ] **Step 1: Add failing tests**

Assert initial state is UNKNOWN, `resolve(... false, false)` is UNKNOWN, source for UNKNOWN is WALLPAPER, and gesture/Recents/All Apps still outrank ordinary baseline.

- [ ] **Step 2: Verify RED**

Run the two JUnit classes.

- [ ] **Step 3: Implement minimal scene changes**

Change default `desired` from APP to UNKNOWN. In `resolve`, after gesture/Recents/All Apps, return UNKNOWN when baseline is not known; otherwise HOME or APP. Make `CaptureSourcePolicy` explicitly return WALLPAPER for UNKNOWN.

- [ ] **Step 4: Verify GREEN and existing special-scene tests**

Run `CaptureSceneStateTest` and `CaptureSourcePolicyTest`.

- [ ] **Step 5: Commit**

`git commit -m "feat: fail closed on unknown home ownership [skip ci]"`

---

### Task 3: Shared SystemUI provider and task executor source

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTaskStateProvider.java`
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiTaskExecutorSource.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SystemUiTaskStateProviderContractTest.java`

**Interfaces:**
- `SystemUiTaskStateProvider.attachContext(Context)` registers one shared provider Binder through the existing broker client.
- `SystemUiTaskStateProvider.providerBinder()` is SystemUI-process local only.
- Shared Binder delegates freeform transaction handling to `SystemUiFreeformLeashProvider.handleTransaction(...)` and HOME transaction handling to the new source from Task 4.
- `SystemUiTaskExecutorSource.executor()` returns the observed existing `ShellTaskOrganizer.getExecutor()` executor or null.

- [ ] **Step 1: Write contract tests**

Assert provider class owns broker registration, freeform provider no longer owns a `brokerClient`/provider Binder, and `SystemUiTaskExecutorSource` hooks `com.android.wm.shell.ShellTaskOrganizer` without constructing one.

- [ ] **Step 2: Verify RED**

Run the new contract test.

- [ ] **Step 3: Implement shared provider**

Move only provider token/broker registration ownership out of `SystemUiFreeformLeashProvider`. `attachContext` is idempotent. Delegated capability failures are isolated.

- [ ] **Step 4: Implement executor observation**

After each existing `ShellTaskOrganizer` constructor returns, invoke its existing `getExecutor()`. Publish only if the value is a `java.util.concurrent.Executor`. Catch all `Throwable` inside SystemUI hooks.

- [ ] **Step 5: Update ModuleMain SystemUI install order**

Install executor source, HOME source (Task 4 once present), then freeform source/shared provider. No exception escapes.

- [ ] **Step 6: Verify freeform source contract still holds**

Run existing `FreeformTaskLeashBridgeContractTest` and `FreeformBridgePolicyTest` together with the new contract test.

- [ ] **Step 7: Commit**

`git commit -m "refactor: share SystemUI task-state provider transport [skip ci]"`

---

### Task 4: Production SystemUI HOME ownership source

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipSource.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipSourceContractTest.java`

**Interfaces:**
- `install(ClassLoader)` passively observes `MultiTaskingTaskRepository` constructors.
- `handles(int code)` / `handleTransaction(int code, Parcel data)` are called only by `SystemUiTaskStateProvider`.
- Requests are scheduled on `SystemUiTaskExecutorSource.executor()`.
- Callback payload contains version/request/status/baseline/retry only; no task metadata.

- [ ] **Step 1: Write source contract tests**

Require the exact repository class name and method names, weak repository reference, shared executor source, no task map, no freeform breaker reference, no diagnostic protocol reference, and no task metadata written into result Parcel.

- [ ] **Step 2: Verify RED**

Run the contract test.

- [ ] **Step 3: Implement repository observation and request parsing**

Resolve `mContext`, `isHomeVisible`, `getHomeTask`, `getTopFullscreenTaskInfo(int)` once at install. On construction publish a weak repository reference and call `SystemUiTaskStateProvider.attachContext(context)`.

- [ ] **Step 4: Implement executor-side sampling**

Read home visibility, home task ID, and top fullscreen task ID only on the shared Shell executor, call `HomeOwnershipPolicy.classify(...)`, and send only policy result fields.

- [ ] **Step 5: Implement capability-local failure handling**

Structural failure disables HOME source only. Request-time failure sends UNKNOWN. Never call the freeform breaker.

- [ ] **Step 6: Verify policy + contract tests**

Run `HomeOwnershipPolicyTest`, `SystemUiHomeOwnershipSourceContractTest`, and freeform bridge contracts.

- [ ] **Step 7: Commit**

`git commit -m "feat: expose SystemUI home ownership baseline [skip ci]"`

---

### Task 5: Launcher asynchronous resolver/runtime

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipResolver.java`
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLeashBrokerClient.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipRuntimeContractTest.java`

**Interfaces:**
- Extend the existing broker client with one optional Launcher provider-change listener so provider death/recovery is observable without polling.
- `HomeOwnershipResolver(Context, Listener)` owns its own request correlation/timeout state but no task state.
- `request(int displayId, String reason)` is asynchronous.
- `HomeOwnershipRuntime.bind(DockLiquidGlassView, Context)` sets UNKNOWN immediately and requests current display baseline.
- `HomeOwnershipRuntime.request(int displayId, String reason)` is the only MainHook entry point after binding.

- [ ] **Step 1: Write failing contract tests**

Require no `ActivityManager`, `RunningTaskInfo`, `getRunningTasks`, package/task metadata, `CountDownLatch`, or blocking wait in resolver/runtime. Require one bounded 160 ms confirmation only when `retryRecommended` is true.

- [ ] **Step 2: Verify RED**

Run the contract test.

- [ ] **Step 3: Add provider-change listener to broker client**

Notify null on provider death/clear and non-null on discovery. Keep freeform resolver behavior unchanged when no listener is registered.

- [ ] **Step 4: Implement resolver**

Use one-way Binder requests, bounded pending map, timeout -> UNKNOWN, protocol/version validation, and exactly one confirmation request. Do not use a process breaker shared with freeform.

- [ ] **Step 5: Implement runtime application**

On HOME/APP/UNKNOWN call the existing Dock scene input using `(known, home)` semantics. APP transition calls existing APP prearm boundary; HOME transition calls existing HOME-return boundary. UNKNOWN must not retain the previous baseline.

- [ ] **Step 6: Verify contracts**

Run runtime contract plus existing freeform bridge tests.

- [ ] **Step 7: Commit**

`git commit -m "feat: consume SystemUI home ownership in Launcher [skip ci]"`

---

### Task 6: Remove Launcher ownership inference

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipConvergenceContractTest.java`

**Interfaces:**
- MainHook uses `HomeOwnershipRuntime.bind(...)` at glass creation and `HomeOwnershipRuntime.request(...)` at Launcher focus boundaries.
- Focus boolean is a trigger only and is never fed into classification.

- [ ] **Step 1: Write the convergence source contract first**

Read `MainHook.java` as text and assert it contains none of:

```text
getRunningTasks(
foregroundTaskWindowingMode
LauncherSceneOwnershipPolicy
seedLauncherLifecycleState
launcherOwnsScene
```

Also assert it contains `HomeOwnershipRuntime` and that `onWindowFocusChanged` requests a SystemUI ownership refresh.

- [ ] **Step 2: Verify RED**

Run the convergence contract against current MainHook.

- [ ] **Step 3: Remove old fields/methods and setup seed**

Delete MainHook's `launcherResumed` / `launcherLifecycleKnown` ownership mirrors, `seedLauncherLifecycleState`, and `foregroundTaskWindowingMode`. Replace both glass-creation seed calls with runtime binding/request.

- [ ] **Step 4: Replace focus ownership logic**

Keep the existing Launcher focus hook only as an event boundary. After the original method, request SystemUI ownership for the display. Remove windowing-mode lookup, policy call, `refreshForegroundAppLayer`, and direct HOME/APP writes.

- [ ] **Step 5: Remove lifecycle ownership fallback**

Delete direct lifecycle hooks whose only purpose is old ownership logging and delete the Activity-level fallback that writes ownership state.

- [ ] **Step 6: Delete old policy/test**

Remove `LauncherSceneOwnershipPolicy.java` and its obsolete test.

- [ ] **Step 7: Verify GREEN**

Run convergence contract, capture scene/source tests, HOME policy tests, and freeform bridge tests.

- [ ] **Step 8: Commit**

`git commit -m "refactor: remove Launcher home ownership inference [skip ci]"`

---

### Task 7: Dead Launcher task-query audit and final verification

**Files:**
- Inspect: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify when safe: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify if physical deletion is deferred: `docs/DEPRECATED_SOURCE.md`
- Modify/Create: relevant source contract test.

**Interfaces:**
- No behavior may depend on `refreshForegroundAppLayer`, `appLayerName`, or `resolveAppLayerByUid`.

- [ ] **Step 1: Audit remaining Launcher `getRunningTasks` references**

Search all production Java source. Distinguish ownership use from the already behavior-dead foreground-layer probe.

- [ ] **Step 2: If exact giant-file editing is safe, remove the dead probe**

Delete the attach-time/call-site probe, cached fields, `refreshForegroundAppLayer()`, and `resolveAppLayerByUid()`. Do not touch unrelated rendering/capture code.

- [ ] **Step 3: If exact giant-file editing is not safe, verify it is behavior-dead and document the physical cleanup boundary**

Keep `docs/DEPRECATED_SOURCE.md` explicit that this remaining query is not an ownership source and is pending physical deletion only because the repository connector lacks patch writes. Do not pretend it was removed.

- [ ] **Step 4: Fresh verification**

Run, when available:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

If Android Gradle is unavailable, run all pure Java smoke tests that can compile with `javac`, plus source-contract inspection. Do not claim Gradle/build success without actual output.

- [ ] **Step 5: Compare implementation branch to formal base**

Confirm no diagnostic shadow classes or diagnostic R8 keep rules were copied wholesale; confirm no changes to unrelated Recents/All Apps/workstation/geometry logic.

- [ ] **Step 6: Commit final cleanup**

`git commit -m "test: verify SystemUI home ownership convergence [skip ci]"`

- [ ] **Step 7: Device handoff**

Provide the target branch HEAD and exact build/install/log commands. Require device reboot so Launcher and SystemUI load the same module generation. Acceptance is the matrix in the approved spec.
