# SystemUI HOME/APP Ownership Shadow Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a diagnostic-only HOME/APP ownership shadow audit that compares the current Launcher decision with SystemUI/WMShell `MultiTaskingTaskRepository.isHomeVisible()` without changing capture behavior.

**Architecture:** The existing Launcher inference remains authoritative. A separate SystemUI diagnostic observes the existing `MultiTaskingTaskRepository`, samples it on its confirmed `mBgExecutor`, and answers one-way metadata-only Binder requests through the already registered SystemUI provider Binder. Launcher logs MATCH/MISMATCH evidence asynchronously and performs exactly one 160 ms recheck after an immediate mismatch. Shadow failures are isolated from the production freeform breaker.

**Tech Stack:** Java 17, Android/HyperOS hidden APIs through reflection, libxposed API 101 hooks, raw Binder/Parcel IPC, JUnit 4.

## Global Constraints

- Diagnostic code lives only on `dev/home-ownership-systemui-shadow`, created from the formal branch after this plan commit.
- Never merge or cherry-pick the diagnostic branch wholesale into `fix/freeform-task-leash-exclusion` or `api101-migration`.
- Shadow results must never assign `launcherResumed`, mutate `CaptureSceneState`, choose a capture source, or alter gesture/Overview/All Apps/workstation/freeform behavior.
- Do not register another `TaskOrganizer`, task listener, repository, or SystemUI task map.
- Observe only the existing `MultiTaskingTaskRepository` and keep only a `WeakReference<Object>`.
- Read repository state only through the confirmed `mBgExecutor`. If `mBgExecutor` or `execute(Runnable)` cannot be resolved, disable only shadow diagnostics.
- Shadow IPC is one-way and asynchronous; no `CountDownLatch`, `Future.get`, `Thread.sleep`, polling, or UI/capture-thread waits.
- Exactly one delayed recheck follows an immediate mismatch. No timer loop or per-frame monitor.
- Shadow errors and disable state must never call or mutate `FreeformBridgePolicy.CircuitBreaker`.
- Keep existing broker/provider caller authentication.
- All commits use `[skip ci]`.

---

## File Map

**Create**

- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java` — diagnostic Binder codes and bounds.
- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java` — pure comparison/special-scene rules.
- `src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java` — read-only SystemUI repository observer and transaction handler.
- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java` — Launcher asynchronous requester, bounded pending contexts, recheck and logging.
- `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java`
- `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Modify narrowly**

- `ModuleMain.java` — independently install shadow observer in SystemUI.
- `SystemUiFreeformLeashProvider.java` — delegate only the reserved shadow transaction to the diagnostic component.
- `FreeformTaskLeashResolver.java` — package-private read-only access to the already discovered provider Binder.
- `FreeformLeashRuntime.java` — expose that Binder to diagnostics.
- `MainHook.java` — sample only after current production decisions; mirror Overview/All Apps flags only as diagnostic metadata.

**Must not change**

- `DockLiquidGlassView.java`
- `CaptureSceneState.java`
- `CaptureSourcePolicy.java`
- `LauncherSceneOwnershipPolicy.java`
- workstation capture logic
- freeform capture-gate semantics

---

### Task 1: Pure Shadow Classification Policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java`

**Produces:**

```java
static boolean matches(boolean launcherHome, boolean systemUiHome)
static boolean baselineEligible(boolean overview, boolean allApps, boolean workstation)
static RecheckResult recheckResult(boolean launcherHome, boolean systemUiHome)
```

- [ ] **Step 1: Write the failing test**

```java
package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeOwnershipShadowPolicyTest {
    @Test public void matchingBaselinesAgreeExactly() {
        assertTrue(HomeOwnershipShadowPolicy.matches(true, true));
        assertTrue(HomeOwnershipShadowPolicy.matches(false, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(true, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(false, true));
    }

    @Test public void specialScenesAreNotMigrationEvidence() {
        assertTrue(HomeOwnershipShadowPolicy.baselineEligible(false, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(true, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, true, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, false, true));
    }

    @Test public void recheckClassifiesConvergence() {
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.TRANSIENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, true));
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.PERSISTENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, false));
    }
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowPolicyTest
```

Expected: compilation failure because the class does not exist.

