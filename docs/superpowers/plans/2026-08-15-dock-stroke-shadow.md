# Dock Stroke Shadow Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the existing `strokeShadow` configuration by rendering its shadow inside `DockStrokeRenderer`, using the renderer's current Dock border geometry and without recreating a standalone shadow view.

**Architecture:** Extend `DockStrokeRenderer.Style` with the already-existing stroke-shadow configuration values, scaled with the same `dock_dimensions_dp` semantics used by the historical overlay. `StrokeDrawable` will render a bounded inward shadow gradient first, using contours interpolated from its already-computed outer/inner border geometry, and then render the existing stroke ring unchanged. The shadow uses the same `buildShape()` path builder and clip/clipOut ring technique as the border, so it adds no second View, SurfaceControl, position model, or `Path.op()` dependency.

**Tech Stack:** Java, Android `Canvas`/`Paint`/`Path`/`RectF`, JUnit 4 source-contract tests, Gradle Android build.

## Global Constraints

- Restore `strokeShadow` only inside `DockStrokeRenderer`.
- Do not recreate a standalone shadow `View`, `RenderNode`, or `SurfaceControl`.
- Do not modify the normal Dock background shadow, workstation Dock geometry/shadow, liquid-glass capture, All Apps, or Recents capture policy.
- `strokeShadow=false` must leave current stroke rendering unchanged.
- Shadow and stroke must derive from the same current outer/inner Dock geometry and `buildShape()` implementation.
- The shadow is drawn before the stroke so the existing translucent stroke remains visually on top.
- Preserve the renderer invariant that the central Dock body is excluded from border-related drawing.
- Do not use `Path.op()` or force the liquid-glass host into `LAYER_TYPE_SOFTWARE`.

---

