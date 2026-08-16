# Two-View Composition Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port only the `4fc9875` two-view glass composition onto `test/capture-rebuild-8ee84ed` while preserving the current Stage 1 Recents capture-source behavior.

**Architecture:** `DockLiquidGlassHostView` becomes the only sharp parent layer and owns final clipping, ADVANCED highlight, and stroke foreground. `DockLiquidGlassView` remains the only child and continues to own capture/refraction/tint/self-blur. `DockStrokeOverlayView` is deleted.

**Tech Stack:** Android Java, RuntimeShader/AGSL, Canvas, Drawable foreground, JUnit source-contract tests, Gradle.

## Global Constraints

- Branch: `test/capture-rebuild-8ee84ed`.
- Source of truth for the composition refactor: commit `4fc9875b18dd628a03dfa6ec702ac96b7dc93e16` only.
- Do not modify `CaptureSourcePolicy.java` or Stage 1 Recents source eligibility.
- Do not modify `DockLiquidGlassView.java` capture behavior.
- Do not address the brief wallpaper frame between haptic prearm and confirmed Recents in this stage.
- Do not run GitHub Actions.

---

### Task 1: Add the two-view architecture regression contracts

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/LiquidGlassLayerContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/TwoViewCompositionContractTest.java`

**Interfaces:**
- Consumes: current three-view source layout.
- Produces: source-contract assertions requiring one glass child, host-owned sharp highlight/stroke, cached host clip geometry, cached stroke geometry, and no `DockStrokeOverlayView` construction.

- [ ] **Step 1: Port the exact contract changes from `4fc9875`**

Use the tests from the source commit without changing capture-policy expectations. The new contracts must assert:

```java
assertFalse(Files.exists(Path.of(
        "src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java")));
assertTrue(host.contains("void setLayers(DockLiquidGlassView glass)"));
assertEquals(1, count(host, "addView("));
assertTrue(host.contains("glass.setActiveBlurBackendListener(this::setActiveBlurBackend)"));
assertTrue(host.indexOf("super.dispatchDraw(canvas)")
        < host.indexOf("drawAdvancedHighlight(canvas)"));
assertTrue(renderer.contains("private boolean geometryDirty = true"));
```

- [ ] **Step 2: Run targeted tests and confirm RED locally**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.hellovoid.liquiddock.DynamicLiquidHighlightContractTest' \
  --tests 'com.hellovoid.liquiddock.LiquidGlassLayerContractTest' \
  --tests 'com.hellovoid.liquiddock.TwoViewCompositionContractTest'
```

Expected on the current three-view baseline: FAIL because `DockStrokeOverlayView.java` exists, the host has two children, and the host does not own the highlight/stroke.

- [ ] **Step 3: Commit the RED contracts**

```bash
git add src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java \
        src/test/java/com/hellovoid/liquiddock/LiquidGlassLayerContractTest.java \
        src/test/java/com/hellovoid/liquiddock/TwoViewCompositionContractTest.java
git commit -m "test: require two-view glass composition"
```