- [ ] **Step 3: Implement minimally**

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

- [ ] **Step 4: Run GREEN**

Use the same Gradle command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicy.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowPolicyTest.java
git commit -m "test: define HOME ownership shadow policy [skip ci]"
```

---

### Task 2: Isolated Diagnostic Binder Protocol and Contracts

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java`
- Create: `src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java`

**Produces:**

```java
TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW = IBinder.FIRST_CALL_TRANSACTION + 2
TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT = IBinder.FIRST_CALL_TRANSACTION + 2
RECHECK_DELAY_MS = 160L
PENDING_TTL_MS = 1500L
MAX_PENDING = 16
```

Request payload:

```text
provider descriptor, requestId(long), displayId(int), callback(IBinder)
```

Response payload:

```text
shadow callback descriptor, requestId(long), status(int), homeVisible(boolean),
homeTaskId(int), topFullscreenTaskId(int), topFullscreenWindowingMode(int), sampleElapsedNanos(long)
```

- [ ] **Step 1: Write the contract test first**

The test must assert distinct transaction codes and later source isolation:

```java
assertNotEquals(FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT,
        HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW);
assertEquals(160L, HomeOwnershipShadowProtocol.RECHECK_DELAY_MS);
assertEquals(1500L, HomeOwnershipShadowProtocol.PENDING_TTL_MS);
assertEquals(16, HomeOwnershipShadowProtocol.MAX_PENDING);
```

It must also source-scan future shadow classes for forbidden strings:

```text
SurfaceControl
CountDownLatch
Future.get
Thread.sleep
registerTaskOrganizer
FreeformBridgePolicy.CircuitBreaker
launcherResumed =
setLauncherState(
```

- [ ] **Step 2: Run RED**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest
```

Expected: missing diagnostic classes.

- [ ] **Step 3: Add protocol constants**

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

- [ ] **Step 4: Run test again**

Expected: protocol assertions pass; source assertions for provider/probe remain RED until later tasks.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProtocol.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "test: reserve HOME ownership shadow protocol [skip ci]"
```

---

### Task 3: Read-Only SystemUI Repository Observer

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java`
- Test: `HomeOwnershipShadowContractTest.java`

**Confirmed target-ROM structure:**

```text
com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository
mContext : Context
mBgExecutor : com.android.wm.shell.common.ShellExecutor
mHomeTaskInfo : RunningTaskInfo
isHomeVisible()
getHomeTask()
getTopFullscreenTaskInfo(int displayId)
```

**Produces:**

```java
static void install(ClassLoader classLoader)
static boolean handles(int code)
static boolean handleTransaction(int code, Parcel data, Context authContext)
```

- [ ] **Step 1: Extend the RED contract**

Require `SystemUiHomeOwnershipShadow.java` to contain:

```text
WeakReference<Object>
MultiTaskingTaskRepository
mBgExecutor
isHomeVisible
getHomeTask
getTopFullscreenTaskInfo
execute
```

Forbid:

```text
SurfaceControl
registerTaskOrganizer
onTaskAppeared
onTaskVanished
FreeformBridgePolicy.CircuitBreaker
```

- [ ] **Step 2: Resolve structure once at install**

Use:

```java
Class<?> repoClass = Class.forName(
        "com.android.wm.shell.multitasking.common.taskmanager.MultiTaskingTaskRepository",
        false, classLoader);
Field contextField = HookUtil.findField(repoClass, "mContext");
Field executorField = HookUtil.findField(repoClass, "mBgExecutor");
Method isHomeVisible = HookUtil.findMethodBestMatch(
        repoClass, "isHomeVisible", new Object[0], false);
Method getHomeTask = HookUtil.findMethodBestMatch(
        repoClass, "getHomeTask", new Object[0], false);
Method getTopFullscreenTaskInfo = HookUtil.findMethodBestMatch(
        repoClass, "getTopFullscreenTaskInfo", new Object[]{0}, false);
```

After obtaining a constructed repository instance, resolve its actual executor method once:

```java
Object executor = executorField.get(repository);
Method execute = HookUtil.findMethodBestMatch(
        executor.getClass(), "execute", new Object[]{(Runnable) () -> {}}, false);
