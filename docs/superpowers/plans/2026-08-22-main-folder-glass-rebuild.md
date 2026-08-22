# Main-based Folder Liquid Glass Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild folder liquid glass directly on current `main`, preserve `main` Dock/workstation behavior unchanged, add Launcher-only highlight component controls for folders/future widgets, and clean the settings ownership/copy requested by the user.

**Architecture:** `ModuleMain` installs a new folder-only Launcher glass hook independently of `MainHook`'s Dock material path. Folder rendering owns a separate Launcher GPU session but reuses the exact portable `PrismalRenderer`/`PrismalParams` optical model already used by Dock. `PrismalRenderer` gains an optional per-render component mask while its existing three-argument `render(...)` remains the all-components-on compatibility path used by Dock. Launcher preferences are read only by folder/future-widget renderers; `Miuix307MaterialPipeline`, `MiuixGlassHook`, `Miuix307ZeroCopyRenderer`, and workstation ownership/geometry code remain outside this feature.

**Tech Stack:** Android Java/Kotlin, libxposed API 101, OpenGL ES/EGL, `TextureView`, existing `:prismal` Android library, Jetpack Compose + Miuix preferences, JUnit 4 source/contract tests, GitHub Actions `API101 migration build`.

**Spec:** `docs/superpowers/specs/2026-08-22-main-folder-glass-rebuild-design.md`

## Global Constraints

- Work only on `feat/main-folder-glass-rebuild`, whose base is `main` commit `a9054bda471218f8a3037fd7ddf8972287d7120a`.
- Do not cherry-pick the experimental `feat/shared-launcher-glass-session` implementation commits wholesale. It is reference material only.
- Do not import `ci/workstation_transition_ownership_transform.py` or any experimental Launcher/Dock shared material ownership transform.
- Do not change `Miuix307MaterialPipeline`, `MiuixGlassHook`, `Miuix307ZeroCopyRenderer`, `WorkstationDockGeometryHook`, or workstation transition behavior unless a test proves an unavoidable compile-only adaptation; if such a need appears, stop and re-evaluate architecture first.
- Folder background source is wallpaper-only GPU content. Never capture folder/icon output, never use screen recording, never use CPU bitmap readback, and never call `glReadPixels`.
- Folder and future widget glass use the same Prismal optical model and the same `LiquidDockConfig.Glass` optical parameters as Dock. There is no second optical model.
- Launcher highlight preferences must not be visible to Dock call sites. Existing Dock `PrismalRenderer.render(texture, geometry, params)` semantics remain all components enabled.
- Do not expose a non-functional widget glass switch/page in this rebuild. Widget rendering is future work.
- Preserve `ConfigSchema.Dock.CORNER_OFFSET` storage/runtime semantics; move only its UI ownership.
- All new user-visible strings must be localized in English and Simplified Chinese resources. The Liquid Glass page must not expose `Prismal`, `PassBlur`, `OES`, `zero-copy`, FBO, SurfaceFlinger, bitmap-capture, or other implementation vocabulary.
- Every production change follows RED -> minimal implementation -> GREEN -> commit. Existing `main` workstation/Dock contracts must stay green after every task.

---

### Task 1: Lock the Main Baseline and Protected Boundaries

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/MainFolderGlassIsolationContractTest.java`
- Preserve unchanged: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Preserve unchanged: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Preserve unchanged: `src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java`
- Preserve unchanged: workstation sources/tests

- [ ] **Step 1: Record exact protected-source hashes/behavior at the main-derived baseline**

The contract should assert architectural ownership rather than hard-code a whole-file SHA. Require:

```java
String module = read("ModuleMain.java");
String pipeline = read("Miuix307MaterialPipeline.java");
String glass = read("MiuixGlassHook.java");
String zeroCopy = read("Miuix307ZeroCopyRenderer.java");

