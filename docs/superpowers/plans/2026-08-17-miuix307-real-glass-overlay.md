# MiuiX 307 Real Glass Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ineffective MiuiX 307 local-capture refraction overlay with the device-validated architecture: native/pass-window blur remains the backdrop and the existing `DockLiquidGlassView` Prismal glass is overlaid through `DockLiquidGlassHostView`.

**Architecture:** Detect the MiuiX dock background early and mark the Launcher process as MiuiX so legacy capture lifecycle hooks cannot mutate the glass state. Extend `MiBlurBridge` with pass-window blur APIs, then build the 307 overlay with `LiquidGlassFactory.create(...)`, preserving the native MiuiX drawable, reading its runtime radius/size, and syncing width/height. Remove the experimental `Miuix307RefractionView` path from the active pipeline.

**Tech Stack:** Android View/SurfaceFlinger blur extensions, libxposed hooks, Java, JUnit source-contract tests, GitHub Actions Gradle build.

## Global Constraints

- Preserve the native `HotSeatsListContentMiuiXBlurBackground` drawable; never call `setBackground(null)`.
- Use `setPassWindowBlurEnabled` + `setMiViewBlurMode` + `setMiBackgroundBlurRadius` for realtime backdrop blur when available; fall back to existing content blur only if pass-window blur fails.
- Reuse `LiquidGlassFactory`, `DockLiquidGlassHostView`, and `DockLiquidGlassView`; do not introduce another refraction renderer.
- MiuiX mode must skip legacy capture/lifecycle hooks and `onRecentsHapticTrigger`.
- Read MiuiX runtime geometry from `mBackground`, `mWidth`, and `mHeight`, and keep the host synchronized from `setBackgroundWidth/Height/Radius`.
- Do not change workstation behavior or low-version `HotSeatsListContentBlurBackground2` behavior.
- Build/test only with GitHub Actions.

---

### Task 1: Lock the validated architecture in failing contracts

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/Miuix307GlassContractTest.java`
- Remove after replacement: `src/test/java/com/hellovoid/liquiddock/Miuix307RefractionContractTest.java`

**Interfaces:**
- Consumes: source files as text.
- Produces: contracts requiring pass-window blur, existing glass factory/host, MiuiX lifecycle isolation, and absence of the experimental refraction path from the active pipeline.

- [ ] **Step 1: Write failing tests**

Require these source-level properties:

```java
assertTrue(miBlur.contains("setPassWindowBlurEnabled"));
assertTrue(miBlur.contains("setMiBackgroundBlurRadius"));
assertTrue(pipeline.contains("LiquidGlassFactory.create"));
assertTrue(pipeline.contains("DockLiquidGlassHostView"));
assertTrue(pipeline.contains("applyPassWindowBlur"));
assertFalse(pipeline.contains("new Miuix307RefractionView"));
assertTrue(mainHook.contains("miuiXDock"));
assertTrue(mainHook.contains("if (miuiXDock) return"));
```

- [ ] **Step 2: Run `./gradlew testDebugUnitTest --stacktrace` in GitHub Actions**

Expected: RED because current `MiBlurBridge` has no pass-window blur, active 307 pipeline constructs `Miuix307RefractionView`, and `MainHook` has no MiuiX lifecycle guard.

### Task 2: Add pass-window blur bridge

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiBlurBridge.java`

**Interfaces:**
- Produces: `static boolean applyPassWindowBlur(View view, int radiusPx)` and `static void clearPassWindowBlur(View view)`.

- [ ] **Step 1: Reflect and cache** `View.setPassWindowBlurEnabled(boolean)`, `View.setMiViewBlurMode(int)`, and `View.setMiBackgroundBlurRadius(int)` independently of legacy self-blur availability.
- [ ] **Step 2: Implement** bounded radius 0..400, mode 1 on enable, mode 0/radius 0 on clear, fail closed with logging.
- [ ] **Step 3: Make `clearContentBlur` clear both legacy/self blur and pass-window blur so teardown is symmetric.

### Task 3: Restore real Prismal glass on the MiuiX background

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`

**Interfaces:**
- `MiuixGlassHook.install(View dockBg, View workspace, LiquidDockConfig config, Object launcher, ClassLoader cl)` creates exactly one glass/host pair.
- `Miuix307MaterialPipeline.install(...)` remains the 307 entry point but delegates binding to `MiuixGlassHook` instead of constructing `Miuix307RefractionView`.

- [ ] **Step 1: Apply pass-window blur** using `config.glass.blur * density`; on failure call `MiBlurBridge.applyContentBlur(dockBg, blurPx, 0.5f)`.
- [ ] **Step 2: Read geometry** from MiuiX `mBackground` `GradientDrawable`, `mWidth`, and `mHeight`, with safe View/layout fallbacks.
- [ ] **Step 3: Create glass** with:

```java
DockLiquidGlassView glass = LiquidGlassFactory.create(
        dockBg, workspace, config.glass, config.dock, false, 0.58f);
DockLiquidGlassHostView host = new DockLiquidGlassHostView(parent.getContext());
host.setLayers(glass);
host.setGeometry(radius, false, 0.58f);
host.reloadOverlay(config.dock, config.glass);
```

- [ ] **Step 4: Insert host** immediately after the MiuiX background, bottom/center aligned, using runtime `mWidth/mHeight` when positive.
- [ ] **Step 5: Configure stroke** without removing/replacing the native MiuiX drawable.
- [ ] **Step 6: Hook** `setBackgroundWidth`, `setBackgroundHeight`, and `setBackgroundRadius` to keep host layout/geometry synchronized.

### Task 4: Isolate MiuiX from old lifecycle state

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`

**Interfaces:**
- Produces: `static volatile boolean miuiXDock` process capability flag.

- [ ] **Step 1: Detect** `HotSeatsListContentMiuiXBlurBackground` immediately after config/master-switch setup.
- [ ] **Step 2: Guard Recents haptic callback** with `if (miuiXDock) return;` before touching `liquidGlassView`.
- [ ] **Step 3: Guard `installLiquidGlassCaptureHooks`** so MiuiX never installs old HOME/APP/RECENTS capture lifecycle hooks.
- [ ] **Step 4: Guard overview state forwarding** wherever it calls `glass.setOverviewActive(...)`.
- [ ] **Step 5: Keep existing 307 pipeline early-return** after successful install so legacy dock background hooks remain untouched.

### Task 5: Remove obsolete experimental refraction path and verify

**Files:**
- Delete: `src/main/java/com/hellovoid/liquiddock/Miuix307RefractionView.java`
- Delete: `src/test/java/com/hellovoid/liquiddock/Miuix307RefractionContractTest.java`

**Interfaces:** none.

- [ ] **Step 1: Run GitHub Actions `testDebugUnitTest`** and require zero failures.
- [ ] **Step 2: Run GitHub Actions `assembleDebug`** and require success.
- [ ] **Step 3: Download the artifact, verify ZIP integrity and APK SHA-256.**
- [ ] **Step 4: Device validation focus:** verify the native MiuiX blur remains visible, Prismal refraction/highlight is actually visible above it, and Recents does not freeze the background.
