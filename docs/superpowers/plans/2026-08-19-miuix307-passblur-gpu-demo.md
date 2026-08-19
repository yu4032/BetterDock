# MiuiX 307 PassBlur GPU Backdrop Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a diagnostic HyperOS 3.0.307 demo where SurfaceFlinger produces the live Dock backdrop into a BufferQueue and LiquidDock samples it directly as `GL_TEXTURE_EXTERNAL_OES`, with a visible split raw/distorted shader and no capture/Bitmap path.

**Architecture:** A new `Miuix307PassBlurBridge` reflects the device-only `SurfaceControl.Transaction` PassBlur methods on the Floating Dock root and binds them to a `Surface` backed by an OES `SurfaceTexture`. `Miuix307PassBlurGpuView` owns the GLSurfaceView output, OES consumer, crop mapping, diagnostic shader, lifecycle, and activation state. `Miuix307ZeroCopyRenderer` uses this GPU view as the 307 exact-background demo backend, and `MiuixGlassHook` validates renderer activation rather than a blur child.

**Tech Stack:** Android 16/17 framework APIs, `SurfaceControl`, `SurfaceTexture`, `Surface`, `GLSurfaceView`, GLES 2.0, `GL_OES_EGL_image_external`, Java reflection, JUnit source-contract tests, GitHub Actions CI.

**Spec:** `docs/superpowers/specs/2026-08-19-miuix307-passblur-gpu-demo-design.md`

## Global Constraints

- Android/Gradle compilation runs only in GitHub CI.
- No local Gradle or Android build.
- Successful demo path contains no `captureScreenAsync`, `ScreenshotHardwareBuffer`, `Bitmap`, or `BitmapShader`.
- Successful demo path contains no `setChargeAnim*`, water-wave, or wallpaper-runtime-shader binding.
- PassBlur scale is exactly `1.0f` for the first demo.
- Existing capture renderer remains fallback only.

---

### Task 1: PassBlur transaction bridge

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java`

**Interfaces:**
- Consumes: attached `View materialHost`, output `SurfaceView`, producer `Surface`.
- Produces: `Miuix307PassBlurBridge.Binding bind(View, SurfaceView, Surface, float)` and `void unbind(Binding)`.

- [ ] **Step 1: Write the failing source-contract test**

Require the bridge to reflect `SetPassBlurSurface`, `setUpdateTextureFlag`, and `setMiBlurWinExc`, target the root `SurfaceControl`, use `1.0f`, exclude both Floating Dock/root and the output child Surface, and clear the producer on unbind.

- [ ] **Step 2: Run CI and verify RED**

Run through GitHub Actions: `./gradlew testDebugUnitTest --stacktrace`.
Expected: only the new PassBlur bridge/demo contract fails because production bridge/view files do not yet exist.

- [ ] **Step 3: Implement the minimal bridge**

The bridge obtains `materialHost.getViewRootImpl()` reflectively, obtains the root `SurfaceControl`, gets the output `SurfaceView.getSurfaceControl()`, resolves:

```java
SetPassBlurSurface(SurfaceControl.class, Surface.class)
setUpdateTextureFlag(SurfaceControl.class, boolean.class, float.class)
setMiBlurWinExc(SurfaceControl.class, String[].class)
```

and submits one transaction with the producer `Surface`, update enabled at `1.0f`, and an exclude array containing root/child layer names plus `NavigationBar`, `StatusBar`, `GestureStub`, and `DockAssistantView`. `unbind()` submits `SetPassBlurSurface(root, null)`, disables update texture, clears the exclude array, and applies.

- [ ] **Step 4: Run CI and verify bridge tests GREEN**

Expected: bridge contract passes; existing tests remain green.

- [ ] **Step 5: Commit**

Commit message: `feat: add HyperOS PassBlur surface bridge`

---

### Task 2: OES GPU diagnostic view

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuView.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuDemoTest.java`

**Interfaces:**
- Consumes: `Context`, attached vendor material `View`.
- Produces: `boolean isGpuBackdropActive()`, `boolean isActivationExhausted()`, `void setGlassRadius(float)`, `void shutdown()`.

- [ ] **Step 1: Extend the failing contract**