### Task 2: Move the sharp composition layer into the host

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java`

**Interfaces:**
- Consumes: `DockLiquidGlassView.ActiveBlurBackendListener`, `DockShapePath`, `DockStrokeRenderer`.
- Produces: `void setLayers(DockLiquidGlassView glass)`, host-owned `reloadOverlay(...)`, cached `ensureClipPath()`, and `drawAdvancedHighlight(Canvas)`.

- [ ] **Step 1: Port the `4fc9875` host implementation**

The host must:

```java
void setLayers(DockLiquidGlassView glass) {
    removeAllViews();
    glassView = glass;
    glass.setActiveBlurBackendListener(this::setActiveBlurBackend);
    addView(glass, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
}
```

It must copy the existing overlay AGSL highlight shader into the host, use `BlendMode.PLUS`, cache `clipPath` with `shapeDirty`, and render in this order:

```java
int save = canvas.save();
canvas.clipPath(clipPath);
super.dispatchDraw(canvas);
drawAdvancedHighlight(canvas);
canvas.restoreToCount(save);
```

`reloadOverlay(...)` must configure both dynamic highlight parameters and `DockStrokeRenderer` on the host.

- [ ] **Step 2: Delete the obsolete overlay View**

Delete:

```text
src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java
```

- [ ] **Step 3: Run targeted contracts**

Run the three tests from Task 1. At this point host/overlay assertions should pass; MainHook and stroke-cache assertions may still fail until Tasks 3–4.

- [ ] **Step 4: Commit the host collapse**

```bash
git add src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java
git rm src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java
git commit -m "refactor: move sharp glass layer into host"
```

### Task 3: Rewire MainHook to assemble one child

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Consumes: `DockLiquidGlassHostView.setLayers(DockLiquidGlassView)` and `reloadOverlay(...)`.
- Produces: runtime assembly with no `DockStrokeOverlayView` instance.

- [ ] **Step 1: Change only `installLiquidGlassLayer(...)` assembly**

Replace the three-view construction with:

```java
DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
host.setId(View.generateViewId());
host.setLayers(glass);

float radius = bgR;
try {
    Object value = HookUtil.getField(background, "mCornerRadius");
    if (value instanceof Float) radius = (Float) value;
} catch (Throwable ignored) {}
host.setGeometry(radius, squircle, squircleCp);
host.reloadOverlay(config.dock, config.glass);
```

Do not alter insertion index, layout params, sync hooks, lifecycle hooks, or capture hooks.

- [ ] **Step 2: Run the two-view contracts**

Run the Task 1 test command. Expected after Tasks 2–3: only stroke-geometry-cache expectations may remain failing.

- [ ] **Step 3: Commit the assembly change**

```bash
git add src/main/java/com/hellovoid/liquiddock/MainHook.java
git commit -m "refactor: assemble liquid glass with one child"
```

### Task 4: Cache stroke ring geometry without changing stroke semantics

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java`

**Interfaces:**
- Consumes: existing `StrokeDrawable.Style`, radius, bounds.
- Produces: `geometryDirty`, `geometryValid`, and `ensureGeometry(Style, Rect)`.

- [ ] **Step 1: Port only the geometry-cache portion of `4fc9875`**

Add:

```java
private boolean geometryDirty = true;
private boolean geometryValid;
```

Mark dirty from `setStyle`, `setRadius`, and `onBoundsChange`. Move outer/inner path construction into:

```java
private boolean ensureGeometry(Style s, Rect bounds)
```

`draw(Canvas)` must call `ensureGeometry` before applying the existing ring-only clip/draw semantics. Do not change stroke style calculations.

- [ ] **Step 2: Run all composition contracts**

Run the Task 1 test command. Expected: PASS.

- [ ] **Step 3: Commit stroke cache**

```bash
git add src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java
git commit -m "perf: cache dock stroke geometry"
```

### Task 5: Verify no capture-policy regression

**Files:**
- Verify only; no planned production edits.

**Interfaces:**
- Consumes: Stage 1 tests and full Gradle build.
- Produces: evidence that composition changes did not alter capture-source eligibility.

- [ ] **Step 1: Run Stage 1 and composition tests together**

```bash
./gradlew testDebugUnitTest \
  --tests 'com.hellovoid.liquiddock.CaptureSourcePolicyTest' \
  --tests 'com.hellovoid.liquiddock.RecentsCaptureConfirmationContractTest' \
  --tests 'com.hellovoid.liquiddock.DynamicLiquidHighlightContractTest' \
  --tests 'com.hellovoid.liquiddock.LiquidGlassLayerContractTest' \
  --tests 'com.hellovoid.liquiddock.TwoViewCompositionContractTest'
```

- [ ] **Step 2: Run full unit tests and build**

```bash
./gradlew testDebugUnitTest assembleDebug
```

Expected: exit code 0 for both commands.

- [ ] **Step 3: Diff guard**

Confirm the production diff for this stage contains only:

```text
DockLiquidGlassHostView.java
DockStrokeOverlayView.java (deleted)
DockStrokeRenderer.java
MainHook.java
```

and that `CaptureSourcePolicy.java` and `DockLiquidGlassView.java` are unchanged from Stage 1 HEAD `2a2c61666c122f3ccfc56248888370497b0abc67`.