assertTrue(module.contains("new MainHook().install(classLoader);"));
assertTrue(pipeline.contains("installWorkstationResumeProducerRecovery(classLoader);"));
assertTrue(glass.contains("Miuix307ZeroCopyRenderer.clear();"));
assertTrue(zeroCopy.contains("new Miuix307PassBlurTextureView("));
assertFalse(module.contains("workstation_transition_ownership_transform"));
```

Also assert the new folder feature, once added, must be installed from `ModuleMain` rather than by mutating Dock ownership:

```java
assertFalse(pipeline.contains("MiuixFolderGlassHook"));
assertFalse(glass.contains("MiuixFolderGlassHook"));
assertFalse(zeroCopy.contains("MiuixFolderGlassHook"));
```

- [ ] **Step 2: Run the untouched baseline suite**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected before the intentionally failing future-hook assertion is added: all existing `main` tests and assembly pass. Save the result in the task commit message/body or verification notes.

- [ ] **Step 3: Add the future integration assertion and verify RED**

Require `ModuleMain` to contain:

```java
MiuixFolderGlassHook.install(classLoader, runtimeConfig);
```

Expected RED: only the new folder integration assertion fails because no folder hook exists on `main`.

- [ ] **Step 4: Do not implement the hook yet; commit the RED contract separately**

Commit message:

```text
test: lock main boundaries for folder glass rebuild
```

This commit is the bisectable proof that the feature starts from `main` without experimental workstation ownership code.

---

### Task 2: Build the Folder Wallpaper-only GPU Glass Core

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassGpuAtlas.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java`
- Create: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Create: `src/test/java/com/hellovoid/liquiddock/FolderWallpaperOnlySourceContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/MainFolderGlassIsolationContractTest.java`

**Interfaces:**
- `MiuixFolderGlassHook.install(ClassLoader, LiquidDockConfig)` is the only Launcher hook entry point.
- `LauncherGlassSession` owns one Launcher-root wallpaper/background producer/session and an atlas of folder outputs.
- `LauncherGlassSinkView` is a passive `TextureView`/surface consumer attached to a folder material owner.
- `LauncherGlassSession` converts the wallpaper-only GPU source into an ordinary 2D texture and calls the existing portable Prismal model; no folder View tree is replayed into the backdrop.

- [ ] **Step 1: Write failing wallpaper-only contracts**

Require production source to contain explicit wallpaper-only semantics and absence of recursive scene replay:

```java
assertTrue(session.contains("wallpaper-only"));
assertTrue(session.contains("PrismalRenderer"));
assertFalse(session.contains("HardwareRenderer"));
assertFalse(session.contains("RenderNode"));
assertFalse(session.contains("RecordingCanvas"));
assertFalse(session.contains("draw(" + "launcherRoot"));
assertFalse(session.contains("glReadPixels"));
assertFalse(session.contains("Bitmap"));
```

Require the folder hook to bind only folder material views and never HotSeats/Dock owners:

```java
assertTrue(folder.contains("com.miui.home.launcher.FolderIcon"));
assertFalse(folder.contains("HotSeats"));
assertFalse(folder.contains("DockContainer"));
assertFalse(folder.contains("Miuix307MaterialPipeline"));
assertFalse(folder.contains("MiuixGlassHook"));
assertFalse(folder.contains("Miuix307ZeroCopyRenderer"));
```

Require `ModuleMain` integration after `MainHook` installation:

```java
int mainHook = module.indexOf("new MainHook().install(classLoader);");
int folderHook = module.indexOf("MiuixFolderGlassHook.install(classLoader, runtimeConfig);");
assertTrue(mainHook >= 0 && folderHook > mainHook);
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace` and verify RED**

Expected: new source files/integration are absent.

- [ ] **Step 3: Implement the minimum folder session**

Reconstruct the proven old branch behavior semantically rather than through compressed transforms:

1. Hook folder icon/material creation or `setIconImageView` at its concrete MIUI class boundary.
2. Resolve only the folder material View.
3. Attach one `LauncherGlassSinkView` to that material View.
4. Maintain a weakly-owned Launcher session/atlas so multiple folders do not each create a full independent wallpaper producer.
5. Background submitted to Prismal must be the launcher wallpaper/background GPU source only. Do not composite Launcher children/folder icons into it.
6. Use `Miuix307PrismalMaterial.fromConfig(config.glass, density)` plus `Miuix307PrismalAdapter.toPortable(...)` so folder optical parameters match Dock exactly.
7. Keep all View ownership via `WeakReference`/`WeakHashMap`; no static strong View references.
8. Make output detach idempotent and reject work once `shuttingDown` is true.

Do not add or touch Dock ownership code to make the folder path work.

- [ ] **Step 4: Run tests and assembly; verify GREEN**

Run:

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Expected: all existing main tests plus folder source/isolation contracts pass.

- [ ] **Step 5: Commit**

Commit message:

```text
feat: rebuild wallpaper-only folder glass on main
```

---