```

If any required structure is unavailable, set a shadow-local `disabledForProcess=true` and log one `[DC-SHADOW]` capability failure. Do not touch production freeform state.

- [ ] **Step 3: Observe only after original constructor succeeds**

For every repository constructor:

```java
HookUtil.hook(ctor, chain -> {
    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
    try {
        observeRepository(chain.getThisObject());
    } catch (Throwable error) {
        disableShadow("observe repository", error);
    }
    return result;
});
```

`observeRepository` publishes one immutable state holding `WeakReference<Object> repository`, `Context context`, executor object and reflected methods.

- [ ] **Step 4: Implement metadata-only request handling**

`handles(code)` returns true only for `TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW`.

`handleTransaction(...)`:

1. returns true without action if `authContext == null` or shadow disabled;
2. authenticates Launcher with `Binder.getCallingUid()` and `getPackagesForUid()`;
3. enforces the production provider descriptor;
4. reads request ID, display ID and callback;
5. schedules one Runnable through the reflected `execute(Runnable)` on `mBgExecutor`;
6. on that executor invokes `isHomeVisible()`;
7. invokes `getHomeTask()` and `getTopFullscreenTaskInfo(displayId)` only for explanatory metadata;
8. reflectively reads task ID / top fullscreen windowing mode; unknown explanatory metadata becomes `-1`;
9. sends one-way callback with `System.nanoTime()`;
10. catches all errors locally and returns unavailable/structure-failure without using the production breaker.

- [ ] **Step 5: Install shadow independently in `ModuleMain`**

In the existing SystemUI branch, after the production freeform install attempt:

```java
try {
    SystemUiHomeOwnershipShadow.install(classLoader);
} catch (Throwable error) {
    Api101Bridge.log("[DC-SHADOW] HOME ownership shadow install unavailable", error);
}
return;
```

Production freeform install failure and shadow install failure remain independent.

- [ ] **Step 6: Delegate only the shadow transaction from the existing provider Binder**

At the beginning of `SystemUiFreeformLeashProvider.PROVIDER_BINDER.onTransact`:

```java
ListenerState state = currentState;
if (SystemUiHomeOwnershipShadow.handles(code)) {
    Context authContext = state != null ? state.context : null;
    return SystemUiHomeOwnershipShadow.handleTransaction(code, data, authContext);
}
if (code != FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT) {
    return super.onTransact(code, data, reply, flags);
}
```

Then keep the existing production freeform branch unchanged. The diagnostic handler catches its own errors.

- [ ] **Step 7: Run focused tests**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java \
        src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java \
        src/main/java/com/hellovoid/liquiddock/ModuleMain.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "feat: add read-only SystemUI HOME shadow source [skip ci]"
```

---

### Task 4: Reuse the Existing Provider Binder

**Files:**
- Modify: `FreeformTaskLeashResolver.java`
- Modify: `FreeformLeashRuntime.java`
- Test: `HomeOwnershipShadowContractTest.java`

**Produces:**

```java
IBinder FreeformTaskLeashResolver.providerBinderForDiagnostics()
static IBinder FreeformLeashRuntime.providerBinderForDiagnostics()
```

- [ ] **Step 1: Add RED contract**

Assert the future `HomeOwnershipShadowProbe` contains `FreeformLeashRuntime.providerBinderForDiagnostics()` and does not contain `new FreeformLeashBrokerClient`.

- [ ] **Step 2: Add resolver accessor**

```java
IBinder providerBinderForDiagnostics() {
    brokerClient.setDemanded(true);
    return brokerClient.launcherProvider();
}
```

This accessor must not inspect or mutate the resolver's `breaker`.

- [ ] **Step 3: Add runtime accessor**

```java
static IBinder providerBinderForDiagnostics() {
    FreeformTaskLeashResolver value = resolver;
    return value != null ? value.providerBinderForDiagnostics() : null;
}
```

- [ ] **Step 4: Run freeform + shadow contracts**

Expected: PASS and no second broker client.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java \
        src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "refactor: expose provider binder to shadow diagnostics [skip ci]"
```

---

### Task 5: Asynchronous Launcher Shadow Probe

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java`
- Test: `HomeOwnershipShadowContractTest.java`

**Exact interface:**

