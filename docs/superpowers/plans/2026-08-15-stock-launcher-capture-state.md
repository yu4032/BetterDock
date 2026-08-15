# Stock Launcher Capture-State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace inferred launcher-owned layer capture/state heuristics with the verified HyperOS Launcher wallpaper and Dock-state paths.

**Architecture:** Launcher-owned scenes (HOME, ALL_APPS, RECENTS) share one wallpaper capture path; only external APP uses full-display capture. Drawer and Recents stock callbacks become state authority while gestures/transitions only prearm. Transition barriers prevent an installed APP frame from surviving into a Launcher-owned scene.

**Tech Stack:** Java 17, Android API 37, libxposed API 101, JUnit 4, GitHub Actions/Gradle.

## Global Constraints

- Keep external APP full-display capture behavior and Floating Dock exclusion intact.
- Do not reintroduce Launcher ViewRoot/layer capture for HOME, ALL_APPS, or RECENTS.
- Do not depend on Mingou workstation APIs.
- Preserve workstation current API: `LauncherModeController.isLaptopMode()` and `LaptopStateManager.onLaptopModeChanged(boolean)`.
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

**Interfaces:** `CaptureSourcePolicy.sourceFor(CaptureScene)` returns only `WALLPAPER` or `FULL_DISPLAY`; `LiveScreenCapture` exposes no layer-capture API.

- [ ] Write failing tests requiring only the two sources and absence of `LOCAL_LAYER`, `captureLayers`, `LayerCaptureArgs`, `captureLayerAsync`, `resolveLauncherOwnedCaptureSurface`, `resolveViewRootSurfaceControl`, and `allAppsCaptureRoot`.
- [ ] Run targeted tests and verify they fail for the old structure.
- [ ] Remove the local-layer path and change policy to APP -> FULL_DISPLAY, all other scenes -> WALLPAPER.
- [ ] Run targeted tests and verify they pass.

### Task 2: Make stock Drawer/Recents callbacks authoritative

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/StockLauncherCaptureContractTest.java`

**Interfaces:** `setAllAppsActive(boolean)` carries state only; stock DrawerStatusService and DockStateManager callback methods drive authoritative open/close state; gestures/transitions remain prearm/fallback signals.

- [ ] Extend source-contract tests for DrawerStatusService `dispatchDrawerOpen/Close` and Dock v3 Recents `onEnterRecent/onExitRecent/onRecentViewShow/onRecentViewHide/onRecentViewAnimationComplete` hooks.
- [ ] Run tests and verify RED.
- [ ] Implement those hooks and remove capture-root plumbing.
- [ ] Run tests and verify GREEN.

### Task 3: Add symmetric backdrop barriers and focus precedence

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/BackdropTransitionPolicy.java`
- Create: `src/test/java/com/hellovoid/liquiddock/BackdropTransitionPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:** `BackdropTransitionPolicy.shouldDropInstalled(installed, target)` returns true across APP <-> Launcher-owned boundaries.

- [ ] Write failing policy tests for APP -> HOME/ALL_APPS/RECENTS and launcher-owned -> APP.
- [ ] Run tests and verify RED.
- [ ] On APP -> Launcher-owned, immediately use a valid current-orientation wallpaper cache when available, otherwise drop the stale APP frame and restore native background until a fresh mode-2 frame arrives.
- [ ] Make Launcher focus classify HOME/APP only when All Apps/Recents is not active.
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
