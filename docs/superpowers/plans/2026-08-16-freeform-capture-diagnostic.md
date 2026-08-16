# One-Shot Freeform Capture Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot, read-only diagnostic that runs inside the injected Launcher process when a visible freeform task exists and the Dock enters capture preflight, so one device reproduction can identify why live capture is falling back to wallpaper.

**Architecture:** Keep the diagnostic isolated in a new `FreeformCaptureDiagnostic` component. `FreeformLayerResolver` and `SurfaceLayerNameResolver` gain read-only diagnostic snapshot APIs that do not alter their production caches or resolution semantics. `DockLiquidGlassView` only passes current preflight facts and the already-computed production exclusion result to the diagnostic on the capture worker, immediately before the existing `safe -> WALLPAPER` fallback.

**Tech Stack:** Java, Android `ActivityManager.RunningTaskInfo`, hidden/reflected SurfaceFlinger APIs already used by the project, JUnit 4 source-contract tests, libxposed API101 runtime.

## Global Constraints

- Run automatically at most once per Launcher process lifetime.
- Trigger only when a visible freeform task is present and the Dock is entering capture preflight.
- Use log tag `LiquidDockDiag` and one diagnostic id for the grouped report.
- Do not call `captureScreenAsync`, `captureLayerAsync`, `captureDisplay`, or any visual capture API from the diagnostic.
- Do not modify `CaptureSceneState`, `CaptureSourcePolicy`, `LiveScreenCapture`, workstation All Apps, workstation Recents, Dock geometry, or ownership logic.
- Do not remove or weaken the current `fullDisplayExclusions.safe -> WALLPAPER` fallback.
- Diagnostic snapshot methods must not mutate the production resolver caches.
- Do not save screenshots or binary artifacts.
- No settings UI and no manual trigger.
- Keep all commits on `dev/freeform-capture-diagnostic` and use `[skip ci]`.
- Do not run CI or build an APK in this implementation pass.

---

### Task 1: Lock the diagnostic safety contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java`

**Interfaces:**
- Consumes: source files for `FreeformCaptureDiagnostic`, `FreeformLayerResolver`, `SurfaceLayerNameResolver`, and `DockLiquidGlassView`.
- Produces: a RED source-contract that requires one-shot behavior, read-only diagnostic APIs, worker-preflight integration, and preservation of the production wallpaper fallback.

- [ ] **Step 1: Write the failing source-contract test**

Create a JUnit 4 test that reads source files and asserts all of the following:

```java
@Test public void diagnosticIsOneShotAndReadOnly() throws Exception {
    String diag = Files.readString(Path.of(
            "src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java"));
    assertTrue(diag.contains("AtomicBoolean"));
    assertTrue(diag.contains("compareAndSet(false, true)"));
    assertTrue(diag.contains("LiquidDockDiag"));
    assertFalse(diag.contains("captureScreenAsync("));
    assertFalse(diag.contains("captureLayerAsync("));
    assertFalse(diag.contains("captureDisplay("));
}

@Test public void diagnosticSnapshotsAreIsolatedFromProductionCaches() throws Exception {
    String tasks = Files.readString(Path.of(
            "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java"));
    String layers = Files.readString(Path.of(
            "src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java"));
    assertTrue(tasks.contains("snapshotForDiagnostics"));
    assertTrue(layers.contains("snapshotForDiagnostics"));
    assertTrue(tasks.contains("DiagnosticSnapshot"));
    assertTrue(layers.contains("DiagnosticSnapshot"));
}

@Test public void dockInvokesDiagnosticWithoutRemovingSafetyFallback() throws Exception {
    String dock = Files.readString(Path.of(
            "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    assertTrue(dock.contains("FreeformCaptureDiagnostic.runOnce("));
    assertTrue(dock.contains("actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY"));
    assertTrue(dock.contains("!fullDisplayExclusions.safe"));
    assertTrue(dock.contains("actualSource = CaptureSourcePolicy.Source.WALLPAPER"));
}
```