### Task 3: Restore Folder Startup, Material Refresh, and Lifecycle Safety

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java`
- Create: `src/test/java/com/hellovoid/liquiddock/FolderMaterialRefreshContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/FolderStartupAttachRecoveryContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/FolderGlassLifecycleContractTest.java`

- [ ] **Step 1: Port the known failing contracts first**

Material refresh contract:

```java
int branch = source.indexOf("if (existing != null && existing.getParent() == parent)");
int returned = source.indexOf("return existing;", branch);
assertTrue(source.indexOf("clearVendorBlur(material);", branch) < returned);
assertTrue(source.indexOf("makeMaterialTransparent(material);", branch) < returned);
```

Startup recovery contract:

```java
assertTrue(source.contains("MAX_STARTUP_RECOVERY_FRAMES"));
assertTrue(source.contains("if (sink == null && icon.isAttachedToWindow())"));
assertTrue(source.contains("scheduleFolderRecovery(icon, glassConfig, 0)"));
assertTrue(source.contains("postOnAnimation"));
assertTrue(source.contains("if (sink == null && attempt < MAX_STARTUP_RECOVERY_FRAMES)"));
```

Lifecycle contract must reject the previously observed dead-thread pattern. Require:

```java
assertTrue(session.contains("if (shuttingDown) return"));
assertTrue(session.contains("renderThread.quitSafely()"));
assertTrue(session.contains("removeCallbacksAndMessages"));
assertFalse(session.contains("renderHandler.post") && session.contains("after quit")); // implement as bounded source-section assertion
```

Also require output detach/removal to be safe when called more than once.

- [ ] **Step 2: Run tests and verify RED**

Expected: minimal Task-2 core lacks at least startup retry/material fast-path/lifecycle guards.

- [ ] **Step 3: Implement material refresh**

On both new attach and existing-sink fast paths:

```java
clearVendorBlur(material);
makeMaterialTransparent(material);
```

Do this before returning the existing sink so a vendor refresh cannot repaint the original folder background over glass.

- [ ] **Step 4: Implement bounded startup recovery**

Use the proven pattern:

```java
private static final int MAX_STARTUP_RECOVERY_FRAMES = 24;
```

Track pending folders in a synchronized `WeakHashMap`. If `setIconImageView` runs after the FolderIcon is attached but before a stable root/surface exists, schedule `postOnAnimation` retries. Stop on successful bind, detach, GC, or attempt exhaustion. Never create a temporary 0x0 root session as fallback.

- [ ] **Step 5: Make session teardown deterministic**

Before quitting the render thread:

1. mark `shuttingDown = true`;
2. detach output bookkeeping and frame callbacks;
3. clear pending render callbacks where safe;
4. release EGL/surface resources on the owning thread;
5. quit the thread once;
6. make later `requestFrame`, detach, or refresh calls no-op instead of posting to a dead Handler.

`requestLifecycleRefresh()` should only request a wallpaper-backed frame; it must not reintroduce Launcher scene replay.

- [ ] **Step 6: Run tests and assembly; verify GREEN**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] **Step 7: Commit**

Commit message:

```text
fix: make folder glass startup and lifecycle deterministic
```

---

### Task 4: Add Per-instance Launcher Highlight Component Controls Without Changing Dock

**Files:**
- Create: `prismal/src/main/java/com/hellovoid/prismal/PrismalComponentMask.java`
- Create: `prismal/src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java`
- Modify: `prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassComponentPreferences.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherComponentIsolationContractTest.java`
- Add/modify `prismal` unit/source contracts as needed.

**Interfaces:**

Keep the current Dock API exactly:

```java
public int render(int backgroundTexture2D, PrismalGeometry geometry, PrismalParams params)
```

It delegates to:

```java
return render(backgroundTexture2D, geometry, params, PrismalComponentMask.ALL_ENABLED);
```

Add Launcher-capable overload:

```java
public int render(
    int backgroundTexture2D,
    PrismalGeometry geometry,
    PrismalParams params,
    PrismalComponentMask components)
