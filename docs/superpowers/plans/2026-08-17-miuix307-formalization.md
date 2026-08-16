# MiuiX 307 Material Pipeline Formalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the device-validated HyperOS 3.0.307+ demo into a source-native opt-in material pipeline while preserving the legacy capture pipeline unchanged when the switch is off or capability probing fails.

**Architecture:** Keep the manual `liquid_miuix_307_pipeline` switch as the only selector. A dedicated `Miuix307MaterialPipeline` owns 307 capability probing, native MiuiX blur refresh, geometry synchronization, highlight overlay, and Dock stroke attachment. `MainHook` delegates to it before any legacy capture hooks are installed; failed capability probing falls through to the existing legacy path without mutating persisted settings.

**Tech Stack:** Android Java/Kotlin, libxposed HookUtil, Compose Miuix settings UI, GitHub Actions Gradle build.

## Global Constraints

- `main` remains untouched until device validation of the formalized branch.
- `liquid_miuix_307_pipeline` defaults to `false`.
- Switch OFF must not probe `HotSeatsListContentMiuiXBlurBackground` and must not change legacy capture behavior.
- Switch ON + supported ROM bypasses `LiveScreenCapture`/`CaptureSceneState` installation for Liquid Glass.
- Switch ON + unsupported ROM falls back to legacy behavior without changing the saved switch.
- Native `MiuiBlurUiHelper` remains the blur owner; LiquidDock does not add high-frequency capture or refresh loops.
- This phase does not add Snell/refraction capture to the 307 pipeline.

---

### Task 1: Formalization Contract

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307FormalPipelineContractTest.java`
- Modify later: `.github/workflows/api101-build.yml`
- Modify later: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify later: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify later: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify later: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`

**Interfaces:**
- Consumes: existing opt-in behavior from the validated demo.
- Produces: a source-level contract that rejects CI source mutation and demo-only class names.

- [ ] **Step 1: Write the failing test**

Create a source contract asserting that configuration/UI/MainHook contain `MIUIX_307_PIPELINE` directly, the workflow does not invoke `patch_miuix307_demo.py`, and `MainHook` delegates to `Miuix307MaterialPipeline.install`.

- [ ] **Step 2: Run test to verify it fails**

Run in GitHub Actions: `./gradlew testDebugUnitTest --stacktrace`
Expected: FAIL because production configuration is still injected only by the CI patcher and `Miuix307MaterialPipeline` does not exist.

- [ ] **Step 3: Commit the RED contract**

Commit only the new test.

---

### Task 2: Source-Native Configuration and Pipeline Selection

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Consumes: `ConfigSchema.Glass.MIUIX_307_PIPELINE` boolean.
- Produces: `LiquidDockConfig.Glass.miuix307Pipeline` and `Miuix307MaterialPipeline.install(ClassLoader, LiquidDockConfig)` delegation.

- [ ] **Step 1: Add the persisted configuration key**

Add `MIUIX_307_PIPELINE = bool("liquid_miuix_307_pipeline", false, false, false, ALWAYS)` next to `BLUR_MODE`.

- [ ] **Step 2: Load the key into typed runtime configuration**

Add `miuix307Pipeline` to `LiquidDockConfig.Glass` and load it from the schema key.

- [ ] **Step 3: Make the Compose switch source-native**

Add the validated manual switch to `LiquidPage`; disable legacy blur backend and dynamic-capture controls while the switch is enabled.

- [ ] **Step 4: Make the runtime selector source-native**

Before legacy capture installation, call `Miuix307MaterialPipeline.install` only when Liquid Glass and the 307 switch are both enabled. Return on success; log and fall through on failure.

---

### Task 3: Promote Demo Adapter to Formal Material Pipeline

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307HighlightView.java`
- Delete: `src/main/java/com/hellovoid/liquiddock/Miuix307DemoPipeline.java`

**Interfaces:**
- Produces: `static boolean install(ClassLoader classLoader, LiquidDockConfig config)`.

- [ ] **Step 1: Move the validated hook behavior into `Miuix307MaterialPipeline`**

Retain capability probing, `Launcher.setupViews` binding, width/height/radius hooks, native `mBlurUiHelper.refreshBlur()`, geometry sync, and existing `DockStrokeRenderer.configure` behavior.

- [ ] **Step 2: Remove demo terminology from runtime logs/comments**

Use `[DC] MiuiX 307 material ...` log prefixes so device logs describe the permanent backend.

- [ ] **Step 3: Keep refresh event-driven**

Call native `refreshBlur()` only on initial bind and native width/height/radius changes. Do not add timers, pre-draw loops, capture cadence, or scene state.

---

### Task 4: Remove CI Source Mutation and Verify

**Files:**
- Delete: `.github/patch_miuix307_demo.py`
- Modify: `.github/workflows/api101-build.yml`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307DemoPipelineContractTest.java` (rename/replace if needed)

**Interfaces:**
- Produces: a normal checkout that compiles exactly the source stored in GitHub.

- [ ] **Step 1: Remove the patcher step and file**

Delete `python3 .github/patch_miuix307_demo.py` from Actions and delete the patcher itself.

- [ ] **Step 2: Run full GREEN verification**

Run in GitHub Actions: `./gradlew testDebugUnitTest --stacktrace`, then `./gradlew assembleDebug --stacktrace`.
Expected: all tests PASS and debug APK builds without source mutation.

- [ ] **Step 3: Validate artifact integrity**

Download the Actions artifact, verify its SHA-256 against GitHub's artifact digest, run ZIP integrity check on the APK, and publish the APK for device testing.
