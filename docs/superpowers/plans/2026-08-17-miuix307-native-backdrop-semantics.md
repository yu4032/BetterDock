# MiuiX 307 Native Backdrop Semantics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Match HyperOS 3.0.307+ Dock backdrop semantics closely enough that APP→HOME icon-flight animation and SystemUI gesture/navigation indicators never become Prismal capture input.

**Architecture:** Keep the existing 307 MiuiX pass-window blur and Prismal optical layer. Add a 307-only source policy that forces wallpaper capture during an explicit `toHome` transition, invalidating any in-flight APP frame, while normal APP and confirmed RECENTS keep mode-1 full-display capture. Extend only the 307 mode-1 exclusion-name set with SystemUI bar/gesture surfaces; legacy Liquid Glass and workstation paths retain their current source/exclusion policy.

**Tech Stack:** Java, libxposed HookUtil hooks, HyperOS `ScreenCapture` vendor modes, JUnit source/behavior contracts, GitHub Actions Android Gradle build.

## Global Constraints

- Do not change legacy/older-HyperOS capture semantics.
- HOME and APP→HOME transition use vendor wallpaper capture mode 2.
- APP and confirmed RECENTS use full-display mode 1.
- 307 mode-1 excludes Floating Dock plus `NavigationBar`, `StatusBar`, `GestureStub`, and `DockAssistantView` layer-name families.
- The `toHome` transition hook must only invalidate capture context and request a wallpaper-backed frame; it must not restore legacy gesture/Recents hooks.
- Preserve existing GUI FPS/capture-scale behavior and MiuiX native blur-radius clamp.
- Build/test only in GitHub Actions.

---

### Task 1: Lock 307 source and exclusion semantics

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307BackdropPolicyTest.java`
- Modify later: `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`
- Modify later: `src/main/java/com/hellovoid/liquiddock/CaptureExclusionNames.java`

**Interfaces:**
- Produces: `CaptureSourcePolicy.sourceForMiuix307(CaptureScene, boolean recentsLiveConfirmed, boolean homeTransition)`.
- Produces: `CaptureExclusionNames.mergeMiuix307(String dockLayer, String dragLayer, Collection<String> freeformLayers)`.

- [ ] **Step 1: Write the failing test**

Test by reflection so the suite compiles before the new production methods exist. Assert APP→HOME transition returns `WALLPAPER`, ordinary APP returns `FULL_DISPLAY`, confirmed RECENTS returns `FULL_DISPLAY`, and the 307 exclusion set includes deterministic SystemUI layer-name families while preserving dock/freeform names.

- [ ] **Step 2: Run test to verify it fails**

Run in GitHub Actions: `./gradlew testDebugUnitTest --stacktrace`.

Expected: only the new 307 backdrop policy test fails because the new production interfaces do not exist.

- [ ] **Step 3: Write minimal implementation**

Add the two pure policy helpers without changing existing legacy entry points.

- [ ] **Step 4: Run test to verify it passes**

Run the full unit-test suite in GitHub Actions.

- [ ] **Step 5: Commit**

Commit the policy helpers after GREEN.

---

### Task 2: Bridge HyperOS `toHome` into the 307 glass capture barrier

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307BackdropPolicyTest.java`

**Interfaces:**
- Produces: `DockLiquidGlassView.setMiuix307BackdropPolicy(boolean)`.
- Produces: `DockLiquidGlassView.onMiuix307HomeTransitionStart()`.
- Produces: `MiuixGlassHook.onHomeTransitionStart()`.

- [ ] **Step 1: Extend the failing test**

Assert the 307 glass enables the dedicated backdrop policy, the 307 material pipeline hooks `com.miui.home.recents.util.StateNotifyUtils.sendStateBroadcast(Context,String,String,String)`, recognizes `toHome`, and forwards to `MiuixGlassHook.onHomeTransitionStart()`.

- [ ] **Step 2: Run test to verify it fails**

Expected: hook/bridge contract fails before production changes.

- [ ] **Step 3: Write minimal implementation**

`onMiuix307HomeTransitionStart()` runs on main, marks the temporary HOME-transition barrier, calls `cancelPendingCaptureWork()` so an in-flight APP bitmap cannot install after the boundary, then requests a fresh capture. `startCapture()` uses `sourceForMiuix307(...)` while the 307 policy flag is enabled. A confirmed HOME ownership update clears the temporary transition marker because HOME itself remains wallpaper-backed. Add a bounded cancel fallback so an aborted home gesture cannot leave APP permanently wallpaper-backed.

- [ ] **Step 4: Apply 307-only full-display exclusions**

`resolveFullDisplayExclusions()` calls `mergeMiuix307(...)` only when the 307 policy is enabled; all other modes keep `merge(...)` unchanged.

- [ ] **Step 5: Run full unit tests and assembleDebug**

Run `./gradlew testDebugUnitTest --stacktrace`, then `./gradlew assembleDebug --stacktrace` in GitHub Actions.

Expected: all tests and APK assembly pass.

- [ ] **Step 6: Verify artifact**

Confirm the GitHub artifact is tied to the final HEAD and provide its APK/hash for device testing.
