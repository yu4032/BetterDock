# Stock Launcher Capture-State Design

## Goal

Align LiquidDock's backdrop source selection and launcher-scene state tracking with the behavior recovered from HyperOS Launcher `RELEASE-4.50.0.1204-1118-07151624`, while preserving live Recents capture.

The original architectural mistake was treating All Apps and Recents as one capture-source category. They are both Launcher-owned states, but they do not need the same backdrop source.

## Verified behavior and regression evidence

- Laptop All Apps is hosted by `LauncherOverlayWindow("Laptop overlay")` and `AllAppsBackground.showBlurEffect()` uses `WallpaperManagerCompat.getCurrentWallpaperAsBitmap()` rather than capturing that overlay's ViewRoot surface.
- `Utilities.captureWallpaperBitmap()` uses `android.window.ScreenCapture.captureDisplay` with vendor capture mode 2 and `"Wallpaper BBQ wrapper"`.
- Stock Dock background `HotSeatsListContentBlurBackground2.addBlur(...)` calls `BlurUtilities.setBackgroundBlur(...)`, which reflects into `View.setBackgroundBlur(...)`. The native Dock therefore blurs the compositor content physically behind the Dock rather than substituting a wallpaper bitmap for every Launcher-owned state.
- Before commit `04ec679`, LiquidDock explicitly used mode-1 full-display capture for `APP` and `RECENTS`, excluding Dock/drag layers; this is the known path that produced live task-card content. Commit `8ee84ed` regressed Recents by forcing it to wallpaper.
- Drawer state is propagated by `DrawerStatusServiceImpl.dispatchDrawerOpen()`, `dispatchDrawerClose()`, and `dispatchDrawerProgress(float)` to Dock v3's drawer listener.
- Recents state is propagated through Dock v3's `DockStateManager$mainStateObserver$1.onEnterRecent(...)` / `onExitRecent()` and `DockStateManager$recentsListener$1.onRecentViewShow()`, `onRecentViewHide()`, and `onRecentViewAnimationComplete()`.
- HOME task leashes (`findHomeSurfaceControl`, `getHomeSurfaceControlCompat`) are transition-animation surfaces, not a general Dock backdrop source.

## Capture-source contract

The only production capture sources are:

- `HOME` -> wallpaper (`captureMode(2)`, `Wallpaper BBQ wrapper`)
- `ALL_APPS` -> wallpaper
- `RECENTS` -> full display (`captureMode(1)`) so task cards remain live behind the Dock
- `APP` -> full display (`captureMode(1)`)

Full-display scenes exclude the Floating Dock surface/layer and current drag layer when available.

`LOCAL_LAYER`, `LayerCaptureArgs`, `captureLayers`, Launcher ViewRoot `SurfaceControl` resolution, and All Apps capture-root plumbing are removed. Recents does not use local-layer capture; it restores the established full-display path.

## State-authority contract

### All Apps

`DrawerStatusServiceImpl` is authoritative for open/close state.

- `dispatchDrawerOpen()` -> `allAppsActive = true`
- `dispatchDrawerClose()` -> `allAppsActive = false`
- `dispatchDrawerProgress(float)` is cadence/prearm information only and must not independently leave the state latched open.

Laptop `AllAppsController.showAllApps()` may prearm `ALL_APPS` before the focusable overlay steals Launcher focus; `closeAllApps()` is retained as a compatibility/early-boundary signal. Normal `AllAppsTransitionController.setState*` remains an early prearm/fallback boundary, not the sole authoritative state source.

### Recents

Gesture and haptic boundaries are prearm signals. In particular, the existing vibration/haptic path must force the first Recents full-display request immediately, before task-card motion begins.

Actual Recents state comes from the stock Dock callbacks:

- `onEnterRecent(...)` / `onRecentViewShow()` -> Recents active
- `onExitRecent()` / `onRecentViewHide()` -> Recents inactive
- `onRecentViewAnimationComplete()` -> request a final refresh only

Legacy `EnterOverviewStateEvent`/`ExitOverviewStateEvent` hooks may remain only as fallback signals if exact Dock callback classes are unavailable on another build; they must not override a successfully installed authoritative Dock callback path.

### Launcher focus

Launcher focus is only a HOME/external-APP discriminator when neither All Apps nor Recents is active. A focus loss during either Launcher-owned state must not switch the scene to APP.

## Backdrop transition barriers

Barriers are based on capture-source domain, not on whether a state is Launcher-owned.

- Wallpaper domain: `HOME`, `ALL_APPS`
- Live full-display domain: `APP`, `RECENTS`

Cross-domain transitions drop the stale installed frame before serving the new source. Same-domain transitions (`APP <-> RECENTS`, `HOME <-> ALL_APPS`) keep continuity.

For live -> wallpaper transitions, a valid current-orientation wallpaper cache can be installed immediately; otherwise the native Dock background is restored until a fresh mode-2 frame arrives. For wallpaper -> live transitions, the stale wallpaper frame is removed and the existing prearm path requests a mode-1 frame immediately.

A transient wallpaper black/null/timeout result never changes scene state or replaces a healthy installed frame.

## Workstation detection

Remove the unverified legacy `DeviceConfig.isMingouLaptopPcModeEnabled()` / `setMingouLaptopPcModeEnabled()` fallback and Mingou log wording. Use only:

- `LauncherModeController.isLaptopMode()`
- `LaptopStateManager.onLaptopModeChanged(boolean)`

If that API is absent, workstation mode is disabled rather than guessed.

## Testing

Add/extend tests to require:

1. `CaptureSourcePolicy` exposes only wallpaper/full-display sources and maps `HOME/ALL_APPS -> WALLPAPER`, `APP/RECENTS -> FULL_DISPLAY`.
2. Production sources contain no `LOCAL_LAYER`, `LayerCaptureArgs`, `captureLayers`, `captureLayerAsync`, `resolveLauncherOwnedCaptureSurface`, or All Apps capture-root plumbing.
3. MainHook installs DrawerStatusService and Dock v3 Recents authoritative hooks.
4. Haptic/gesture Recents prearm remains present and maps to a full-display source.
5. Backdrop barriers trigger only across capture-source domains.
6. No production source references Mingou workstation APIs.
7. Existing unit tests and `assembleDebug` remain green.

Device validation remains separate: CI can verify source policy/build behavior, but the real All Apps black-background fix and live Recents card capture must still be checked on HyperOS hardware.
