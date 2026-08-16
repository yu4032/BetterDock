# APP → HOME wallpaper zoom synchronization design

Status: approved by the user after rejecting the higher-cost FULL_DISPLAY exclusion experiments.

## Goal

Keep APP → HOME on HyperOS's low-cost wallpaper-only capture path while making the captured Dock backdrop follow the wallpaper zoom/scale that Launcher animates during CLOSE_TO_HOME. The previously validated Recents exit lifecycle remains unchanged.

## Reverse-engineered facts

- `LocalWallpaperElement` owns a spring named `zoom`.
- `LocalWallpaperElement$mAnimListener$1.onAnimationUpdate` forwards the current spring value to `LocalWallpaperElement.updateTargetParams(float)` every animation frame.
- `updateTargetParams(float scale)` maps that visual scale to the hidden WallpaperManager zoom-out API.
- `WallpaperParam` targets are visual scales: HOME=1.00, RECENTS=1.06, APP=1.14 on this Launcher build.
- `SystemWallpaperElement` uses a separate vendor wallpaper-command path; it is observed for diagnostics but no timing curve is synthesized.

## Architecture

### `WallpaperZoomHook`

Hook `LocalWallpaperElement.updateTargetParams(float)` after the original call and publish the current visual scale through a small launcher-process runtime bridge. Hook Local/System `animTo` and `setTo` only for diagnostics so logs identify the active vendor path.

### `WallpaperZoomRuntime`

Hold a weak reference to the current `DockLiquidGlassView`. Forward scale updates on the main thread. It is not an ownership or scene authority.

### `DockLiquidGlassView`

Maintain the last valid Launcher wallpaper scale and a monotonically increasing wallpaper-transform revision. On a meaningful scale change:

1. invalidate the cached wallpaper strip;
2. mark the cache as not ready;
3. request a fresh wallpaper capture through the existing cadence/coalescing path when HOME is the active scene.

The cache records the transform revision that produced each strip. A late callback from an older zoom revision may still be displayed briefly, but it can never become a valid cache hit for a newer revision; a dirty request immediately follows it.

`GestureToHome` also drops the static HOME cache before its first wallpaper request, closing the race where the destination event can precede the first per-frame zoom callback.

## Conservative first device candidate

Earlier cached-vs-uncached testing suggested that mode-2 itself can observe the correct APP→HOME wallpaper transform, while reuse of an older cached strip is one APP-path failure mode. Recents had a separate lifecycle bug and has since been fixed independently.

Therefore the first device candidate does **not** apply an inverse crop or bitmap rescale. `WallpaperZoomTransform` contains tested center-based crop math for a later isolated A/B only if cache synchronization still leaves a residual positional error. This prevents accidental double application if SurfaceFlinger mode-2 already includes the wallpaper surface transform.

## Safety and fallback

- Accept sampled visual scale only when finite and in [0.8, 1.25].
- Do not mutate WallpaperManager, Launcher animation state, SurfaceControl hierarchy, ownership, or Floating Dock focus.
- Do not carry forward `AppHomeAnimationHook`, Activity-leash exclusion, or FULL_DISPLAY APP→HOME hold from rejected branches.
- If no LocalWallpaperElement per-frame callback is observed, keep the ordinary wallpaper-only path and diagnostic logs; never synthesize an animation from delays.

## Tests

- Pure crop math remains tested but is not wired into the conservative candidate.
- Runtime contract requires the exact LocalWallpaperElement per-frame scale hook without ownership mutation.
- Cache contract requires HOME-target invalidation and transform-revision binding.
- Existing Recents lifecycle tests remain green.
