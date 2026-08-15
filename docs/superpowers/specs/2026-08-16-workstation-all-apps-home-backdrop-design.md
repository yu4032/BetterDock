# Workstation All Apps Uses HOME Backdrop — Design

## Goal

In workstation mode, opening or closing All Apps must not change the Dock backdrop scene. The Dock should look and behave exactly like the workstation HOME Dock while All Apps is open.

## Required behavior

- Workstation HOME keeps the current working Liquid Glass behavior.
- Workstation All Apps is a UI state only; for Dock backdrop/capture purposes it is treated as HOME.
- Entering All Apps must not start a workstation capture burst solely because `allAppsActive` became true.
- Leaving All Apps must not start a workstation capture burst solely because `allAppsActive` became false.
- All Apps must not switch the Dock to a different capture source or capture the All Apps panel/icons as the Dock backdrop.
- Dock geometry, radius, stroke, shadow, Liquid Glass appearance, visibility, and native-background alpha must not change merely because All Apps opens or closes.
- Existing native-background fallback remains: if no valid Liquid Glass frame exists, native Dock background stays visible; after a valid frame installs, Liquid Glass may hide the native background.
- Rotation/configuration recovery may still refresh the HOME-style backdrop while All Apps is open; this must remain a HOME-style refresh, not an All Apps-specific scene transition.
- Recents is intentionally unchanged and keeps its workstation live-capture behavior.

## Architecture

`DockLiquidGlassView.setAllAppsActive()` continues to record All Apps state for launcher/layout/state consumers, but workstation backdrop ownership is decoupled from that state. In workstation mode, the method must not call `startWorkstationCaptureBurst()` on All Apps enter/exit and must not request an All Apps-specific backdrop transition.

For capture-scene resolution, HOME and workstation All Apps are equivalent from the Dock's perspective. Existing workstation HOME Liquid Glass remains active across the entire HOME → All Apps → HOME sequence.

`WorkstationWallpaperOnlyHook` remains only as the compatibility bridge that keeps the LiquidGlass host usable and provides native-background fallback during genuine frame gaps. It must not reintroduce vendor wallpaper-only policy or All Apps-specific capture behavior.

## Data flow

```text
Workstation HOME
  -> HOME Liquid Glass active
  -> open All Apps
  -> record allAppsActive=true
  -> Dock backdrop unchanged
  -> All Apps UI shown over launcher
  -> close All Apps
  -> record allAppsActive=false
  -> Dock backdrop unchanged
  -> Workstation HOME

Workstation Recents
  -> existing workstation Recents capture/burst path remains unchanged
```

## Failure handling

If the current glass frame becomes invalid because of rotation, capture failure, view recreation, or another genuine rendering failure, use the existing native-background fallback. Recovery may request a HOME-style frame. All Apps itself must never be the reason for dropping the current valid frame or hiding the Dock background.

## Scope

Expected production change is narrowly centered on `DockLiquidGlassView.setAllAppsActive()` and, only if required by scene resolution, the smallest adjacent policy code. Do not modify workstation-mode detection, APP→HOME behavior, Recents behavior, Dock geometry, freeform exclusion, or generic capture architecture.

No additional CI workflow or APK build is required for this change. Commits should use `[skip ci]` unless the user explicitly asks for a build.