The test must not import or instantiate Android runtime classes.

- [ ] **Step 2: Verify RED statically**

Before production changes, confirm:

```text
FreeformCaptureDiagnostic.java does not exist
FreeformLayerResolver has no snapshotForDiagnostics()
SurfaceLayerNameResolver has no snapshotForDiagnostics()
DockLiquidGlassView has no FreeformCaptureDiagnostic.runOnce(...)
```

This is the expected RED state.

- [ ] **Step 3: Commit the RED contract**

Commit only the new test:

```text
test: require one-shot freeform capture diagnostic [skip ci]
```

---

### Task 2: Add read-only task and SurfaceFlinger snapshots

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`

**Interfaces:**
- Produces: `FreeformLayerResolver.DiagnosticSnapshot snapshotForDiagnostics()`.
- Produces: `SurfaceLayerNameResolver.DiagnosticSnapshot snapshotForDiagnostics(Collection<Integer> targetUids, Collection<String> keywords)`.
- Consumes: existing `FreeformCapturePolicy.shouldExclude(...)` semantics and existing reflected SurfaceFlinger API names.

- [ ] **Step 1: Add task diagnostic records without touching caches**

Add package-private immutable nested records/classes to `FreeformLayerResolver`:

```java
static final class DiagnosticTask {
    final int taskId;
    final int displayId;
    final int windowingMode;
    final boolean visible;
    final String topActivity;
    final String baseActivity;
    final String bounds;
    final String packageName;
    final Integer packageUid;
    final boolean visibleFreeform;
    final String error;
}

static final class DiagnosticSnapshot {
    final List<DiagnosticTask> tasks;
    final List<Integer> freeformOwnerUids;
    final List<String> freeformKeywords;
    final String error;

    boolean hasVisibleFreeformTask() {
        return !freeformOwnerUids.isEmpty();
    }
}
```

Implement:

```java
DiagnosticSnapshot snapshotForDiagnostics()
```

using an independent `ActivityManager.getRunningTasks(MAX_RUNNING_TASKS)` scan. It must not read or write `taskCacheUntilNanos`, `layerCacheUntilNanos`, `cachedOwnerUids`, or `cachedLayerNames`.

For every inspected task, record the current `windowingMode(task)` and `isVisible(task)` result, activity/package identity, package UID where resolvable, and whether `FreeformCapturePolicy.shouldExclude(mode, visible)` is true.

Use a diagnostic-only helper to resolve bounds through reflection. Failure to read optional fields is recorded as text and must not abort the snapshot.

- [ ] **Step 2: Add a staged SurfaceFlinger diagnostic snapshot**

Add package-private immutable nested classes to `SurfaceLayerNameResolver`:

```java
static final class DiagnosticLayer {
    final String name;
    final Integer ownerUid;
    final boolean targetUidMatch;
    final boolean keywordMatch;
    final boolean suspiciousSystemLayer;
    final String extra;
}

static final class DiagnosticSnapshot {
    final boolean serviceAvailable;
    final boolean composerAvailable;
    final boolean methodAvailable;
    final boolean invocationSucceeded;
    final int totalLayerCount;
    final List<DiagnosticLayer> candidates;
    final String failureStage;
    final String error;
}
```

Implement:

```java
DiagnosticSnapshot snapshotForDiagnostics(
        Collection<Integer> targetUids,
        Collection<String> keywords)
