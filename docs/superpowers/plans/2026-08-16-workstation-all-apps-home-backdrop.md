# Workstation All Apps HOME Backdrop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make workstation All Apps a UI-only state for Dock backdrop purposes so HOME → All Apps → HOME keeps the same HOME Liquid Glass without an All Apps capture transition.

**Architecture:** `CaptureSceneState` will continue recording `allAppsActive`, but will also record whether that UI state aliases the HOME backdrop. `DockLiquidGlassView.setAllAppsActive()` will select that alias in workstation mode and return before any All Apps-specific capture/burst scheduling. Recents remains untouched.

**Tech Stack:** Java, libxposed API101, existing LiquidDock capture state machine.

## Global Constraints

- Workstation HOME behavior remains unchanged.
- Workstation All Apps must use the HOME backdrop policy.
- Do not start a workstation capture burst solely because All Apps opens or closes.
- Do not request a new backdrop solely because All Apps opens or closes.
- Keep `allAppsActive` observable as UI state.
- Recents behavior remains unchanged.
- Do not modify workstation detection, APP→HOME, Dock geometry, freeform capture, or generic capture architecture.
- Do not trigger CI or build an APK; commits use `[skip ci]`.

---

### Task 1: Decouple All Apps UI state from capture scene

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java`

**Interfaces:**
- Consumes: `CaptureScene.HOME`, `CaptureScene.ALL_APPS`, existing lifecycle arguments in `resolve()`.
- Produces: `setAllAppsActive(boolean active, boolean useHomeBackdrop)` while preserving `allAppsActive()`.

- [ ] **Step 1: Extend All Apps state with a HOME-backdrop alias flag**

Add:

```java
private boolean allAppsUsesHomeBackdrop;
```

Replace the current setter with:

```java
void setAllAppsActive(boolean active, boolean useHomeBackdrop) {
    boolean nextUsesHomeBackdrop = active && useHomeBackdrop;
    if (allAppsActive == active && allAppsUsesHomeBackdrop == nextUsesHomeBackdrop) return;
    allAppsActive = active;
    allAppsUsesHomeBackdrop = nextUsesHomeBackdrop;

    if (useHomeBackdrop) {
        // Workstation All Apps is UI-only for Dock capture. Do not bump the capture
        // revision merely because the overlay opened/closed, and keep HOME authoritative.
        if (active && desired == CaptureScene.ALL_APPS) {
            desired = CaptureScene.HOME;
            revision++;
        }
        return;
    }

    revision++;
    if (active) {
        desired = CaptureScene.ALL_APPS;
    } else if (desired == CaptureScene.ALL_APPS) {
        desired = CaptureScene.APP;
    }
}
```

- [ ] **Step 2: Resolve workstation All Apps as HOME**

Change the All Apps branch in `resolve()` to:

```java
if (allAppsActive) {
    return allAppsUsesHomeBackdrop ? CaptureScene.HOME : CaptureScene.ALL_APPS;
}
```

This preserves normal-mode All Apps semantics while making workstation All Apps capture-equivalent to HOME.

- [ ] **Step 3: Static verification**

Verify the resulting file contains both `allAppsUsesHomeBackdrop` and the conditional HOME resolution, and that Recents resolution remains before All Apps resolution.

---

### Task 2: Remove workstation All Apps capture transitions

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:**
- Consumes: `CaptureSceneState.setAllAppsActive(boolean, boolean)`.
- Produces: UI state updates with no workstation All Apps-specific burst/source/visibility handoff.

- [ ] **Step 1: Pass workstation backdrop policy into the scene state**

In `setAllAppsActive(boolean active, View captureRoot)`, replace:

```java
sceneState.setAllAppsActive(active);
```

with:

```java
sceneState.setAllAppsActive(active, workstationMode);
```

- [ ] **Step 2: Make workstation All Apps capture-transparent**

After recording state and detecting whether anything changed, return early in workstation mode:

```java
if (workstationMode) {
    if (!active) allAppsCaptureRoot = null;
    logI("workstation All Apps UI state=" + active + "; keeping HOME Dock backdrop");
    return;
}
```

The return must occur before `observationValid = false`, `lastCaptureStartNanos = 0L`, `startWorkstationCaptureBurst(...)`, `updateDesiredScene()`, and `requestStateCapture(...)`.

Remove the old workstation-only block:

```java
if (workstationMode) {
    workstationSuspendWhenBurstSettles = !active;
    if (active) {
        startWorkstationCaptureBurst("all-apps-enter");
    } else {
        startWorkstationCaptureBurst("all-apps-exit");
    }
}
```

Normal-mode All Apps continues through the existing observation/update/capture path unchanged.

- [ ] **Step 3: Preserve Recents and fallback behavior**

Do not modify `setOverviewActive()`, `onWorkstationRecentsButton()`, `startWorkstationCaptureBurst()` itself, `suspendWorkstationGlass()`, or the native-background fallback bridge.

- [ ] **Step 4: Static diff verification**

Verify production diff is limited to `CaptureSceneState.java` and `DockLiquidGlassView.java`; confirm no changes to `WorkstationWallpaperOnlyHook.java`, `MainHook.java`, or Recents code.

---

### Task 3: Repository-level verification

**Files:**
- No additional production files.

- [ ] **Step 1: Inspect final source semantics**

Confirm these invariants from the branch source:

```text
workstation HOME -> HOME scene
workstation All Apps active -> allAppsActive=true, resolved capture scene HOME
workstation All Apps enter/exit -> no workstation capture burst, no capture request
normal All Apps -> ALL_APPS scene unchanged
workstation Recents -> existing live workstation capture path unchanged
```

- [ ] **Step 2: Verify commit scope and CI suppression**

Use GitHub compare to confirm the production commit changes only the intended two Java files. Confirm the final commit message includes `[skip ci]` and no workflow run exists for that SHA.

No build/runtime success claim may be made until the user compiles and tests this revision on-device.