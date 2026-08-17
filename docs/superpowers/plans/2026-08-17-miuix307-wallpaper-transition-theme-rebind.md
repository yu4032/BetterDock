# MiuiX 307 Wallpaper Transition and Theme Rebind Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make visible freeform and App→HOME side-swipe transitions wallpaper-backed on MiuiX 307, and make Prismal glass self-heal after icon-theme hierarchy rebuilds.

**Architecture:** Keep the 307 specialized pipeline narrow. Freeform policy is enforced both before capture submission and at the final mode-1 gate; App→HOME signals converge on `MiuixGlassHook.onHomeTransitionStart()`; theme recovery is event-driven from actual Dock view detach/hierarchy invalidation and reuses `ensureGlassBound()` rather than rebuilding glass logic elsewhere.

**Tech Stack:** Java, Android View/SurfaceControl APIs, libxposed hook helpers, JUnit source-contract tests, Gradle/GitHub Actions.

## Global Constraints

- Do not restore the complete legacy Launcher capture/state pipeline inside MiuiX 307.
- Do not use Launcher/Floating Dock window focus as scene ownership evidence.
- Do not add periodic theme polling or fixed-delay theme detection.
- Do not make freeform task-leash resolution a requirement for wallpaper fallback.
- Do not alter workstation capture semantics as part of this change.
- Keep the previous drag-surface exclusion/retry behavior intact.

---

### Task 1: Freeform wallpaper ownership

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/FreeformCaptureExclusionTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307BackdropPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java`

**Interfaces:**
- Consumes: `FreeformLayerResolver.hasVisibleFreeformTasks()`, `FreeformTaskLeashResolver.Resolution.hasVisibleFreeformTasks()`.
- Produces: source-selection rule `APP + visible freeform -> WALLPAPER`; final gate rule `mode1 + visible freeform -> mode2`.

- [ ] **Step 1: Write failing tests**

Require the source-selection path to force wallpaper when `requestScene == CaptureScene.APP` and `hasVisibleFreeformTasks()` is true. Update the final-gate contract to require `args[5] = 2` for any visible freeform task and to reject `PASS_THROUGH_UNRESOLVED_FREEFORM` / `EXCLUDE_TASK_LEASHES` as the terminal action for visible-freeform mode-1 capture.

- [ ] **Step 2: Run RED verification**

Run CI test task on a CI-enabled carrier branch:

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: only the newly changed freeform contracts fail.

- [ ] **Step 3: Implement minimal production change**

In `DockLiquidGlassView`, before ordinary non-workstation APP full-display selection, compute the visible-freeform condition and select `CaptureSourcePolicy.Source.WALLPAPER` for APP when true.

In `FreeformCaptureLeashHook`, replace the visible-freeform branch with a fail-closed wallpaper rewrite:

```java
if (visibleFreeform) {
    args[3] = null;
    args[4] = null;
    args[5] = 2;
    action = "WALLPAPER_VISIBLE_FREEFORM";
}
```

The resolver result is still closed in `finally`; non-freeform mode-1 requests pass through unchanged.

- [ ] **Step 4: Run GREEN verification**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/FreeformCaptureExclusionTest.java \
        src/test/java/com/hellovoid/liquiddock/Miuix307BackdropPolicyTest.java \
        src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java \
        src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java
git commit -m "fix: use wallpaper while freeform is visible"
```

---

### Task 2: Side-swipe App→HOME prearm

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307MaterialPipelineContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Keep: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java`

**Interfaces:**
- Consumes: `MiuixGlassHook.onHomeTransitionStart()`.
- Produces: optional constructor hook for `com.miui.home.launcher.dock.v3.GestureToHome` that converges on the same HOME prearm as `StateNotifyUtils("toHome")`.

- [ ] **Step 1: Write failing contract test**

Add assertions requiring the 307 pipeline source to contain the exact `GestureToHome` class name, hook its declared constructors, call `MiuixGlassHook.onHomeTransitionStart()`, retain the existing `StateNotifyUtils` / `toHome` hook, and log installation failure without returning `false` from the whole pipeline.

- [ ] **Step 2: Run RED verification**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: the new 307 gesture contract fails while existing tests remain green.

- [ ] **Step 3: Implement minimal production hook**

Add a helper in `Miuix307MaterialPipeline`:

```java
private static void installHomeGesturePrearm(ClassLoader classLoader) {
    try {
        Class<?> eventClass = Class.forName(
                "com.miui.home.launcher.dock.v3.GestureToHome", false, classLoader);
        for (java.lang.reflect.Constructor<?> ctor : eventClass.getDeclaredConstructors()) {
            HookUtil.hook(ctor, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                MiuixGlassHook.onHomeTransitionStart();
                return result;
            });
        }
        MainHook.log("[DC] MiuiX 307 GestureToHome wallpaper prearm installed");
    } catch (Throwable error) {
        MainHook.log("[DC] MiuiX 307 GestureToHome prearm unavailable: " + error);
    }
}
```

Call it during successful 307 pipeline installation before `installed = true`. Do not install `LauncherSceneController` or any other legacy state hooks.

- [ ] **Step 4: Run GREEN verification**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/Miuix307MaterialPipelineContractTest.java \
        src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java
git commit -m "fix: prearm wallpaper on MiuiX home gesture"
```

