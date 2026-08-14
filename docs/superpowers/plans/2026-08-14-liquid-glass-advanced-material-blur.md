# Liquid Glass Advanced Material Blur Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a selectable MIUI/HyperOS self-blur backend for Liquid Glass with shader fallback and fix the unblurred upper-left rounded-corner region without changing the persisted user choice.

**Architecture:** Persist `liquid_blur_mode` as `shader` or `advanced_material`. A cached `MiBlurBridge` applies `View.setMiSelfBlur` when available; `DockLiquidGlassView` keeps one shader and bypasses its 40-sample kernel only while self-blur is active. A `DockLiquidGlassHostView` performs final geometry clipping after the self-blurred glass child, while `DockStrokeOverlayView` renders sharp highlight/stroke above it.

**Tech Stack:** Java/Kotlin Android, libxposed API101, Android RuntimeShader/AGSL, MIUI hidden `View.setMi*` reflection, Compose Miuix settings, JUnit4, GitHub Actions.

## Global Constraints

- Work directly on `api101-migration` as explicitly requested.
- Existing users default to `shader`; no silent backend change.
- Runtime capability failure must not write SharedPreferences or change `liquid_blur_mode`.
- Advanced material uses direct `View.setMi*`; do not depend on `HyperMaterialUtils.isEnable()`.
- Preserve capture, rotation, black-frame, grid, widget, and workstation placement behavior.
- Workstation remains experimentally unsupported; the complete Liquid Glass host must suspend together there.

---

### Task 1: Persisted blur-mode contract

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/config/ConfigCodec.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/config/PresetManager.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LiquidBlurMode.java`
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Test: `src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java`
- Test: `src/test/java/com/hellovoid/liquiddock/config/PresetManagerTest.java`
- Create test: `src/test/java/com/hellovoid/liquiddock/LiquidBlurModeTest.java`

**Interfaces:**
- Produces: `LiquidBlurMode.SHADER`, `LiquidBlurMode.ADVANCED_MATERIAL`, `LiquidBlurMode.fromPersisted(String)`, `LiquidBlurMode.persistedValue()`.
- Produces: `LiquidDockConfig.Glass.blurMode`.

- [ ] **Step 1: Write failing config tests**

Add tests asserting:

```java
assertEquals("shader", ConfigCodec.exportValues(new HashMap<>()).get("liquid_blur_mode"));

Map<String, Object> json = new HashMap<>();
json.put("liquid_blur_mode", "advanced_material");
assertEquals("advanced_material",
        ConfigCodec.importValues(json).get("liquid_blur_mode"));
```

Add `LiquidBlurModeTest` asserting missing/unknown values normalize to `SHADER` and `advanced_material` maps to `ADVANCED_MATERIAL`.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: failures because `liquid_blur_mode` and `LiquidBlurMode` do not yet exist.

- [ ] **Step 3: Implement schema/codec/runtime/UI support**

Add a STRING schema key:

```java
public static final ConfigKey<String> BLUR_MODE = string(
        "liquid_blur_mode", "shader", "shader", "shader",
        ConfigKey.ExportMode.ALWAYS);
```

Add STRING handling to `ConfigCodec.importValue`, String support to `PresetManager.applyDefault`, default preset value `liquid_blur_mode=shader`, `LiquidBlurMode`, and `LiquidDockConfig.Glass.blurMode`.

In `LiquidPage`, add:

```kotlin
StringDropdown(
    prefs,
    ConfigSchema.Glass.BLUR_MODE.name(),
    "模糊方式",
    ConfigSchema.Glass.BLUR_MODE.uiDefault(),
    listOf("标准 Shader 模糊" to "shader", "高级材质模糊" to "advanced_material"),
    masterEnabled && liquidGlass,
)
```

- [ ] **Step 4: Run tests to verify GREEN**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: all config and mode tests pass.

- [ ] **Step 5: Commit**

Commit message: `feat: add liquid glass blur mode config`

---

### Task 2: MIUI self-blur bridge and fallback policy

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LiquidBlurBackendPolicy.java`
- Create test: `src/test/java/com/hellovoid/liquiddock/LiquidBlurBackendPolicyTest.java`

**Interfaces:**
- Produces: `MiBlurBridge.applyContentBlur(View,int,float): boolean`.
- Produces: `MiBlurBridge.clearContentBlur(View): void`.
- Produces: `LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode requested, boolean capabilityApplied)`.

- [ ] **Step 1: Write failing backend-policy tests**

Test:

```java
assertEquals(LiquidBlurMode.SHADER,
        LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode.SHADER, true));
assertEquals(LiquidBlurMode.ADVANCED_MATERIAL,
        LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode.ADVANCED_MATERIAL, true));
assertEquals(LiquidBlurMode.SHADER,
        LiquidBlurBackendPolicy.activeBackend(LiquidBlurMode.ADVANCED_MATERIAL, false));
```

