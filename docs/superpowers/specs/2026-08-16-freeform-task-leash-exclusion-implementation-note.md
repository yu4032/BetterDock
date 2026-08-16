# Freeform Task Leash Exclusion — Implementation Note

Date: 2026-08-16
Branch: `fix/freeform-task-leash-exclusion`
Design: `2026-08-16-freeform-task-leash-exclusion-design.md`

## Purpose

The approved design requires the final FULL_DISPLAY safety decision to be made immediately before capture submission, using WMShell freeform task `SurfaceControl` leashes and failing closed to wallpaper when complete coverage is unavailable.

The implementation preserves that behavior but uses a narrower integration boundary than the design document originally named.

## Integration boundary

`DockLiquidGlassView.java` is intentionally left unchanged. It is a very large existing file and the available remote Contents API can only replace a whole file, which would create unnecessary overwrite risk for a small capture fix.

Instead:

1. `FreeformLayerResolver` remains as a compatibility facade for the Dock's existing preflight API. It performs visible-freeform presence detection and provider/capture-gate capability checking. It never resolves package UIDs, SurfaceFlinger debug layers, or freeform task layer names.
2. `hasVisibleFreeformTasks()` remains a truthful presence signal because the existing Dock also uses it to select HOME live-backdrop behavior.
3. When a visible/possible freeform task exists but the task-leash capability is not ready, the facade returns no legacy exclusion names. The existing Dock therefore preserves `safe=false` and uses wallpaper.
4. When the capability is ready, the old Dock API still requires a non-empty name collection to represent “full-display exclusions available”. The compatibility facade returns only the already-existing `"Floating Dock"` Dock exclusion. This is not a freeform layer guess and adds no new freeform name-based capture behavior; the same Dock layer is already excluded by mode 1.
5. The final safety gate is `FreeformCaptureLeashHook`, a hook on LiquidDock's own `LiveScreenCapture.captureScreenAsync(...)` method. It is not a hook on Android's `ScreenCapture`, SurfaceFlinger, WMShell capture APIs, or any unrelated Launcher screenshot path.
6. For each mode-1 submission the gate performs a fresh current-display freeform task scan, requests every leash in one SystemUI batch, and either:
   - merges all remote task leashes into the existing `SurfaceControl[]` exclusion array; or
   - rewrites this one LiquidDock request to mode 2 wallpaper when coverage is incomplete, timed out, unavailable, or the gate itself fails.
7. The original capture method is invoked exactly once. Remote parcel-created `SurfaceControl` wrappers are released in `finally` after the original method returns from request submission.

## Why this is not a second state machine

The compatibility facade caches only the same short-lived visible-freeform presence fact that the previous resolver already cached. It does not cache task IDs or leashes and does not make the final task-coverage decision.

The capture gate has no task lifecycle state. Every mode-1 capture uses a fresh `RunningTaskInfo` scan and a fresh SystemUI leash batch. WMShell remains the sole owner of task/leash lifecycle state.

## Compatibility marker limitation

The single existing Dock-name marker exists only because the current `DockLiquidGlassView.FullDisplayExclusions` contract derives `safe` from whether the legacy freeform-name collection is empty, while the same class separately consumes truthful freeform presence for HOME source selection.

This marker must not grow into a freeform-name scheme. Production freeform exclusion is exclusively by task `SurfaceControl` leash. If the Dock preflight interface is widened in a normal local worktree, the compatibility marker should be deleted and the capability boolean passed explicitly.

## R8 safety

Because the final gate locates one LiquidDock method by reflection, the keep rules preserve only the name/signature of `LiveScreenCapture.captureScreenAsync(...)`. No whole capture class or SystemUI implementation class is kept solely for this feature.

The broker component is addressed using `FreeformLeashBrokerService.class.getName()` rather than a hard-coded service class-name string, so no additional broker `-keepnames` rule is required.

## Future local refactor

When editing through a normal local worktree, the gate and explicit preflight capability can be inlined into the normal Dock/LiveScreenCapture call path without changing the Binder protocol or behavior. Such a refactor is not required for the current safety semantics and should only be done with the full test/build/device matrix available.