---

### Task 3: Event-driven theme hierarchy rebind

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307GlassContractTest.java`
- Modify: `src/test/java/com/hellovoid/liquiddock/Miuix307MaterialPipelineContractTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java` only if a host/background detach callback must be exposed cleanly.

**Interfaces:**
- Consumes: `resolveBackground(Object hotSeats)`, `ensureGlassBound(View, LiquidDockConfig, ClassLoader)`, `MiuixGlassHook.isBoundTo(View)`.
- Produces: weak HotSeats owner reference; coalesced `scheduleHierarchyRebind()`; attach/detach sentinel that re-resolves the current active MiuiX background.

- [ ] **Step 1: Write failing tests**

Require:

```java
new java.lang.ref.WeakReference<>(hotSeats)
```

(or equivalent typed field), `View.OnAttachStateChangeListener`, a boolean/atomic coalescing latch, a one-shot `post(...)` rebind, re-resolution through `resolveBackground(...)`, and reuse of `ensureGlassBound(...)`. Assert there is no `postDelayed` retry loop for theme recovery.

Also require the valid-current-binding short circuit to remain so a theme callback cannot stack a second host.

- [ ] **Step 2: Run RED verification**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: only the new hierarchy-rebind contracts fail.

- [ ] **Step 3: Implement minimal event-driven recovery**

In `Miuix307MaterialPipeline`:

```java
private static java.lang.ref.WeakReference<Object> hotSeatsRef =
        new java.lang.ref.WeakReference<>(null);
private static boolean hierarchyRebindPosted;
```

During `setupViews`, save `mHotSeats` weakly. After a successful bind, install an `OnAttachStateChangeListener` on the active background (and, if necessary, on the injected host via a small callback exposed by `MiuixGlassHook`). On detach call `scheduleHierarchyRebind(config, classLoader)`.

The scheduler posts exactly one main-queue task via the last known background/root view, clears the coalescing latch, gets `hotSeatsRef.get()`, re-runs `resolveBackground(owner)`, and invokes `ensureGlassBound(current, config, classLoader)` when a parented current background exists. It must not poll when the hierarchy is not ready.

Ensure old listeners are removed/replaced when binding changes so detached obsolete backgrounds cannot keep scheduling repairs forever.

- [ ] **Step 4: Run GREEN verification**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/hellovoid/liquiddock/Miuix307GlassContractTest.java \
        src/test/java/com/hellovoid/liquiddock/Miuix307MaterialPipelineContractTest.java \
        src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java \
        src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java
git commit -m "fix: rebind MiuiX glass after hierarchy rebuild"
```

---

### Task 4: Full verification and APK artifact

**Files:**
- No production changes unless verification exposes a regression.

**Interfaces:**
- Consumes: Tasks 1–3 final branch head.
- Produces: passing full unit suite, successful debug APK build, CI artifact and hashes.

- [ ] **Step 1: Run full unit suite**

```bash
./gradlew testDebugUnitTest --stacktrace
```

Expected: `BUILD SUCCESSFUL` and zero test failures.

- [ ] **Step 2: Build APK**

```bash
./gradlew assembleDebug --stacktrace
```

Expected: `BUILD SUCCESSFUL` and `build/outputs/apk/debug/LiquidDock-debug.apk`.

- [ ] **Step 3: Run through CI-enabled carrier branch**

Fast-forward `demo/miuix307-material-pipeline` to the verified feature head and use `.github/workflows/api101-build.yml` to repeat tests/build and upload `LiquidDock-api101-debug`.

- [ ] **Step 4: Download artifact and calculate hashes**

Record workflow run/job IDs, artifact ID, artifact ZIP digest, and APK SHA-256. Copy the APK to `/mnt/data/` for user access if the connector download materializes a local artifact.

- [ ] **Step 5: Device verification instructions**

Collect:

```bash
adb logcat -d -v time | grep -E 'freeform capture gate|GestureToHome|HOME wallpaper|hierarchy invalidated|rebind|\[DC\]\[MG\] drag'
```

Expected device behaviors: freeform Dock uses wallpaper; side-swipe App→HOME never samples the shrinking app; icon-theme change self-heals glass; drag exclusion still works afterward.
