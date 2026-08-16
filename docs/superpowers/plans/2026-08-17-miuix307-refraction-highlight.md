# MiuiX 307 Refraction Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local GPU-backed refraction/highlight overlay to the opt-in HyperOS 3.0.307+ MiuiX material pipeline while preserving native blur and avoiding the legacy capture state machine.

**Architecture:** `Miuix307MaterialPipeline` keeps native MiuiX blur ownership and replaces the current highlight-only overlay with `Miuix307RefractionView`. The new View uses the existing low-level `LiveScreenCapture.captureScreenAsync()` primitive to capture only Dock bounds plus bleed in mode 1, feeds the returned hardware-backed Bitmap into a BitmapShader/RuntimeShader, and composites partially transparent refraction plus highlight over native blur.

**Tech Stack:** Android View/HWUI, AGSL `RuntimeShader`, `BitmapShader`, HyperOS `ScreenCapture` reflection already encapsulated by `LiveScreenCapture`, GitHub Actions Gradle tests/build.

## Global Constraints

- Work only on `feat/miuix307-refraction-highlight`, forked from the verified `62e33303e80d69b27fd62ae3ea5fa0d06b7f8914` 307 material baseline.
- Do not merge `main` as part of implementation.
- `liquid_miuix_307_pipeline=false` must preserve existing low-version behavior.
- Do not introduce `CaptureSceneState`, `BackdropTransitionPolicy`, wallpaper cache, APP/HOME ownership or legacy capture lifecycle into the 307 refraction path.
- Native MiuiX blur remains enabled and visible under the refraction overlay.
- Capture only Dock bounds plus bounded bleed, mode 1 with Floating Dock exclusion.
- Cap capture at 30 FPS and clamp scale to 0.25-0.50.
- Capture failures degrade to highlight-only; they must not disable the 307 native material pipeline.

---

### Task 1: Add contract tests for the 307 refraction boundary

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307RefractionContractTest.java`
- Modify: `.github/workflows/api101-build.yml`

**Interfaces:**
- Consumes: existing `Miuix307MaterialPipeline`, `LiveScreenCapture`.
- Produces: source-level contracts that require `Miuix307RefractionView`, local mode-1 capture, bounded cadence/scale and absence of legacy state-machine dependencies.

- [ ] **Step 1: Write the failing test**

Add source-contract assertions that:

```java
assertTrue(Files.exists(main.resolve("Miuix307RefractionView.java")));
assertTrue(viewSource.contains("uniform shader content"));
assertTrue(viewSource.contains("captureScreenAsync"));
assertTrue(viewSource.contains("MAX_CAPTURE_FPS = 30"));
assertTrue(viewSource.contains("MAX_CAPTURE_SCALE = 0.50f"));
assertTrue(viewSource.contains("CAPTURE_MODE_FULL_DISPLAY = 1"));
assertFalse(viewSource.contains("CaptureSceneState"));
assertFalse(viewSource.contains("BackdropTransitionPolicy"));
assertFalse(viewSource.contains("captureWallpaper"));
assertTrue(pipelineSource.contains("Miuix307RefractionView"));
assertFalse(pipelineSource.contains("new Miuix307HighlightView"));
```

- [ ] **Step 2: Run CI to verify RED**

Add `feat/miuix307-refraction-highlight` to the branch list in `.github/workflows/api101-build.yml`, push the failing contract test, and verify `testDebugUnitTest` fails before `assembleDebug`.

Expected: contract test failure because `Miuix307RefractionView.java` does not exist and the pipeline still constructs `Miuix307HighlightView`.

- [ ] **Step 3: Commit RED test state**

Commit message:

```text
test: specify MiuiX 307 refraction overlay
```

---

### Task 2: Implement the local GPU-backed refraction View

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307RefractionView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307RefractionContractTest.java`

**Interfaces:**
- Constructor: `Miuix307RefractionView(Context context, ClassLoader launcherClassLoader, LiquidDockConfig config)`
- Geometry update: `void setMaterialGeometry(float radius, float highlightAlpha, float highlightWidth)`
- Capture refresh: internal `requestBackdrop(boolean immediate)`
- Input: latest hardware-backed `Bitmap` from `LiveScreenCapture.captureScreenAsync()`
- Output: refraction/highlight pixels drawn by the View.

- [ ] **Step 1: Implement bounded capture constants and lifecycle**

Use:

```java
private static final int MAX_CAPTURE_FPS = 30;
private static final float MIN_CAPTURE_SCALE = 0.25f;
private static final float MAX_CAPTURE_SCALE = 0.50f;
private static final int CAPTURE_MODE_FULL_DISPLAY = 1;
```

Create `LiveScreenCapture` lazily/on construction. Start the scheduler from `onAttachedToWindow()`, stop and bump a generation token from `onDetachedFromWindow()`. Do not schedule while `!isShown()`, width/height <= 1, or display is null.

- [ ] **Step 2: Compute a local crop only**

For each request:

```java
getLocationOnScreen(tmpLocation);
int bleed = computeBleedPx();
Rect crop = new Rect(x - bleed, y - bleed, x + getWidth() + bleed, y + getHeight() + bleed);
display.getRealSize(tmpDisplaySize);
crop.intersect(0, 0, tmpDisplaySize.x, tmpDisplaySize.y);
```

