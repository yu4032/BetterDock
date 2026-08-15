# Stock Launcher Capture-State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace inferred launcher-owned layer capture/state heuristics with verified HyperOS Launcher state callbacks while preserving live Recents card capture.

**Architecture:** HOME/ALL_APPS use wallpaper capture; APP/RECENTS use full-display capture below the Floating Dock. Drawer and Recents stock callbacks become state authority while gesture/haptic/transitions prearm first frames. Transition barriers operate only when the capture-source domain changes.

**Tech Stack:** Java 17, Android API 37, libxposed API 101, JUnit 4, GitHub Actions/Gradle.

## Global Constraints

- Preserve external APP full-display capture and Floating Dock exclusion.
- Preserve Recents full-display mode-1 live capture, including vibration/haptic prearm.
- Do not use Launcher ViewRoot/layer capture for All Apps or Recents.
- Do not depend on Mingou workstation APIs.
- Preserve `LauncherModeController.isLaptopMode()` and `LaptopStateManager.onLaptopModeChanged(boolean)`.
- CI must run `testDebugUnitTest` and `assembleDebug` before publication.
- Device-side visual correctness is not claimed by CI alone.

---

### Task 1: Lock the capture-source architecture

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiveScreenCapture.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/StockLauncherCaptureContractTest.java`

**Interfaces:** `CaptureSourcePolicy.sourceFor(CaptureScene)` returns only `WALLPAPER` or `FULL_DISPLAY`: HOME/ALL_APPS -> WALLPAPER; APP/RECENTS -> FULL_DISPLAY. `LiveScreenCapture` exposes no local-layer capture API.

- [ ] Write failing tests requiring the two-source mapping and absence of `LOCAL_LAYER`, `captureLayers`, `LayerCaptureArgs`, `captureLayerAsync`, `resolveLauncherOwnedCaptureSurface`, `resolveViewRootSurfaceControl`, and `allAppsCaptureRoot`.
- [ ] Run tests and verify RED.
- [ ] Remove the local-layer path; restore Recents full-display mode-1 capture with Dock/drag exclusion while keeping All Apps wallpaper-only.
- [ ] Run targeted tests and verify GREEN.

### Task 2: Make stock Drawer/Recents callbacks authoritative without weakening prearm

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/StockLauncherCaptureContractTest.java`

**Interfaces:** `setAllAppsActive(boolean)` carries state only. Stock DrawerStatusService and DockStateManager callbacks drive authoritative open/close state. Existing gesture/haptic Recents boundaries remain early prearm and force an immediate full-display request.

- [ ] Extend source-contract tests for DrawerStatusService `dispatchDrawerOpen/Close/Progress`, Dock v3 Recents `onEnterRecent/onExitRecent/onRecentViewShow/onRecentViewHide/onRecentViewAnimationComplete`, and retained `onRecentsHapticTrigger`/`prearmRecentsCapture` flow.
- [ ] Run tests and verify RED.
- [ ] Implement authoritative hooks and remove All Apps capture-root plumbing.
- [ ] Keep haptic/gesture prearm but stop treating event construction as final state authority when stock callbacks are installed.
- [ ] Run tests and verify GREEN.

### Task 3: Add source-domain backdrop barriers and focus precedence

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/BackdropTransitionPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/BackdropTransitionPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:** `BackdropTransitionPolicy.shouldDropInstalled(installed, target)` is true only when `CaptureSourcePolicy.sourceFor(installed) != CaptureSourcePolicy.sourceFor(target)`.

- [ ] Write failing tests for wallpaper <-> live source changes and same-domain continuity (`APP <-> RECENTS`, `HOME <-> ALL_APPS`).
- [ ] Run tests and verify RED.
- [ ] For live -> wallpaper, install valid current-orientation wallpaper cache immediately when possible, otherwise restore native background until a fresh mode-2 frame arrives.
- [ ] For wallpaper -> live, drop stale wallpaper and use existing APP/Recents prearm to request mode-1 immediately.
- [ ] Make Launcher focus classify HOME/APP only when neither All Apps nor Recents is active.
- [ ] Run targeted tests and verify GREEN.

### Task 4: Remove obsolete workstation fallback and verify

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/StockLauncherCaptureContractTest.java`

- [ ] Add a failing contract requiring absence of `isMingouLaptopPcModeEnabled`, `setMingouLaptopPcModeEnabled`, and `Mingou workstation`.
- [ ] Verify RED.
- [ ] Delete the legacy fallback; if the verified current laptop API is unavailable, disable workstation mode rather than guessing.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace`, `./gradlew assembleDebug --stacktrace`, and `git diff --check`.
- [ ] Publish the reviewed changes as a normal commit on `api101-migration` only after all verification commands succeed.
