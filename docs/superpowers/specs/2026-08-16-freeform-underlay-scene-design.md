# Freeform Underlay Scene Preservation — Design

## Goal

A visible freeform/small-window task must behave as a transparent overlay for Dock backdrop ownership. Opening a freeform window must not by itself change whether the Dock backdrop belongs to HOME or to a fullscreen APP beneath it.

Required behavior:

```text
HOME + freeform -> HOME backdrop
APP  + freeform -> APP live backdrop
```

The freeform window itself remains excluded from the captured Dock backdrop.

## Current problem

`LauncherSceneOwnershipPolicy.launcherOwnsScene()` currently treats `WINDOWING_MODE_FREEFORM` as equivalent to Launcher owning the scene. That correctly preserves HOME when a small window is opened from the desktop, but it also incorrectly converts `APP + freeform` into HOME/wallpaper behavior.

The capture layer already has the right rendering primitives:

- APP scene selects live full-display capture.
- visible freeform SurfaceFlinger layers are resolved and excluded from full-display captures.
- the Floating Dock surface is excluded independently.

Therefore the fix belongs in scene ownership, not in capture-source selection or SurfaceFlinger capture.

## Design

Treat freeform as a scene-transparent overlay.

The ownership decision receives both:

1. the current Launcher lifecycle/focus signal; and
2. the previously established base ownership (HOME vs APP).

Policy:

```text
if foreground task is FREEFORM:
    preserve previous/base ownership
else:
    use the current Launcher lifecycle/focus signal
```

This yields:

```text
previous HOME + freeform -> HOME
previous APP  + freeform -> APP
```

No new capture scene, no freeform-specific capture mode, and no task-stack underlay resolver are introduced for this fix.

## Component responsibilities

### LauncherSceneOwnershipPolicy

Owns only the pure rule for preserving base ownership across a freeform overlay. It must not inspect Android services or SurfaceFlinger state.

### MainHook

Maintains the already-existing `launcherResumed`/Launcher-ownership state and passes that previous ownership into the policy when a focus/lifecycle transition reports a freeform foreground task.

### FreeformLayerResolver / FreeformCapturePolicy

Remain unchanged. They continue to identify visible freeform layers that must be excluded from a full-display APP capture.

### DockLiquidGlassView / CaptureSourcePolicy

Remain unchanged. APP continues to use live full-display capture; HOME continues to use HOME policy. The fix should reach the correct existing branch by preserving the base scene.

## Data flow

### Desktop with freeform

```text
HOME established
-> freeform becomes foreground task
-> policy sees FREEFORM
-> preserve previous HOME ownership
-> CaptureScene remains HOME
-> freeform layer remains excluded as today
```

### Fullscreen app with freeform

```text
APP established
-> freeform becomes foreground task
-> policy sees FREEFORM
-> preserve previous APP ownership
-> CaptureScene remains APP
-> APP uses live full-display capture
-> freeform layer excluded
-> Floating Dock excluded
```

## Failure / fallback behavior

This change intentionally does not add a task-stack resolver for the case where the process starts with a freeform overlay already present and no trustworthy previous ownership has ever been established. Existing safe fallback behavior remains for that unobserved cold-start edge case.

If later device evidence shows that cold-start case matters, it should be handled by a dedicated underlay resolver rather than by adding freeform exceptions inside `DockLiquidGlassView`.

## Testing

Update/add pure policy tests covering:

- HOME ownership preserved across FREEFORM;
- APP ownership preserved across FREEFORM;
- fullscreen foreground task still follows the current Launcher signal;
- resumed Launcher still resolves HOME ownership normally.

Add a source-contract assertion that `MainHook` passes previous/base ownership into the policy. Existing freeform exclusion tests must remain unchanged and continue to prove that visible freeform layers are excluded from full-display capture.

## Scope constraints

Do not modify:

- workstation All Apps behavior;
- workstation Recents behavior;
- `DockLiquidGlassView` capture state machine;
- `CaptureSourcePolicy` APP/HOME source selection;
- `FreeformLayerResolver` layer-resolution logic;
- Dock geometry or workstation detection.

Keep the production diff minimal and commits tagged `[skip ci]` unless a build is explicitly requested.