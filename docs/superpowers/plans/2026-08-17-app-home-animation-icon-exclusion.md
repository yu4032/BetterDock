# APP → HOME Animated Icon Exclusion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exclude HyperOS Launcher’s root SurfaceControl from full-display backdrop capture only while APP → HOME `CLOSE_TO_HOME` is pending, preventing `FloatingIconView2` from being baked into the Dock glass.

**Architecture:** A focused reflection adapter captures the vendor-owned `WindowElement.mFloatingIconLayerLeash` after `bindIconLayerLeashIfNeeded()`. `CaptureSceneState` exposes the already-existing pending handoff bit, and `DockLiquidGlassView` conditionally appends the valid Launcher root leash to the existing SurfaceControl exclusion array for APP transition captures only.

**Tech Stack:** Java 17, Android hidden APIs via reflection/hooks, SurfaceControl capture, JUnit source-contract tests, GitHub Actions Gradle build.

## Global Constraints

- Preserve SystemUI as sole ordinary HOME/APP ownership authority.
- Preserve current APP → HOME `CLOSE_TO_HOME` lifecycle and Recents lifecycle behavior.
- Do not change capture mode, wallpaper cache, capture cadence, workstation policy, freeform policy, or Launcher visible rendering state.
- Missing/invalid vendor leash must fail open to the existing capture path rather than alter source semantics.

---

### Task 1: RED vendor-leash and capture-wiring contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/AppHomeAnimationLayerExclusionContractTest.java`
- Modify: `.github/workflows/api101-build.yml`

**Interfaces:**
- Consumes: existing `AppHomeAnimationHook`, `CaptureSceneState`, `DockLiquidGlassView`.
- Produces: failing contract requiring `AppHomeAnimationLayerExclusion.install(ClassLoader)`, `currentValidSurface()`, `CaptureSceneState.appHomeHandoffPending()`, and conditional Dock exclusion wiring.

- [ ] **Step 1: Write the failing source contract** requiring exact vendor strings `com.miui.home.recents.anim.WindowElement`, `bindIconLayerLeashIfNeeded`, and `mFloatingIconLayerLeash`, plus conditional use only for APP + pending handoff.
- [ ] **Step 2: Add the feature branch to the existing CI push branch list.**
- [ ] **Step 3: Run GitHub Actions `./gradlew testDebugUnitTest --stacktrace`.** Expected: FAIL because the adapter/getter/wiring do not exist.

### Task 2: Capture the vendor Launcher-root leash

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/AppHomeAnimationLayerExclusion.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/AppHomeAnimationHook.java`

**Interfaces:**
- Produces: `static void install(ClassLoader)` and `static SurfaceControl currentValidSurface()`.

- [ ] **Step 1: Hook `WindowElement.bindIconLayerLeashIfNeeded()` after the original method.** Reflect `mFloatingIconLayerLeash`, accept only `SurfaceControl` instances for which `isValid()` is true, and cache the latest valid handle.
- [ ] **Step 2: Install the adapter from `AppHomeAnimationHook.install(classLoader)` so it follows the same Launcher process lifecycle.**
- [ ] **Step 3: Do not mutate, reparent, hide, or release the Launcher-owned SurfaceControl.**

### Task 3: Gate exclusion to the pending APP → HOME capture only

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:**
- Produces: package-private `boolean appHomeHandoffPending()`.

- [ ] **Step 1: Add the read-only pending getter to `CaptureSceneState`.**
- [ ] **Step 2: In `DockLiquidGlassView.startCapture()`, snapshot whether `requestScene == CaptureScene.APP && sceneState.appHomeHandoffPending()` and resolve the Launcher-root exclusion only in that case.**
- [ ] **Step 3: Build the mode-1 `excludeLayers` array from the existing Dock SurfaceControl plus the valid Launcher-root SurfaceControl, de-duplicating identical handles.** Ordinary APP, Recents, HOME, workstation, and non-pending captures must retain current behavior.
- [ ] **Step 4: Add a debug log showing whether the transition Launcher root was excluded, without changing capture scheduling.**

### Task 4: GREEN verification and artifact

**Files:** no additional production files.

- [ ] **Step 1: Run GitHub Actions `testDebugUnitTest`.** Expected: PASS including the new contract and existing APP/Recents state tests.
- [ ] **Step 2: Run GitHub Actions `assembleDebug`.** Expected: PASS.
- [ ] **Step 3: Verify the workflow run head SHA equals the feature branch HEAD and download the APK artifact.**
- [ ] **Step 4: Device-test APP → HOME for two properties: no animated icon appears inside the Dock glass, and the background still follows the full return animation without the old positional snap. Also verify Recents → HOME remains fixed.**
