# HyperOS 307 Themed MiShadow Suppression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Suppress the 143px native MiShadow only for the currently bound `HotSeatsListContentBlurBackground2` under LiquidDock Prismal.

**Architecture:** `Miuix307MaterialPipeline` hooks `MiShadowUtils.applyViewShadow(...)` once. It asks `MiuixGlassHook.shouldSuppressCompatMiShadow(View)` whether the target is the exact active themed background; only then is the original skipped. Native background blur and Prismal optical-only behavior remain unchanged.

**Tech Stack:** Java, libxposed API101, Android View APIs, JUnit source-regression tests, GitHub Actions Gradle build.

## Global Constraints

- Keep `fix/miuix307-drag-freeform-followup` as the source branch.
- Do not reintroduce Dock window-focus gating.
- Do not globally disable `MiShadowUtils` or `View.setMiShadow`.
- Do not change default `HotSeatsListContentMiuiXBlurBackground` shadow behavior.
- Do not change drag/capture behavior.

---

### Task 1: Add a failing themed-MiShadow regression

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307ThemeDragDeviceRegressionTest.java`

**Interfaces:**
- Consumes: source text of `Miuix307MaterialPipeline.java` and `MiuixGlassHook.java`.
- Produces: regression `thirdPartyBackground2SuppressesOnlyItsBoundMiShadow()`.

- [ ] **Step 1: Write the failing test**

Assert that production source contains `installCompatMiShadowSuppression`, `MiShadowUtils`, `applyViewShadow`, `shouldSuppressCompatMiShadow`, exact `COMPAT_BACKGROUND_CLASS` equality, and `dockBg == backgroundRef`; also assert there is no global `View.setMiShadow` hook.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: exactly the new contract fails while existing tests remain green.

- [ ] **Step 3: Commit RED**

Commit message: `test: lock themed MiShadow suppression contract`.

### Task 2: Implement precise MiShadow interception

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`

**Interfaces:**
- Consumes: `MiuixGlassHook.backgroundRef`, `hostRef`, `COMPAT_BACKGROUND_CLASS`.
- Produces: `static boolean shouldSuppressCompatMiShadow(View dockBg)` and `installCompatMiShadowSuppression(ClassLoader)`.

- [ ] **Step 1: Add helper in `MiuixGlassHook`**

Implement `shouldSuppressCompatMiShadow(View)` to return true only when the target is the exact compat class, is identical to `backgroundRef`, and the Prismal host is still parented next to that background. Maintain a `View compatMiShadowLoggedFor` latch and log `compat BlurBackground2 MiShadow suppressed` once per bound instance.

- [ ] **Step 2: Install the process-wide utility hook**

In `Miuix307MaterialPipeline.install(...)`, call `installCompatMiShadowSuppression(classLoader)`. Resolve `com.miui.home.launcher.common.MiShadowUtils`, enumerate non-static `applyViewShadow` methods if needed or hook the exact static overload discovered in the DEX, inspect argument 0 as `View`, and return without `chain.proceed(...)` only when the helper returns true. All other calls proceed unchanged.

- [ ] **Step 3: Run full unit tests**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: all tests pass.

- [ ] **Step 4: Build APK**

Run: `./gradlew assembleDebug --stacktrace`
Expected: build succeeds.

- [ ] **Step 5: Commit GREEN**

Commit message: `fix: suppress themed Dock MiShadow`.

### Task 3: Final verification and delivery

**Files:**
- Remove temporary spec/plan files if final source diff should remain production-focused.

**Interfaces:**
- Consumes: final source branch HEAD.
- Produces: clean CI run and verified APK artifact.

- [ ] **Step 1: Compare final diff against pre-fix HEAD `393033a90f62d09df52df2fbffc0a5fb26970503`**

Expected production/test changes are limited to the MiShadow suppression implementation and regression.

- [ ] **Step 2: Push final HEAD to `demo/miuix307-material-pipeline` and run `api101-build.yml`**

Expected: `testDebugUnitTest`, `assembleDebug`, and artifact upload all succeed.

- [ ] **Step 3: Verify artifact hashes**

Download `LiquidDock-api101-debug`, compute ZIP and APK SHA-256, and compare ZIP digest to GitHub artifact metadata.

- [ ] **Step 4: Report device validation signals**

Expected themed log: one `compat BlurBackground2 MiShadow suppressed` per bound themed background instance. The repeated `MiShadowUtils.applyViewShadow ... HotSeatsListContentBlurBackground2 radius=143` / associated 143 shadow-blur generation should no longer occur for that target, while default MiuiX behavior remains unchanged.
