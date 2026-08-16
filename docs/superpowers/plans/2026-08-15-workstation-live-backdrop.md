# Workstation Live Backdrop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add workstation-only adaptive live backdrop capture for All Apps and Recents without sampling Dock icons.

**Architecture:** Introduce a small pure-Java workstation burst state machine and workstation-specific capture-source policy. DockLiquidGlassView owns scene integration: entry/exit boundaries force a capture, successful frames feed the burst signature, and only an active burst schedules the next workstation frame. Local launcher layers are preferred; full-display fallback explicitly excludes the Dock SurfaceControl.

**Tech Stack:** Java, Android View/SurfaceControl capture pipeline, JUnit 4, Gradle/GitHub Actions.

## Global Constraints

- Only workstation mode behavior may change.
- Entry and exit of workstation All Apps and Recents must force a fresh backdrop.
- Continue sampling while background signatures change; stop after stabilization.
- Never use a workstation full-display fallback without excluding the Dock window surface.
- Normal-mode capture behavior must remain unchanged.

---

### Task 1: Workstation capture policy and burst state

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WorkstationCaptureBurst.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java`
- Test: `src/test/java/com/hellovoid/liquiddock/WorkstationCaptureBurstTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/CaptureSourcePolicyTest.java`

**Interfaces:**
- Produces: `WorkstationCaptureBurst.start()`, `stop()`, `isActive()`, `onFrame(long signature)`.
- Produces: `CaptureSourcePolicy.sourceForWorkstationScene(CaptureScene, boolean)`.

- [ ] Write failing tests: workstation ALL_APPS/RECENTS choose LOCAL_LAYER when available and FULL_DISPLAY otherwise; APP remains WALLPAPER. Burst remains active through changed signatures and stops only after two stable comparisons following at least three samples.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` and verify the new tests fail because the APIs do not exist.
- [ ] Implement the minimal policy/state classes.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` and verify green.

### Task 2: Integrate workstation All Apps/Recents lifecycle

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/WorkstationLiveBackdropContractTest.java`

**Interfaces:**
- Consumes: `WorkstationCaptureBurst` and `CaptureSourcePolicy.sourceForWorkstationScene`.
- Produces: workstation-only entry/exit refresh and adaptive continuation behavior.

- [ ] Write failing source-contract tests asserting All Apps and Recents boundaries start/refresh the workstation burst, workstation full-display capture still uses Dock exclusion, and frame completion only self-schedules workstation capture while the burst remains active.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` and verify RED.
- [ ] Integrate workstation All Apps/Recents activation, capture-source selection, Dock exclusion fallback, signature feedback, and stable-stop suspension.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` and verify green.

### Task 3: CI/build verification

**Files:**
- Temporarily modify then restore: `.github/workflows/api101-build.yml` only if needed to trigger the test branch.

- [ ] Run the complete GitHub Actions workflow on `test/capture-rebuild-8ee84ed`.
- [ ] Verify `testDebugUnitTest` succeeds.
- [ ] Verify `assembleDebug` succeeds.
- [ ] Verify the APK artifact is uploaded.
- [ ] Restore any temporary workflow trigger change without altering production behavior.
