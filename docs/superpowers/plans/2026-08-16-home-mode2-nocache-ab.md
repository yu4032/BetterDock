# HOME Mode-2 Cache A/B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build two diagnostic APKs from one baseline to isolate whether HOME `wallpaperStripCache` reuse causes the final backdrop offset: A keeps cache; B forces every HOME mode-2 request to reach SurfaceFlinger.

**Architecture:** Use one diagnostic branch and one GitHub Actions matrix job with variants `cached-A` and `nocache-B`. Both jobs checkout the same commit; only B applies a deterministic workspace-only patch that bypasses `tryServeWallpaperFromCache(...)`. Both jobs run the same unit-test and debug-assemble gates and upload separately named APK artifacts.

**Tech Stack:** Java 17, Android Gradle Plugin/Gradle wrapper already in repository, GitHub Actions, Python 3 patch helper for the B workspace-only mutation.

## Global Constraints

- Common source baseline is `fix/wallpaper-live-handoff` commit `06c930829baa57e9f4caebce56143a14c9bb3960` plus diagnostic docs/workflow only.
- A remains HOME `CaptureSourcePolicy.Source.WALLPAPER` with SurfaceFlinger vendor `captureMode(2)` and existing cache serving.
- B remains the same HOME source and same vendor `captureMode(2)`, but must never serve a HOME request from `wallpaperStripCache`.
- Do not switch either variant to mode 1.
- Do not change capture cadence, stop grace, HOME ownership, gesture-target handling, `makeCaptureRequest()`, wallpaper transform hooks, or crop geometry.
- Diagnostic changes must not be merged into `main`.
- Required outputs: `LiquidDock-home-mode2-cached-A.apk` and `LiquidDock-home-mode2-nocache-B.apk`.
- Both variants must pass `testDebugUnitTest` and `assembleDebug` before handoff.

---

### Task 1: Add a deterministic B-only no-cache patch

**Files:**
- Create: `.github/apply_home_mode2_nocache.py`
- Test: `src/test/java/com/hellovoid/liquiddock/HomeMode2NoCacheDiagnosticContractTest.java` generated only inside the B workspace by the patch helper

**Interfaces:**
- Consumes: the exact `DockLiquidGlassView.java` call site containing `tryServeWallpaperFromCache(req, requestScene, requestSceneRevision, attempt)`.
- Produces: B workspace source where the wallpaper cache serve condition is guarded by a diagnostic constant that is always false for serving, while capture mode/source selection is untouched.

- [ ] **Step 1: Write the B-only failing contract test into the patch helper**

The helper must create this source-inspection test only for B:

```java
package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class HomeMode2NoCacheDiagnosticContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"));
    }

    @Test public void diagnosticBypassesWallpaperCacheServe() throws Exception {
        String s = source();
        assertTrue(s.contains("HOME_MODE2_NOCACHE_DIAGNOSTIC"));
        assertTrue(s.contains("if (!HOME_MODE2_NOCACHE_DIAGNOSTIC"));
    }

    @Test public void diagnosticDoesNotSwitchHomeToFullDisplay() throws Exception {
        String policy = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/CaptureSourcePolicy.java"));
        assertTrue(policy.contains("if (scene == CaptureScene.HOME) return Source.WALLPAPER;"));
        assertFalse(policy.contains("if (scene == CaptureScene.HOME) return Source.FULL_DISPLAY;"));
    }
}
```

- [ ] **Step 2: Run B test before applying the source mutation and verify RED**

Run in the B matrix workspace after creating the test but before mutating production source:

```bash
./gradlew testDebugUnitTest --tests '*HomeMode2NoCacheDiagnosticContractTest' --stacktrace
```

Expected: FAIL because `HOME_MODE2_NOCACHE_DIAGNOSTIC` is absent from `DockLiquidGlassView.java`.

- [ ] **Step 3: Apply the minimal B-only source mutation**

The helper must make exactly two production-source edits:

```java
private static final boolean HOME_MODE2_NOCACHE_DIAGNOSTIC = true;
```

and change only the cache-serve conditional from:

```java
if (wallpaperMode
        && !(workstationMode && workstationCaptureBurst.isActive())
        && tryServeWallpaperFromCache(
        req, requestScene, requestSceneRevision, attempt)) {
    return;
}
```

