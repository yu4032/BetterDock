# HyperOS 3.0.307 TextureView PassBlur Calibration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the active `GLSurfaceView` PassBlur output with a feedback-safe `TextureView + EGL` calibration backend that renders strict full-domain passthrough with no refraction.

**Architecture:** SurfaceFlinger PassBlur continues to render into a caller-owned input `SurfaceTexture` attached to `GL_TEXTURE_EXTERNAL_OES`. A dedicated EGL render thread samples that texture and renders into the `TextureView` output `SurfaceTexture`, which is composited inside the existing Floating Dock root. Stage A deliberately uses the whole SurfaceTexture domain and performs no Dock-local crop or optical displacement.

**Tech Stack:** Android `TextureView`, `SurfaceTexture`, `Surface`, `HandlerThread`, EGL14/GLES20, `GL_TEXTURE_EXTERNAL_OES`, hidden HyperOS `SurfaceControl.Transaction` PassBlur APIs, JUnit source-contract tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-19-miuix307-textureview-passblur-calibration-design.md`

## Global Constraints

- Android/Gradle builds run only through GitHub CI; no local Android build.
- Normal calibration path contains no `Bitmap`, `captureScreenAsync`, `ScreenshotHardwareBuffer`, `glReadPixels`, or CPU texture upload.
- No `GLSurfaceView`/`SurfaceView` output in the active calibration renderer.
- No `sdRoundRect`, `edgeWeight`, displacement, `uGlassRadius`, Snell/IOR, dispersion, roughness, Fresnel, caustic, tone/tint, or advanced highlight in the calibration shader.
- Rotation resizes the existing PassBlur input `SurfaceTexture` in place and must not call `SetPassBlurSurface(null)` merely for geometry/config-rotation changes.
- Validation timeout must not switch to legacy capture.
- Safe Dock foreground stroke may remain.

---

### Task 1: Lock the feedback-safe TextureView/EGL contracts

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewPassBlurCalibrationTest.java`
- Modify: existing 307 GPU source-contract tests only where they assert the retired active `GLSurfaceView` backend.

**Interfaces:**
- Consumes: current `Miuix307ZeroCopyRenderer`, `Miuix307PassBlurBridge`, and zero-copy validation policy.
- Produces: failing contracts for `Miuix307PassBlurTextureView`, strict passthrough, no active SurfaceView output, and root-only PassBlur exclusion.

- [ ] **Step 1: Write the failing test**

Create tests that require:

```java
assertTrue(renderer.contains("Miuix307PassBlurTextureView"));
assertFalse(renderer.contains("new Miuix307PassBlurGpuView"));
assertTrue(view.contains("extends TextureView"));
assertTrue(view.contains("implements TextureView.SurfaceTextureListener"));
assertTrue(view.contains("EGL14.eglCreateWindowSurface"));
assertTrue(view.contains("GLES11Ext.GL_TEXTURE_EXTERNAL_OES"));
assertTrue(view.contains("uTexMatrix * vec4(vUv, 0.0, 1.0)"));
assertFalse(view.contains("sdRoundRect") || view.contains("edgeWeight") || view.contains("uGlassRadius"));
assertFalse(view.contains("uCrop") || view.contains("glReadPixels") || view.contains("Bitmap"));
assertFalse(bridge.contains("SurfaceView") || bridge.contains("outputView.getSurfaceControl()"));
```

Also require the hook validation timeout to contain `legacy capture disabled` and not call `installCaptureFallback(`.

- [ ] **Step 2: Run GitHub CI to verify RED**

Push only the tests to `feat/miuix307-passblur-gpu-demo` and inspect the PR-triggered workflow. Expected: only the new TextureView/EGL calibration contracts fail; legacy unrelated tests remain green.

- [ ] **Step 3: Commit RED**

Commit message: `test: require TextureView PassBlur calibration backend`.

---

### Task 2: Implement TextureView + EGL strict passthrough backend

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurGpuView.java` only to compile against the bridge if needed; it remains inactive/retired.

**Interfaces:**
- Consumes: `Miuix307PassBlurBridge.bind(View, Surface, float)` and HyperOS producer geometry from `ViewRootImpl.mSurfaceSize`.
- Produces: `Miuix307PassBlurTextureView(Context, View)`, `isGpuBackdropActive()`, `isActivationExhausted()`, `shutdown()`.

- [ ] **Step 1: Generalize the PassBlur bridge away from SurfaceView output identity**

Use the active signature:

```java
static Binding bind(View materialHost, Surface producerSurface, float requestedScale)
```

Exclusions must include the Floating Dock root name and system overlays only:

```java
String[] exclusions = new String[]{
    rootName,
    "NavigationBar",
    "StatusBar",
    "GestureStub",
    "DockAssistantView"
};
```

Do not query a child output `SurfaceControl`; TextureView output is part of the excluded Floating Dock root.

- [ ] **Step 2: Create the TextureView component and EGL thread**

`Miuix307PassBlurTextureView` must:

```java
final class Miuix307PassBlurTextureView extends TextureView
        implements TextureView.SurfaceTextureListener