### Task 1: Restore stroke-shadow rendering in `DockStrokeRenderer`

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/DockStrokeShadowContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java`

**Interfaces:**
- Consumes: `LiquidDockConfig.Dock.strokeShadow`, `strokeShadowRadius`, `strokeShadowAlpha`, `dimensionsDp`.
- Produces: `Style.shadowEnabled`, `Style.shadowRadiusPx`, `Style.shadowAlpha`; `StrokeDrawable.drawStrokeShadow(Canvas, Style)` using the same cached border geometry as normal stroke drawing.

- [ ] **Step 1: Write the failing regression test**

Create `DockStrokeShadowContractTest.java` with source-level contracts that work in the existing plain JVM test suite:

```java
package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DockStrokeShadowContractTest {
    private static String source() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java"),
                StandardCharsets.UTF_8);
    }

    @Test public void styleConsumesExistingStrokeShadowConfig() throws IOException {
        String source = source();
        assertTrue(source.contains("final boolean shadowEnabled;"));
        assertTrue(source.contains("final float shadowRadiusPx;"));
        assertTrue(source.contains("final int shadowAlpha;"));
        assertTrue(source.contains("config.strokeShadow,"));
        assertTrue(source.contains("config.strokeShadowRadius * dimensionScale"));
        assertTrue(source.contains("config.strokeShadowAlpha"));
    }

    @Test public void shadowUsesSharedStrokeGeometryWithoutIndependentView() throws IOException {
        String source = source();
        assertTrue(source.contains("drawStrokeShadow(canvas, s);"));
        assertTrue(source.contains("buildShape(shadowOuter"));
        assertTrue(source.contains("buildShape(shadowInner"));
        assertTrue(source.contains("outerRect"));
        assertTrue(source.contains("innerRect"));
        assertFalse(source.contains("Path.Op."));
        assertFalse(source.contains("setLayerType(View.LAYER_TYPE_SOFTWARE"));
    }

    @Test public void shadowIsClippedOutOfDockInteriorAndDrawnBeforeStroke() throws IOException {
        String source = source();
        assertTrue(source.contains("canvas.clipPath(shadowOuter);"));
        assertTrue(source.contains("canvas.clipOutPath(shadowInner);"));
        int shadow = source.indexOf("drawStrokeShadow(canvas, s);");
        int stroke = source.indexOf("canvas.drawPath(outer, paint);", shadow);
        assertTrue("stroke shadow must be rendered before the foreground stroke",
                shadow >= 0 && stroke > shadow);
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.DockStrokeShadowContractTest --stacktrace
```

Expected: FAIL because `DockStrokeRenderer.Style` currently has no shadow fields and `StrokeDrawable` does not call `drawStrokeShadow`.

- [ ] **Step 3: Extend `Style` with the existing shadow configuration**

In `DockStrokeRenderer.Style`, add:

```java
final boolean shadowEnabled;
final float shadowRadiusPx;
final int shadowAlpha;
```

Extend the constructor with those three arguments, and in `Style.from(...)` append:

```java
config.strokeShadow,
Math.max(0f, config.strokeShadowRadius * dimensionScale),
Math.max(0, Math.min(255, config.strokeShadowAlpha))
```

This deliberately uses `dimensionScale`, matching the legacy overlay's `dock_dimensions_dp` scaling for `strokeShadowRadius`.

- [ ] **Step 4: Cache only shared geometry values needed to interpolate shadow contours**

In `StrokeDrawable`, add reusable paths and shared geometry metadata:

```java
private final Path shadowOuter = new Path();
private final Path shadowInner = new Path();
private float geometryThickness;
private float outerRadius;
private float innerRadius;
private float innerCp;
```

In `ensureGeometry(...)`, after the existing local geometry is validated and before returning success, assign the values already used to build `outer` and `inner`:

```java
geometryThickness = thickness;
this.outerRadius = outerRadius;
this.innerRadius = innerRadius;
this.innerCp = innerCp;
```

These are not a second geometry model; they cache the same contour parameters already computed for the stroke.

- [ ] **Step 5: Implement the bounded legacy-style shadow gradient**

Add the following helpers inside `StrokeDrawable`:

```java
private void drawStrokeShadow(Canvas canvas, Style s) {
    if (!s.shadowEnabled || s.shadowRadiusPx <= 0f || s.shadowAlpha <= 0
            || geometryThickness <= 0f) {
        return;
    }

    float reach = Math.min(s.shadowRadiusPx, geometryThickness);
    int steps = Math.max(1, Math.min(40, (int) Math.ceil(reach)));
    Paint shadowPaint = paint;
    shadowPaint.clearShadowLayer();
    shadowPaint.setShader(null);
    shadowPaint.setStyle(Paint.Style.FILL);
    shadowPaint.setColorFilter(colorFilter);

    for (int i = steps; i >= 1; i--) {
        float outerDistance = reach * (i - 1f) / steps;
        float innerDistance = reach * i / steps;
        float outerT = Math.min(1f, outerDistance / geometryThickness);
        float innerT = Math.min(1f, innerDistance / geometryThickness);

        buildInterpolatedContour(shadowOuter, outerT, s);
        buildInterpolatedContour(shadowInner, innerT, s);

        float strength = 1f - (i - 1f) / steps;
        int alpha = Math.round(s.shadowAlpha * strength * strength
                * drawableAlpha / 255f);
        shadowPaint.setColor(Color.argb(alpha, 0, 0, 0));

        int save = canvas.save();
        canvas.clipPath(shadowOuter);
        canvas.clipOutPath(shadowInner);
        canvas.drawPath(shadowOuter, shadowPaint);
        canvas.restoreToCount(save);
    }
}

private void buildInterpolatedContour(Path out, float t, Style s) {
    float clamped = Math.max(0f, Math.min(1f, t));
    RectF rect = new RectF(
            lerp(outerRect.left, innerRect.left, clamped),
            lerp(outerRect.top, innerRect.top, clamped),
            lerp(outerRect.right, innerRect.right, clamped),
            lerp(outerRect.bottom, innerRect.bottom, clamped));
    float contourRadius = lerp(outerRadius, innerRadius, clamped);
    float contourCp = lerp(s.squircleCp, innerCp, clamped);
    out.rewind();
    buildShape(out, rect, contourRadius, s.squircle, contourCp);
}

private static float lerp(float start, float end, float t) {
    return start + (end - start) * t;
}
```

Use the existing `Paint` rather than allocating a Paint per gradient band. The maximum number of bands remains 40, matching the historical shadow implementation's cap.

- [ ] **Step 6: Draw the shadow before the existing stroke ring**

In `StrokeDrawable.draw(...)`, immediately after `ensureGeometry(...)` succeeds and before configuring the existing stroke color, call:

```java
drawStrokeShadow(canvas, s);
```

Then keep the current stroke-ring code unchanged. Because the shadow bands are each clipped between two contours derived from the existing outer/inner geometry, they cannot paint the central Dock body.

- [ ] **Step 7: Run the focused test and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests com.hellovoid.liquiddock.DockStrokeShadowContractTest --stacktrace
```

Expected: PASS.

- [ ] **Step 8: Run full regression tests**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all unit/contract tests PASS, including Dock shadow, workstation, freeform capture, Recents, and liquid-glass contracts.

- [ ] **Step 9: Build the Debug APK**

Run:

```bash
./gradlew assembleDebug --stacktrace
git diff --check
```

Expected: both commands exit 0 and `build/outputs/apk/debug/*.apk` is produced.

- [ ] **Step 10: Commit verified production changes**

```bash
git add src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java \
        src/test/java/com/hellovoid/liquiddock/DockStrokeShadowContractTest.java
git commit -m "fix: restore Dock stroke shadow renderer"
```

The generated APK still requires device verification for visual strength/alignment; CI proves the configuration is consumed, the shared-geometry invariant is enforced by regression tests, and the Android project compiles.