Store crop-relative inset:

```java
captureInsetX = x - crop.left;
captureInsetY = y - crop.top;
```

Pass `null` explicit SurfaceControl exclusions and rely on the existing mode-1 `LiveScreenCapture` path, which always adds `Floating Dock` to its layer-name exclusions.

- [ ] **Step 3: Submit at most one async capture**

Clamp scale:

```java
captureScale = Math.max(MIN_CAPTURE_SCALE,
        Math.min(MAX_CAPTURE_SCALE, config.glass.captureScale));
```

Clamp FPS:

```java
captureFps = Math.max(1, Math.min(MAX_CAPTURE_FPS, config.glass.captureFps));
```

Call:

```java
capture.captureScreenAsync(crop, captureScale, displayId,
        null, null, CAPTURE_MODE_FULL_DISPLAY, callback);
```

If one request is active, set a single `captureDirty` flag. After callback completion, schedule one trailing request if dirty.

- [ ] **Step 4: Install the hardware Bitmap as shader input**

On the main thread, reject stale-generation frames and recycled/zero-sized bitmaps. Create:

```java
BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
refraction.setInputShader("content", shader);
```

Derive exact effective scale from bitmap dimensions and the submitted crop:

```java
captureScaleX = bitmap.getWidth() / (float) crop.width();
captureScaleY = bitmap.getHeight() / (float) crop.height();
```

Retire the previous Bitmap after a short delayed grace period so the RenderThread does not race a recycled hardware bitmap.

- [ ] **Step 5: Implement the AGSL refraction pass**

Use a rounded-rectangle SDF and finite-difference gradient. The shader must:

```text
baseSample = (coord + captureInset) * effectiveCaptureScale
edgeWeight = strongest near rounded-rect boundary
lensOffset = normal * lensRefraction * edgeWeight * normalStrength * (ior - 1)
domeOffset = normalized center vector * small interior dome contribution
R/G/B sample at lensOffset +/- chromatic offset
```

Return a partially transparent refracted layer so MiuiX blur remains visible underneath. Outside the rounded rectangle return transparent.

- [ ] **Step 6: Keep highlight as a second pass**

After drawing the RuntimeShader rectangle, draw the same directional white gradient stroke behavior currently implemented by `Miuix307HighlightView`. If no backdrop shader is ready, skip refraction and still draw the highlight stroke.

- [ ] **Step 7: Run targeted tests**

Run GitHub Actions `testDebugUnitTest`.

Expected: new contract passes; existing tests remain green.

- [ ] **Step 8: Commit**

Commit message:

```text
feat: add GPU-backed MiuiX 307 refraction view
```

---

### Task 3: Wire refraction into the 307 material pipeline

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307RefractionContractTest.java`

**Interfaces:**
- Consumes: `Miuix307RefractionView` constructor and `setMaterialGeometry(...)`.
- Produces: one refraction overlay per native MiuiX background View.

- [ ] **Step 1: Replace the highlight overlay map**

Change:

```java
WeakHashMap<View, Miuix307HighlightView>
```

to:

```java
WeakHashMap<View, Miuix307RefractionView>
```

Store the Launcher class loader captured by `install(...)` so `bind(...)` can construct the refraction View with the same loader used by `LiveScreenCapture` reflection.

- [ ] **Step 2: Preserve geometry and stroke behavior**

Keep current width/height/margin synchronization and `DockStrokeRenderer.configure(...)`. Send radius/highlight configuration to `Miuix307RefractionView.setMaterialGeometry(...)`.

- [ ] **Step 3: Preserve failure isolation**

If refraction View creation fails, log once and install `Miuix307HighlightView` as a fallback overlay. Do not return `false` from the entire 307 pipeline after native hooks are installed.

- [ ] **Step 4: Run full tests and build**

Run:

```text
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

through GitHub Actions.

Expected: both succeed and the APK artifact uploads.

- [ ] **Step 5: Commit**

Commit message:

```text
feat: enable refraction over MiuiX 307 material
```

---

### Task 4: Device-test artifact verification

**Files:**
- No source changes unless device evidence requires them.

**Interfaces:**
- Input: successful GitHub Actions Debug artifact.
- Output: integrity-checked APK for device testing.

- [ ] **Step 1: Download the final Actions artifact**

Use the artifact associated with the exact feature-branch HEAD.

- [ ] **Step 2: Verify artifact integrity**

Check artifact SHA-256, APK SHA-256 and `unzip -t` on the APK.

- [ ] **Step 3: Device acceptance criteria**

With `HyperOS 3.0.307+ new material pipeline` ON:

```text
- native MiuiX blur remains visible
- background bends near Dock glass boundary
- chromatic edge shift is visible at configured non-zero chromatic value
- directional highlight remains visible
- Dock/app gestures do not re-enable the legacy scene-state capture loop
- no Dock self-image/recursive capture appears
- performance is materially closer to the current 307 native-material demo than to old full-display LiquidDock capture
```

With the 307 switch OFF, existing legacy behavior must be unchanged.