```

- [ ] **Step 1: Write failing renderer-isolation tests**

Require:

```java
assertTrue(renderer.contains("PrismalComponentMask.ALL_ENABLED"));
assertTrue(renderer.contains("PrismalComponentMask components"));
assertTrue(renderer.contains("PrismalComponentGateShader.apply"));
```

Require Dock production code to remain on the three-argument API and never read Launcher component preferences:

```java
assertFalse(read("Miuix307PassBlurTextureView.java").contains("LauncherGlassComponentPreferences"));
assertFalse(read("Miuix307ZeroCopyRenderer.java").contains("LauncherGlassComponentPreferences"));
assertFalse(read("Miuix307MaterialPipeline.java").contains("LauncherGlassComponentPreferences"));
assertFalse(read("MiuixGlassHook.java").contains("LauncherGlassComponentPreferences"));
```

Require `LauncherGlassSession` to be the consumer of the component mask.

- [ ] **Step 2: Run tests and verify RED**

Expected: mask/overload do not exist.

- [ ] **Step 3: Implement immutable component mask**

Fields:

```text
skyHaze
specular
litRim
oppositeRim
cornerRim
faceSheen
plainHighlight
caustics
pressGlow
compactSafeHighlight
```

`ALL_ENABLED` must preserve current Dock shader behavior. Launcher defaults preserve the previously verified compact folder baseline: standard highlight subcomponents off, compact-safe highlight on, unless an existing persisted Launcher preference says otherwise.

- [ ] **Step 4: Gate only highlight terms, not the optical model**

`PrismalComponentGateShader` transforms the final corrected fragment source by adding component uniforms/terms around:

- sky haze mix;
- dual specular contribution;
- lit rim;
- opposite rim;
- corner rim;
- face sheen;
- plain highlight;
- caustics;
- press glow;
- compact-safe edge highlight.

Do not alter refraction, blur, IOR, thickness, chromatic sampling, tint, geometry, or existing `PrismalSingleEdgeShader` corrections.

`PrismalRenderer.ensurePrograms()` should compile one compatible gated shader. On each render, upload the mask values. The old three-argument call supplies `ALL_ENABLED`, giving exact current Dock behavior.

- [ ] **Step 5: Add Launcher-only persisted preferences**

`LauncherGlassComponentPreferences` owns only Launcher-surface keys, for example:

```text
launcher_glass_component_sky_haze
launcher_glass_component_specular
...
launcher_glass_component_compact_safe_highlight
```

Do not create any `DOCK_*` component preference group. Load the mask once from the process config snapshot and pass it explicitly into the folder session. The class should be reusable later by a widget consumer without changes to Prismal.

- [ ] **Step 6: Run tests and verify GREEN including existing Prismal boundary tests**

```bash
./gradlew :prismal:testDebugUnitTest --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] **Step 7: Commit**

Commit message:

```text
feat: isolate launcher glass highlight components
```

---

### Task 5: Expose Folder Highlight Controls and Clean Settings Ownership/Copy

**Files:**
- Modify: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Modify: `src/main/res/values/strings.xml`
- Modify: `src/main/res/values-zh-rCN/strings.xml`
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherComponentGuiContractTest.java`
- Create: `src/test/java/com/hellovoid/liquiddock/SettingsOwnershipCopyContractTest.java`

- [ ] **Step 1: Write failing GUI ownership/copy tests**

Require `ConfigSchema.Dock.CORNER_OFFSET` to be absent from `dockSpecs` and present in `strokeSpecs` with `IntSection.StrokeGeometry`. Require `BLUR_CORNER_OFFSET` to remain in `dockSpecs`.

Require a Launcher/folder highlight component page reachable from Liquid Glass and no Dock component page:

```java
assertTrue(compose.contains("LauncherComponents")); // exact enum name may be FolderHighlights if cleaner
assertFalse(compose.contains("DockComponents"));
assertFalse(compose.contains("dockComponentToggles"));
```

Require both locales for all component names/summaries.

Require Liquid Glass visible UI source/resources to omit implementation vocabulary:

```text
Prismal ·
PassBlur
OES
zero-copy
FBO
SurfaceFlinger
Bitmap capture / Bitmap 捕获
Launcher Prismal
```

The About/Credits page may still name Prismal as a third-party project; the contract must scope its scan to Liquid-page titles/summaries and `liquid_*` user-facing resources rather than banning the project name globally.

- [ ] **Step 2: Run tests and verify RED**

Expected: `CORNER_OFFSET` is still on Dock page, Liquid page has implementation text, and Launcher component page is absent.

- [ ] **Step 3: Move stroke corner ownership only**

Remove:

```kotlin
IntSpec(ConfigSchema.Dock.CORNER_OFFSET, "描边圆角偏移")
```

from `dockSpecs` and add the same `ConfigKey` to `strokeSpecs` in `StrokeGeometry`. Do not rename the key or change ConfigSchema/runtime logic. Keep `BLUR_CORNER_OFFSET` on Dock.

- [ ] **Step 4: Add Launcher/folder highlight component secondary page**

The page is for **Folder / Launcher glass highlights**, not a second optical model. It edits only `LauncherGlassComponentPreferences` keys. Do not show a widget glass switch yet. Wording may note that these highlight settings are used by Launcher glass surfaces; current rendered consumer is folder glass.

- [ ] **Step 5: Rewrite Liquid Glass copy as user-facing properties**

Page summary:

```text
EN: Adjust blur, refraction, dispersion, tint, and highlight effects. Restart the launcher to apply changes.
ZH-CN: 调节模糊、折射、色散、颜色与高光效果；修改后重启桌面生效。
```

Rename titles such as `Prismal · 折射内缩` to property-only names such as `折射内缩`; similarly remove `Prismal ·` from all visible Liquid options. Rewrite summaries to explain what a control changes visually/functionally, not how the GPU backend implements it.

Change `liquid_enable_summary` and home Liquid summary so they describe liquid-glass behavior rather than capture/backend architecture.

- [ ] **Step 6: Localize all new visible strings**

Use `R.string` for the new component page, component names/summaries, page summary, and any touched Liquid-page visible text. Do not add new hard-coded Chinese strings for these controls.

- [ ] **Step 7: Run tests and assembly; verify GREEN**

```bash
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