Require `GLSurfaceView`, `GL_TEXTURE_EXTERNAL_OES`, `SurfaceTexture`, `Surface`, `RENDERMODE_WHEN_DIRTY`, `updateTexImage()`, `getTransformMatrix()`, split raw/distorted fragment logic, crop uniforms, and no Bitmap/capture symbols.

- [ ] **Step 2: Run CI and verify RED**

Expected: only new GPU-view contract assertions fail.

- [ ] **Step 3: Implement the GPU view**

Use GLES 2.0. `onSurfaceCreated()` creates an OES texture and `SurfaceTexture`, wraps it in a producer `Surface`, installs a frame listener that calls `requestRender()`, and posts PassBlur binding back to the UI thread. `onDrawFrame()` consumes the latest buffer with `updateTexImage()`, obtains the transform matrix, samples the root-window crop, draws raw on the left and a static sinusoidal horizontal displacement on the right, and marks activation true after the first successful draw.

The UI thread sizes the producer buffer to the root View, recomputes normalized crop coordinates on pre-draw, and applies the Dock corner radius to the output child `SurfaceControl`. Shader/program/texture errors set activation exhausted and log `[DC][PBGL]`.

- [ ] **Step 4: Run CI and verify GREEN**

Expected: all GPU-view source contracts and existing tests pass.

- [ ] **Step 5: Commit**

Commit message: `feat: render PassBlur backdrop with OES shader`

---

### Task 3: Wire the demo as the 307 zero-copy backend

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307ZeroCopyContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307SurfaceRefractionProbeTest.java`

**Interfaces:**
- `Miuix307ZeroCopyRenderer.isActive()` delegates to the GPU view.
- `Miuix307ZeroCopyRenderer.isActivationExhausted()` delegates to the GPU view.
- `Miuix307ZeroCopyRenderer.activeWidth()/activeHeight()` expose diagnostic dimensions.

- [ ] **Step 1: Write failing wiring contracts**

Require the exact Background2 path to insert `Miuix307PassBlurGpuView`, tone, and sharp optics, and forbid `Miuix307RefractionSurfaceProbeView`, `Miuix307RefractionExperiment`, `setChargeAnim`, and exact-background-blur calibration from the successful demo composition. Require hook validation to query renderer activation and preserve capture fallback only after activation failure/timeout.

- [ ] **Step 2: Run CI and verify RED**

Expected: wiring tests fail against the current charge-refraction/background-blur renderer.

- [ ] **Step 3: Implement minimal wiring**

For `HotSeatsListContentBlurBackground2`, create the GPU demo view, add it before tone, reload sharp optics, and store weak refs. Renderer clear calls `gpuView.shutdown()`. Non-exact material paths may return false and use the existing capture fallback for this demo branch.

Update `MiuixGlassHook` validation to allow up to 90 animation frames for EGL/BufferQueue startup, declare active only after `Miuix307ZeroCopyRenderer.isActive()`, and log `backend=passblur-gles`. On failure, clear the GPU renderer and run the unchanged capture fallback.

- [ ] **Step 4: Run CI and verify GREEN**

Expected: complete unit suite passes.

- [ ] **Step 5: Commit**

Commit message: `feat: switch 307 demo to PassBlur GPU backend`

---

### Task 4: Full CI build and artifact verification

**Files:**
- No production changes unless verification exposes a real defect.

- [ ] **Step 1: Run full CI**

GitHub Actions must run both:

```text
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: all unit tests pass and `assembleDebug` succeeds.

- [ ] **Step 2: Download the CI artifact**

Download `LiquidDock-api101-debug` from the successful workflow.

- [ ] **Step 3: Verify artifact integrity**

Verify the ZIP SHA-256 matches GitHub's artifact digest, run `unzip -t`, extract the APK, and compute its SHA-256.

- [ ] **Step 4: Device validation target**

The decisive log filter is:

```bash
adb logcat -d -v threadtime | grep -E '\[DC\]\[(PBGL|ZC)\]'
```

Expected successful markers:

```text
[DC][PBGL] PassBlur producer bound scale=1.0
[DC][PBGL] first OES frame
[DC][PBGL] first GLES backdrop draw
[DC][ZC] zero-copy active backend=passblur-gles
```

and no `zero-copy unavailable; capture fallback`.

Visually, the left part of the Dock should align with the live scene and the right part should show obvious horizontal spatial displacement of the same scene.
