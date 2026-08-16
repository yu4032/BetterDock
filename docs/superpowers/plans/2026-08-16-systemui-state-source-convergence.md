# SystemUI State-Source Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make HyperOS SystemUI/WMShell the sole authority for visible freeform task enumeration and leashes, while deleting the now-dead foreground-app layer-name state from LiquidDock.

**Architecture:** `SystemUiFreeformLeashProvider` will enumerate the existing `FreeformTaskListener.mTasks` on the existing Shell executor and return one all-or-nothing visible-freeform leash snapshot for a requested display. Launcher will stop reading `RunningTaskInfo` for freeform exclusion and will only request/validate the snapshot immediately before mode-1 capture. The existing HOME/APP scene state, Overview, All Apps, Workstation, and capture revision logic remain unchanged.

**Tech Stack:** Java 17, Android SDK 37, libxposed API 101, raw Binder/Parcel IPC, JUnit 4 contract/unit tests, HyperOS WMShell reflection.

## Global Constraints

- Work only on `fix/freeform-task-leash-exclusion`; do not merge or cherry-pick `dev/freeform-capture-diagnostic`.
- Do not register a second `TaskOrganizer` and do not maintain a LiquidDock task lifecycle map.
- Read `FreeformTaskListener.mTasks` only on the listener's existing `ShellTaskOrganizer.getExecutor()` executor.
- SystemUI injection is passive: no task mutation, no `SurfaceControl.Transaction`, no canonical leash release, and no LiquidDock exception may escape into SystemUI/WMShell.
- Send canonical WMShell leashes with `Parcel.writeTypedObject(surface, 0)`, never `PARCELABLE_WRITE_RETURN_VALUE`.
- Launcher owns and deterministically releases only the parcel-created `SurfaceControl` wrappers it receives.
- Provider absence, Binder/process death, timeout, malformed protocol, invalid leash, incomplete snapshot, or structural mismatch must fail closed to wallpaper for the affected mode-1 capture.
- Keep `REQUEST_TIMEOUT_MS = 25L` and `MAX_TASKS = 32`.
- HOME remains `WALLPAPER`; APP remains `FULL_DISPLAY` subject to the final freeform capture gate.
- Do not change GestureToHome/App/Recent, Overview/Recents, All Apps, Workstation/Laptop, notification shade, capture revisioning, timeout breaker, or capture cadence in this phase.
- Commit messages must include `[skip ci]`; do not force-push.

---

## File Structure

**Modify**

- `src/main/java/com/hellovoid/liquiddock/FreeformBridgePolicy.java` — Android-free fail-closed candidate-filter policy; remove task-ID deduplication once unused.
- `src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java` — introduce a new snapshot request/result transaction pair so mixed process generations fail closed.
- `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java` — enumerate WMShell's existing freeform states and emit complete display-scoped leash snapshots.
- `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java` — become a pure remote snapshot client; remove `ActivityManager`/`RunningTaskInfo` inference.
- `src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java` — eagerly demand provider discovery when the resolver is installed so first APP capture does not depend on a Launcher-side task scan.
- `src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java` — preserve the final fail-closed gate and adapt only if needed to the simplified `Resolution` semantics.
- `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java` — remove freeform preflight facade and obsolete app-layer bookkeeping; leave scene state/capture behavior otherwise unchanged.
- `src/test/java/com/hellovoid/liquiddock/FreeformBridgePolicyTest.java` — unit-test fail-closed candidate inclusion.
- `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java` — lock down SystemUI-only enumeration, new wire shape, ownership, and deleted Launcher task inference.
- `src/test/java/com/hellovoid/liquiddock/FreeformCaptureExclusionTest.java` — keep APP/HOME source and capture fallback regressions locked.

**Delete after consumer scan**

- `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`
- `src/main/java/com/hellovoid/liquiddock/FreeformCapturePolicy.java`
- `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`

No other production subsystem is in scope.

---