to:

```java
if (!HOME_MODE2_NOCACHE_DIAGNOSTIC
        && wallpaperMode
        && !(workstationMode && workstationCaptureBurst.isActive())
        && tryServeWallpaperFromCache(
        req, requestScene, requestSceneRevision, attempt)) {
    return;
}
```

The helper must assert each replacement matches exactly once and abort otherwise.

- [ ] **Step 4: Run the B-only contract test and verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --tests '*HomeMode2NoCacheDiagnosticContractTest' --stacktrace
```

Expected: PASS.

- [ ] **Step 5: Commit diagnostic helper and plan-visible test generation**

```bash
git add .github/apply_home_mode2_nocache.py docs/superpowers/plans/2026-08-16-home-mode2-nocache-ab.md
git commit -m "test: add HOME mode2 no-cache diagnostic patch"
```

### Task 2: Build A and B in one matrix workflow

**Files:**
- Modify: `.github/workflows/api101-build.yml`

**Interfaces:**
- Consumes: Task 1 helper `.github/apply_home_mode2_nocache.py`.
- Produces: two jobs from one commit: `cached-A` with untouched source and `nocache-B` with the B-only patch applied in the runner workspace.

- [ ] **Step 1: Add the diagnostic branch trigger and matrix**

For branch `diagnostic/home-mode2-nocache-ab`, add:

```yaml
strategy:
  fail-fast: false
  matrix:
    variant: [cached-A, nocache-B]
```

The existing normal branches must retain their current single-build behavior; the diagnostic matrix may be scoped with job `if`/matrix logic or a dedicated diagnostic job to avoid duplicating main builds.

- [ ] **Step 2: Apply the B patch only in `nocache-B`**

Add:

```yaml
- name: Apply no-cache B diagnostic
  if: matrix.variant == 'nocache-B'
  run: python .github/apply_home_mode2_nocache.py
```

A must not execute the helper.

- [ ] **Step 3: Run full verification in both variants**

Each variant runs:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: both succeed. B includes the generated contract test; A runs the repository baseline suite unchanged.

- [ ] **Step 4: Rename APKs before upload**

Use:

```bash
if [ "${{ matrix.variant }}" = "cached-A" ]; then
  cp build/outputs/apk/debug/*.apk LiquidDock-home-mode2-cached-A.apk
else
  cp build/outputs/apk/debug/*.apk LiquidDock-home-mode2-nocache-B.apk
fi
```

- [ ] **Step 5: Upload separately named artifacts**

Use one artifact per matrix job:

```yaml
name: LiquidDock-home-mode2-${{ matrix.variant }}
path: LiquidDock-home-mode2-*.apk
```

- [ ] **Step 6: Commit workflow**

```bash
git add .github/workflows/api101-build.yml
git commit -m "ci: build HOME mode2 cache A/B"
```

### Task 3: Verify outputs and hand off device experiment

**Files:**
- No source changes.

**Interfaces:**
- Consumes: successful matrix workflow run artifacts.
- Produces: two APK files and their SHA-256 digests for device-side A/B comparison.

- [ ] **Step 1: Verify both matrix jobs completed successfully**

Check that both `cached-A` and `nocache-B` jobs show `testDebugUnitTest` success, `assembleDebug` success, and artifact upload success.

- [ ] **Step 2: Download both artifacts**

Retrieve both workflow artifacts and extract:

```text
LiquidDock-home-mode2-cached-A.apk
LiquidDock-home-mode2-nocache-B.apk
```

- [ ] **Step 3: Compute SHA-256 for both APKs**

Run:

```bash
sha256sum LiquidDock-home-mode2-cached-A.apk LiquidDock-home-mode2-nocache-B.apk
```

Record both digests in the handoff message.

- [ ] **Step 4: Device procedure**

Test A then B with the same wallpaper/orientation using both paths:

```text
APP → pull out Dock → continue swipe to HOME
Recents → HOME
```

Observe only whether the final small downward backdrop movement is followed correctly.

- [ ] **Step 5: Interpret the result without making a production change yet**

```text
A wrong / B correct  -> cache reuse is the root cause.
A wrong / B wrong    -> investigate captureMode(2) transform/coordinate semantics.
A correct / B correct -> reproduction depends on another state; gather more evidence.
```
