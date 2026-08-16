# APP → HOME wallpaper zoom synchronization design

Status: approved by the user after rejecting the higher-cost FULL_DISPLAY exclusion experiments.

## Goal

Keep APP → HOME on HyperOS's low-cost wallpaper-only capture path while making the captured Dock backdrop follow the wallaper zoom/scale that Launcher animates during CLOSE_TO_HOME. The previously validated Recents exit lifecycle remains unchanged.

## Reverse-engineered facts

- `LocalWallpaperElement` owns a spring named `zoom`.
- `LocalWallpaperElement$mAnimListener$1.onAnimationUpdate` forwards the current spring value to `LocalWallpaperElement.updateTargetParams(float)` every animation frame.
- `updateTargetParams(float scale)` calls hidden `WallpaperManager.setWallpaperZoomOut(windowToken, clamp(6 - 5 * scale, 0, 1))`.
- `WallpaperParam` targets are visual scales: HOME=1.00, RECENTS=1.06, APP=1.14 on this Launcher build.
- AOSP WindowManager maps wallpaper zoom-out to a surface scale. With the platform max scale 1.2, the Launcher's conversion makes the current spring value equal the visual wallpaper surface scale.
- `SystemWallpaperElement` uses the vendor wallpaper command path instead; it will be observed/logged but not synthesized frame-by-frame unless the local callback is actually present.

## Architecture

### `WallpaperZoomHook`

Hook `LocalWallpaperElement.updateTargetParams(float)` after the original call. Publish the current visual scale to a small launcher-process runtime bridge. Also hook `LocalWallpaperElement.animTo/setTo` and `SystemWallpaperElement.animTo/setTo` only for diagnostics so logs identify which vendor path is active.

### `WallpaperZoomRuntime`

Hold a weak reference to the current `DockLiquidGlassView`. Forward scale updates on the main thread. Do not become an ownership or scene authority.

### `DockLiquidGlassView`

Maintain the last valid Launcher wallpaper scale and a monotonically increasing wallpaper-transform revision. On a meaningful scale change:

1. invalidate the cached wallpaper strip;
2. mark the capture source dirty;
3. request a wallpaper capture through the existing cadence/coalescing path.

For wallpaper-only mode 2, transform the requested display crop around the display center:

`raw = center + (screen - center) / scale`

Because the raw crop becomes smaller by `1/scale`, multiply the compositor frame scale by `scale` so the returned bitmap dimensions remain identical to the uncorrected request. APP/FULL_DISPLAY and RECENTS/FULL_DISPLAY requests are untouched.

## Safety and fallback

- Apply correction only when source is WALLPAPER and scale is finite in a conservative [0.8, 1.25] range.
- Scale 1.0 is identity.
- If no LocalWallpaperElement per-frame callback is observed, keep the existing uncorrected wallpaper path and emit diagnostics; never synthesize an animation from timing guesses.
- Do not mutate WallpaperManager, Launcher animation state, SurfaceControl hierarchy, ownership, or Floating Dock focus.
- Do not carry forward `AppHomeAnimationHook`, Activity-leash exclusion, or FULL_DISPLAY APP→HOME hold from the rejected experiment branches.

## Tests

- Pure crop math: identity at 1.0; center invariant; dimensions preserved after adjusted compositor scale; clamping stays within display bounds.
- Source contract: wallpaper correction is never applied to APP/RECENTS full-display capture.
- Runtime contract: scale changes invalidate wallpaper cache and schedule capture without changing scene authority.
- Existing Recents lifecycle tests remain green.
