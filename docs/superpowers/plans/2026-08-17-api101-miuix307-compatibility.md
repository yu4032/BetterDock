# API101 HyperOS 3.0.307+ Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the device-validated HyperOS 3.0.307+ material/capture compatibility to API101 behind a default-off manual feature flag, preserving ordinary v1.3.0/API101 behavior when disabled.

**Architecture:** Keep `MainHook` as a thin dispatcher and isolate all 307 behavior behind `Miuix307Compatibility`. Reuse generic Liquid Glass capture/render code; introduce focused 307 material and drag adapters only for exact HyperOS classes. Native material View identity/outline/MiShadow remain, while Prismal is composed inside it only after geometry is ready.

**Tech Stack:** Android/Java/Kotlin, LSPosed API101, Xposed hooks, Jetpack Compose settings, JUnit, Gradle/GitHub Actions.

## Global Constraints

- `liquid_miuix_307_pipeline` remains the persisted key.
- UI/runtime/export defaults are `false`.
- No automatic OS-version enablement.
- Disabled mode installs zero 307-specific hooks and preserves ordinary v1.3.0/API101 paths.
- Never gate Floating Dock capture on `hasWindowFocus()`.
- No fixed startup delay; geometry-ready state drives handoff.
- Native material View alpha stays visible; only the vendor material body/GPU blur is suppressed.
- Physical optics use native material radius; configurable stroke geometry remains independent.
- Do not modify `main`; final validated result fast-forwards `api101-migration`.

---

### Task 1: Feature flag and UI contract

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Test: `src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/ConfigLoadPolicyTest.java`

**Interfaces:**
- Produces: `LiquidDockConfig.Glass.miuix307Pipeline:boolean`.
- Persisted key: `ConfigSchema.Glass.MIUIX_307_PIPELINE` -> `liquid_miuix_307_pipeline`.

- [ ] Add failing tests asserting schema/UI/runtime/export defaults are false and runtime config reads explicit true.
- [ ] Run targeted tests and verify RED because the key/field do not exist on `main`.
- [ ] Add the schema key and typed runtime field.
- [ ] Add the Liquid Glass settings switch named `HyperOS 3.0.307+ 高级材质兼容`, summary explaining manual compatibility mode, enabled only when master + Liquid Glass are enabled. Do not disable ordinary blur/capture controls merely because this flag is on.
- [ ] Re-run targeted tests and verify GREEN.
- [ ] Commit.

### Task 2: Single 307 dispatch boundary

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307Compatibility.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307FeatureGateContractTest.java`

**Interfaces:**
- Produces: `static boolean Miuix307Compatibility.install(ClassLoader, LiquidDockConfig)`.
- `MainHook` calls the facade only when `config.glass.enabled && config.glass.miuix307Pipeline`.

- [ ] Add a failing source/contract test proving `MainHook` has one gated 307 dispatch and no unconditional 307 installation.
- [ ] Run targeted test and verify RED.
- [ ] Add the facade and minimal gated dispatch without changing the ordinary path when the flag is false.
- [ ] Run targeted test and verify GREEN.
- [ ] Commit.

### Task 3: Native material in-place ownership and geometry-ready handoff

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java` only if the exact suppress/clear API is absent from main.
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307MaterialOwnershipContractTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307GeometryReadyContractTest.java`

**Interfaces:**
- `MiuixGlassHook.hasReadyNativeGeometry(View):boolean`.
- Supported exact classes: `HotSeatsListContentMiuiXBlurBackground`, `HotSeatsListContentBlurBackground2`.
- `MiuixGlassHook.install(...)` inserts the host as a child of the native material ViewGroup.

- [ ] Add failing contracts for attachment/parent/size/radius readiness; native View alpha not hidden; host parent equals native material View; native radius drives optics; vendor foreground is not layered as a second outline.
- [ ] Run targeted tests and verify RED.
- [ ] Implement exact-class material adapter using final validated semantics: defer placeholder geometry, suppress vendor GPU background blur, transparent vendor material body, preserve native View/MiShadow/outline, in-place Prismal child, native-radius clip/refraction/highlight.
- [ ] Keep LiquidDock stroke geometry independent from optical geometry.
- [ ] Re-run targeted tests and verify GREEN.
- [ ] Commit.

### Task 4: 307 geometry compatibility hooks

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307GeometryCustomizationContractTest.java`

**Interfaces:**
- Applies existing `LiquidDockConfig.Dock` widthOffset, heightOffset, spacing, bottomOffset and radius synchronization through exact 307 callbacks.

- [ ] Add failing contracts proving the 307 path does not bypass Dock customization and uses exact lifecycle hooks rather than global View hooks.
- [ ] Run targeted test and verify RED.
- [ ] Implement the minimal exact hooks needed for width/height/radius/spacing/bottom-offset synchronization and theme background replacement.
- [ ] Re-run targeted test and verify GREEN.
- [ ] Commit.

### Task 5: Transition, freeform and drag compatibility

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/Miuix307DragCaptureHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java` only for reusable callbacks already validated on the experiment branch.
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307CaptureCompatibilityTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307ThemeDragDeviceRegressionTest.java`

**Interfaces:**
- 307 GestureToHome -> `MiuixGlassHook.onHomeTransitionStart()` / existing glass HOME target callback.
- Valid drag SurfaceControl -> live capture + exclusion.
- Missing excludable SurfaceControl -> freeze last clean backdrop until drag end.

- [ ] Add failing contracts for no `hasWindowFocus()` gate, GestureToHome prearm, freeform wallpaper semantics, drag freeze/exclusion and post-drag fresh capture.
- [ ] Run targeted tests and verify RED.
- [ ] Implement the focused capture/drag adapter without broad legacy capture hooks or static guessed drag masks.
- [ ] Re-run targeted tests and verify GREEN.
- [ ] Commit.

### Task 6: Integration verification and API101 fast-forward

**Files:**
- Temporary CI workflow only if the existing workflow does not trigger for the integration branch; remove it before final ref move.

- [ ] Run `./gradlew testDebugUnitTest --stacktrace` on the integration branch and require all tests green.
- [ ] Run `./gradlew assembleDebug --stacktrace` and require success.
- [ ] Compare `main...integration` and review every production file; remove experimental-only docs/classes/dead hooks and duplicate ownership paths.
- [ ] Confirm the config default remains false and disabled-mode path is structurally ordinary API101.
- [ ] Download the debug APK and record SHA-256.
- [ ] Fast-forward `api101-migration` to the validated integration commit with `force=false`.
- [ ] Verify `main...api101-migration` now shows API101 ahead only by the intended 307 integration commits, with `main` unchanged.
