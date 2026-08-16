# SystemUI HOME/APP Ownership Shadow Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a diagnostic-only HOME/APP ownership shadow audit that compares the current Launcher decision with SystemUI/WMShell `MultiTaskingTaskRepository.isHomeVisible()` without changing any capture behavior.

**Architecture:** Keep the current Launcher inference fully authoritative. A separate diagnostic component observes the existing SystemUI `MultiTaskingTaskRepository`, answers one-way Binder shadow queries on its existing `mBgExecutor`, and returns only metadata. Launcher asynchronously logs MATCH/MISMATCH evidence and performs exactly one delayed recheck for an immediate mismatch. The diagnostic uses the existing broker/provider Binder identity but has its own protocol codes, state, error handling, and disable flag so it cannot trip the production freeform leash breaker.

**Tech Stack:** Java 17, Android/HyperOS hidden APIs through reflection, libxposed API 101 hooks, raw Binder/Parcel IPC, JUnit 4 source-contract and pure-policy tests.

## Global Constraints

- Implement diagnostic code only on `dev/home-ownership-systemui-shadow` created from the formal branch after this plan commit.
- Never merge or cherry-pick the diagnostic branch wholesale into `fix/freeform-task-leash-exclusion` or `api101-migration`.
- Do not change `launcherResumed`, `CaptureSceneState`, capture source, gesture target, Overview, All Apps, workstation, or freeform leash behavior from shadow results.
- Do not register another `TaskOrganizer`, task listener, repository, or task-state map in SystemUI.
- Observe only the existing `MultiTaskingTaskRepository`; keep only a weak reference.
- Read repository state only on the existing `mBgExecutor`; if the executor/method structure is unavailable, disable only the shadow capability.
- Shadow IPC is one-way and asynchronous. Never block Launcher main/UI or capture threads with `CountDownLatch`, `Future.get`, sleep, or polling.
- One immediate mismatch gets exactly one delayed recheck; no repeating monitor and no per-frame sampling.
- Structural or runtime shadow failures must not affect the production `FreeformBridgePolicy.CircuitBreaker` or freeform provider availability.
- Existing freeform provider/broker caller authentication remains authoritative.
- All commits use `[skip ci]`.

---

## File Structure

### New diagnostic files

- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java` — diagnostic-only Binder transaction codes, status values, descriptors, and timing bounds.
- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java` — Android-free MATCH/MISMATCH/special-scene classification helpers.
- `src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java` — observes the existing `MultiTaskingTaskRepository`, owns a separate shadow disable flag, and serves diagnostic requests on `mBgExecutor`.
- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java` — Launcher-only asynchronous requester, bounded pending contexts, one-shot mismatch recheck, and `[DC-SHADOW]` logging.
- `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java` — pure Java classification tests.
- `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java` — source contracts that enforce read-only isolation and no behavior writes.

### Narrow modifications

- `src/main/java/com/hellovoid/liquiddock/ModuleMain.java` — install `SystemUiHomeOwnershipShadow` only in SystemUI after the production freeform provider install attempt; shadow install failures are caught independently.
- `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java` — delegate only the reserved shadow transaction code to `SystemUiHomeOwnershipShadow`; the delegate must self-contain all shadow errors and never call the production breaker.
- `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java` — expose the already-discovered provider Binder through a package-private diagnostic accessor only; no new broker/service connection.
- `src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java` — expose that provider Binder to the diagnostic probe without changing production capture resolution.
- `src/main/java/com/hellovoid/liquiddock/MainHook.java` — invoke shadow sampling after existing ownership decisions and mirror only diagnostic Overview/All Apps context into the shadow probe.

No changes to `DockLiquidGlassView`, `CaptureSceneState`, `CaptureSourcePolicy`, `LauncherSceneOwnershipPolicy`, workstation behavior, or capture cadence are part of this diagnostic.

---

### Task 1: Add Android-Free Shadow Classification Policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java`

**Interfaces:**
- Produces: `HomeOwnershipShadowPolicy.matches(boolean launcherHome, boolean systemUiHome)`
- Produces: `HomeOwnershipShadowPolicy.baselineEligible(boolean overview, boolean allApps, boolean workstation)`
- Produces: `HomeOwnershipShadowPolicy.recheckResult(boolean launcherHome, boolean systemUiHome)` returning `TRANSIENT_MISMATCH` or `PERSISTENT_MISMATCH`.

- [ ] **Step 1: Write the failing pure-policy test**

