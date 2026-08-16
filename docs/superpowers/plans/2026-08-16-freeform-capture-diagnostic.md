# One-Shot Freeform Capture Diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot, read-only report that identifies why freeform exclusion causes live Dock capture to fall back to wallpaper.

**Architecture:** Keep the report isolated in `FreeformCaptureDiagnostic`. Add independent diagnostic snapshots to the existing task and SurfaceFlinger resolvers. Trigger the report from `FreeformLayerResolver.resolveVisibleLayerNames()`, which is the actual worker-side exclusion preflight called by `DockLiquidGlassView.resolveFullDisplayExclusions()`. This exposes the real production owner UIDs, resolved layers, and swallowed resolution exception without modifying the 154 KB Dock capture state machine.

**Tech Stack:** Java, Android `ActivityManager.RunningTaskInfo`, reflected SurfaceFlinger APIs already used by LiquidDock, JUnit 4 source-contract tests, libxposed API101 runtime.

## Global Constraints

- Work only on `dev/freeform-capture-diagnostic`.
- Run at most once per Launcher process.
- Do not claim the one-shot gate until a visible freeform condition is established.
- Use log tag `LiquidDockDiag`.
- Do not call any screen/layer capture API from diagnostic code.
- Do not change production return values, caches, capture source, scene state, or scheduling.
- Keep `DockLiquidGlassView` unchanged.
- Keep `fullDisplayExclusions.safe -> WALLPAPER` unchanged.
- Keep `CaptureSourcePolicy`, `CaptureSceneState`, `LiveScreenCapture`, `MainHook`, `LauncherSceneOwnershipPolicy`, All Apps, Recents, and workstation behavior unchanged.
- Do not trigger CI or build an APK. Commits use `[skip ci]`.

---

### Task 1: RED safety contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java`

- [x] Require `FreeformCaptureDiagnostic` to contain an `AtomicBoolean` one-shot gate and `LiquidDockDiag` tag.
- [x] Forbid `captureScreenAsync(`, `captureLayerAsync(`, and `captureDisplay(` in the diagnostic component.
- [x] Require both resolvers to expose `snapshotForDiagnostics` and `DiagnosticSnapshot`.
- [x] Require the production resolver boundary to call `FreeformCaptureDiagnostic.runOnce(...)`.
- [x] Separately require `DockLiquidGlassView` to retain its existing `FULL_DISPLAY && !safe -> WALLPAPER` block and contain no diagnostic call.
- [x] Commit the RED contract with `[skip ci]`.

---

### Task 2: Read-only task snapshot

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`

**Produces:**

```java
DiagnosticSnapshot snapshotForDiagnostics()
```

- [x] Add immutable diagnostic task records for taskId, displayId, windowingMode, visibility, top/base activity, bounds, package, UID, freeform classification, and error.
- [x] Make the snapshot perform an independent `getRunningTasks(MAX_RUNNING_TASKS)` scan; it must not read or mutate production cache timestamps/lists.
- [x] Track `visibleFreeformDetected` independently from UID resolution so package-UID failure can still trigger the report.
- [x] Keep production `hasVisibleFreeformTasks()` behavior unchanged.

---

### Task 3: Staged SurfaceFlinger snapshot

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java`

**Produces:**

```java
DiagnosticSnapshot snapshotForDiagnostics(
        Collection<Integer> targetUids,
        Collection<String> keywords)
```

- [x] Probe and record these stages independently: SurfaceFlinger service lookup, `ISurfaceComposer.Stub.asInterface`, `getLayerDebugInfo` lookup, invocation, candidate extraction.
- [x] Unwrap reflected invocation failures and record exact exception class/message.
- [x] Record total layer count and a bounded candidate list.
- [x] Candidate union: target UID match, freeform package/activity keyword match, or suspicious names containing `freeform`, `miuifreeform`, `task`, `leash`, or `window`.
- [x] Attempt optional id/parent/z metadata without making missing fields fatal.
- [x] Leave existing production `queryLayers()` and resolver methods unchanged.

---

### Task 4: One-shot grouped report

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java`

**Consumes:**

```java
Context
FreeformLayerResolver.DiagnosticSnapshot
SurfaceLayerNameResolver
production owner UIDs
production resolved layer names
production resolution Throwable
```

- [x] Gate with `AtomicBoolean.compareAndSet(false, true)` only after freeform is established.
- [x] Always log under `LiquidDockDiag`, independent of the normal debug-log switch.
- [x] Emit `BEGIN`, `PROCESS`, `TASKS/TASK`, `SF/LAYER`, `RESOLVER`, `DECISION`, `END` under one id.
- [x] Report exact production owner UIDs, exact production resolved layer list, and actual swallowed production resolution exception.
- [x] Compute report-only `productionSafe = ownerUids empty || resolvedLayers non-empty` and `wouldSafetyFallback = ownerUids non-empty && resolvedLayers empty`.
- [x] Classify evidence as `TASK_DETECTION_FAILED`, `SURFACEFLINGER_API_FAILED`, `UID_MATCH_FAILED`, `PRODUCTION_RESOLVER_FAILED`, `POST_RESOLUTION_CAPTURE_PATH`, or `UNKNOWN`.
- [x] Catch all diagnostic exceptions and never rethrow into production capture.

---

### Task 5: Wire at the actual resolver boundary

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java`

- [x] Preserve production `resolveVisibleLayerNames()` output and cache semantics.
- [x] Replace the diagnostic-only `catch (Throwable ignored)` information loss with a local `Throwable resolutionError`, while still producing the same empty production layer list on failure.
- [x] Call `runDiagnosticIfNeeded(resolutionError)` before returning the production result.
- [x] Skip all independent diagnostic work once `FreeformCaptureDiagnostic.hasAttempted()` is true.
- [x] Permit diagnostic trigger if a freeform task was detected even when package UID resolution produced no production owner UID.
- [x] Do not modify `DockLiquidGlassView`.

---

### Task 6: Repository verification and device handoff

- [ ] Compare `10455c6bb10d90e01c9f1299d0af0a6754ac54ab..dev/freeform-capture-diagnostic`.
- [ ] Expected code/test changes are limited to:

```text
src/main/java/com/hellovoid/liquiddock/FreeformCaptureDiagnostic.java
src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java
src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java
src/test/java/com/hellovoid/liquiddock/FreeformCaptureDiagnosticContractTest.java
```

- [ ] Confirm no changes to:

```text
DockLiquidGlassView.java
CaptureSourcePolicy.java
CaptureSceneState.java
LiveScreenCapture.java
MainHook.java
LauncherSceneOwnershipPolicy.java
WorkstationWallpaperOnlyHook.java
```

- [ ] Confirm final dev-branch commit has no workflow run.
- [ ] Do not claim Gradle/JUnit/runtime success because this pass intentionally does not build.
- [ ] Device reproduction:

```text
open any fullscreen app
-> open a freeform/small window
-> summon Dock once
-> adb logcat -d -s LiquidDockDiag
```

- [ ] Do not merge this diagnostic branch into `api101-migration` before the report is analyzed and the root cause is confirmed.
