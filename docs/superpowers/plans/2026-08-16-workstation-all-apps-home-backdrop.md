# Workstation All Apps HOME Backdrop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make workstation All Apps a UI-only state for Dock backdrop purposes so HOME → All Apps → HOME keeps the same HOME Liquid Glass without an All Apps capture transition.

**Architecture:** `CaptureSceneState` continues recording `allAppsActive`, but records whether that UI state aliases the HOME backdrop. The existing `WorkstationWallpaperOnlyHook` compatibility bridge intercepts `DockLiquidGlassView.setAllAppsActive()` only in workstation mode, updates the scene state directly, and intentionally skips the original method's All Apps burst/capture scheduling. Normal mode proceeds through the original method unchanged; Recents remains untouched.

**Tech Stack:** Java, libxposed API101, existing LiquidDock capture state machine and workstation compatibility bridge.

## Global Constraints

- Workstation HOME behavior remains unchanged.
- Workstation All Apps must use the HOME backdrop policy.
- Do not start a workstation capture burst solely because All Apps opens or closes.
- Do not request a new backdrop solely because All Apps opens or closes.
- Keep `allAppsActive` observable as UI state.
- Recents behavior remains unchanged.
- Do not modify workstation detection, APP→HOME, Dock geometry, freeform capture, or generic capture architecture.
- Avoid whole-file replacement of the 154 KB `DockLiquidGlassView.java` through the remote contents API when the existing compatibility bridge can express the same workstation-only behavior safely.
- Do not trigger CI or build an APK; commits use `[skip ci]`.

---

### Task 1: Decouple All Apps UI state from capture scene

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/CaptureSceneState.java`

**Interfaces:**
- Consumes: `CaptureScene.HOME`, `CaptureScene.ALL_APPS`, existing lifecycle arguments in `resolve()`.
- Produces: `setAllAppsActive(boolean active, boolean useHomeBackdrop)` while preserving `allAppsActive()`.

- [ ] **Step 1: Extend All Apps state with a HOME-backdrop alias flag**

Add:

```java
private boolean allAppsUsesHomeBackdrop;
```

Replace the current setter with:

```java
void setAllAppsActive(boolean active, boolean useHomeBackdrop) {
    boolean nextUsesHomeBackdrop = active && useHomeBackdrop;
    if (allAppsActive == active && allAppsUsesHomeBackdrop == nextUsesHomeBackdrop) return;
    allAppsActive = active;
    allAppsUsesHomeBackdrop = nextUsesHomeBackdrop;

    if (useHomeBackdrop) {
        // Workstation All Apps is UI-only for Dock capture. Do not bump the capture
        // revision merely because the overlay opened/closed, and keep HOME authoritative.
        if (active && desired == CaptureScene.ALL_APPS) {
            desired = CaptureScene.HOME;
            revision++;
        }
        return;
    }

    revision++;
    if (active) {
        desired = CaptureScene.ALL_APPS;
    } else if (desired == CaptureScene.ALL_APPS) {
        desired = CaptureScene.APP;
    }
}
```

- [ ] **Step 2: Resolve workstation All Apps as HOME**

Change the All Apps branch in `resolve()` to:

```java
if (allAppsActive) {
    return allAppsUsesHomeBackdrop ? CaptureScene.HOME : CaptureScene.ALL_APPS;
}
```

Recents resolution must remain before this branch so workstation Recents still wins over the All Apps UI flag.

- [ ] **Step 3: Static verification**

Verify the resulting file contains `allAppsUsesHomeBackdrop`, the two-argument setter, and conditional HOME resolution. Verify the ordinary `useHomeBackdrop=false` path retains the previous `ALL_APPS` behavior.

---

### Task 2: Bypass All Apps capture scheduling only in workstation mode

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/WorkstationWallpaperOnlyHook.java`

**Interfaces:**
- Consumes: `DockLiquidGlassView.setAllAppsActive(boolean, View)`, private `sceneState`, private `allAppsCaptureRoot`, and `CaptureSceneState.setAllAppsActive(boolean, boolean)`.
- Produces: workstation All Apps UI state updates with no All Apps-specific burst, capture request, visibility swap, or source transition.

- [ ] **Step 1: Install a workstation-only All Apps HOME-backdrop bridge**

Add `installAllAppsHomeBackdropBridge()` to `install()` before the existing burst/suspend/capture guards:

```java
installAllAppsHomeBackdropBridge();
```

Hook the existing method:

```java
private static void installAllAppsHomeBackdropBridge() {
    try {
        HookUtil.hookMethod(DockLiquidGlassView.class,
                "setAllAppsActive", new Class<?>[]{boolean.class, View.class}, chain -> {
                    if (!MainHook.isWorkstationMode()) {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    }

                    DockLiquidGlassView glass = (DockLiquidGlassView) chain.getThisObject();
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    boolean active = Boolean.TRUE.equals(args[0]);
                    View captureRoot = args[1] instanceof View ? (View) args[1] : null;

                    if (active && captureRoot != null) {
                        HookUtil.setField(glass, "allAppsCaptureRoot", captureRoot);
                    }
                    Object sceneState = HookUtil.getField(glass, "sceneState");
                    HookUtil.invoke(sceneState, "setAllAppsActive", active, true);
                    if (!active) HookUtil.setField(glass, "allAppsCaptureRoot", null);

                    lastGlass = new WeakReference<>(glass);
                    ensureHostVisible(glass);
                    MainHook.log("[DC] workstation All Apps UI state=" + active
                            + "; keeping HOME Dock backdrop");
                    return null;
                });
    } catch (Throwable error) {
        MainHook.log("[DC] workstation All Apps HOME-backdrop bridge unavailable: " + error);
    }
}
```

Because the workstation branch does not call `chain.proceed(...)`, the original `setAllAppsActive()` cannot execute its `startWorkstationCaptureBurst("all-apps-enter/exit")`, `updateDesiredScene()`, or `requestStateCapture(...)` path. Normal mode still calls the original method unchanged.

- [ ] **Step 2: Preserve the existing workstation safety bridge**

Do not alter `installBurstHandoff()`, `installSuspendFallback()`, `installCaptureReveal()`, or `installMainSyncHostGuard()`. They continue to handle genuine HOME/Recents capture gaps and native-background fallback.

- [ ] **Step 3: Preserve Recents**

Do not intercept `setOverviewActive()` or `onWorkstationRecentsButton()`. Recents remains the only workstation UI transition with an independent live backdrop scene.

- [ ] **Step 4: Static verification**

Verify workstation `setAllAppsActive()` bypasses `chain.proceed`, while the non-workstation branch still proceeds. Verify no Mingou snapshot/live-blur policy is reintroduced.

---

### Task 3: Repository-level verification

**Files:**
- No additional production files.

- [ ] **Step 1: Inspect final source semantics**

Confirm these invariants from branch source:

```text
workstation HOME -> HOME scene
workstation All Apps active -> allAppsActive=true, resolved capture scene HOME
workstation All Apps enter/exit -> original DockLiquidGlassView method is bypassed
workstation All Apps enter/exit -> no workstation capture burst and no capture request
normal All Apps -> original ALL_APPS behavior unchanged
workstation Recents -> existing live workstation capture path unchanged
```

- [ ] **Step 2: Verify commit scope and CI suppression**

Use GitHub compare to confirm the production commit changes only `CaptureSceneState.java` and `WorkstationWallpaperOnlyHook.java`. Confirm the final commit message includes `[skip ci]` and no workflow run exists for that SHA.

No build/runtime success claim may be made until the user compiles and tests this revision on-device.