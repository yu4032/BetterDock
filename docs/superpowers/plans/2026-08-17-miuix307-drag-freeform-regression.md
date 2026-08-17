# MiuiX 307 Drag and Freeform Regression Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the original Dock-drag exclusion behavior in the 307 path and obtain bounded evidence for the freeform regression without disturbing the validated HOME/APP or native MiuiX material paths.

**Architecture:** Reuse the existing `DragController.startDrag/endDrag -> DockLiquidGlassView.setDockDragging(...)` mechanism, but route its target to the currently bound MiuiX glass when the legacy `MainHook.liquidGlassView` is absent. Keep the existing SystemUI freeform task-leash gate unchanged semantically; add a deduplicated state log at the final mode-1 submission boundary so device testing can distinguish provider, leash-merging, and native-material causes.

**Tech Stack:** Java 17, Android/HyperOS hidden APIs, libxposed API101, JUnit4, GitHub Actions.

## Global Constraints

- Do not call the full legacy `installLiquidGlassCaptureHooks()` from the 307 path.
- Do not re-enable the legacy Recents haptic/prearm path for MiuiX 307.
- Do not change HOME/APP ownership, `toHome` wallpaper barrier, shader optics, native blur-radius clamp, or freeform fail-closed semantics.
- Freeform diagnostics must log only when their state signature changes; never log every capture frame.
- Build and test only with GitHub Actions.

---

### Task 1: Restore original Dock drag exclusion in 307

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307CaptureCompatibilityTest.java`

**Interfaces:**
- Consumes: existing `DockLiquidGlassView.setDockDragging(boolean, String)` and `MainHook.resolveDragSurfaceLayerName(...)` behavior.
- Produces: `MiuixGlassHook.currentGlass()` and a package-visible `MainHook.installDockDragHooks(ClassLoader)` used by 307 setup.

- [ ] **Step 1: Write failing contract test** requiring the 307 pipeline to install only the existing drag hook and requiring the drag callback to fall back to `MiuixGlassHook.currentGlass()`.
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace` in GitHub Actions** and confirm the new contract fails for missing 307 wiring.
- [ ] **Step 3: Implement the minimum bridge**: expose the current MiuiX glass, make the existing drag-hook installer package-visible, route drag callbacks to legacy glass first and MiuiX glass second, and invoke that installer from the 307 pipeline.
- [ ] **Step 4: Verify the full unit suite passes** in GitHub Actions.

### Task 2: Add bounded freeform submission diagnostics

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307CaptureCompatibilityTest.java`

**Interfaces:**
- Consumes: `FreeformTaskLeashResolver.Resolution` final mode-1 decision.
- Produces: one deduplicated diagnostic state describing `visible`, `safe`, remote leash count, requested action, and whether 307 is active.

- [ ] **Step 1: Extend the failing contract test** to require a state-deduplicated diagnostic helper rather than per-frame logging.
- [ ] **Step 2: Implement `logGateStateIfChanged(...)`** using one atomic last-signature value and call it only after the final freeform decision is known.
- [ ] **Step 3: Verify tests and `assembleDebug`** in GitHub Actions.
- [ ] **Step 4: Download the CI artifact and provide the APK** for a device run covering one freeform-open/close cycle and one Dock icon drag.