```java
package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeOwnershipShadowPolicyTest {
    @Test public void homeAndAppBaselinesMatchOnlyWhenBothSourcesAgree() {
        assertTrue(HomeOwnershipShadowPolicy.matches(true, true));
        assertTrue(HomeOwnershipShadowPolicy.matches(false, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(true, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(false, true));
    }

    @Test public void specialLauncherScenesAreNotMigrationEvidence() {
        assertTrue(HomeOwnershipShadowPolicy.baselineEligible(false, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(true, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, true, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, false, true));
    }

    @Test public void recheckSeparatesTransientFromPersistentMismatch() {
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.TRANSIENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, true));
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.PERSISTENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, false));
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowPolicyTest
```

Expected: compilation failure because `HomeOwnershipShadowPolicy` does not exist.

- [ ] **Step 3: Implement the minimal policy**

```java
package com.hellovoid.liquiddock;

final class HomeOwnershipShadowPolicy {
    enum RecheckResult { TRANSIENT_MISMATCH, PERSISTENT_MISMATCH }

    private HomeOwnershipShadowPolicy() {}

    static boolean matches(boolean launcherHome, boolean systemUiHome) {
        return launcherHome == systemUiHome;
    }

    static boolean baselineEligible(boolean overview, boolean allApps, boolean workstation) {
        return !overview && !allApps && !workstation;
    }

    static RecheckResult recheckResult(boolean launcherHome, boolean systemUiHome) {
        return matches(launcherHome, systemUiHome)
                ? RecheckResult.TRANSIENT_MISMATCH
                : RecheckResult.PERSISTENT_MISMATCH;
    }
}
```

- [ ] **Step 4: Run the test and verify GREEN**

Run the same Gradle command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java
git commit -m "test: define HOME ownership shadow classification [skip ci]"
```

---

### Task 2: Define an Isolated Diagnostic Binder Protocol

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Interfaces:**
- Produces: `TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW = IBinder.FIRST_CALL_TRANSACTION + 2`
- Produces: `TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT = IBinder.FIRST_CALL_TRANSACTION + 2`
- Produces: request `{requestId: long, displayId: int, callback: IBinder}`.
- Produces: response `{requestId: long, status: int, homeVisible: boolean, homeTaskId: int, topFullscreenTaskId: int, topFullscreenWindowingMode: int, sampleElapsedNanos: long}`.
- Produces: `RECHECK_DELAY_MS = 160L`, `PENDING_TTL_MS = 1500L`, `MAX_PENDING = 16`.

- [ ] **Step 1: Write source contracts before implementation**

Add tests that assert:

```java
assertNotEquals(FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT,
        HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW);
assertEquals(160L, HomeOwnershipShadowProtocol.RECHECK_DELAY_MS);
assertEquals(16, HomeOwnershipShadowProtocol.MAX_PENDING);
```

Also load production sources and assert the future shadow implementation contains no `SurfaceControl`, no `CountDownLatch`, no `Future.get`, no `registerTaskOrganizer`, and no call to `FreeformBridgePolicy.CircuitBreaker`.

- [ ] **Step 2: Run the contract test and verify RED**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest
```

Expected: compilation/source-read failure because diagnostic protocol/provider files do not exist.

- [ ] **Step 3: Add the protocol constants**

```java
package com.hellovoid.liquiddock;

import android.os.IBinder;

final class HomeOwnershipShadowProtocol {
    static final String CALLBACK_DESCRIPTOR =
            "com.hellovoid.liquiddock.IHomeOwnershipShadowCallback";

    static final int TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW =
            IBinder.FIRST_CALL_TRANSACTION + 2;
    static final int TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT =
            IBinder.FIRST_CALL_TRANSACTION + 2;

    static final int STATUS_OK = 0;
    static final int STATUS_UNAVAILABLE = 1;
    static final int STATUS_STRUCTURE_FAILURE = 2;

    static final long RECHECK_DELAY_MS = 160L;
    static final long PENDING_TTL_MS = 1500L;
    static final int MAX_PENDING = 16;

    private HomeOwnershipShadowProtocol() {}
}
```

- [ ] **Step 4: Keep the contract RED only for the not-yet-created provider/probe sources**