```java
static void setOverviewActive(boolean active)
static void setAllAppsActive(boolean active)
static void sample(String reason, int displayId, Boolean focus,
                   int topWindowingMode, boolean launcherHome,
                   boolean workstation)
```

The probe owns diagnostic-only `volatile boolean overviewActive` and `allAppsActive`. `sample(...)` snapshots them into its pending context.

- [ ] **Step 1: Add asynchronous source contracts**

Require:

```text
IBinder.FLAG_ONEWAY
Handler(Looper.getMainLooper())
RECHECK_DELAY_MS
PENDING_TTL_MS
MAX_PENDING
[DC-SHADOW]
FreeformLeashRuntime.providerBinderForDiagnostics()
```

Forbid:

```text
CountDownLatch
Future.get
Thread.sleep
new FreeformLeashBrokerClient
launcherResumed =
setLauncherState(
CaptureSceneState
FreeformBridgePolicy.CircuitBreaker
```

- [ ] **Step 2: Implement bounded pending contexts**

Use:

```java
private static final AtomicLong REQUEST_IDS = new AtomicLong();
private static final LinkedHashMap<Long, PendingSample> PENDING = new LinkedHashMap<>();
private static final Handler MAIN = new Handler(Looper.getMainLooper());
```

Before insert, remove entries older than `PENDING_TTL_MS`. If still at `MAX_PENDING`, evict the oldest. Pending data is diagnostic only.

`PendingSample` stores reason, display ID, focus, top mode, Launcher HOME result, snapshotted Overview/All Apps/workstation flags, request timestamp and phase (`IMMEDIATE` or `RECHECK`).

- [ ] **Step 3: Implement nonblocking request submission**

`sample(...)` calls:

```java
IBinder provider = FreeformLeashRuntime.providerBinderForDiagnostics();
```

If null, rate-limit an `UNAVAILABLE` log and return.

Submit only:

```java
provider.transact(
        HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW,
        data, null, IBinder.FLAG_ONEWAY);
```

Never wait for a response.

- [ ] **Step 4: Implement callback and exactly one recheck**

The callback validates descriptor/request ID/status, removes the pending sample, and computes:

```java
boolean eligible = HomeOwnershipShadowPolicy.baselineEligible(
        sample.overview, sample.allApps, sample.workstation);
boolean match = HomeOwnershipShadowPolicy.matches(
        sample.launcherHome, systemUiHomeVisible);
```

Rules:

- match → log `MATCH` with `eligible`, context and latency;
- immediate mismatch → log `MISMATCH phase=immediate`, then schedule exactly one fresh request after `RECHECK_DELAY_MS` with the same Launcher decision/context and phase `RECHECK`;
- recheck agreement → `TRANSIENT_MISMATCH`;
- recheck disagreement → `PERSISTENT_MISMATCH`;
- missing provider/failed recheck → `UNAVAILABLE_RECHECK`;
- no result ever changes production state.

Suggested record:

```text
[DC-SHADOW] home-ownership result=MATCH reason=focus launcherHome=true systemUiHome=true focus=true topMode=1 overview=false allApps=false workstation=false eligible=true latencyMs=4
```

- [ ] **Step 5: Run contract**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadowProbe.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "feat: add asynchronous HOME ownership shadow probe [skip ci]"
```

---

### Task 6: Instrument Existing Launcher Decision Boundaries

**Files:**
- Modify: `MainHook.java`
- Test: `HomeOwnershipShadowContractTest.java`
- Keep: `LauncherSceneOwnershipPolicyTest.java`

- [ ] **Step 1: Lock production ownership logic in the test**

The contract must continue asserting that `MainHook` contains:

```text
foregroundTaskWindowingMode
LauncherSceneOwnershipPolicy.launcherOwnsScene
```

and that `LauncherSceneOwnershipPolicyTest` remains present. Shadow is evidence only.

- [ ] **Step 2: Add a small display-ID helper in MainHook**

```java
private static int launcherDisplayId(Object launcher) {
    if (!(launcher instanceof Activity)) return android.view.Display.DEFAULT_DISPLAY;
    try {
        android.view.Display display = ((Activity) launcher).getDisplay();
        return display != null ? display.getDisplayId() : android.view.Display.DEFAULT_DISPLAY;
    } catch (Throwable ignored) {
        return android.view.Display.DEFAULT_DISPLAY;
    }
}
```

This uses public `Display.getDisplayId()` and introduces no hidden task field.

- [ ] **Step 3: Sample after `seedLauncherLifecycleState()` computes production state**

After current code computes/logs `launcherResumed`, call:

```java
HomeOwnershipShadowProbe.sample(
        "seed", launcherDisplayId(launcher),
        focused instanceof Boolean ? (Boolean) focused : null,
        windowingMode, launcherResumed, workstationMode);