```

Use a dedicated `HandlerThread`/`Handler`. On `onSurfaceTextureAvailable`, wrap the TextureView output texture in a `Surface`, initialize EGL14 display/config/context/window-surface on the render thread, make it current, create the OES texture and input `SurfaceTexture`, then post the PassBlur bind back to the UI thread.

- [ ] **Step 3: Implement strict passthrough shader**

Fragment shader must be equivalent to:

```glsl
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uTexture;
uniform mat4 uTexMatrix;
varying vec2 vUv;
void main() {
  vec4 transformed = uTexMatrix * vec4(vUv, 0.0, 1.0);
  gl_FragColor = texture2D(uTexture, transformed.xy);
}
```

No crop, displacement, glass radius, SDF, tint, or highlight terms.

- [ ] **Step 4: Implement frame delivery and swap**

The input `SurfaceTexture.OnFrameAvailableListener` posts one render operation to the EGL thread. Draw path calls:

```java
inputSurfaceTexture.updateTexImage();
inputSurfaceTexture.getTransformMatrix(textureMatrix);
GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
EGL14.eglSwapBuffers(eglDisplay, eglSurface);
```

Log first OES frame, first passthrough draw, texture matrix, `configRot`, and producer geometry.

- [ ] **Step 5: Implement in-place producer resize and output lifecycle**

Read producer size from `ViewRootImpl.mSurfaceSize`. For same root identity and changed dimensions/config rotation, post:

```java
inputSurfaceTexture.setDefaultBufferSize(bufferWidth, bufferHeight);
```

without unbinding. `onSurfaceTextureSizeChanged` updates viewport/output size only. `onSurfaceTextureDestroyed` destroys only the EGL output window surface. Full `shutdown()` performs final PassBlur unbind, releases input/output wrappers, EGL resources, and quits the render thread.

- [ ] **Step 6: Run GitHub CI and verify GREEN for the new backend**

Expected: new TextureView/EGL contracts pass. If old tests fail only because they encode the retired GLSurfaceView/lens behavior, update those tests without changing production behavior.

- [ ] **Step 7: Commit implementation**

Commit message: `feat: add TextureView EGL PassBlur calibration`.

---

### Task 3: Activate the calibration backend and keep optics disabled

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Modify: affected 307 source-contract tests.

**Interfaces:**
- Consumes: `Miuix307PassBlurTextureView` from Task 2.
- Produces: active zero-copy renderer reports `passblur-textureview-egl` and never instantiates the retired GLSurfaceView backend.

- [ ] **Step 1: Switch renderer ownership**

Change renderer weak reference and installation to:

```java
Miuix307PassBlurTextureView backdrop =
    new Miuix307PassBlurTextureView(materialHost.getContext(), materialHost);
host.removeAllViews();
host.addView(backdrop, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
```

`sync()` must not apply glass radius/refraction state during calibration.

- [ ] **Step 2: Update zero-copy logs and validation naming**

Use explicit backend text:

```text
PassBlur TextureView EGL calibration installed
zero-copy active backend=passblur-textureview-egl
```

Keep validation timeout capture-disabled.

- [ ] **Step 3: Verify the visual shell remains neutral**

Tests must assert renderer/hook do not add tone/tint or advanced optical highlight and that safe `DockStrokeRenderer.configureReplacingForeground(...)` remains allowed.

- [ ] **Step 4: Run final GitHub CI**

Required final gate:

```text
all unit tests PASS
assembleDebug PASS
artifact upload PASS
```

- [ ] **Step 5: Validate artifact integrity**

Download the GitHub Actions artifact, compare ZIP SHA-256 against GitHub artifact digest, run `unzip -t`, extract APK, compute APK SHA-256. Do not perform a local Gradle build.

- [ ] **Step 6: Commit final test cleanup if needed**

Commit message: `test: retire GLSurfaceView calibration contracts`.

---

## Device validation for this rollout

This rollout is Stage A only. The user should judge only:

1. recursive/spiral feedback is gone;
2. live backdrop keeps updating;
3. repeated portrait/landscape rotation does not crash Launcher;
4. the output is strict passthrough with no intentional refraction/tint/highlight.

Exact Dock-local position/orientation is *not* a Stage A pass condition. Stage B mapping is implemented only after Stage A proves feedback-free.