```

The method must independently perform these stages and record the exact failing stage:

```text
ServiceManager.getService("SurfaceFlinger")
ISurfaceComposer.Stub.asInterface(...)
getLayerDebugInfo method lookup
getLayerDebugInfo invocation
layer iteration / candidate extraction
```

Candidate selection is the union of:

```text
ownerUid in targetUids
OR layer name contains any freeform package/activity keyword, case-insensitive
OR layer name contains one of: freeform, miuifreeform, task, leash, window
```

For each candidate, attempt optional reflected fields/methods such as `getId`, `getLayerId`, `getParentId`, and `getZ`. Missing optional metadata is represented as `-`; it must not set the whole snapshot to failed.

Do not change the existing `queryLayers()`, `resolveTopmostByOwnerUid()`, or `resolveAllByOwnerUids()` behavior.

- [ ] **Step 3: Inspect the resolver diff**

Verify production cache fields and production resolution methods are byte-for-byte semantically unchanged except for the addition of the new diagnostic-only methods/classes.

- [ ] **Step 4: Commit resolver diagnostics**

Commit only these two production files:

```text
debug: expose freeform task and SF diagnostic snapshots [skip ci]
```

---

### Task 3: Implement the one-shot grouped report

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java`

**Interfaces:**
- Consumes: `Context`, `FreeformLayerResolver`, `SurfaceLayerNameResolver`, capture preflight facts, and already-computed production freeform exclusion facts.
- Produces: `static void runOnce(...)` with no return value and no effect on production capture state.

- [ ] **Step 1: Define the capture facts object**

Inside `FreeformCaptureDiagnostic`, add:

```java
static final class CaptureFacts {
    final boolean workstationMode;
    final boolean fullscreenCapture;
    final boolean useFullscreen;
    final CaptureScene scene;
    final CaptureSourcePolicy.Source requestedSource;
    final boolean launcherLifecycleKnown;
    final boolean launcherResumed;
    final boolean windowVisible;
    final boolean windowFocused;
    final boolean systemUiPanelExpanded;
    final int displayId;
    final String dockWindowLayerName;
    final String dragLayerName;
    final boolean dockSurfaceValid;
}
```

Use a constructor assigning every field. The object is a read-only snapshot; it must not retain a `DockLiquidGlassView` reference.

- [ ] **Step 2: Implement one-shot trigger and grouped logging**

Use:

```java
private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);
private static final String TAG = "LiquidDockDiag";
```

`runOnce(...)` first obtains `FreeformLayerResolver.snapshotForDiagnostics()`. If it reports no visible freeform task, return without claiming the gate. Once a visible freeform task exists:

```java
if (!ATTEMPTED.compareAndSet(false, true)) return;
```

Generate one id using elapsed realtime or nano time and emit:

```text
BEGIN id=...
CAPTURE ...
TASK ...
SF ...
RESOLVER ...
DECISION ...
END id=...
```

Use `Log.i(TAG, ...)` for normal evidence and `Log.w(TAG, ...)` for failures. Logging must not depend on `MainHook.debugLogging` so the report is always present in the diagnostic build.

- [ ] **Step 3: Implement informational classification**

Classification must be report-only and never returned to the caller as control flow. Use these rules:

```text
no visible freeform task -> do not trigger
SurfaceFlinger service/composer/method/invocation failure -> SURFACEFLINGER_API_FAILED
SF invocation succeeded and no target-UID candidates while production freeform resolution is empty -> UID_MATCH_FAILED
production freeform resolution is non-empty -> LAYER_RESOLUTION_SUCCEEDED
production resolution is non-empty, source is FULL_DISPLAY, safe=true -> POST_RESOLUTION_CAPTURE_PATH
otherwise -> UNKNOWN
```

If task snapshot itself failed before a trustworthy freeform task can be established, log `TASK_DETECTION_FAILED` only when diagnostic execution has already been triggered from a previously established freeform condition; otherwise do not consume the one-shot gate.

- [ ] **Step 4: Catch all diagnostic failures internally**

`runOnce(...)` must catch `Throwable`, log exact exception class and message, and always emit `END` after the gate is claimed. It must not rethrow.

- [ ] **Step 5: Commit the diagnostic component**

Commit only `FreeformCaptureDiagnostic.java`:

```text
debug: add one-shot freeform capture report [skip ci]
```

---