```

Do not use the return value; `sample` is `void`.

- [ ] **Step 4: Sample after focus ownership is computed**

Immediately after existing `launcherOwnsScene` calculation/logging:

```java
HomeOwnershipShadowProbe.sample(
        "focus", launcherDisplayId(chain.getThisObject()), hasFocus,
        windowingMode, launcherOwnsScene, workstationMode);
```

Then continue the existing `onLauncherFocusLost/setLauncherState/prearm` behavior unchanged.

- [ ] **Step 5: Sample after fallback `onPause` computes `launcherResumed`**

```java
HomeOwnershipShadowProbe.sample(
        "fallback-pause", launcherDisplayId(chain.getThisObject()), Boolean.FALSE,
        windowingMode, launcherResumed, workstationMode);
```

- [ ] **Step 6: Mirror special-scene tags for diagnostics only**

After existing Overview production handling:

```java
HomeOwnershipShadowProbe.setOverviewActive(active);
```

In existing All Apps enter/exit paths, after the production state update:

```java
HomeOwnershipShadowProbe.setAllAppsActive(true);
HomeOwnershipShadowProbe.setAllAppsActive(false);
```

These diagnostic flags are never read by production capture code.

- [ ] **Step 7: Run focused regression tests**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.HomeOwnershipShadowContractTest \
  --tests com.hellovoid.liquiddock.LauncherSceneOwnershipPolicyTest \
  --tests com.hellovoid.liquiddock.CaptureSceneStateTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/MainHook.java \
        src/test/java/com/hellovoid/liquiddock/HomeOwnershipShadowContractTest.java
git commit -m "diag: compare Launcher and SystemUI HOME ownership [skip ci]"
```

---

### Task 7: Verification and Device Evidence

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Build diagnostic APK**

```bash
./gradlew assembleDebug
```

Expected: successful APK generation.

- [ ] **Step 3: Prove policy files are untouched**

```bash
git diff fix/freeform-task-leash-exclusion...HEAD -- \
  src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java \
  src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java \
  src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java \
  src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java
```

Expected: empty diff.

- [ ] **Step 4: Prove shadow contains no behavior writes or production breaker coupling**

```bash
grep -R "launcherResumed =\|setLauncherState\|CaptureSceneState\|FreeformBridgePolicy.CircuitBreaker" \
  src/main/java/com/hellovoid/liquiddock/HomeOwnershipShadow* \
  src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipShadow.java || true
```

Expected: no matches.

- [ ] **Step 5: Device evidence matrix**

Exercise:

```text
stable HOME
stable fullscreen APP
HOME + freeform
APP + freeform
APP -> HOME
HOME -> APP
Recents enter/exit
normal All Apps enter/exit
workstation boundaries
HOME rotation
APP rotation
SystemUI restart/reconnect
```

Expected production behavior: identical to the already validated formal branch.

Migration-quality shadow evidence requires:

- stable HOME → eligible MATCH true/true;
- stable APP → eligible MATCH false/false;
- settled HOME + freeform → eligible MATCH true/true;
- settled APP + freeform → eligible MATCH false/false;
- ordinary transition disagreements, if any, converge to `TRANSIENT_MISMATCH` on the single 160 ms recheck;
- no `PERSISTENT_MISMATCH` at normal HOME/APP source-decision boundaries;
- Overview/All Apps/workstation samples are `eligible=false` and are not migration evidence;
- SystemUI restart produces diagnostic unavailable/recovery only, with no freeform/capture regression.

- [ ] **Step 6: Keep diagnostic branch isolated**

Record its HEAD and evidence summary. Do not merge or cherry-pick the diagnostic branch. A successful audit starts a new formal design cycle for deleting `foregroundTaskWindowingMode()` and `LauncherSceneOwnershipPolicy`.