# PassBlur Prismal Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore upstream Prismal optical parity on the HyperOS 3.0.307 zero-copy PassBlur backend and eliminate invalid Stage-B edge clamping during Dock animation.

**Architecture:** Normalize the external-OES PassBlur producer into a Dock-local RGBA texture using one Stage-B adapter pass, then run upstream Prismal's half-resolution H/V Gaussian blur passes, then run a Prismal-equivalent glass pass over raw/blurred 2D textures. Producer-domain coverage is computed separately and unavailable rows render transparent instead of sampling a clamped edge texel.

**Tech Stack:** Java, Android GLES20/EGL14, SurfaceTexture external OES, JUnit source-contract/unit tests, GitHub Actions Gradle build.

**Spec:** `docs/superpowers/specs/2026-08-19-passblur-prismal-parity-design.md`

## Global Constraints

- No Bitmap capture, `captureScreenAsync`, `glReadPixels`, or CPU texture upload.
- Keep `Miuix307PassBlurBridge` and the dedicated TextureView EGL renderer.
- Preserve live LiquidDock GUI sync.
- Reference current upstream Prismal `master` optical formulas and calibrated base values.
- Every production change follows a failing-test-first cycle.

---

### Task 1: Stage-B producer coverage math

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307BackdropMapping.java`
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307BackdropMappingTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`

**Interfaces:**
- Produces: `Miuix307BackdropMapping.compute(hostLeft, hostTop, hostWidth, hostHeight, frameLeft, frameTop, frameWidth, frameHeight)` returning mapping and Dock-local valid coverage.
- Consumes: integer screen/window geometry already read by `Miuix307PassBlurTextureView`.

- [ ] Write tests for fully covered, partially covered and fully outside hosts, asserting unclamped `backdropRect` plus valid Dock-local coverage.
- [ ] Run `testDebugUnitTest` and confirm the new tests fail because the helper is missing.
- [ ] Implement the pure helper and use it from `updateBackdropMapping()`.
- [ ] Log producer coverage state and pass valid coverage to the GL pipeline.
- [ ] Run unit tests and confirm green.

### Task 2: Upstream Prismal parameter parity

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307PrismalLegacyParityTest.java`

**Interfaces:**
- Produces: `Miuix307PrismalMaterial.Params` with upstream-compatible values and no legacy lens/depth rescaling.

- [ ] Extend parity tests to assert the upstream calibrated values: IOR 1.55, thickness 18dp, normal 1.15, displacement 1.15, height transition 19dp, smoothing 1.8dp, brightness 1.08, inset 20, edge falloff 4, caustic 0.28, rim 1.22, specular 1.52, shininess 88, light (-0.5,-0.8), shadow softness 10, upstream tint/shadow defaults.
- [ ] Run unit tests and confirm they fail against current defaults.
- [ ] Update schema defaults and `defaults()/fromConfig()` semantics.
- [ ] Run unit tests and confirm green.

### Task 3: OES normalization and producer-domain masking

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurShaders.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewBackdropMappingTest.java`

**Interfaces:**
- Produces: a Dock-local RGBA raw backdrop texture.
- Consumes: OES texture, SurfaceTexture matrix, config rotation, backdrop rectangle, valid Dock-local coverage.

- [ ] Change contract tests to require Stage-B/OES logic only in the normalization shader and require transparent masking outside producer coverage.
- [ ] Run unit tests and confirm red.
- [ ] Add normalization shader/program and full-size raw FBO texture.
- [ ] Render normalization pass before optical passes; do not clamp invalid producer rows to edge texels.
- [ ] Run unit tests and confirm green.

### Task 4: Restore upstream two-pass Gaussian blur

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurShaders.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307PrismalLegacyParityTest.java`

**Interfaces:**
- Produces: half-resolution vertically blurred `GL_TEXTURE_2D` texture.
- Consumes: normalized raw RGBA texture and Prismal blur radius.

- [ ] Add failing source-contract assertions for two 0.5x FBO passes and Gaussian H/V shaders.
- [ ] Run unit tests and confirm red.
- [ ] Add blur FBO textures/framebuffers, resize/release lifecycle, and H/V passes using upstream sigma semantics.
- [ ] Run unit tests and confirm green.

### Task 5: Restore upstream glass equations

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307PrismalLegacyParityTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewBackdropMappingTest.java`

**Interfaces:**
- Glass shader consumes `sampler2D uBackgroundTexture`, `sampler2D uBlurredTexture`, `uUseBlurredTexture`, Prismal uniforms, and Dock-local UVs only.

- [ ] Add failing tests forbidding OES/Stage-B logic, LiquidDock-only drop-lens exponent changes, final color clamp, and `uEdgeBand/uHighlightAlpha` optical modifiers in the parity shader.
- [ ] Run unit tests and confirm red.
- [ ] Port current upstream Prismal equations and texture selection to the 2D-texture material shader while preserving naming needed by Java uniform uploads.
- [ ] Bind raw and blurred textures in the final pass.
- [ ] Run unit tests and confirm green.

### Task 6: Clear/discard hygiene and full verification

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java`

**Interfaces:**
- Final EGL frame clears transparent before material rendering; every FBO pass clears its target.

- [ ] Add failing test requiring transparent `glClearColor(0,0,0,0)` plus `glClear(GL_COLOR_BUFFER_BIT)` before final shader drawing.
- [ ] Run unit tests and confirm red.
- [ ] Add final/FBO clear calls and resource cleanup.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` in GitHub Actions and inspect the complete result.
- [ ] Run `./gradlew assembleDebug --stacktrace` in the same Actions run and inspect the complete result.
- [ ] Compare the original branch to the work branch and verify only intended files changed.
- [ ] Fast-forward `feat/miuix307-passblur-gpu-demo` to the verified work-branch head.