- [ ] **Step 8: Commit**

Commit message:

```text
feat: expose folder highlights and clean settings copy
```

---

### Task 6: Full Regression, Exact-head CI, and Device-test APK

**Files:**
- Modify only if tests expose a real defect in Tasks 2-5.
- Add: `docs/superpowers/verification/2026-08-22-main-folder-glass-rebuild.md`

- [ ] **Step 1: Re-run the complete local/CI-equivalent verification gates**

```bash
./gradlew :prismal:testDebugUnitTest --stacktrace
./gradlew testDebugUnitTest --stacktrace
./gradlew assembleDebug --stacktrace
```

Also explicitly run/inspect these contracts:

```text
WorkstationDockGeometryContractTest
WorkstationDockBackgroundRecoveryContractTest
DockBottomGeometryContractTest
PrismalModuleBoundaryContractTest
FolderWallpaperOnlySourceContractTest
FolderMaterialRefreshContractTest
FolderStartupAttachRecoveryContractTest
FolderGlassLifecycleContractTest
LauncherComponentIsolationContractTest
LauncherComponentGuiContractTest
SettingsOwnershipCopyContractTest
```

- [ ] **Step 2: Diff protected Dock/workstation files against the main base**

Require no feature diff in:

```text
src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java
src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java
src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java
src/main/java/com/hellovoid/liquiddock/WorkstationDockGeometryHook.java
```

The only intentional shared optical change is inside portable `:prismal`, with the existing three-argument all-enabled Dock API preserved.

- [ ] **Step 3: Static safety scan**

New folder/Launcher production code must contain none of:

```text
glReadPixels
captureScreenAsync
MediaProjection
HardwareRenderer launcher scene replay
RenderNode launcher scene replay
workstation_transition_ownership_transform
```

Confirm no `DOCK_*` component preference keys were added.

- [ ] **Step 4: Trigger/observe exact-head `API101 migration build`**

Require on the final commit:

```text
testDebugUnitTest  success
assembleDebug      success
artifact upload    success
```

If push CI does not automatically start for connector-created commits, explicitly dispatch the existing workflow rather than changing the workflow merely to trigger it.

- [ ] **Step 5: Download and verify the CI artifact**

Extract the debug APK and compute/report:

```text
GitHub artifact digest
APK SHA-256
final commit SHA
```

- [ ] **Step 6: Write verification record and final commit if documentation changed**

The verification record must separate automated evidence from physical-device acceptance. Do not claim visual workstation/folder correctness from CI.

- [ ] **Step 7: Hand off device acceptance checklist**

Ask the user to validate with the exact CI APK:

1. Restart launcher: folders must show correct glass background immediately, with no drag needed.
2. Repeated folder open/close: no white/self-captured/recursive icon background.
3. Toggle folder highlight components and restart: only folder appearance changes; Dock remains unchanged.
4. Enable Dock size + blur + liquid-glass master; repeatedly enter/leave workstation: native workstation background remains visible.
5. Verify `描边圆角偏移` is under Stroke and absent from Dock.
6. Verify Liquid Glass UI contains only functional/property descriptions.

Automated completion is reached only after exact-head CI and artifact hash verification; visual acceptance remains pending until this device checklist is reported by the user.
