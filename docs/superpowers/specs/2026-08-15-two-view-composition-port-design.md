# Two-View Composition Port Design

## Goal

Port only the glass-composition refactor from commit `4fc9875b18dd628a03dfa6ec702ac96b7dc93e16` onto `test/capture-rebuild-8ee84ed`, preserving the current Stage 1 Recents capture-source behavior unchanged.

## Scope

The resulting composition has exactly two runtime Views for LiquidDock glass:

1. `DockLiquidGlassHostView` — final shape clip, ADVANCED sharp highlight, configurable stroke foreground.
2. `DockLiquidGlassView` — capture/refraction/tint body and MIUI self-blur target.

`DockStrokeOverlayView` is removed.

## Production changes

### `DockLiquidGlassHostView`

- Accept exactly one child through `setLayers(DockLiquidGlassView glass)`.
- Receive `DockLiquidGlassView.ActiveBlurBackendListener` directly.
- Own the ADVANCED highlight `RuntimeShader` and additive `BlendMode.PLUS` paint previously owned by `DockStrokeOverlayView`.
- Draw the sharp ADVANCED highlight after `super.dispatchDraw(canvas)` and inside the final shared shape clip.
- Own `DockStrokeRenderer.configure(this, ...)` and `DockStrokeRenderer.updateRadius(this, ...)`.
- Cache the final `DockShapePath` until geometry or size changes.
- Keep the child glass RenderNode rectangular; the final clip remains outside the self-blurred child.

### `DockStrokeRenderer`

- Preserve existing stroke semantics and rendering output.
- Cache outer/inner stroke geometry until bounds, radius, or style changes.
- Do not change color, width, fillDiff, squircle, alpha, or foreground-restoration semantics.

### `MainHook`

- Stop constructing `DockStrokeOverlayView`.
- Assemble `DockLiquidGlassHostView` with `host.setLayers(glass)`.
- Initialize geometry and then call `host.reloadOverlay(config.dock, config.glass)` so stroke/highlight settings are owned by the host.
- Preserve insertion index, host sizing/sync, launcher lifecycle hooks, capture hooks, workstation behavior, and all Stage 1 Recents source logic.

### Remove `DockStrokeOverlayView`

Delete the file after its sharp highlight responsibilities are moved into the host.

## Explicit non-goals

- Do not change `CaptureSourcePolicy`.
- Do not change `CaptureSceneState`.
- Do not change Recents haptic/prearm/confirmation behavior.
- Do not change `DockLiquidGlassView` capture source, capture cadence, EINVAL handling, foreground authority, HOME settle, worker recovery, or capture barrier behavior.
- Do not fix the known brief wallpaper frame between haptic prearm and confirmed Recents in this stage.
- Do not port unrelated commits adjacent to `4fc9875`.

## Success criteria

- `DockLiquidGlassHostView` has exactly one child View: `DockLiquidGlassView`.
- No production source references or instantiates `DockStrokeOverlayView`.
- ADVANCED highlight remains sharp because it is rendered by the parent host after the self-blurred child.
- Configurable stroke remains a foreground owned by the host and preserves its existing ring-only semantics.
- Final round/squircle clipping remains outside the glass self-blur RenderNode.
- Current Stage 1 Recents policy remains byte-for-byte unchanged in `CaptureSourcePolicy.java` and semantically unchanged in `DockLiquidGlassView.java`.

## Verification

Use the contract tests ported from `4fc9875`:

- `DynamicLiquidHighlightContractTest`
- `LiquidGlassLayerContractTest`
- `TwoViewCompositionContractTest`

Then run the existing Stage 1 tests together with the full unit-test suite and `assembleDebug` locally. GitHub Actions must not be used for this iteration.
