# MiuiX 307 Wallpaper Transition and Theme Rebind Design

Date: 2026-08-17
Branch: `fix/miuix307-drag-freeform-followup`

## Goal

Correct three MiuiX 307 Dock glass behaviors without restoring the full legacy capture pipeline:

1. When a freeform window is visible, the Dock backdrop must use wallpaper capture instead of APP live full-display capture.
2. When an app returns to HOME through the side-swipe back gesture, the Dock must switch to wallpaper at the start of the App -> HOME animation, not after the animation has already begun.
3. After changing the icon theme, the MiuiX 307 Prismal glass layer must automatically rebind if HyperOS rebuilds or detaches the Dock background/host hierarchy.

The fixes must remain narrow to the 307 material path and must not make Floating Dock window focus a prerequisite.

## Current behavior and root causes

### 1. Freeform APP capture

`CaptureSourcePolicy` maps ordinary APP scenes to `FULL_DISPLAY`. The final `FreeformCaptureLeashHook` currently treats a visible freeform task with an unresolved/late task leash as `PASS_THROUGH_UNRESOLVED_FREEFORM`, leaving mode 1 active.

That behavior is now explicitly wrong for the desired visual contract. A visible freeform window is a wallpaper-backed condition regardless of whether its remote leash can be resolved. The source must not temporarily remain APP live capture while waiting for a leash.

### 2. Side-swipe App -> HOME transition

The 307 specialized pipeline exits `MainHook.install()` early and intentionally bypasses the full legacy capture/state lifecycle. It currently restores only a `StateNotifyUtils.sendStateBroadcast(..., "toHome", ...)` hook that calls `MiuixGlassHook.onHomeTransitionStart()`.

The older generic Launcher state path also hooked construction of:

`com.miui.home.launcher.dock.v3.GestureToHome`

and forwarded that boundary to the HOME gesture target. That hook is absent from the 307 specialized pipeline. A side-swipe back-to-home gesture can therefore begin the HOME remote animation before the existing `toHome` broadcast path fires, or without that path being the earliest signal.

The missing 307 `GestureToHome` prearm is the specific lifecycle gap to restore.

### 3. Icon-theme change invalidates glass

`Miuix307MaterialPipeline` currently self-heals on:

- `Launcher.setupViews()`; and
- `setBackgroundWidth`, `setBackgroundHeight`, and `setBackgroundRadius` callbacks on `HotSeatsListContentMiuiXBlurBackground`.

This covers many background-instance swaps, but not every hierarchy mutation. A theme reload may detach the injected `DockLiquidGlassHostView` or replace the active MiuiX background without immediately invoking one of those geometry callbacks. In that state `MiuixGlassHook.isBoundTo(background)` becomes false, but nothing necessarily calls `ensureGlassBound()` again.

The missing contract is an event-driven hierarchy rebind boundary.

## Design

### A. Freeform is wallpaper-owned before capture submission

The ordinary non-workstation source-selection path in `DockLiquidGlassView` will treat:

`requestScene == APP && visibleFreeform == true`

as `CaptureSourcePolicy.Source.WALLPAPER` before a mode-1 request is submitted.

This means the expected path is:

`APP + visible freeform -> WALLPAPER -> capture mode 2`

and not:

`APP -> mode 1 -> attempt freeform leash exclusion -> maybe wallpaper`.

The final `FreeformCaptureLeashHook` remains as a race-safety backstop. If a mode-1 capture reaches the gate and the current resolution reports `visibleFreeform=true`, the gate must rewrite the request to wallpaper mode regardless of `safe` or remote-leash availability. This covers a freeform task becoming visible between source selection and final capture submission.

The old `PASS_THROUGH_UNRESOLVED_FREEFORM` behavior will be removed.

Workstation behavior remains isolated and unchanged unless its own source path explicitly reaches this gate.

### B. One HOME-transition prearm entry point

`MiuixGlassHook.onHomeTransitionStart()` remains the single semantic entry point for the 307 App -> HOME handoff.

Both of these signals will call it:

- existing `StateNotifyUtils(..., "toHome", ...)` signal;
- `com.miui.home.launcher.dock.v3.GestureToHome` constructor signal.

No capture logic is duplicated in either hook.

`onHomeTransitionStart()` continues to call `glass.setGestureCaptureTarget("HOME")`. This immediately advances the HOME scene revision, makes previously installed/in-flight APP captures stale, and causes subsequent capture selection to use wallpaper.

The required visual rule is:

> Once a confirmed App -> HOME gesture begins, the Dock may show the current wallpaper-backed Dock material, but it must not install a new APP frame containing the shrinking/returning app animation.