### Task 1: Lock Fail-Closed Snapshot Filtering in Android-Free Policy

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformBridgePolicy.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformBridgePolicyTest.java`

**Interfaces:**
- Produces: `static boolean shouldIncludeFreeformCandidate(Integer taskDisplayId, Boolean visible, int requestedDisplayId)`.
- Semantics: known invisible -> false; known different display -> false; unknown visibility/display -> true; known visible current-display -> true.

- [ ] **Step 1: Write the failing policy tests**

Add tests equivalent to:

```java
@Test public void visibleCurrentDisplayCandidateIsIncluded() {
    assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, true, 0));
}

@Test public void knownInvisibleCandidateIsSkipped() {
    assertFalse(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, false, 0));
}

@Test public void knownOtherDisplayCandidateIsSkipped() {
    assertFalse(FreeformBridgePolicy.shouldIncludeFreeformCandidate(2, true, 0));
}

@Test public void unknownMetadataFailsClosedByIncludingCandidate() {
    assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(null, true, 0));
    assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, null, 0));
    assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(null, null, 0));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.FreeformBridgePolicyTest
```

Expected: FAIL because `shouldIncludeFreeformCandidate` does not exist yet.

- [ ] **Step 3: Implement the minimal pure-Java policy**

Add:

```java
static boolean shouldIncludeFreeformCandidate(
        Integer taskDisplayId, Boolean visible, int requestedDisplayId) {
    if (Boolean.FALSE.equals(visible)) return false;
    return taskDisplayId == null || taskDisplayId == requestedDisplayId;
}
```

Do not add Android dependencies to this class.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same Gradle command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/FreeformBridgePolicy.java \
        src/test/java/com/hellovoid/liquiddock/FreeformBridgePolicyTest.java
git commit -m "test: define fail-closed freeform snapshot policy [skip ci]"
```

---

### Task 2: Move Visible-Freeform Enumeration Fully Into SystemUI

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java`

**Interfaces:**
- Consumes: `FreeformBridgePolicy.shouldIncludeFreeformCandidate(...)`.
- Produces request transaction: `TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT = IBinder.FIRST_CALL_TRANSACTION + 1`.
- Produces callback transaction: `TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT = IBinder.FIRST_CALL_TRANSACTION + 1`.
- Request payload: interface token, `long requestId`, `int displayId`, callback binder.
- Result payload: callback token, `long requestId`, `int overallStatus`, `int count`, then `count` typed `SurfaceControl` values.

- [ ] **Step 1: Replace old source-contract assertions with snapshot-contract assertions**

Update the contract test to require all of the following:

```java
assertTrue(provider.contains("mTaskInfo"));
assertTrue(provider.contains("tasks.size()"));
assertTrue(provider.contains("tasks.valueAt("));
assertTrue(provider.contains("shouldIncludeFreeformCandidate"));
assertTrue(provider.contains("writeTypedObject(surfaces[i], 0)"));
assertTrue(provider.contains("TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT"));
assertTrue(provider.contains("TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT"));
assertFalse(provider.contains("readTaskIds("));
assertFalse(provider.contains("taskIds"));
assertFalse(provider.contains("PARCELABLE_WRITE_RETURN_VALUE"));
```

Also require the protocol to contain the two new transaction constants and keep `MAX_TASKS == 32` / `REQUEST_TIMEOUT_MS == 25L`.

- [ ] **Step 2: Run the focused contract test and verify RED**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: FAIL because the provider still consumes Launcher-supplied task IDs.

- [ ] **Step 3: Add version-distinct transaction constants**

In `FreeformLeashProtocol`, keep the broker transactions unchanged and add:

```java
static final int TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT =
        IBinder.FIRST_CALL_TRANSACTION + 1;
static final int TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT =
        IBinder.FIRST_CALL_TRANSACTION + 1;
