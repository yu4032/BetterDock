# HOME mode-2 wallpaper cache A/B diagnostic

## Goal

Determine whether the HOME return offset bug is caused by reusing `wallpaperStripCache` during the final Launcher/remote-animation movement, rather than by `captureMode(2)` itself.

## Baseline

Use `fix/wallpaper-live-handoff` commit `06c930829baa57e9f4caebce56143a14c9bb3960` as the common source baseline. Do not use the HOME mode-1 diagnostic branch and do not add any timing, ownership, or geometry changes.

## A/B variants

### A — cached mode 2

Keep the baseline behavior unchanged:

- HOME source remains `CaptureSourcePolicy.Source.WALLPAPER`.
- Wallpaper capture remains SurfaceFlinger vendor `captureMode(2)` including `Wallpaper BBQ wrapper`.
- `tryServeWallpaperFromCache(...)` remains enabled.

### B — uncached mode 2

Keep the same HOME source and the same SurfaceFlinger `captureMode(2)` request, but disable `wallpaperStripCache` serving for HOME requests so every requested HOME frame reaches SurfaceFlinger.

Do not switch to mode 1. Do not alter `makeCaptureRequest()`, capture cadence, stop grace, HOME ownership, gesture-target handling, or wallpaper transform hooks.

`cacheWallpaperStrip(...)` may still update the cache internally; B simply must not serve captures from it. This keeps the experiment to one variable: cache reuse versus real mode-2 readback.

## Build output

Produce two separately identifiable debug APKs from the same baseline and build environment:

- `LiquidDock-home-mode2-cached-A.apk`
- `LiquidDock-home-mode2-nocache-B.apk`

Both variants must pass `testDebugUnitTest` and `assembleDebug` before being handed off.

## Device procedure

Test both APKs with the same wallpaper and orientation using these two paths:

1. APP → pull out Dock → continue swiping Dock to HOME.
2. Enter Recents → return to HOME.

Observe only the final small downward movement of the glass backdrop.

## Interpretation

- If A reproduces the upward-stale offset while B tracks the final movement correctly, cache reuse during the transition is the root cause. The production fix should disable wallpaper cache only while the transition is active, then re-enable it after settlement.
- If A and B both reproduce the offset, cache reuse is not the root cause; the next investigation target is the coordinate/transform semantics of vendor `captureMode(2)` itself.
- If both are correct, the reproduction is sensitive to another state and no production change should be made from this experiment alone.

## Scope guard

This diagnostic must not be merged into `main`. It is evidence gathering only. No unrelated refactoring or capture-policy changes are included.