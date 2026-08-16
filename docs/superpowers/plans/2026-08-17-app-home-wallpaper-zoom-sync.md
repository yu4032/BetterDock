# APP → HOME Wallpaper Zoom Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep APP → HOME on wallpaper-only capture while reproducing HyperOS Launcher wallpaper zoom in LiquidDock's captured strip.

**Architecture:** A small pure-math helper converts a screen-space Dock crop to the unzoomed wallpaper coordinate space. `WallpaperZoomHook` samples the Launcher's actual per-frame `LocalWallpaperElement.updateTargetParams(float)` scale and publishes it through a runtime bridge to `DockLiquidGlassView`, which invalidates wallpaper cache and requests fresh mode-2 captures. Full-display APP/RECENTS capture remains unchanged.

**Tech Stack:** Java, libxposed API101 hooks, Android `Rect`/`Point`, HyperOS Launcher internals, GitHub Actions Gradle tests/build.

## Global Constraints

- Branch from validated Recents lifecycle fix `0feef012ecb265bac9f1930401ab62bbacc720a2`.
- Do not carry forward APP `CLOSE_TO_HOME` FULL_DISPLAY hold or SurfaceControl exclusion experiments.
- Do not change SystemUI HOME/APP ownership authority.
- Do not use Floating Dock window focus as a capture/correctness gate.
- Apply zoom correction only to wallpaper mode 2.
- Scale 1.0 must be identity; invalid/out-of-range scale must fall back to identity.

---

### Task 1: Pure wallpaper zoom crop transform

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WallpaperZoomTransform.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WallpaperZoomTransformTest.java`

**Interfaces:**
- Produces: `WallpaperZoomTransform.Result adjust(Rect screenCrop, int displayWidth, int displayHeight, float visualScale, float captureScale)` with `Rect sourceCrop` and `float frameScale`.

- [ ] Write RED tests for identity at 1.0, center-point invariance, inverse scaling around display center, dimension preservation (`adjustedCropSize * adjustedFrameScale ≈ originalCropSize * captureScale`), and invalid-scale fallback.
- [ ] Run `./gradlew testDebugUnitTest --tests '*WallpaperZoomTransformTest'` and verify failure because helper is missing.
- [ ] Implement minimal helper using `raw = center + (screen-center)/scale`; clamp crop to display bounds; set `frameScale = captureScale * scale` when correction is active.
- [ ] Run focused tests and commit.

### Task 2: Sample HyperOS Launcher wallpaper scale

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/WallpaperZoomHook.java`
- Create: `src/main/java/com/hellovoid/liquiddock/WallpaperZoomRuntime.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/RecentsHapticHook.java`
- Create: `src/test/java/com/hellovoid/liquiddock/WallpaperZoomHookContractTest.java`

**Interfaces:**
- `WallpaperZoomHook.install(ClassLoader)` hooks `com.miui.home.recents.anim.LocalWallpaperElement.updateTargetParams(float)` after original execution.
- `WallpaperZoomRuntime.bind(DockLiquidGlassView)` stores only a weak view reference.
- `WallpaperZoomRuntime.onScale(float)` forwards to `DockLiquidGlassView.setLauncherWallpaperVisualScale(float)` on main thread.

- [ ] Write RED source contract requiring the exact LocalWallpaperElement method, a diagnostic hook for Local/System `animTo`/`setTo`, and no ownership mutation.
- [ ] Run focused contract test and verify failure.
- [ ] Implement hooks by method-name/one-float-signature discovery for `updateTargetParams`; log sampled scale only when debug logging is enabled.
- [ ] Install the hook next to the existing Recents lifecycle hook and run focused/full unit tests.

### Task 3: Integrate scale revision with wallpaper capture/cache

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/HomeOwnershipRuntime.java` only if binding is needed from the existing glass binding point.
- Create: `src/test/java/com/hellovoid/liquiddock/WallpaperZoomCaptureContractTest.java`

**Interfaces:**
- `DockLiquidGlassView.setLauncherWallpaperVisualScale(float scale)` validates [0.8, 1.25], ignores sub-epsilon repeats, increments a transform revision, clears `wallpaperStripCache`, zeros capture cadence boundary, and requests a capture.
- Wallpaper mode capture uses `WallpaperZoomTransform.adjust(...)`; FULL_DISPLAY uses the original `req.stripRect` and original `captureScale` exactly.

- [ ] Write RED contract proving wallpaper mode uses adjusted crop/scale, full-display path does not, and zoom updates invalidate cache before requesting capture.
- [ ] Verify RED.
- [ ] Implement scale state plus cache invalidation and capture scheduling.
- [ ] Apply the transform only immediately before mode-2 `captureScreenAsync`, keeping `CaptureRequest` screen geometry unchanged for downstream tile mapping.
- [ ] Add logs: `wallpaper zoom scale=... revision=...`, `wallpaper zoom capture screenCrop=... sourceCrop=... frameScale=...`.
- [ ] Run all unit tests and `assembleDebug`.

### Task 4: CI/device candidate

**Files:**
- Modify: `.github/workflows/api101-build.yml` only to include `fix/app-home-wallpaper-zoom-sync`, then restore workflow parity after candidate artifact is produced if needed.

- [ ] Verify exact branch HEAD in GitHub Actions.
- [ ] Require `testDebugUnitTest`, `assembleDebug`, and artifact upload all success.
- [ ] Download artifact, verify ZIP/APK integrity and SHA-256.
- [ ] Do not merge to `main`; device test APP → HOME and Recents → HOME first.
