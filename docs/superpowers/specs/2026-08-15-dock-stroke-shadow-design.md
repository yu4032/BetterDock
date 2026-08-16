# Dock Stroke Shadow Restoration Design

## Goal

Restore the existing `strokeShadow` setting by rendering the shadow inside the current `DockStrokeRenderer` path. Do not recreate a separate shadow view and do not alter the existing Dock background shadow, liquid-glass capture, workstation geometry, or Recents behavior.

## Rendering behavior

`DockStrokeRenderer` remains the single owner of stroke geometry. When `strokeShadow` is disabled, rendering is unchanged. When enabled, the renderer draws a shadow using the exact same current Dock path used by the stroke, then draws the stroke above it.

The shadow must follow the current rounded/squircle geometry, current width/height, corner parameters, and any geometry updates already consumed by the stroke renderer. It must not maintain a second position or size model.

## Layering and performance

The renderer may use a software-backed drawing layer only where required for `Paint.setShadowLayer`-style shadow rendering. This must be scoped to the stroke renderer rather than forcing the liquid-glass host or capture layer into software rendering.

Shadow state is derived from the existing `strokeShadow` configuration. No new user-facing setting is introduced.

## Isolation

The following remain independent and unchanged:

- the normal Dock background shadow;
- workstation Dock shadow/geometry synchronization;
- liquid-glass capture and SurfaceFlinger exclusion logic;
- All Apps and Recents capture-source policy.

The restored stroke shadow must not create an auxiliary `View`, `SurfaceControl`, or geometry synchronization loop.

## Tests

Add regression coverage before production changes:

1. `strokeShadow=false` does not enable stroke-shadow rendering.
2. `strokeShadow=true` is consumed by `DockStrokeRenderer` rather than becoming an unused configuration value.
3. Shadow and stroke share the same renderer geometry/path source; no separate shadow geometry state is introduced.
4. Existing stroke rendering remains active and ordered above the shadow.
5. Run the full `testDebugUnitTest` suite and `assembleDebug` after implementation.

## Success criteria

The setting once again produces a visible outline shadow around the Dock stroke, aligned to the current Dock/squircle border through size, position, radius, and animation changes, without reintroducing the stale-position issues associated with a standalone shadow view.
