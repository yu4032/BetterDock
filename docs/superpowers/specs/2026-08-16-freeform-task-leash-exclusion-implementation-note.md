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

1. `FreeformLayerResolver` remains as a compatibility facade for the Dock's existing preflight API. It performs only visible-freeform presence detection and provider/capture-gate capability checking. It never resolves package UIDs, SurfaceFlinger debug layers, or freeform layer names.
2. When a visible/possible freeform task exists but the task-leash capability is not ready, the facade preserves the existing Dock fail-closed result (`safe=false`), so capture remains wallpaper.
3. Once the task-leash capability is ready, the final safety gate is `FreeformCaptureLeashHook`, a hook on LiquidDock's own `LiveScreenCapture.captureScreenAsync(...)` method. It is not a hook on Android's `ScreenCapture`, SurfaceFlinger, WMShell capture APIs, or any unrelated Launcher screenshot path.
4. For each mode-1 submission the gate performs a fresh current-display freeform task scan, requests every leash in one SystemUI batch, and either:
   - merges all remote task leashes into the existing `SurfaceControl[]` exclusion array; or
   - rewrites this one LiquidDock request to mode 2 wallpaper when coverage is incomplete, timed out, unavailable, or the gate itself fails.
5. The original capture method is invoked exactly once. Remote parcel-created `SurfaceControl` wrappers are released in `finally` after the original method returns from request submission.

## Why this is not a second state machine

The compatibility facade caches only the same short-lived visible-freeform presence fact that the previous resolver already cached. It does not cache task IDs or leashes and does not participate in the final exclusion decision once the capture gate is available.

The capture gate has no task lifecycle state. Every mode-1 capture uses a fresh `RunningTaskInfo` scan and a fresh SystemUI leash batch. WMShell remains the sole owner of task/leash lifecycle state.

## No string-state fallback

An earlier implementation draft reused the existing `"Floating Dock"` name as a non-empty readiness marker for the old Dock interface. That draft was removed during review. The production compatibility facade now returns no freeform layer names at all. Provider readiness is represented only as capability state, not as a layer-name string.

## R8 safety

Because the final gate locates one LiquidDock method by reflection, the keep rules preserve only the name/signature of `LiveScreenCapture.captureScreenAsync(...)`. No whole capture class or SystemUI implementation class is kept solely for this feature.

The broker component is addressed using `FreeformLeashBrokerService.class.getName()` rather than a hard-coded service class-name string, so no additional broker `-keepnames` rule is required.

## Future local refactor

When editing through a normal local worktree, the gate can be inlined into the normal Dock/LiveScreenCapture call path without changing the Binder protocol or behavior. Such a refactor is not required for correctness and should only be done with the full test/build/device matrix available.