The `GestureToHome` hook is fail-open: if the class or constructor changes on a future launcher build, the existing `toHome` path remains installed and the material pipeline itself must still load.

### C. Event-driven theme/hierarchy rebind

The 307 pipeline will retain a weak reference to the active `HotSeats` owner observed during `Launcher.setupViews()`.

After a successful Prismal installation, the 307 path will install lightweight attach/detach lifecycle sentinels for the bound MiuiX background/host. If the injected host or its bound MiuiX background is detached, the pipeline will schedule one main-thread rebind pass for the next queue turn.

The rebind pass will:

1. Resolve the current active Dock background again from the retained HotSeats owner using `getHotSeatsBackground()` first and recursive class lookup second.
2. If no valid parented background exists yet, leave the pipeline unbound and rely on the next actual lifecycle/geometry event; do not poll on a timer.
3. If a valid background exists and `MiuixGlassHook.isBoundTo(background)` is false, call the existing `ensureGlassBound()` path.
4. On successful installation, restore all existing contracts through the normal installer: native pass-window blur, Prismal optical-only settings, HOME ownership binding, Dock stroke configuration, native-background preservation, and `Miuix307DragCaptureHook.bind(background)`.

Rebind requests will be coalesced so a theme reload that detaches several related views does not create stacked glass hosts.

No theme-package name, icon-pack name, broadcast action, or fixed delay will be used. The repair is based on the actual View hierarchy becoming invalid.

## Error handling

- Freeform source selection fails toward wallpaper, never toward full-display capture.
- `GestureToHome` hook installation failure is logged but does not disable the 307 pipeline.
- A theme rebind attempt with no parented active background is deferred to later real events rather than spinning or installing into an old hierarchy.
- Existing background instances and detached glass hosts must not remain drag-capture targets.
- No `hasWindowFocus()` gate is introduced; the Floating Dock is `FLAG_NOT_FOCUSABLE` on the target device.

## Tests

Implementation must follow RED -> GREEN.

### Freeform tests

Add/adjust contracts so they prove:

- APP with visible freeform selects or is rewritten to wallpaper.
- `PASS_THROUGH_UNRESOLVED_FREEFORM` is absent from the final gate behavior.
- a visible freeform with a valid remote leash still does not keep APP mode 1, because the desired policy is wallpaper ownership, not leash-based live capture.
- HOME remains wallpaper-backed.

### App -> HOME gesture tests

Add a 307 contract requiring:

- the specialized material pipeline hooks `com.miui.home.launcher.dock.v3.GestureToHome`;
- the hook forwards to `MiuixGlassHook.onHomeTransitionStart()`;
- the existing `StateNotifyUtils("toHome")` hook remains;
- both signals converge on the same HOME prearm method rather than implementing separate state changes.

Existing tests for HOME source selection and stale scene revisions must remain green.

### Theme rebind tests

Add contracts proving:

- the 307 pipeline retains only a weak reference to the HotSeats owner for re-resolution;
- a detach/hierarchy invalidation schedules a coalesced rebind rather than a repeating timer;
- rebind resolves the current background again and calls the existing `ensureGlassBound()` path;
- successful rebind restores `Miuix307DragCaptureHook.bind(background)`;
- no second Prismal host is installed while the current host is still valid.

## Device verification

After unit/contract tests and `assembleDebug` pass, verify on the HyperOS 3.0 Pad:

1. Open a normal app, open a freeform window, expose the Dock. Expected: wallpaper-backed glass; no app/freeform content sampled into the Dock.
2. Open a normal full-screen app, side-swipe back to HOME. Expected: at animation start the Dock changes to wallpaper-backed material and never captures the shrinking app animation.
3. Change icon theme while Launcher stays alive. Expected: the Dock Prismal glass remains active or automatically reappears after the hierarchy rebuild without restarting Launcher.
4. Drag a Dock icon afterward to ensure the previous drag-surface exclusion work still functions after a theme rebind.

Useful logs should distinguish the boundaries, for example:

- freeform wallpaper selection / gate rewrite;
- `GestureToHome -> HOME wallpaper capture target`;
- `MiuiX 307 hierarchy invalidated; rebind scheduled`;
- `MiuiX 307 background instance changed; rebinding Prismal glass`.

## Non-goals

- Do not restore the complete legacy Launcher capture/state pipeline inside MiuiX 307.
- Do not use Launcher window focus as scene ownership evidence.
- Do not add periodic theme polling.
- Do not make freeform task-leash resolution a requirement for the new wallpaper policy.
- Do not alter workstation capture semantics as part of this change.