Run the contract test. Expected: constants compile; source assertions for missing implementation files still fail.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "test: reserve isolated HOME shadow protocol [skip ci]"
```

---

### Task 3: Observe the Existing SystemUI MultiTaskingTaskRepository

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Interfaces:**
- Consumes target-ROM class: `com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository`.
- Consumes confirmed fields: `mContext`, `mBgExecutor`, `mHomeTaskInfo`.
- Consumes confirmed methods: `isHomeVisible()`, `getHomeTask()`, `getTopFullscreenTaskInfo(int)`.
- Produces: `SystemUiHomeOwnershipShadow.install(ClassLoader)`.
- Produces: `SystemUiHomeOwnershipShadow.handles(int code)`.
- Produces: `SystemUiHomeOwnershipShadow.handleTransaction(int code, Parcel data, Context authContext)`; returns `true` only for the reserved diagnostic request.

- [ ] **Step 1: Strengthen the source contract**

Require the provider source to contain:

```java
WeakReference<Object>
MultiTaskingTaskRepository
mBgExecutor
isHomeVisible
getHomeTask
getTopFullscreenTaskInfo
execute
```

Require absence of:

```text
SurfaceControl
registerTaskOrganizer
onTaskAppeared
onTaskVanished
FreeformBridgePolicy.CircuitBreaker
```

Require `SystemUiFreeformLeashProvider` to delegate the shadow code before its production freeform transaction branch, with all shadow errors contained by the shadow component.

- [ ] **Step 2: Run the contract and verify RED**

Expected: missing `SystemUiHomeOwnershipShadow` and missing delegation/install.

- [ ] **Step 3: Implement install-time repository observation**

Implementation rules:

```java
Class<?> repoClass = Class.forName(
    "com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository",
    false, classLoader);
Field contextField = HookUtil.findField(repoClass, "mContext");
Field executorField = HookUtil.findField(repoClass, "mBgExecutor");
Method homeVisible = HookUtil.findMethodBestMatch(repoClass, "isHomeVisible", new Object[0], false);
Method getHomeTask = HookUtil.findMethodBestMatch(repoClass, "getHomeTask", new Object[0], false);
Method getTopFullscreen = HookUtil.findMethodBestMatch(
    repoClass, "getTopFullscreenTaskInfo", new Object[]{0}, false);
Method execute = HookUtil.findMethodBestMatch(
    executorField.getType(), "execute", new Object[]{(Runnable) () -> {}}, false);
```

After each original constructor succeeds, publish one immutable state containing a `WeakReference<Object>` to that repository, its context, executor object, and resolved methods. No mutation of repository fields is allowed.

Use a shadow-local `volatile boolean disabledForProcess`; any install-time structure mismatch sets it immediately. Runtime request failure logs and returns unavailable but does not call the freeform breaker.

- [ ] **Step 4: Implement the one-way request handler**

For the reserved transaction:

1. authenticate caller using the same Launcher UID/package check as the production provider;
2. read `requestId`, `displayId`, callback Binder;
3. post a `Runnable` to `mBgExecutor.execute(...)` via the resolved reflective method;
4. on that executor call `isHomeVisible()`;
5. call `getHomeTask()` and `getTopFullscreenTaskInfo(displayId)` only for explanatory IDs/windowing metadata;
6. extract task IDs and top fullscreen windowing mode reflectively; unknown explanatory values are `-1` and do not invalidate `homeVisible`;
7. reply one-way to the callback with the diagnostic metadata and `System.nanoTime()`;
8. catch every Throwable inside the diagnostic component and reply `STATUS_UNAVAILABLE` or `STATUS_STRUCTURE_FAILURE` without touching production freeform state.

- [ ] **Step 5: Install the observer independently in `ModuleMain`**

In the SystemUI package branch, keep production setup first, then independently:

```java
try {
    SystemUiHomeOwnershipShadow.install(classLoader);
} catch (Throwable error) {
    Api101Bridge.log("[DC-SHADOW] HOME ownership shadow install unavailable", error);
}
return;
```

Do not let shadow install failure skip or disable the production freeform provider.

- [ ] **Step 6: Add delegation to the existing provider Binder**

At the top of `PROVIDER_BINDER.onTransact`, after standard Binder/noncustom handling and before the freeform request branch:

```java
if (SystemUiHomeOwnershipShadow.handles(code)) {
    return SystemUiHomeOwnershipShadow.handleTransaction(code, data, stateContextIfAvailable());
}
```

The exact helper should pass a valid SystemUI context for caller authentication; if production listener state is not yet available, the shadow handler returns unavailable rather than throwing.

- [ ] **Step 7: Run focused tests**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: PASS. The existing freeform contract must remain unchanged/green.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java \
        src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java \
        src/main/java/com/hellovoid/liquiddock/ModuleMain.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "feat: add read-only SystemUI HOME shadow source [skip ci]"
```

---

### Task 4: Reuse the Existing Provider Binder in Launcher

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Interfaces:**
- Produces: `FreeformTaskLeashResolver.providerBinderForDiagnostics()` returning the already-discovered provider `IBinder` or null.
- Produces: `FreeformLeashRuntime.providerBinderForDiagnostics()` returning that Binder without changing capture behavior.