```

Do not reuse the old request/result code for the new wire shape. Old/new process overlap must yield an unsupported transaction or timeout, which already falls back to wallpaper.

- [ ] **Step 4: Resolve canonical State fields at provider install time**

During `install(ClassLoader)`, resolve:

```java
Class<?> stateClass = Class.forName(
        "com.android.wm.shell.freeform.FreeformTaskListener$State", false, classLoader);
taskInfoField = HookUtil.findField(stateClass, "mTaskInfo");
leashField = HookUtil.findField(stateClass, "mLeash");
```

Validate `mLeash` is assignable to `SurfaceControl`. Any structural mismatch must keep the existing behavior: `BREAKER.disableForProcess()` and no exception propagation into SystemUI.

- [ ] **Step 5: Change provider request parsing to display-scoped snapshot input**

For `TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT`, parse only:

```java
data.enforceInterface(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
long requestId = data.readLong();
int displayId = data.readInt();
IBinder callback = data.readStrongBinder();
```

Reject `displayId < 0` fail-closed. Binder thread still performs only authentication/parsing/scheduling; all `mTasks` reads stay inside `state.executor.execute(...)`.

- [ ] **Step 6: Enumerate WMShell's existing freeform states on the Shell executor**

Implement the executor-side logic with this shape:

```java
SparseArray<?> tasks = (SparseArray<?>) tasksField.get(listener);
ArrayList<SurfaceControl> included = new ArrayList<>();
for (int i = 0; i < tasks.size(); i++) {
    Object taskState = tasks.valueAt(i);
    if (taskState == null) continue;

    Object taskInfo = taskInfoField.get(taskState);
    Integer taskDisplayId = reflectedDisplayId(taskInfo); // null = unknown
    Boolean visible = reflectedVisibility(taskInfo);      // null = unknown
    if (!FreeformBridgePolicy.shouldIncludeFreeformCandidate(
            taskDisplayId, visible, displayId)) continue;

    Object leashValue = leashField.get(taskState);
    if (!(leashValue instanceof SurfaceControl)
            || !((SurfaceControl) leashValue).isValid()) {
        throw new IllegalStateException("candidate freeform leash unavailable");
    }
    included.add((SurfaceControl) leashValue);
    if (included.size() > FreeformLeashProtocol.MAX_TASKS) {
        throw new IllegalStateException("too many visible freeform tasks");
    }
}
```

`reflectedDisplayId` and `reflectedVisibility` must return `null` when metadata cannot be determined, not a value that causes the task to be skipped. They may reflect field names and getter names, but must never directly compile against hidden `RunningTaskInfo.displayId`.

- [ ] **Step 7: Send one all-or-nothing snapshot result**

Replace per-task statuses with:

```java
sendSnapshotResult(callback, requestId,
        FreeformLeashProtocol.STATUS_OK,
        included.toArray(new SurfaceControl[0]));
```

Failure/unavailable responses use count `0`. The result writer must use:

```java
out.writeInt(overallStatus);
out.writeInt(surfaces.length);
for (int i = 0; i < surfaces.length; i++) {
    out.writeTypedObject(surfaces[i], 0);
}
```

Never release a WMShell-owned surface in SystemUI.

- [ ] **Step 8: Run focused tests and verify GREEN**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.FreeformBridgePolicyTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java \
        src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java \
        src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java
git commit -m "refactor: source freeform snapshot from SystemUI [skip ci]"
```

---

### Task 3: Reduce Launcher Resolver to a Remote Snapshot Client

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java` only if needed for simplified result semantics.
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java`

**Interfaces:**
- `Resolution resolveVisibleLeashes(int displayId)` remains the capture-gate entry point.
- `Resolution.noFreeform()` means authoritative successful snapshot with zero freeform surfaces.
- `Resolution.unavailable(true)` means the snapshot is unknown/unsafe and must force wallpaper.
- Successful non-empty snapshots return `Resolution(visibleFreeform=true, safe=true, ownedRemoteLeashes)`.

- [ ] **Step 1: Strengthen the contract test to forbid Launcher task inference**

Require:

```java
assertFalse(resolver.contains("ActivityManager"));
assertFalse(resolver.contains("RunningTaskInfo"));
assertFalse(resolver.contains("getRunningTasks"));
assertFalse(resolver.contains("getWindowingMode"));
assertFalse(resolver.contains("displayId(task)"));
assertFalse(resolver.contains("isVisible(task)"));
assertFalse(resolver.contains("requestedTaskIds"));
assertFalse(resolver.contains("LinkedHashMap<Integer, SurfaceControl>"));
assertTrue(resolver.contains("TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT"));
assertTrue(resolver.contains("TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT"));
assertTrue(resolver.contains("REQUEST_TIMEOUT_MS"));
assertTrue(resolver.contains("surface.release()"));
```

- [ ] **Step 2: Run the focused test and verify RED**

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest
```

Expected: FAIL because the resolver still calls `ActivityManager.getRunningTasks()` and sends task IDs.

- [ ] **Step 3: Make `resolveVisibleLeashes(displayId)` request the authoritative snapshot directly**

At request start:

```java
brokerClient.setDemanded(true);
if (breaker.isDisabled()) return Resolution.unavailable(true);
IBinder provider = brokerClient.launcherProvider();
if (provider == null) return Resolution.unavailable(true);
```

Write only:

```java
request.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
request.writeLong(requestId);
request.writeInt(displayId);
request.writeStrongBinder(state.callback);
```

Use `TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT` and preserve the 25 ms latch wait.

- [ ] **Step 4: Simplify callback ownership around overall status + surface array**

`RequestState` should own a simple list/array of received wrappers, not a task-ID map. Parse:

```java
long responseId = data.readLong();
int status = data.readInt();
int count = data.readInt();
```

Rules:

```text
responseId mismatch -> malformed, release every parsed wrapper
count < 0 or count > MAX_TASKS -> malformed
STATUS_OK + count == 0 -> authoritative no-freeform
STATUS_OK + count > 0 -> every wrapper must be non-null and valid
STATUS_UNAVAILABLE / STATUS_INFRASTRUCTURE_FAILURE -> count must be 0, return unsafe
unknown status -> malformed/unsafe
late callback after expire -> release every parsed wrapper
```

`takeResolution()` must transfer ownership exactly once and leave no wrapper in the request state.

- [ ] **Step 5: Eagerly start provider discovery when the runtime resolver is installed**

Change `FreeformLeashRuntime.install(...)` to:

```java
static void install(FreeformTaskLeashResolver value) {
    if (value == null) return;
    resolver = value;
    value.setProviderDemanded(true);
}
```

This does not query SystemUI on HOME; it only begins broker/provider discovery so the first APP mode-1 capture is less likely to spend its only frame waiting for service binding.

- [ ] **Step 6: Preserve the capture gate's single-proceed fail-closed behavior**

`FreeformCaptureLeashHook` must still obey:

```text
success count 0 -> original FULL_DISPLAY unchanged
success count >0 -> merge all remote leashes into existing exclusions
unsafe/timeout/malformed -> args[3]=null, args[4]=null, args[5]=2
original capture method -> proceed exactly once
finally -> resolution.close()
```

Do not introduce a second scene/task state machine.

- [ ] **Step 7: Run focused tests and verify GREEN**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.FreeformBridgePolicyTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest \
  --tests com.hellovoid.liquiddock.FreeformCaptureExclusionTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java \
        src/main/java/com/hellovoid/liquiddock/FreeformLeashRuntime.java \
        src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java \
        src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java
git commit -m "refactor: remove Launcher freeform task scans [skip ci]"
```

---

### Task 4: Delete Dock Freeform Preflight and Dead Foreground-App Layer State

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/FreeformCapturePolicy.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformBridgePolicy.java` if `deduplicateTaskIds` becomes unused.
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformTaskLeashBridgeContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureExclusionTest.java`

**Interfaces:**
- Full-display name exclusions remain only Dock/drag-layer names via existing `CaptureExclusionNames`.
- Freeform safety is exclusively the final `FreeformCaptureLeashHook` immediately before mode-1 submission.
- `CaptureSourcePolicy` remains the source authority: HOME -> WALLPAPER, APP -> FULL_DISPLAY.

- [ ] **Step 1: Write deletion/source contracts before changing Dock code**

Replace old `FreeformLayerResolver` assertions with:

```java
String dock = source("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java");
assertFalse(dock.contains("FreeformLayerResolver"));
assertFalse(dock.contains("SurfaceLayerNameResolver"));
assertFalse(dock.contains("appLayerName"));
assertFalse(dock.contains("appLayerPkg"));
assertFalse(dock.contains("refreshForegroundAppLayer"));
assertFalse(dock.contains("resolveAppLayerByUid"));
assertFalse(dock.contains("liveHomeBehindFreeform"));
```

Add file-existence assertions that the three obsolete classes no longer exist after this task. Keep the existing source-policy assertions that HOME is wallpaper and APP is full-display.

- [ ] **Step 2: Run the focused tests and verify RED**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest \
  --tests com.hellovoid.liquiddock.FreeformCaptureExclusionTest
```

Expected: FAIL because Dock still constructs/uses the preflight facade and old app-layer fields.

- [ ] **Step 3: Remove the old resolver fields and constructor wiring from Dock**

Delete:

```java
private final SurfaceLayerNameResolver surfaceLayerNameResolver;
private final FreeformLayerResolver freeformLayerResolver;
```

and constructor setup equivalent to:

```java
this.surfaceLayerNameResolver = new SurfaceLayerNameResolver();
this.freeformLayerResolver = new FreeformLayerResolver(getContext(), surfaceLayerNameResolver);
```

Instead ensure the Launcher resolver is installed directly once from the Dock constructor/setup point:

```java
FreeformLeashRuntime.install(new FreeformTaskLeashResolver(getContext()));
```

Do not create a second resolver per frame.

- [ ] **Step 4: Remove freeform presence invalidation/preflight from Dock**

Delete `freeformLayerResolver.invalidate()` calls.

Simplify `resolveFullDisplayExclusions()` so it only merges the existing local Dock/drag names and is always locally safe; freeform completeness is no longer represented through fake/non-empty layer names. Conceptually:

```java
private FullDisplayExclusions resolveFullDisplayExclusions() {
    String[] names = CaptureExclusionNames.merge(
            dockWindowLayerName, dragLayerName, java.util.Collections.emptyList());
    return new FullDisplayExclusions(names, true);
}
```

Match the actual `FullDisplayExclusions` constructor/signature already present in the file; do not change unrelated capture logic.

- [ ] **Step 5: Remove obsolete HOME freeform source plumbing**

Delete the `liveHomeBehindFreeform` calculation and pass `false` (or use the existing overload without that compatibility argument) into `CaptureSourcePolicy`.

Do **not** change policy behavior:

```text
HOME -> WALLPAPER
APP -> FULL_DISPLAY
```

- [ ] **Step 6: Delete dead foreground-app layer bookkeeping**

Delete:

```text
appLayerName
appLayerPkg
refreshForegroundAppLayer()
resolveAppLayerByUid()
attach/focus calls that only refresh/log appLayerName
```

Do not replace this dead state with a SystemUI query.

- [ ] **Step 7: Delete obsolete classes and Android-free dead helper**

After a source search confirms no production consumers, delete:

```text
FreeformLayerResolver.java
FreeformCapturePolicy.java
SurfaceLayerNameResolver.java
```

If `FreeformBridgePolicy.deduplicateTaskIds(...)` has no source consumer after Task 3, remove that method and its obsolete test cases as well.

- [ ] **Step 8: Run focused tests and verify GREEN**

```bash
./gradlew testDebugUnitTest \
  --tests com.hellovoid.liquiddock.FreeformBridgePolicyTest \
  --tests com.hellovoid.liquiddock.FreeformTaskLeashBridgeContractTest \
  --tests com.hellovoid.liquiddock.FreeformCaptureExclusionTest \
  --tests com.hellovoid.liquiddock.CaptureSourcePolicyTest
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A src/main/java/com/hellovoid/liquiddock \
           src/test/java/com/hellovoid/liquiddock
git commit -m "refactor: remove redundant freeform state inference [skip ci]"
```

---

### Task 5: Full Regression and Scope Verification

**Files:**
- Modify only if verification exposes a defect in Tasks 1-4.
- Update: `docs/superpowers/specs/2026-08-16-systemui-state-source-convergence-design.md` only if implementation facts require a non-behavioral clarification.

**Interfaces:**
- No new interfaces. This task proves the approved scope was preserved.

- [ ] **Step 1: Run the complete unit/contract suite**

```bash
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with all tests passing.

- [ ] **Step 2: Build the app**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL and a debug APK under `build/outputs/apk/debug/`.

- [ ] **Step 3: Scan production source for retired inference paths**

Run:

```bash
grep -R -n -E \
  'FreeformLayerResolver|FreeformCapturePolicy|SurfaceLayerNameResolver|appLayerName|appLayerPkg|refreshForegroundAppLayer|resolveAppLayerByUid' \
  src/main/java || true

grep -R -n -E \
  'ActivityManager|getRunningTasks|RunningTaskInfo|getWindowingMode|displayId\(task\)|isVisible\(task\)' \
  src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java || true
```

Expected: no matches in the first command; no matches in `FreeformTaskLeashResolver` for the second command.

Do **not** require zero `ActivityManager/getRunningTasks` globally: the deferred HOME/APP ownership path in `MainHook.foregroundTaskWindowingMode()` intentionally remains for the next diagnostic phase.

- [ ] **Step 4: Verify SystemUI safety invariants statically**

Run:

```bash
grep -n -E \
  'mTasks|executor\.execute|mTaskInfo|mLeash|writeTypedObject' \
  src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java

grep -n -E \
  'registerTaskOrganizer|new SurfaceControl\.Transaction|PARCELABLE_WRITE_RETURN_VALUE' \
  src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java || true
```

Expected: first command shows Shell-executor snapshot code and flags-0 parceling; second command has no matches.

- [ ] **Step 5: Review the net diff against the pre-convergence commit**

Compare against `f508042f7e4a504e67b5b8bf18b738ee986dd349` and confirm the diff contains only the files/scopes listed in this plan. In particular, no edits to `LauncherSceneOwnershipPolicy`, Overview event hooks, All Apps hooks, Workstation behavior, notification shade behavior, capture revisioning, or cadence are allowed.

- [ ] **Step 6: Commit any verification-only doc clarification if needed**

Only if documentation needs alignment:

```bash
git add docs/superpowers/specs/2026-08-16-systemui-state-source-convergence-design.md
git commit -m "docs: align SystemUI convergence implementation note [skip ci]"
```

Otherwise make no extra commit.

- [ ] **Step 7: Hand off the device matrix without claiming it was run here**

Device verification must cover:

```text
APP, no freeform                 -> live FULL_DISPLAY
APP, one freeform                -> live FULL_DISPLAY; freeform excluded
APP, multiple freeforms          -> all freeforms excluded
freeform closes during capture   -> no crash/leak; next snapshot converges
HOME, with/without freeform      -> WALLPAPER
APP -> HOME -> APP + freeform    -> correct source switches
Recents entry/exit               -> unchanged
Workstation                      -> unchanged
SystemUI restart                 -> temporary wallpaper fallback, then recovery
Launcher restart                 -> provider rediscovery, then recovery
protocol/provider unavailable    -> wallpaper, never unsafe FULL_DISPLAY
```

Do not merge into `api101-migration` until this device matrix passes.