### Task 4: Wire diagnostic into capture worker preflight

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java`

**Interfaces:**
- Consumes: `FreeformCaptureDiagnostic.CaptureFacts` and `FreeformCaptureDiagnostic.runOnce(...)`.
- Produces: one worker-thread diagnostic invocation before the existing FULL_DISPLAY safety fallback.

- [ ] **Step 1: Preserve the production resolver facts needed by the report**

Enrich the private `FullDisplayExclusions` value object with read-only diagnostic fields:

```java
final boolean freeformActive;
final String[] freeformLayerNames;
```

`resolveFullDisplayExclusions()` already has both facts locally. Populate them without changing how `layerNames` or `safe` are computed.

`FullDisplayExclusions.NONE` uses:

```text
freeformActive=false
freeformLayerNames=[]
safe=true
```

- [ ] **Step 2: Build immutable capture facts before worker submission**

After `requestedSource` is finalized and the Dock surface preflight has run, create a `FreeformCaptureDiagnostic.CaptureFacts` snapshot containing current booleans/strings/scene/source/display id. Do not pass the view itself.

- [ ] **Step 3: Invoke the diagnostic on the capture worker**

Inside the existing `worker.post(...)`, after `fullDisplayExclusions` is computed but before:

```java
if (actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY
        && !fullDisplayExclusions.safe) {
```

call:

```java
FreeformCaptureDiagnostic.runOnce(
        getContext(),
        freeformLayerResolver,
        surfaceLayerNameResolver,
        diagnosticFacts,
        fullDisplayExclusions.freeformActive,
        fullDisplayExclusions.freeformLayerNames,
        fullDisplayExclusions.layerNames,
        fullDisplayExclusions.safe);
```

When `requestedSource` does not require production full-display exclusions, the `NONE` object is passed; the independent task/SF snapshots still reveal whether an upstream source decision already selected wallpaper.

The call must not assign to `actualSource`, `requestedSource`, `sourceDirty`, `capturing`, or any scene state.

- [ ] **Step 4: Verify the safety fallback remains after the diagnostic**

Confirm the source still contains the same sequence:

```java
if (actualSource == CaptureSourcePolicy.Source.FULL_DISPLAY
        && !fullDisplayExclusions.safe) {
    ...
    actualSource = CaptureSourcePolicy.Source.WALLPAPER;
}
```

The diagnostic call appears before this block and does not alter its condition.

- [ ] **Step 5: Re-read the source-contract test against the new tree**

The contract should now find:

```text
FreeformCaptureDiagnostic.java
AtomicBoolean.compareAndSet(false, true)
LiquidDockDiag
snapshotForDiagnostics in both resolvers
FreeformCaptureDiagnostic.runOnce in DockLiquidGlassView
unchanged safe -> WALLPAPER fallback
```

Do not claim JUnit execution unless a local Gradle environment is actually run.

- [ ] **Step 6: Commit integration**

Commit the Dock integration and contract updates:

```text
debug: run freeform diagnostic at capture preflight [skip ci]
```

---

### Task 5: Repository-level verification and handoff

**Files:**
- No additional production files.

- [ ] **Step 1: Compare the development branch against its base**

Compare:

```text
10455c6bb10d90e01c9f1299d0af0a6754ac54ab
..
dev/freeform-capture-diagnostic
```

Expected implementation files beyond the already-approved spec/plan are only:

```text
src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java
src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java
src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java
src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java
src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java
```

- [ ] **Step 2: Verify forbidden production changes are absent**

Confirm there are no changes to:

```text
CaptureSourcePolicy.java
CaptureSceneState.java
LiveScreenCapture.java
LauncherSceneOwnershipPolicy.java
MainHook.java
WorkstationWallpaperOnlyHook.java
```

- [ ] **Step 3: Verify no CI run was triggered**

Check workflow runs for the final development-branch SHA. The count should remain zero because commits use `[skip ci]`.

- [ ] **Step 4: Device reproduction handoff**

The only required device reproduction is:

```text
start any fullscreen app
-> open a freeform/small window above it
-> summon the Dock once
-> export: adb logcat -d -s LiquidDockDiag
```

Do not interpret runtime root cause until the resulting `LiquidDockDiag` report is provided.
