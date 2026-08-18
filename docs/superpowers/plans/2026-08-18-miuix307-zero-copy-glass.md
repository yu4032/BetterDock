# MiuiX 307 Zero-Copy Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the MiuiX 307 material path render its backdrop through SurfaceFlinger pass-window blur with zero LiquidDock screen readback, while retaining the current capture renderer as fallback.

**Architecture:** Add a dedicated pass-window blur child below the existing optical-highlight host. `MiuixGlassHook` selects zero-copy first, keeps the vendor parent blur disabled, and only creates `DockLiquidGlassView` if zero-copy activation fails. Existing 4.50 capture/transition hooks remain installed but are inert when no glass capture view is bound.

**Tech Stack:** Android View/HWUI, MIUI hidden View blur APIs via `MiBlurBridge`, RuntimeShader, libxposed API 101, JUnit source-contract tests, Gradle/GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-18-miuix307-zero-copy-glass-design.md`

## Global Constraints

- Preserve baseline `9ca842752df27e0a229af53fa17b04e12fad6097` on `archive/307-capture-stable-20260818`.
- Work only on `feat/miuix307-zero-copy-glass`.
- Do not remove the capture renderer; it is the fallback.
- Successful zero-copy must not create `DockLiquidGlassView` or bind `HomeOwnershipRuntime`.
- Parent/vendor pass-window blur stays disabled; only the dedicated child owns pass-window blur.
- First-stage zero-copy keeps optical highlight/stroke but does not claim true UV refraction.

---

### Task 1: Zero-copy blur capability and backdrop child

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java`
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyBackdropView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307ZeroCopyContractTest.java`

**Interfaces:**
- Produces: `MiBlurBridge.isPassWindowBlurAvailable()`.
- Produces: `Miuix307ZeroCopyBackdropView(Context,int)` with `setBlurRadius(int)`, `isBlurActive()`, and `clearBlur()`.

- [ ] **Step 1:** Add a source-contract test requiring the pass-window capability accessor and a backdrop child that calls `MiBlurBridge.applyPassWindowBlur(this, ...)` and clears it on detach.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest --tests '*Miuix307ZeroCopyContractTest*'` and verify RED because the new API/class is absent.
- [ ] **Step 3:** Implement the capability accessor and child View. Apply blur only after attachment; retry on animation frames for a bounded number of frames if the first call occurs before a valid ViewRoot.
- [ ] **Step 4:** Run the focused test and verify GREEN.

### Task 2: Zero-copy composition installer with capture fallback

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307ZeroCopyContractTest.java`

**Interfaces:**
- Produces: `Miuix307ZeroCopyRenderer.install(ViewGroup,DockLiquidGlassHostView,LiquidDockConfig.Glass,int)` returning boolean.
- Produces: `MiuixGlassHook.isZeroCopyActive()` and `MiuixGlassHook.currentZeroCopyBackdrop()` for diagnostics/tests.

- [ ] **Step 1:** Extend the source-contract test to require zero-copy-first installation, no `LiquidGlassFactory.create`/`HomeOwnershipRuntime.bind` on the successful block, and explicit capture fallback.
- [ ] **Step 2:** Run focused test and verify RED.
- [ ] **Step 3:** Implement `Miuix307ZeroCopyRenderer`: attach the zero-copy child to `DockLiquidGlassHostView`, keep existing highlight geometry/optics, set the host's ADVANCED highlight backend, and report activation.
- [ ] **Step 4:** Refactor `MiuixGlassHook.install()` into zero-copy-first plus existing capture fallback. Keep vendor parent blur suppression and transparent material body for both paths.
- [ ] **Step 5:** Update `syncSize()`/`syncGeometry()` so zero-copy blur radius/host geometry stay synchronized while capture fallback remains unchanged.
- [ ] **Step 6:** Run focused tests and verify GREEN.

### Task 3: Ensure successful zero-copy does not need active capture authority

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307GestureBackdropHoldHook.java` only if required by tests/runtime null handling.
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307RecentsInputHook.java` only if required by tests/runtime null handling.
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307ZeroCopyContractTest.java`

**Interfaces:**
- Consumes: `MiuixGlassHook.isZeroCopyActive()`.
- Produces: capture hooks remain no-op when zero-copy is active and still work for fallback.

- [ ] **Step 1:** Add a contract asserting no zero-copy code path explicitly requests captures and capture hooks can remain installed only as fallback/no-op infrastructure.
- [ ] **Step 2:** Run focused test and verify RED only if current hook code can still force a capture without a bound `DockLiquidGlassView`.
- [ ] **Step 3:** Add the narrowest zero-copy guard only where needed; do not alter the archived GestureToHome semantics for fallback.
- [ ] **Step 4:** Run focused and existing 307 contract tests.

### Task 4: Full verification and artifact

**Files:**
- Modify: `.github/workflows/api101-build.yml` to include `feat/**` only if the branch is not already covered (it is currently covered, so no change expected).

- [ ] **Step 1:** Run/trigger full `testDebugUnitTest`.
- [ ] **Step 2:** Run/trigger `assembleDebug`.
- [ ] **Step 3:** Verify the workflow artifact exists and download the APK.
- [ ] **Step 4:** Hash the APK and ZIP and verify ZIP integrity.
- [ ] **Step 5:** Device validation: confirm `[DC][ZC] zero-copy active` and confirm repeated transitions do not emit LiquidDock readback captures. If zero-copy activation fails, collect `[DC][ZC]` logs and verify capture fallback remains functional.
