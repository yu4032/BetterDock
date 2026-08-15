# Stock Launcher Capture-State Design

## Goal

Align LiquidDock's backdrop source selection and launcher-scene state tracking with the behavior recovered from HyperOS Launcher `RELEASE-4.50.0.1204-1118-07151624`.

The change fixes the architectural mistake introduced by the launcher-owned local-layer capture path: Launcher-owned scenes must use the wallpaper path, while only a genuine external app uses full-display capture below the Floating Dock.

## Verified stock behavior

- Laptop All Apps is hosted by `LauncherOverlayWindow("Laptop overlay")` and `AllAppsBackground.showBlurEffect()` uses `WallpaperManagerCompat.getCurrentWallpaperAsBitmap()` rather than capturing that overlay's ViewRoot surface.
- `Utilities.captureWallpaperBitmap()` uses `android.window.ScreenCapture.captureDisplay` with vendor capture mode 2 and `"Wallpaper BBQ wrapper"`.
- Drawer state is propagated by `DrawerStatusServiceImpl.dispatchDrawerOpen()`, `dispatchDrawerClose()`, and `dispatchDrawerProgress(float)` to Dock v3's drawer listener.
- Recents state is propagated through Dock v3's `DockStateManager$mainStateObserver$1.onEnterRecent(...)` / `onExitRecent()` and `DockStateManager$recentsListener$1.onRecentViewShow()`, `onRecentViewHide()`, and `onRecentViewAnimationComplete()`.
- HOME task leashes (`findHomeSurfaceControl`, `getHomeSurfaceControlCompat`) are transition-animation surfaces, not Dock backdrop capture sources.

## Capture-source contract

The only production capture sources are:

- `HOME` -> wallpaper (`captureMode(2)`, `Wallpaper BBQ wrapper`)
- `ALL_APPS` -> wallpaper
- `RECENTS` -> wallpaper
- `APP` -> full display (`captureMode(1)`) with the Floating Dock surface/layer excluded

`LOCAL_LAYER`, `LayerCaptureArgs`, `captureLayers`, Launcher ViewRoot `SurfaceControl` resolution, and All Apps capture-root plumbing are removed.

## State-authority contract

### All Apps

`DrawerStatusServiceImpl` is authoritative for open/close state.

- `dispatchDrawerOpen()` -> `allAppsActive = true`
- `dispatchDrawerClose()` -> `allAppsActive = false`
- `dispatchDrawerProgress(float)` is cadence/prearm information only and must not independently leave the state latched open.

Laptop `AllAppsController.showAllApps()` may prearm `ALL_APPS` before the focusable overlay steals Launcher focus; `closeAllApps()` is retained as a compatibility/early-boundary signal, but DrawerStatusService is the normal authoritative path.

Normal `AllAppsTransitionController.setState*` remains only an early prearm/fallback boundary, not the sole authoritative state source.

### Recents

Gesture/event construction is prearm only. Actual Recents state comes from the stock Dock callbacks:

- `onEnterRecent(...)` / `onRecentViewShow()` -> Recents active
- `onExitRecent()` / `onRecentViewHide()` -> Recents inactive
- `onRecentViewAnimationComplete()` -> request a final refresh only

Legacy `EnterOverviewStateEvent`/`ExitOverviewStateEvent` hooks may remain only as fallback signals if exact Dock callback classes are unavailable on another build; they must not override a successfully installed authoritative Dock callback path.

### Launcher focus

Launcher focus is only a HOME/external-APP discriminator when no Launcher-owned scene is active. A focus loss during `ALL_APPS` or `RECENTS` must not switch capture to `APP`.

## Backdrop transition barriers

APP already has a barrier that drops a stale Launcher-owned wallpaper frame before an APP frame is available.

The reverse boundary is added:

- entering `HOME`, `ALL_APPS`, or `RECENTS` from an installed `APP` frame immediately stops displaying that APP frame;
- if the current-orientation wallpaper strip cache is valid, install it immediately as a temporary launcher-owned backdrop;
- otherwise restore the native Dock background until a fresh wallpaper frame arrives.

A transient mode-2 black/null/timeout result never changes scene state or replaces a healthy installed frame. Wallpaper cache remains the continuity source while a fresh capture is retried.

## Workstation detection

Remove the unverified legacy `DeviceConfig.isMingouLaptopPcModeEnabled()` / `setMingouLaptopPcModeEnabled()` fallback and Mingou log wording. Use only the verified current API:

- `LauncherModeController.isLaptopMode()`
- `LaptopStateManager.onLaptopModeChanged(boolean)`

If that API is absent, workstation mode is disabled rather than guessed.

## Testing

Add/extend unit/source-contract tests to require:

1. `CaptureSourcePolicy` exposes only wallpaper/full-display sources and maps Launcher-owned scenes to wallpaper.
2. Production sources contain no `LOCAL_LAYER`, `LayerCaptureArgs`, `captureLayers`, `captureLayerAsync`, `resolveLauncherOwnedCaptureSurface`, or All Apps capture-root plumbing.
3. MainHook installs DrawerStatusService and Dock v3 Recents authoritative hooks.
4. Gesture/overview hooks are prearm/fallback rather than the sole authority.
5. No production source references Mingou workstation APIs.
6. Existing unit tests and `assembleDebug` remain green.

Device validation remains separate: CI can verify structure/build behavior, but the real All Apps/Recents visual result must still be checked on HyperOS hardware.
