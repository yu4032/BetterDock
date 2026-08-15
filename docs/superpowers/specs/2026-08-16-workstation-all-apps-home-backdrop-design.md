# Workstation All Apps Uses HOME Backdrop — Design

## Goal

In workstation mode, opening or closing All Apps must not change the Dock backdrop scene. The Dock keeps the same HOME Liquid Glass while the All Apps overlay is open.

## Required behavior

- Workstation HOME keeps the current working Liquid Glass behavior.
- Workstation All Apps is a Launcher UI state, not a Dock capture scene.
- No workstation All Apps callback may call `DockLiquidGlassView.setAllAppsActive()`.
- Opening or closing workstation All Apps must not start a capture burst, switch capture source, invalidate the current frame, or change glass/native-background visibility.
- The focus transfer from the main Launcher window to the workstation All Apps overlay remains Launcher-owned and must not be misclassified as an external APP.
- Normal-mode All Apps keeps its existing `setAllAppsActive()` capture-state behavior.
- Recents remains independent and keeps its workstation live-capture path.
- Existing native-background fallback remains unchanged for genuine capture/view failures.

## Architecture

The separation happens at the Launcher event boundary in `MainHook`, before an All Apps UI event reaches the Dock capture state machine.

`MainHook` keeps one UI-ownership fact, `workstationAllAppsOpen`. It is set by the exact laptop `showAllApps` boundary and cleared by `closeAllApps` (and when workstation mode is disabled). Its only purpose is to classify Launcher focus correctly while the focusable laptop overlay owns focus. It is not a capture-scene flag and is never read by `DockLiquidGlassView` or `CaptureSceneState`.

Every All Apps capture-forwarding hook exits early when `workstationMode` is true. Therefore workstation All Apps never enters `CaptureSceneState`, never selects `CaptureScene.ALL_APPS`, and cannot trigger All Apps-specific burst/source/visibility behavior. The existing HOME capture state simply remains authoritative.

`DockLiquidGlassView`, `CaptureSceneState`, `CaptureSourcePolicy`, and `WorkstationWallpaperOnlyHook` are intentionally unchanged by this feature.

## Data flow

```text
Workstation HOME
  -> HOME Liquid Glass active
  -> showAllApps()
       -> workstationAllAppsOpen = true
       -> run stock All Apps UI
       -> do NOT forward All Apps into Dock capture state
       -> Launcher focus transfer is ignored as overlay-owned
  -> All Apps remains visually on top of the same HOME Dock glass
  -> closeAllApps()
       -> run stock close UI
       -> workstationAllAppsOpen = false
       -> do NOT forward All Apps into Dock capture state
  -> HOME Liquid Glass was never replaced

Normal All Apps
  -> existing setAllAppsActive() path unchanged

Workstation Recents
  -> existing workstation Recents capture/burst path unchanged
```

## Failure handling

If `showAllApps()` throws, the UI-ownership latch is cleared before the exception is rethrown. `closeAllApps()` clears the latch in `finally`, so an exceptional close cannot leave stale focus ownership behind. Disabling workstation mode also clears the latch.

Capture failures, rotation recovery, view recreation, and native-background fallback continue through the existing Liquid Glass code and are outside this All Apps policy.

## Scope

Production changes are limited to `MainHook.java`:

- one workstation All Apps UI-ownership boolean;
- a focus-ownership guard;
- workstation early exits at the four All Apps capture-forwarding callbacks;
- latch cleanup when workstation mode is disabled.

Do not modify `DockLiquidGlassView`, `CaptureSceneState`, `CaptureSourcePolicy`, `WorkstationWallpaperOnlyHook`, APP→HOME behavior, Recents behavior, Dock geometry, or freeform capture.

No additional CI workflow or APK build is required for this change. Commits use `[skip ci]` unless the user explicitly asks for a build.
