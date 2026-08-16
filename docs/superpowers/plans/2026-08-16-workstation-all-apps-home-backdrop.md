# Workstation All Apps HOME Backdrop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep workstation HOME Liquid Glass unchanged across HOME → All Apps → HOME by preventing workstation All Apps UI events from entering the Dock capture state machine.

**Architecture:** `MainHook` is the boundary between Launcher UI events and Dock capture events. Workstation All Apps is filtered there instead of adding another state to `CaptureSceneState` or another hook to `WorkstationWallpaperOnlyHook`. A single `workstationAllAppsOpen` UI-ownership latch protects the focus transfer to the focusable laptop overlay; it is never part of capture scene/source selection.

**Tech Stack:** Java, libxposed API101, existing LiquidDock Launcher hooks.

## Global Constraints

- Workstation HOME Liquid Glass remains unchanged.
- Workstation All Apps is UI-only for Dock capture purposes.
- No workstation All Apps callback may invoke `DockLiquidGlassView.setAllAppsActive()`.
- Workstation All Apps focus transfer must remain Launcher-owned.
- Normal-mode All Apps behavior remains unchanged.
- Workstation Recents behavior remains unchanged.
- Do not modify `DockLiquidGlassView`, `CaptureSceneState`, `CaptureSourcePolicy`, or `WorkstationWallpaperOnlyHook`.
- Do not modify APP→HOME, Dock geometry, freeform capture, or generic capture architecture.
- Do not trigger CI or build an APK; commits use `[skip ci]`.

---

### Task 1: Lock the workstation All Apps event-boundary contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/WorkstationAllAppsHomeBackdropContractTest.java`

**Interfaces:**
- Consumes: `MainHook.installAllAppsCaptureHooks()` and `MainHook.installLiquidGlassCaptureHooks()` source.
- Produces: regression coverage for UI ownership, capture-state isolation, focus ownership, normal All Apps preservation, and Recents preservation.

- [x] **Step 1: Add a failing contract**

The contract requires:

```text
MainHook owns workstationAllAppsOpen
laptop show/close manage that ownership
workstation focus transfer is ignored while ownership is active
all four All Apps forwarding callbacks bypass Dock capture state in workstation mode
normal mode still calls setAllAppsActive()
workstation Recents still calls onWorkstationRecentsButton()
```

- [x] **Step 2: Verify RED against the old MainHook**

Before the production change, the source had no `workstationAllAppsOpen`, no workstation focus guard, and no workstation early exits at the All Apps forwarding callbacks.

---

### Task 2: Filter workstation All Apps before Dock capture state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Consumes: exact laptop `AllAppsController.showAllApps/closeAllApps` boundaries and generic `AllAppsTransitionController` boundaries.
- Produces: workstation All Apps UI with no Dock capture-scene transition.

- [x] **Step 1: Add the UI-ownership latch**

Add next to the other workstation facts:

```java
private static volatile boolean workstationAllAppsOpen;
```

This flag means only that the focusable workstation All Apps overlay currently owns Launcher focus. It is not a capture flag.

- [x] **Step 2: Protect workstation All Apps focus transfer**

In `onWindowFocusChanged`, before normal All Apps/capture ownership handling:

```java
if (workstationMode && workstationAllAppsOpen) {
    log("[DC] liquid focus ignored while workstation All Apps overlay owns focus: "
            + hasFocus);
    return r;
}
```

- [x] **Step 3: Keep laptop All Apps out of Dock capture state**

For workstation `showAllApps`:

```java
workstationAllAppsOpen = true;
try {
    return chain.proceed(chain.getArgs().toArray(new Object[0]));
} catch (Throwable error) {
    workstationAllAppsOpen = false;
    throw error;
}
```

For workstation `closeAllApps`, run the stock close while ownership is still active and clear it in `finally`:

```java
try {
    return chain.proceed(chain.getArgs().toArray(new Object[0]));
} finally {
    workstationAllAppsOpen = false;
}
```

Neither workstation path calls `glass.setAllAppsActive(...)`.

- [x] **Step 4: Guard generic All Apps transition callbacks too**

At both generic `AllAppsTransitionController` callbacks:

```java
if (workstationMode) return chain.proceed(chain.getArgs().toArray(new Object[0]));
```

This keeps the policy correct even if a vendor revision routes workstation All Apps through both laptop and generic transition code.

- [x] **Step 5: Clear stale UI ownership when workstation mode exits**

In `setWorkstationMode(false)` path, clear `workstationAllAppsOpen` before applying normal-mode state.

---

### Task 3: Verify clean scope

**Files:**
- Verify only; no additional production files.

- [x] **Step 1: Inspect the production commit diff**

The production commit must modify only `MainHook.java`; no changes are allowed in `DockLiquidGlassView`, `CaptureSceneState`, `CaptureSourcePolicy`, or `WorkstationWallpaperOnlyHook`.

- [x] **Step 2: Verify repository net diff from the last user-confirmed workstation-glass baseline**

Production net changes must remain limited to `MainHook.java`; documentation and the regression contract may be added separately. Any exploratory state-machine/source-policy changes must have zero net diff.

- [ ] **Step 3: Device verification**

User test sequence:

```text
workstation HOME -> correct existing HOME Liquid Glass
open All Apps -> Dock glass does not visually transition or refresh to All Apps
keep All Apps open -> Dock remains HOME-style glass
close All Apps -> no Dock backdrop handoff; same HOME glass remains
open Recents -> existing workstation live Recents glass still works
```

No build/runtime success claim is valid until this sequence is tested on-device.
