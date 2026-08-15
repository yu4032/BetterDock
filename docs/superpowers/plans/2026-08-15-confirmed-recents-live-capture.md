# Confirmed Recents Live Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore live Recents task-card capture on the device-verified `8ee84ed` timing baseline without allowing haptic, distance, or gesture-constructor prearm to issue an early mode-1 FULL_DISPLAY request.

**Architecture:** Keep `CaptureScene.RECENTS` as an intent/state value during prearm, but make capture-source selection depend on a separate confirmed-Overview latch. `overviewActive` becomes confirmation owned only by `setOverviewActive(...)`; gesture-target construction no longer writes it. A confirmed Recents boundary forces one immediate source refresh, after which the existing mode-1 + Floating Dock exclusion path is reused unchanged.

**Tech Stack:** Java 17, Android API 37, libxposed API 101, JUnit 4, existing hidden HyperOS ScreenCapture bridge.

## Global Constraints

- Baseline is `8ee84ed61e74b3199f7e4d6fb1dd30cfdc2c3294` plus the already-approved design/spec commit.
- Haptic/upward-distance/speculative RECENTS prearm must remain WALLPAPER.
- Only confirmed Recents may use FULL_DISPLAY.
- APP remains FULL_DISPLAY with the existing Floating Dock exclusion path.
- HOME and ALL_APPS remain WALLPAPER.
- Do not introduce `DockExcludeRecovery`, foreground-ownership machinery, HOME settle logic, fixed delays, or 2-View changes in this stage.
- Do not run GitHub Actions; device/build verification is local.

---

### Task 1: Encode the confirmed-Recents source contract

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/RecentsCaptureConfirmationContractTest.java`

**Interfaces:**
- Consumes: existing `CaptureSourcePolicy.sourceFor(CaptureScene, boolean)` behavior.
- Produces: required overload `CaptureSourcePolicy.sourceFor(CaptureScene, boolean, boolean recentsLiveConfirmed)` and source-contract requirements for `DockLiquidGlassView`.

- [ ] **Step 1: Write failing policy tests**

Add a helper that reflects the three-argument source selector and fails clearly when it is absent. Require:

```java
assertEquals("WALLPAPER", sourceFor("RECENTS", false, false));
assertEquals("FULL_DISPLAY", sourceFor("RECENTS", false, true));
assertEquals("FULL_DISPLAY", sourceFor("APP", false, false));
assertEquals("WALLPAPER", sourceFor("HOME", false, true));
assertEquals("WALLPAPER", sourceFor("ALL_APPS", false, true));
```

- [ ] **Step 2: Write failing timing/source-contract tests**

Create `RecentsCaptureConfirmationContractTest` that reads `DockLiquidGlassView.java` and requires all of the following:

```java
// Gesture construction must not claim confirmed Overview.
assertFalse(gestureMethod.contains("overviewActive = \"RECENTS\".equals(target)"));

// Exact Overview lifecycle remains the confirmation owner.
assertTrue(overviewMethod.contains("overviewActive = active"));

// Runtime source resolution passes confirmation separately.
assertTrue(startCapture.contains("isRecentsVisible()"));
assertTrue(startCapture.contains("CaptureSourcePolicy.sourceFor"));
```

- [ ] **Step 3: Run the targeted tests and verify RED**

Run locally:

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.CaptureSourcePolicyTest' \
  --tests 'com.hellovoid.liquiddock.RecentsCaptureConfirmationContractTest'
```

Expected: FAIL because the three-argument policy does not exist and `setGestureCaptureTarget` still writes `overviewActive`.

- [ ] **Step 4: Commit the RED tests**

```bash
git add src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java \
        src/test/java/com/hellovoid/liquiddock/RecentsCaptureConfirmationContractTest.java
git commit -m "test: define confirmed recents capture boundary"
```

---

### Task 2: Make confirmed Recents the only live Recents source

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:**
- Consumes: `boolean recentsLiveConfirmed` from `DockLiquidGlassView.isRecentsVisible()` where `overviewActive` is owned only by `setOverviewActive(...)`.
- Produces: `CaptureSourcePolicy.sourceFor(CaptureScene, boolean, boolean)`.

- [ ] **Step 1: Implement the minimal source-policy overload**

Use:

```java
static Source sourceFor(CaptureScene scene, boolean localLayerAvailable) {
    return sourceFor(scene, localLayerAvailable, false);
}

static Source sourceFor(CaptureScene scene, boolean localLayerAvailable,
                        boolean recentsLiveConfirmed) {
    if (scene == null || scene == CaptureScene.HOME) return Source.WALLPAPER;
    if (scene == CaptureScene.APP) return Source.FULL_DISPLAY;
    if (scene == CaptureScene.RECENTS) {
        return recentsLiveConfirmed ? Source.FULL_DISPLAY : Source.WALLPAPER;
    }
    return Source.WALLPAPER;
}
```

The legacy two-argument method intentionally preserves `8ee84ed` behavior and therefore treats RECENTS as unconfirmed/wallpaper.

- [ ] **Step 2: Stop gesture construction from claiming confirmed Overview**

In `setGestureCaptureTarget(String target)`, remove only:

```java
overviewActive = "RECENTS".equals(target);
```

Do not change `sceneState.setGestureTarget(...)`; RECENTS intent/prearm must still exist.

- [ ] **Step 3: Pass confirmed Overview into source resolution**

In `startCapture()`, replace:

```java
requestedSource = CaptureSourcePolicy.sourceFor(
        requestScene, localCaptureSurface != null);
```

with:

```java
requestedSource = CaptureSourcePolicy.sourceFor(
        requestScene, localCaptureSurface != null, isRecentsVisible());
```

Do not change the existing FULL_DISPLAY exclusion implementation.

- [ ] **Step 4: Verify the exact Overview boundary already forces a fresh frame**

Keep the existing `setOverviewActive(...)` behavior:

```java
overviewActive = active;
observationValid = false;
lastCaptureStartNanos = 0L;
...
requestStateCapture(active ? "overview-enter-" + reason : "overview-exit-" + reason);
```

This is the false -> true source-boundary refresh; no additional timer or state machine is needed.

- [ ] **Step 5: Run targeted tests and verify GREEN**

Run locally:

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.CaptureSourcePolicyTest' \
  --tests 'com.hellovoid.liquiddock.RecentsCaptureConfirmationContractTest'
```

Expected: PASS.

- [ ] **Step 6: Run the full unit suite/build locally**

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: PASS. Do not invoke GitHub Actions.

- [ ] **Step 7: Commit implementation**

```bash
git add src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java
git commit -m "fix: gate recents live capture on overview confirmation"
```

---

## Device verification

After pulling the branch and installing locally, verify in this order:

1. External APP: pull Dock without crossing the Recents confirmation boundary. Background remains live APP and never contains Dock icons.
2. Haptic boundary only: no Dock-icon contamination and no early mode-1 failure.
3. Continue into real Recents: once `setOverviewActive(true, ...)` lands, task cards become live behind the Dock.
4. Cancel/return before real Recents: no unsafe mode-1 Recents request.
5. Recents -> HOME and APP -> HOME are not judged as fixed in this stage; only regressions relative to `8ee84ed` are blockers.
