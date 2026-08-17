# 307 In-Place Material Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 307 vendor material View itself the Dock visual shell and compose the existing LiquidDock glass host inside it.

**Architecture:** Keep `DockLiquidGlassView` and `DockLiquidGlassHostView` intact as the renderer/capture stack, but attach the host as a MATCH_PARENT child of the live vendor material `FrameLayout`. Preserve the vendor View alpha/foreground/outline/MiShadow, keep vendor GPU blur disabled, and restore 307-side Dock geometry compatibility that the specialized early-return currently skips.

**Tech Stack:** Android View/FrameLayout composition, libxposed hooks, RuntimeShader/Prismal renderer, Gradle/GitHub Actions.

## Global Constraints

- Work on `fix/miuix307-drag-freeform-followup` only.
- No global `View.draw()`/`FrameLayout.dispatchDraw()` hook.
- Do not restore vendor pass-window/background/region blur.
- Do not hide the vendor material View after capture.
- Do not add or run unit tests for this experiment.
- Verification is source-diff scope plus `./gradlew assembleDebug --stacktrace`.

---

### Task 1: Preserve the vendor material shell

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`

**Interfaces:**
- Produces: `void setPreserveGeometrySourceVisuals(boolean preserve)`.
- Consumes: existing `geometrySource` and `nativeBackgroundHiddenByGlass` lifecycle.

- [ ] Add a default-false preserve flag and package-private setter.
- [ ] In `installCapture`, keep `geometrySource` alpha at 1 and the hidden latch false when preserve mode is enabled; retain legacy behavior otherwise.

### Task 2: Separate optical host rendering from host stroke

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`

**Interfaces:**
- Produces: `void reloadOpticsOnly(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass)`.

- [ ] Move geometry/highlight parameter reload into `reloadOpticsOnly`.
- [ ] Make existing `reloadOverlay` call `reloadOpticsOnly` and then install `DockStrokeRenderer` on the host, preserving legacy behavior.

### Task 3: Attach Prismal inside the 307 material View

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`

**Interfaces:**
- Consumes: `setPreserveGeometrySourceVisuals(true)` and `reloadOpticsOnly`.
- Produces: host child binding where `hostRef.getParent() == backgroundRef`.

- [ ] Require supported 307 background to be a `ViewGroup`.
- [ ] Remove any old injected host before rebinding.
- [ ] Create the existing glass/host stack with Dock-configured shape.
- [ ] Enable preserve-source mode.
- [ ] Add the host as MATCH_PARENT child of the vendor material View instead of as a sibling.
- [ ] Keep vendor GPU blur suppression unchanged.
- [ ] Keep `DockStrokeRenderer.configure` on the vendor material View only.
- [ ] Simplify size sync to MATCH_PARENT/requestLayout and keep geometry sync on host optics + vendor foreground stroke.

### Task 4: Restore 307 Dock customization hooks

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`

**Interfaces:**
- Consumes: `LiquidDockConfig.Dock` width/height/corner/spacing/bottom settings.

- [ ] Apply width/height offsets before native/themed background setters proceed.
- [ ] Apply `blurCornerOffset` before radius setters proceed; `DockStrokeRenderer` derives the stroke radius delta from the same config.
- [ ] Install bottom-offset and spacing hooks inside the 307 pipeline because MainHook returns before the legacy block.
- [ ] Resolve the bound host as a child of the exact vendor background first, retaining sibling fallback only for transition safety.
- [ ] Keep theme rebind and HOME/drag/capture compatibility hooks unchanged.

### Task 5: Build-only verification

**Files:**
- Temporary: `tools/tmp_apply_307_inplace_material_glass.py`
- Temporary: `.github/workflows/tmp-307-inplace-material-glass.yml`

- [ ] Apply the exact production patch through a temporary workflow and verify changed production files are only the four files above.
- [ ] Commit the production patch on `fix/miuix307-drag-freeform-followup`.
- [ ] Run only `./gradlew assembleDebug --stacktrace`; do not invoke any unit-test task.
- [ ] Upload the debug APK.
- [ ] Delete temporary workflow/script and the temporary design/plan documents so the final tree contains production code only.
- [ ] Compare final tree against the pre-experiment baseline and deliver APK plus SHA-256.