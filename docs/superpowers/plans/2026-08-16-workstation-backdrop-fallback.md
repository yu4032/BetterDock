# Workstation Backdrop Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop workstation mode from permanently hiding every Dock backdrop while preserving the existing workstation Recents/All Apps live-glass burst behavior.

**Architecture:** The stock/native Dock background becomes the stable workstation fallback. `DockLiquidGlassView` remains suspended while workstation HOME is idle, but suspension restores the native background instead of hiding it; live workstation capture bursts continue to swap to glass by hiding the native background only while the glass child is visible. `MainHook` must keep the glass host available and must not force the native background off merely because workstation mode is active.

**Tech Stack:** Java 17, Android View visibility/alpha state, existing LiquidDock workstation capture state.

## Global Constraints

- Modify only workstation backdrop visibility behavior.
- Preserve workstation mode detection, workstation geometry, icon offsets, and Recents/All Apps capture behavior.
- Preserve normal-mode APP→HOME behavior unchanged.
- Preserve live workstation burst behavior: glass visible, native background alpha 0 while live glass owns the backdrop.
- Do not add or trigger GitHub Actions or other CI builds.
- Verification for this change is static source/tree verification only; runtime verification remains device-side.

---

### Task 1: Keep the workstation host and native fallback available

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Consumes: `workstationMode`, `oldBg`, `liquidGlassHostView`, `liquidGlassView.setWorkstationMode(boolean)`.
- Produces: workstation entry/setup that leaves `oldBg` and the host available while delegating glass suspension/live switching to `DockLiquidGlassView`.

- [ ] **Step 1: Remove permanent workstation host hiding in `Launcher.setupViews`**

Replace the workstation branch so it keeps `oldBg` alpha at 1 and the host `VISIBLE`, then calls `liquidGlassView.setWorkstationMode(true)`.

- [ ] **Step 2: Remove permanent native-background hiding in `setWorkstationMode(true)`**

Keep the workstation shadow behavior, keep the host `VISIBLE`, call `liquidGlassView.setWorkstationMode(true)`, and leave `oldBg` at alpha 1 after the glass enters its suspended state.

- [ ] **Step 3: Stop `syncAll()` from turning the host `GONE` in workstation mode**

Set the host visibility to `View.VISIBLE` regardless of workstation state; the glass child controls whether glass itself is visible.

---

### Task 2: Make workstation suspension reveal the native backdrop

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:**
- Consumes: `geometrySource`, `nativeBackgroundHiddenByGlass`, workstation capture burst lifecycle.
- Produces: idle workstation state with native background visible and glass child invisible.

- [ ] **Step 1: Change `suspendWorkstationGlass(String reason)` fallback state**

Set `geometrySource.setAlpha(1f)` and `nativeBackgroundHiddenByGlass = false` before setting the glass child `INVISIBLE`.

- [ ] **Step 2: Preserve live workstation burst swapping unchanged**

Confirm `startWorkstationCaptureBurst(String reason)` still makes the glass child `VISIBLE`, sets `geometrySource` alpha to 0, and sets `nativeBackgroundHiddenByGlass = true`.

---

### Task 3: Static verification and commit

**Files:**
- Verify the two production files above and this plan only.

- [ ] **Step 1: Verify source contracts statically**

Confirm all of the following in current branch source:

```text
MainHook workstation setup: host VISIBLE, no permanent oldBg alpha 0
MainHook workstation mode entry: no permanent oldBg alpha 0
MainHook syncAll: host not GONE because workstationMode=true
suspendWorkstationGlass: geometrySource alpha 1, nativeBackgroundHiddenByGlass=false, glass INVISIBLE
startWorkstationCaptureBurst: glass VISIBLE, geometrySource alpha 0, nativeBackgroundHiddenByGlass=true
```

- [ ] **Step 2: Verify commit file scope**

The production commit must change only `MainHook.java` and `DockLiquidGlassView.java`; no workflow file may be added or modified.

- [ ] **Step 3: Do not run CI/build**

Per user instruction, leave compilation and device verification to the user's local environment.