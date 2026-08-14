# Dynamic Liquid Highlight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the existing single-pass dynamic specular/rim/caustic lighting in Shader blur mode while rendering the same sharp geometric highlight in `DockStrokeOverlayView` when the active backend is MIUI advanced-material self-blur.

**Architecture:** `DockLiquidGlassView` owns blur-backend selection and reports the active backend to `DockLiquidGlassHostView`. The glass RuntimeShader receives a `highlightEnabled` uniform so only the Shader/fallback backend adds dynamic highlight internally. `DockStrokeOverlayView` replaces its static Canvas `LinearGradient` with a second RuntimeShader that computes the same geometric specular/rim/caustic model and is enabled only when the active backend is `ADVANCED_MATERIAL`.

**Tech Stack:** Android Java, AGSL `RuntimeShader`, `BlendMode.PLUS`, libxposed launcher integration, JUnit source/contract regression tests, Gradle 9.6.1 / Android debug build.

## Global Constraints

- Work directly on `api101-migration`.
- Preserve existing preference keys and JSON compatibility; add no new preference key for this feature.
- Preserve `liquid_blur_mode` fallback semantics: runtime failure falls back to Shader without rewriting the saved `advanced_material` preference.
- Do not alter capture scheduling, grid/widget placement, Dock sizing, workstation behavior, or MIUI self-blur capability detection.
- Do not add Prismal background reflection sampling (`reflSample`) in this change.
- Standard Shader mode must remain one highlight pass; no duplicate overlay highlight.
- Overlay highlight must be clipped with `DockShapePath`, use `RuntimeShader` and `BlendMode.PLUS`, and keep `DockStrokeRenderer` sharp above the glass.
- `liquid_highlight_alpha` remains functional in both blur backends; `liquid_highlight_width` remains on the existing glass/Fresnel path.

---

### Task 1: Lock Backend Highlight Routing

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`

**Interfaces:**
- Produces: `DockLiquidGlassView.ActiveBlurBackendListener#onActiveBlurBackendChanged(LiquidBlurMode mode)`
- Produces: `DockLiquidGlassView#setActiveBlurBackendListener(ActiveBlurBackendListener listener)`
- Consumes: existing `LiquidBlurMode.SHADER` / `LiquidBlurMode.ADVANCED_MATERIAL`

- [ ] **Step 1: Write the failing contract tests**

Add assertions that production source contains:

```java
assertTrue(glass.contains("uniform float highlightEnabled;"));
assertTrue(glass.contains("refraction.setFloatUniform(\"highlightEnabled\""));
assertTrue(glass.contains("ActiveBlurBackendListener"));
assertTrue(glass.contains("onActiveBlurBackendChanged(activeBlurBackend)"));
assertTrue(host.contains("setActiveBlurBackend"));
```

Also assert the glass shader does not unconditionally add all highlight terms after the new gate:

```java
assertTrue(glass.contains("if(highlightEnabled>0.5)"));
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace` and verify RED**

Expected: `DynamicLiquidHighlightContractTest` fails because `highlightEnabled` and the active-backend callback do not exist.

- [ ] **Step 3: Implement minimal routing**

In `DockLiquidGlassView`:

```java
interface ActiveBlurBackendListener {
    void onActiveBlurBackendChanged(LiquidBlurMode mode);
}

private ActiveBlurBackendListener activeBlurBackendListener;

void setActiveBlurBackendListener(ActiveBlurBackendListener listener) {
    activeBlurBackendListener = listener;
    if (listener != null) listener.onActiveBlurBackendChanged(activeBlurBackend);
}
```

Add AGSL:

```java
+ "uniform float highlightEnabled;"
```

Wrap only the final `specP/rim/caust` color additions:

```agsl
float3 hl = specP*float3(0.99,0.993,1.0)
          +float3(0.98,0.992,1.008)*rimLitSide
          +float3(0.952,0.968,1.018)*rimOpposite
          +caust*float3(1.0,0.96,0.90);
if(highlightEnabled>0.5){color+=hl*highlightAlpha;}
```

Add `uniform float highlightAlpha;` and feed it from the existing `liquid_highlight_alpha` runtime field. In `updateBlurBackend()`, after updating `activeBlurBackend`, notify the listener only when the active mode changed.

In `DockLiquidGlassHostView.setLayers(...)`, attach a listener that forwards the active mode to the overlay:

```java
glass.setActiveBlurBackendListener(mode -> {
    if (overlayView != null) overlayView.setActiveBlurBackend(mode);
});
```

- [ ] **Step 4: Run unit tests and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java
git commit -m "feat: route liquid highlight by active blur backend"
```

---

### Task 2: Replace Static Overlay Highlight with RuntimeShader

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java`

**Interfaces:**
- Consumes: `setActiveBlurBackend(LiquidBlurMode mode)` from Task 1.
- Produces: `setHighlightParams(float normalStrength, float dome, float specularSharp, float specularStrength, float rimLight, float caustics, float edgeBand, float highlightAlpha)`.

- [ ] **Step 1: Extend the failing test**

Assert:

```java
assertTrue(overlay.contains("RuntimeShader"));
assertTrue(overlay.contains("BlendMode.PLUS"));
assertTrue(overlay.contains("DockShapePath.build"));
assertTrue(overlay.contains("canvas.clipPath(shape)"));
assertTrue(overlay.contains("specP"));
assertTrue(overlay.contains("rimLitSide"));
assertTrue(overlay.contains("caust"));
assertFalse(overlay.contains("LinearGradient"));
assertTrue(overlay.contains("setActiveBlurBackend"));
assertTrue(overlay.contains("setHighlightParams"));
```

- [ ] **Step 2: Run unit tests and verify RED**

Expected: the new overlay assertions fail because the current implementation still uses `LinearGradient`.

- [ ] **Step 3: Implement the dynamic overlay**

Replace the static gradient with a `RuntimeShader HIGHLIGHT_SHADER` containing the geometric subset of `REFRACTION_SHADER`: shape SDF, height/dome blend, `gradH`, `N`, `specP`, `rimLitSide`, `rimOpposite`, and `caust`.

Use these uniforms:

```text
size, offset, cornerRadii, refractionHeight,
liquidDome, normalStrength,
specularSharp, specularStrength,
rimLight, causticStrength, edgeBand,
highlightAlpha
```

Return premultiplied-compatible output:

```agsl
float3 hl = clamp((spec + rim + caustic) * highlightAlpha, 0.0, 1.0);
float a = max(hl.r, max(hl.g, hl.b));
return half4(hl, a);
```

Configure the paint once:

```java
highlightPaint.setShader(highlightShader);
highlightPaint.setBlendMode(BlendMode.PLUS);
```

Draw only when `activeBlurBackend == LiquidBlurMode.ADVANCED_MATERIAL` and `highlightAlpha > 0`. Build and clip the same shape before drawing the full rectangle:

```java
DockShapePath.build(shape, w, h, radius, squircle, squircleCp);
int save = canvas.save();
canvas.clipPath(shape);
canvas.drawRect(0f, 0f, w, h, highlightPaint);
canvas.restoreToCount(save);
```

Keep `DockStrokeRenderer.configure(...)` unchanged as the foreground stroke path.

- [ ] **Step 4: Run unit tests and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java \
        src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java
git commit -m "feat: render advanced liquid highlight with RuntimeShader"
```

---

### Task 3: Hot Reload, Docs, and Exact-Head Verification

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java`
- Modify: `README.md`
- Modify: `FEATURES.md`
- Modify: `HOOKS.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- `DockLiquidGlassHostView.reloadOverlay(LiquidDockConfig.Dock dock, LiquidDockConfig.Glass glass)` forwards all dynamic highlight parameters on each existing appearance/config reload.

- [ ] **Step 1: Add failing hot-reload assertions**

Require `DockStrokeOverlayView.reload(...)` to forward the existing Glass values:

```java
assertTrue(overlay.contains("glass.normalStrength"));
assertTrue(overlay.contains("glass.dome"));
assertTrue(overlay.contains("glass.specularSharp"));
assertTrue(overlay.contains("glass.specularStrength"));
assertTrue(overlay.contains("glass.rimLight"));
assertTrue(overlay.contains("glass.caustics"));
assertTrue(overlay.contains("glass.edgeBand"));
assertTrue(overlay.contains("glass.highlightAlpha"));
```

Require the glass path to load `highlightAlpha` and feed its shader uniform.

- [ ] **Step 2: Run unit tests and verify RED if forwarding is incomplete**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: only the newly added forwarding assertions fail if Task 2 does not already satisfy them.

- [ ] **Step 3: Complete hot reload**

Update `DockStrokeOverlayView.reload(...)` so it calls `setHighlightParams(...)` with the `LiquidDockConfig.Glass` values. Keep setters value-sensitive so unchanged 1 Hz reloads do not invalidate unnecessarily.

Update the existing `DockLiquidGlassView` appearance reload to maintain a runtime `glassHighlightAlpha` and set:

```java
refraction.setFloatUniform("highlightAlpha", glassHighlightAlpha);
refraction.setFloatUniform("highlightEnabled",
        activeBlurBackend == LiquidBlurMode.SHADER ? 1f : 0f);
```

When active backend changes, invalidate the glass and notify the overlay in the same UI-thread update so one backend never shows both highlight paths.

- [ ] **Step 4: Update current documentation**

Document that:

- Shader mode keeps dynamic spec/rim/caustics inside the main RuntimeShader.
- Advanced-material mode moves those terms to the sharp overlay RuntimeShader after self-blur.
- `liquid_highlight_alpha` controls dynamic geometric-light intensity in both modes.
- Background-dependent `reflSample` is intentionally not implemented in this phase.

- [ ] **Step 5: Run full verification**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: both commands exit 0.

Then verify repository text contains no `LinearGradient` in `DockStrokeOverlayView`, no temporary patch script/workflow write permissions, and no new config keys.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/DynamicLiquidHighlightContractTest.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java \
        src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java \
        README.md FEATURES.md HOOKS.md ARCHITECTURE.md CHANGELOG.md
git commit -m "docs: describe backend-aware dynamic liquid highlight"
```

- [ ] **Step 7: Verify exact-head CI**

Use the `API101 migration build` workflow on the final `api101-migration` HEAD and require:

```text
testDebugUnitTest  success
assembleDebug      success
artifact upload    success
```

Do not claim device-level visual success until the resulting APK is tested on the HyperOS device.
