# HyperOS 3.0.307 PassBlur Stage B Mapping Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the feedback-free TextureView/EGL Stage-A backend into an accurate Dock-local passthrough by mapping the Dock's real root-window rectangle and explicit HyperOS `configRot` orientation before the existing SurfaceTexture transform.

**Architecture:** Keep PassBlur producer ownership, TextureView/EGL output, no-swap producer buffer, in-place rotation resize, and strict passthrough unchanged. Compute one normalized GL bottom-left backdrop rectangle from `materialHost.getLocationOnScreen()` relative to `materialHost.getRootView().getLocationOnScreen()` and `ViewRootImpl.mSurfaceSize`; map local `vUv` into that root rectangle, apply the explicit `configRot` 0/90/180/270 transform, then apply `SurfaceTexture.getTransformMatrix()` as the final producer-to-OES mapping.

**Tech Stack:** Android View/TextureView coordinates, EGL14/GLES20, `SurfaceTexture`, hidden HyperOS PassBlur APIs, JUnit source-contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-19-miuix307-textureview-passblur-calibration-design.md`

## Global Constraints

- Keep `Miuix307PassBlurTextureView`; do not return to GLSurfaceView/SurfaceView output.
- Keep producer buffer dimensions equal to `ViewRootImpl.mSurfaceSize`; do not reintroduce the old 90/270 physical buffer swap in this Stage-B experiment.
- Geometry/config rotation changes resize the existing input SurfaceTexture in place and do not hot-unbind PassBlur.
- Normal path remains GPU-only: no Bitmap/captureScreenAsync/ScreenshotHardwareBuffer/glReadPixels/CPU upload.
- Shader remains optically neutral: no SDF lens, edgeWeight, displacement, glass radius, IOR, dispersion, tone/tint, or highlight.
- Validation timeout remains capture-disabled.
- Safe Dock foreground stroke may remain.

---

### Task 1: Lock the single-coordinate-model Stage-B contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewBackdropMappingTest.java`

**Interfaces:**
- Consumes: `Miuix307PassBlurTextureView`, `ProducerGeometry.surfaceWidth/surfaceHeight/configRotation`.
- Produces: source contracts for `uBackdropRect`, explicit `uConfigRot`, material-host/root screen coordinates, and post-matrix diagnostics.

- [ ] **Step 1: Write RED tests** requiring the active TextureView shader to contain `uniform vec4 uBackdropRect`, `uniform int uConfigRot`, local-to-root mapping, the four explicit rotation transforms, and `uTexMatrix` after rotation. Require Java to derive rect from `materialHost.getLocationOnScreen`, `materialHost.getRootView().getLocationOnScreen`, `boundSurfaceWidth/Height`, and upload both uniforms. Require logs for `rootScreen`, `hostScreen`, `backdropRect`, `configRot`, `texture matrix`, and mapped corners.
- [ ] **Step 2: Run GitHub CI**. Expected: only the new Stage-B contracts fail.
- [ ] **Step 3: Commit RED** with `test: require TextureView Dock-local backdrop mapping`.

---

### Task 2: Implement accurate Dock-local passthrough mapping

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`

**Interfaces:**
- Produces volatile backdrop rect `(x, yBottom, width, height)` in normalized root-surface GL coordinates.

- [ ] **Step 1: Add backdrop mapping state** initialized to full-domain `(0,0,1,1)`.
- [ ] **Step 2: On attach/pre-draw and after producer geometry changes, compute `hostScreen - rootScreen`; normalize X/width by surface width and convert screen-top Y to GL-bottom with `1 - (top + height)/surfaceHeight`.
- [ ] **Step 3: Update the strict shader**: `rootUv = uBackdropRect.xy + vUv * uBackdropRect.zw`; apply config rotation `0: (x,y)`, `1: (y,1-x)`, `2: (1-x,1-y)`, `3: (1-y,x)`; then `uTexMatrix * vec4(orientedUv,0,1)` and one OES sample.
- [ ] **Step 4: Upload `uBackdropRect` and `uConfigRot` on every draw.** Mapping changes may request a draw of the latest consumed frame without requiring a new PassBlur frame.
- [ ] **Step 5: Add one-shot-per-geometry Stage-B diagnostics** containing root/host screen coordinates, root surface dimensions, backdrop rect, configRot, texture matrix, and four final mapped corners.
- [ ] **Step 6: Run GitHub CI**. If failures are only Stage-A source tests that mandate full-domain shader text, update those tests to preserve feedback-free/neutral constraints while accepting Stage-B mapping.
- [ ] **Step 7: Commit GREEN** with `feat: map PassBlur backdrop to Dock coordinates`.

---

### Task 3: Final gate and device artifact

**Files:**
- Modify only stale source-contract tests if CI proves they encode Stage-A full-domain behavior.

- [ ] **Step 1: Run final GitHub CI** and require all unit tests, `assembleDebug`, and artifact upload to pass.
- [ ] **Step 2: Download artifact and verify ZIP SHA against GitHub digest, `unzip -t`, and APK SHA. No local Gradle build.**
- [ ] **Step 3: Device validation** checks portrait continuity first, then landscape orientation/continuity, repeated rotations, no recursive feedback, and no optical displacement. Refraction remains disabled until all mapping checks pass.