- [ ] **Step 1: Add a failing source contract**

Assert that the diagnostic path does **not** instantiate another `FreeformLeashBrokerClient` and that `HomeOwnershipShadowProbe` later obtains the provider through `FreeformLeashRuntime.providerBinderForDiagnostics()`.

- [ ] **Step 2: Add minimal accessors**

In `FreeformTaskLeashResolver`:

```java
IBinder providerBinderForDiagnostics() {
    brokerClient.setDemanded(true);
    return brokerClient.launcherProvider();
}
```

In `FreeformLeashRuntime`:

```java
static IBinder providerBinderForDiagnostics() {
    FreeformTaskLeashResolver value = resolver;
    return value != null ? value.providerBinderForDiagnostics() : null;
}
```

Do not consult or mutate the freeform resolver breaker for this accessor. The diagnostic sees provider availability only.

- [ ] **Step 3: Run freeform + shadow contracts**

Expected: existing capture resolver behavior still passes its source contracts, and the shadow contract confirms no second broker client.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java \
        src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "refactor: expose provider binder to shadow diagnostics [skip ci]"
```

---

### Task 5: Add the Asynchronous Launcher Shadow Probe

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Interfaces:**
- Produces: `HomeOwnershipShadowProbe.sample(String reason, int displayId, Boolean focus, int topWindowingMode, boolean launcherHome, boolean overview, boolean allApps, boolean workstation)`.
- Produces: `HomeOwnershipShadowProbe.setOverviewActive(boolean)` and `setAllAppsActive(boolean)` only if keeping these flags inside the probe simplifies MainHook call sites.
- No method returns a scene decision.

- [ ] **Step 1: Add source contracts for asynchronous-only behavior**

Require:

```text
IBinder.FLAG_ONEWAY
Handler(Looper.getMainLooper())
RECHECK_DELAY_MS
PENDING_TTL_MS
MAX_PENDING
[DC-SHADOW]
```

Forbid:

```text
CountDownLatch
Future.get
Thread.sleep
launcherResumed =
CaptureSceneState
setLauncherState(
FreeformBridgePolicy.CircuitBreaker
```

- [ ] **Step 2: Implement bounded pending contexts**

Use an `AtomicLong` request ID and a synchronized `LinkedHashMap<Long, PendingSample>` capped at `MAX_PENDING`. Before adding a request, evict expired entries older than `PENDING_TTL_MS`; if still full, remove the oldest entry. Pending state is diagnostic context only.

`PendingSample` stores:

```text
reason, displayId, focus, topWindowingMode, launcherHome,
overview, allApps, workstation, requestElapsedNanos, phase
```

- [ ] **Step 3: Implement nonblocking request submission**

`sample(...)` obtains `FreeformLeashRuntime.providerBinderForDiagnostics()`. If null, log a rate-limited `UNAVAILABLE` and return immediately.

Build a Parcel with production provider descriptor, request ID, display ID, callback Binder, and call:

```java
provider.transact(
    HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW,
    data, null, IBinder.FLAG_ONEWAY);
```

Do not wait for a response.

- [ ] **Step 4: Implement callback classification and one-shot recheck**

On callback:

1. validate transaction code, descriptor, request ID, and status;
2. remove the matching pending sample;
3. calculate `eligible = baselineEligible(overview, allApps, workstation)`;
4. if Launcher/SystemUI agree, log `MATCH` plus `eligible`/special-scene tag;
5. if they disagree and phase is immediate, log `MISMATCH phase=immediate`, then schedule exactly one `mainHandler.postDelayed(..., RECHECK_DELAY_MS)` using the same Launcher decision/context but a fresh request ID and `phase=recheck`;
6. on recheck, log `TRANSIENT_MISMATCH`, `PERSISTENT_MISMATCH`, or `UNAVAILABLE_RECHECK`;
7. never mutate production state.

Suggested log core:

```text
[DC-SHADOW] home-ownership result=MATCH reason=focus launcherHome=true systemUiHome=true focus=true topMode=1 overview=false allApps=false workstation=false eligible=true latencyMs=4
```

- [ ] **Step 5: Run the shadow contract**

Expected: PASS and no forbidden production-state writes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "feat: add asynchronous HOME ownership shadow probe [skip ci]"
```

---

### Task 6: Instrument Existing Launcher Ownership Boundaries Without Changing Decisions

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java`

**Interfaces:**
- Consumes existing `launcherOwnsScene` / `launcherResumed` values only after production code computes them.
- Consumes `HomeOwnershipShadowProbe.sample(...)`.

- [ ] **Step 1: Add a source contract proving shadow is post-decision only**

Require the MainHook source to contain shadow calls at the three approved boundaries:

```text
seed
focus
fallback-pause
```

Keep existing assertions that `foregroundTaskWindowingMode` and `LauncherSceneOwnershipPolicy.launcherOwnsScene` are still present. Add assertions that no shadow return value is assigned to `launcherResumed`.

- [ ] **Step 2: Instrument initial seed**

After existing seed code has computed `launcherResumed`, determine display ID from the Launcher Activity's public `Display.getDisplayId()` when available, then call:

```java
HomeOwnershipShadowProbe.sample(
    "seed", displayId, focused instanceof Boolean ? (Boolean) focused : null,
    windowingMode, launcherResumed,
    false, false, workstationMode);
```

If Overview/All Apps context is already tracked in the probe, pass the probe's diagnostic flags rather than hard-coding; do not create production state for them.

- [ ] **Step 3: Instrument `onWindowFocusChanged`**

After `launcherOwnsScene` is computed and logged, call `sample("focus", ...)` with the same `windowingMode` and final `launcherOwnsScene`. Do this before/after the existing glass callbacks only as needed for logging; never branch on the shadow result.

- [ ] **Step 4: Instrument fallback `onPause`**

After current code computes `launcherResumed = LauncherSceneOwnershipPolicy.launcherOwnsScene(false, windowingMode)`, call `sample("fallback-pause", ...)`.

- [ ] **Step 5: Mirror special-scene tags only for diagnostics**

In existing Overview event hook, call `HomeOwnershipShadowProbe.setOverviewActive(active)` after production Overview handling.

In existing All Apps enter/exit hooks, call `HomeOwnershipShadowProbe.setAllAppsActive(active)` after production All Apps handling.

These flags are diagnostic metadata only; they must never feed `CaptureSceneState` or production decisions.

- [ ] **Step 6: Run focused regression tests**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest \
  --tests com.hellovoid.liquiddock.LauncherSceneOwnershipPolicyTest \
  --tests com.hellovoid.liquiddock.CaptureSceneStateTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: PASS. The ownership policy test must still prove the old policy is active.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "diag: compare Launcher and SystemUI HOME ownership [skip ci]"
```

---

### Task 7: Verify Isolation, Build, and Device Evidence Matrix

**Files:**
- Modify only if verification exposes a diagnostic bug. Do not broaden scope.
- Evidence is captured from logs; do not commit generated logs to the formal branch.

**Interfaces:**
- Produces diagnostic evidence only; no production migration.

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build the diagnostic APK**

```bash
./gradlew assembleDebug
```

Expected: successful APK generation.

- [ ] **Step 3: Review branch diff against the formal checkpoint**

```bash
git diff --stat fix/freeform-task-leash-exclusion...HEAD
git diff fix/freeform-task-leash-exclusion...HEAD -- \
  src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java \
  src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java \
  src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java
```

Expected: no production-policy edits to those three files.

- [ ] **Step 4: Verify forbidden write patterns**

```bash
grep -R "launcherResumed =" src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadow* || true
grep -R "setLauncherState\|CaptureSceneState\|FreeformBridgePolicy.CircuitBreaker" \
  src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadow* \
  src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java || true
```

Expected: no matches for production-state writes or production freeform breaker coupling.

- [ ] **Step 5: Device-test the evidence matrix**

Exercise, with `[DC-SHADOW]` logs enabled:

```text
stable HOME
stable fullscreen APP
HOME + one freeform
APP + one freeform
APP -> HOME
HOME -> APP
enter/exit Recents
enter/exit normal All Apps
workstation boundaries
HOME rotation
APP rotation
SystemUI restart/reconnect
```

Expected production behavior: identical to the already validated formal branch.

Expected shadow evidence:

- stable HOME: eligible MATCH with `launcherHome=true/systemUiHome=true`;
- stable APP: eligible MATCH with both false;
- HOME + freeform: settled eligible MATCH true/true;
- APP + freeform: settled eligible MATCH false/false;
- transition disagreements may be immediate MISMATCH but must converge to `TRANSIENT_MISMATCH` within the one 160 ms recheck to qualify for migration;
- Overview/All Apps/workstation samples are tagged ineligible/special and do not count as baseline evidence;
- SystemUI restart produces only diagnostic UNAVAILABLE/recovery; freeform exclusion and capture behavior remain unaffected.

- [ ] **Step 6: Do not merge the diagnostic branch**

Record the diagnostic branch HEAD and the observed mismatch summary. Keep the branch isolated. Any production cleanup based on this evidence gets a new formal design/plan and is reimplemented independently.
