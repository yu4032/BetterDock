# APP → HOME Wallpaper Zoom Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep APP → HOME on low-cost wallpaper-only capture while synchronizing LiquidDock's wallpaper cache with HyperOS Launcher's real per-frame wallpaper scale.

**Architecture:** `WallpaperZoomHook` samples the actual `LocalWallpaperElement.updateTargetParams(float)` visual scale and publishes it through a weak runtime bridge to `DockLiquidGlassView`. Scale changes and the HOME gesture invalidate the mode-2 wallpaper cache; each cached strip is bound to the transform revision that produced it. A tested crop-transform helper is retained for a later isolated A/B but is intentionally not wired into the conservative first device candidate.

**Tech Stack:** Java, libxposed API101 hooks, HyperOS Launcher internals, GitHub Actions Gradle tests/build.

## Global Constraints

- Branch from validated Recents lifecycle fix `0feef012ecb265bac9f1930401ab62bbacc720a2`.
- Do not carry forward APP `CLOSE_TO_HOME` FULL_DISPLAY hold or SurfaceControl exclusion experiments.
- Do not change SystemUI HOME/APP ownership authority.
- Do not use Floating Dock window focus as a capture/correctness gate.
- Keep the first device candidate on the original wallpaper mode-2 crop/scale.
- Never synthesize wallpaper animation timing from fixed delays.

---

### Task 1: Pure wallpaper zoom crop transform — completed, not wired

**Files:**
- `src/main/java/com/hellovoid/liquiddock/WallpaperZoomTransform.java`
- `src/test/java/com/hellovoid/liquiddock/WallpaperZoomTransformTest.java`

- [x] RED tests for identity, center invariance, inverse scaling, dimension preservation, and invalid-scale fallback.
- [x] Implemented center-based inverse transform.
- [x] Full unit suite passed after implementation.
- [x] Leave helper unused in the conservative candidate to avoid possible double application of a SurfaceFlinger transform.

### Task 2: Sample HyperOS Launcher wallpaper scale — completed

**Files:**
- `src/main/java/com/hellovoid/liquiddock/WallpaperZoomHook.java`
- `src/main/java/com/hellovoid/liquiddock/WallpaperZoomRuntime.java`
- `src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java`
- `src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java`
- `src/test/java/com/hellovoid/liquiddock/WallpaperZoomHookContractTest.java`

- [x] RED contract verified.
- [x] Hook exact `LocalWallpaperElement.updateTargetParams(float)` after original execution.
- [x] Add Local/System `animTo`/`setTo` diagnostics only; do not mutate wallpaper APIs.
- [x] Bind a weak runtime bridge to the current glass view.
- [x] Preserve the validated Recents lifecycle hook.

### Task 3: Synchronize wallpaper cache with zoom revision — completed pending final CI

**Files:**
- `.github/apply_wallpaper_zoom_sync.py`
- `src/test/java/com/hellovoid/liquiddock/WallpaperZoomCaptureContractTest.java`

- [x] RED contract verified for zoom-driven cache invalidation/revision binding.
- [x] `setLauncherWallpaperVisualScale(float)` validates [0.8, 1.25], increments `wallpaperTransformRevision`, marks wallpaper cache not ready, clears it safely, and requests HOME capture through the existing cadence/coalescing path.
- [x] Bind each cached wallpaper strip to the transform revision that produced it; stale in-flight captures cannot become valid cache hits for a newer zoom revision.
- [x] Add a second RED contract for the race before the first zoom callback.
- [x] `GestureToHome` clears the static HOME cache before its first wallpaper request.
- [x] Do not wire `WallpaperZoomTransform.adjust(...)` into capture yet.

### Task 4: CI/device candidate

- [ ] Verify exact final branch HEAD in GitHub Actions.
- [ ] Require `testDebugUnitTest`, `assembleDebug`, and artifact upload all success.
- [ ] Download artifact and verify ZIP/APK integrity plus SHA-256.
- [ ] Do not merge to `main`; device test APP → HOME and Recents → HOME first.