Also assert that the requested enum object is not mutated or replaced by the policy API.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: missing `LiquidBlurBackendPolicy`.

- [ ] **Step 3: Implement bridge and policy**

`MiBlurBridge` resolves once:

```java
View.class.getMethod("setMiSelfBlur", int.class, java.util.ArrayList.class);
View.class.getMethod("setPassTextureScale", float.class);
View.class.getMethod("setMiSelfBlurEnhanceFlag", int.class, int.class);
```

`applyContentBlur` calls self-blur with `null` color modes, texture scale `0.5f`, and enhance flag `(0x200, 0x200)`. It returns false on any reflection failure or explicit `Boolean.FALSE` texture-scale result. No preference APIs are referenced.

- [ ] **Step 4: Run tests to verify GREEN**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: policy tests pass and Android compilation accepts the bridge.

- [ ] **Step 5: Commit**

Commit message: `feat: add MIUI self blur backend bridge`

---

### Task 3: Split glass body from sharp overlay and move final clip to host

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassHostView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LiquidGlassFactory.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Create test: `src/test/java/com/hellovoid/liquiddock/LiquidGlassLayerContractTest.java`

**Interfaces:**
- `DockLiquidGlassHostView.glassView()` returns the glass child.
- `DockLiquidGlassHostView.setGeometry(float radius, boolean squircle, float cp)` owns final clipping.
- `DockLiquidGlassHostView.reloadOverlay(LiquidDockConfig.Dock, LiquidDockConfig.Glass)` updates sharp overlay appearance.
- `DockLiquidGlassView.setBlurMode(LiquidBlurMode)` selects requested backend without touching preferences.
- `DockLiquidGlassView.setBlurRadiusPx(int)` updates shader/self-blur radius.

- [ ] **Step 1: Write failing source-contract test**

`LiquidGlassLayerContractTest` reads production sources and asserts:

```java
assertTrue(hostSource.contains("setClipToOutline(true)"));
assertTrue(glassSource.contains("shaderBlurEnabled"));
assertTrue(glassSource.contains("MiBlurBridge.applyContentBlur"));
assertFalse(glassSource.contains("setDockStrokeConfig(fullConfig.dock)"));
assertTrue(mainHookSource.contains("DockLiquidGlassHostView"));
```

Also assert both `setupViews` branches call one shared assembly helper rather than duplicating overlay creation.

- [ ] **Step 2: Run tests to verify RED**

Run: `./gradlew testDebugUnitTest --stacktrace`
Expected: host/overlay sources are absent.

- [ ] **Step 3: Implement the host and overlay**

`DockLiquidGlassHostView` is exact Dock size, clips children to the round/squircle outline, and contains glass first then overlay.

`DockStrokeOverlayView` draws the existing white diagonal LinearGradient highlight in `onDraw` and uses `DockStrokeRenderer.configure(this, dockConfig, radius)` for the configurable border.

Overlay must be non-clickable/non-focusable and must not receive self-blur.

- [ ] **Step 4: Convert DockLiquidGlassView to backend-aware drawing**

Add AGSL uniform:

```java
uniform float shaderBlurEnabled;
```

At the start of `blurred(p)`:

```java
if (shaderBlurEnabled < 0.5 || blurRadius <= 0.5) return source(p);
```

At draw time use `1f` for shader/fallback and `0f` only for an active advanced-material backend.

Move Canvas highlight and DockStrokeRenderer foreground ownership out of the glass child. When advanced material is active, draw the body over the full child rectangle and let the host perform the final rounded/squircle clip after self-blur. When shader/fallback is active, retain the existing shape clip.

- [ ] **Step 5: Wire both MainHook setup paths through one helper**

Create a shared helper that creates the factory layer, stores both host and glass references, adds the host at the old glass z-position, binds lifecycle/touch hooks to the glass child, and schedules `syncAll`.

`syncAll` must resize the host to `bgW x bgH`, call host geometry refresh, and invalidate both layers. In workstation suspension hide the complete host so overlay cannot remain visible alone.

- [ ] **Step 6: Run tests and build**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: both succeed.

- [ ] **Step 7: Commit**

Commit message: `feat: add advanced material liquid blur rendering`

---

### Task 4: Documentation and exact-head verification

**Files:**
- Modify: `README.md`
- Modify: `HOOKS.md`
- Modify: `FEATURES.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update documentation**

Document the two Liquid Glass blur modes, runtime-only fallback semantics, direct `View.setMi*` bridge, host/body/overlay layering, and the upper-left corner fix. Do not claim workstation support.

- [ ] **Step 2: Run full verification**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Then verify the exact `api101-migration` HEAD GitHub Actions run succeeds and uploads `LiquidDock-api101-debug`.

- [ ] **Step 3: Commit**

Commit message: `docs: document advanced material liquid blur`
