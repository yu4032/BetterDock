# Freeform Underlay Scene Preservation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the already-established HOME or APP Dock backdrop scene when a freeform/small-window task becomes foreground, while continuing to exclude the freeform surface from live APP captures.

**Architecture:** `LauncherSceneOwnershipPolicy` becomes a pure transparent-overlay policy: a FREEFORM foreground task preserves previous Launcher ownership when that previous ownership is known; otherwise it keeps the existing safe HOME fallback. `MainHook` passes the previous ownership and whether it was already known before applying the current focus/lifecycle signal. Capture-source selection, SurfaceFlinger capture, freeform layer exclusion, workstation All Apps, and Recents remain unchanged.

**Tech Stack:** Java, JUnit 4, libxposed API101, existing LiquidDock Launcher/capture state pipeline.

## Global Constraints

- `HOME + freeform -> HOME backdrop`.
- `APP + freeform -> APP live backdrop`.
- The freeform window itself remains excluded from the captured backdrop.
- Do not add a task-stack underlay resolver for this observed transition path.
- If previous ownership is not yet trustworthy at cold start, preserve the current safe HOME fallback.
- Do not modify `DockLiquidGlassView`, `CaptureSourcePolicy`, `FreeformLayerResolver`, `FreeformCapturePolicy`, workstation All Apps, workstation Recents, Dock geometry, or workstation detection.
- Keep the production diff minimal; do not trigger CI or build an APK for this change.
- Commits use `[skip ci]`.

---

### Task 1: Make freeform ownership transparent

**Files:**
- Modify: `src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java`

**Interfaces:**
- Consumes: current Launcher ownership signal, foreground windowing mode, previous Launcher ownership, previous-ownership-known flag.
- Produces: `static boolean launcherOwnsScene(boolean launcherSignal, int foregroundWindowingMode, boolean previousLauncherOwnership, boolean previousOwnershipKnown)`.

- [ ] **Step 1: Replace the old freeform test with failing transparent-overlay cases**

Use these policy expectations:

```java
@Test public void freeformPreservesKnownHomeOwnership() {
    assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 5, true, true));
}

@Test public void freeformPreservesKnownAppOwnership() {
    assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 5, false, true));
}

@Test public void freeformWithoutPreviousOwnershipKeepsSafeHomeFallback() {
    assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 5, false, false));
}

@Test public void fullscreenForegroundUsesCurrentLauncherSignal() {
    assertFalse(LauncherSceneOwnershipPolicy.launcherOwnsScene(false, 1, true, true));
    assertTrue(LauncherSceneOwnershipPolicy.launcherOwnsScene(true, 1, false, true));
}
```

Keep the source-contract test, but update it to require that `MainHook` passes previous ownership and known state into the policy.

- [ ] **Step 2: Verify the old production policy cannot satisfy the new tests**

The current two-argument method has no way to distinguish `HOME + freeform` from `APP + freeform`; the new four-argument calls therefore fail to compile against the old policy. This is the expected RED state.

- [ ] **Step 3: Implement the minimal pure policy**

Replace the old method with:

```java
static boolean launcherOwnsScene(boolean launcherSignal, int foregroundWindowingMode,
                                 boolean previousLauncherOwnership,
                                 boolean previousOwnershipKnown) {
    if (foregroundWindowingMode == WINDOWING_MODE_FREEFORM) {
        return previousOwnershipKnown ? previousLauncherOwnership : true;
    }
    return launcherSignal;
}
```

No Android service lookups or capture logic belong in this class.

- [ ] **Step 4: Verify the policy semantics statically**

Confirm the source contains exactly one FREEFORM branch, that it preserves known previous ownership, and that non-freeform modes return the current Launcher signal.

---

### Task 2: Pass previous base ownership from MainHook

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java`

**Interfaces:**
- Consumes: `launcherLifecycleKnown`, `launcherResumed`, current focus/resume signal, `foregroundTaskWindowingMode(...)`.
- Produces: calls to the four-argument `LauncherSceneOwnershipPolicy.launcherOwnsScene(...)` without changing capture-state APIs.

- [ ] **Step 1: Preserve previous ownership before each policy decision**

At each ownership decision, capture the state before setting `launcherLifecycleKnown = true`:

```java
boolean previousOwnershipKnown = launcherLifecycleKnown;
boolean previousLauncherOwnership = launcherResumed;
launcherLifecycleKnown = true;
```

Then call:

```java
LauncherSceneOwnershipPolicy.launcherOwnsScene(
        currentLauncherSignal, windowingMode,
        previousLauncherOwnership, previousOwnershipKnown);
```

Apply this to:

1. `seedLauncherLifecycleState(...)` when `paused instanceof Boolean`;
2. `onWindowFocusChanged(...)`;
3. fallback `Activity.onPause(...)`.

- [ ] **Step 2: Keep the direct onResume fallback authoritative**

Do not change the existing fallback `onResume` branch that explicitly sets:

```java
launcherLifecycleKnown = true;
launcherResumed = true;
```

A resumed Launcher is an authoritative HOME ownership signal and does not need the transparent-overlay policy.

- [ ] **Step 3: Update comments to match the new semantics**

Replace wording that says a freeform task means Launcher owns the scene. The comment must state that freeform is an overlay whose base HOME/APP ownership is preserved.

- [ ] **Step 4: Verify no capture-layer changes occurred**

Confirm `DockLiquidGlassView.java`, `CaptureSourcePolicy.java`, `FreeformLayerResolver.java`, and `FreeformCapturePolicy.java` are unchanged. Existing APP full-display capture and freeform exclusion must remain the rendering mechanism.

---

### Task 3: Repository-level verification

**Files:**
- No additional production files.

- [ ] **Step 1: Inspect final behavior contracts**

Confirm these invariants from source/tests:

```text
known HOME + FREEFORM -> HOME ownership
known APP  + FREEFORM -> APP ownership
unknown base + FREEFORM -> safe HOME fallback
fullscreen + focus lost -> APP ownership
fullscreen + focus gained/resumed -> HOME ownership
APP scene source selection remains FULL_DISPLAY
visible freeform layers remain excluded from full-display capture
workstation All Apps and Recents are untouched
```

- [ ] **Step 2: Inspect the production diff**

The intended production files are only:

```text
src/main/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicy.java
src/main/java/com/hellovoid/liquiddock/MainHook.java
```

The only test file changed is:

```text
src/test/java/com/hellovoid/liquiddock/LauncherSceneOwnershipPolicyTest.java
```

No reflection bridge, new state machine, underlay resolver, or `DockLiquidGlassView` special case is allowed.

- [ ] **Step 3: Verify CI suppression**

Confirm the production commit message contains `[skip ci]` and no workflow run exists for its SHA.

Because this session is intentionally not building an APK, do not claim compile/runtime success. Device validation must verify both HOME + small-window and APP + small-window behavior.