# Workstation Liquid Glass Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `DockLiquidGlassView` the only workstation capture/background state machine, while keeping the native Dock background as a no-gap fallback until a fresh glass frame is installed.

**Architecture:** Remove the legacy wallpaper-only/vendor snapshot policy from `WorkstationWallpaperOnlyHook`. Repurpose that small compatibility hook only to bridge legacy `MainHook` visibility decisions: keep the glass host available, show native background while a workstation burst is waiting for its first valid frame, and reveal the glass only after `installCapture()` succeeds. All Apps/Recents scene selection and capture source remain owned by `DockLiquidGlassView`.

**Tech Stack:** Android View, libxposed API101, existing `HookUtil` interception helpers.

## Global Constraints

- Do not change workstation mode detection.
- Do not change ordinary-mode APP→HOME capture logic.
- Do not force workstation wallpaper-only capture or vendor Mingou snapshot mode.
- Do not add a second scene/capture state machine.
- Do not run or trigger extra CI builds; commits use `[skip ci]`.

---

### Task 1: Remove legacy wallpaper-only ownership

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkstationWallpaperOnlyHook.java`

- [ ] Remove hooks that block `onWorkstationRecentsButton()` or force `setMingouStaticDockLiveBlurVisible(false)`, `setMingouStaticDockSnapshotMode(true)`, and snapshot refresh calls.
- [ ] Keep the class as a compatibility bridge so `ModuleMain` does not need another integration change.

### Task 2: Add no-gap workstation glass handoff

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkstationWallpaperOnlyHook.java`

- [ ] After `startWorkstationCaptureBurst(String)`, keep the host available, hide the glass child, and restore the native background while the new frame is pending.
- [ ] After `installCapture(...)`, reveal the glass child; `installCapture()` itself remains responsible for hiding the native background.
- [ ] After `suspendWorkstationGlass(String)`, restore native background and keep the host attached; when workstation remains active, schedule a HOME glass restart except for the initial workstation-enter handoff.
- [ ] After `setWorkstationMode(true)`, post a workstation HOME glass activation so `MainHook.setupViews()` cannot permanently leave the host `GONE`.
- [ ] After `MainHook.syncAll(View)`, force only the host visibility back to `VISIBLE` in workstation mode; do not alter scene/capture decisions.

### Task 3: Static verification

- [ ] Verify the rewritten hook contains no vendor wallpaper-only/snapshot method names.
- [ ] Verify `DockLiquidGlassView` still owns All Apps and Recents burst calls.
- [ ] Verify the production diff is limited to this compatibility hook plus this plan.
- [ ] Verify the final commit has no workflow run.
