# MiuiX 307 / Launcher 4.50 Capture Research Archive

Archived code baseline: `9ca842752df27e0a229af53fa17b04e12fad6097`

Archive branch: `archive/307-capture-stable-20260818`

This document records the device-validated APP/HOME/RECENTS capture behavior for HyperOS 3.0.307+ with Xiaomi Launcher 4.50, including approaches that were tried and rejected. It exists so later rendering experiments can start from a known-good behavioral baseline without reopening already-settled lifecycle questions.

## Device-validated stable behavior

- APP backdrop uses live full-display capture.
- APP gesture interaction keeps a live high-cadence capture lease.
- Raw `ACTION_UP` / `ACTION_CANCEL` does not commit HOME or RECENTS.
- Launcher 4.50 `com.miui.home.launcher.dock.v3.GestureToHome` is the authoritative APP→HOME source boundary.
- At `GestureToHome`, capture scene switches to HOME and therefore wallpaper/mode-2. Existing scene revision checks reject any asynchronous APP frame that returns after this boundary.
- `FloatingIconView2` remains visible on the real Launcher display but is not sampled into LiquidDock after the HOME source commit, eliminating the final icon-flight ghost.
- Exact Overview remains authoritative for RECENTS.
- WMShell/SystemUI transitions remain useful as cross-process lifecycle/fallback authority for start, merge, finish, abort, and reverse transitions; WMShell finish is not the first HOME source authority.
- APP→HOME capture is continuous until the vendor HOME boundary; capture is not frozen during the gesture or transition.
- Floating Dock `hasWindowFocus()` is not a required capture gate because the 4.50 Floating Dock overlay is `FLAG_NOT_FOCUSABLE`.
- Dock icon removal / drag-drop completion uses the actual Launcher 4.50 drop animation lifecycle and remains device-confirmed fixed.

## Final source-state model

```text
GestureModeApp.onStartGesture
    → APP / FULL_DISPLAY
    → continuous capture burst

Pointer MOVE
    → APP / FULL_DISPLAY

Raw ACTION_UP/CANCEL
    → no source commit

CLOSE_TO_HOME
    → APP / FULL_DISPLAY until vendor destination event

GestureToHome
    → HOME / WALLPAPER
    → scene revision changes
    → stale in-flight APP captures are discarded

FloatingIconView2
    → normal on-screen Launcher animation
    → not sampled by LiquidDock

WMShell finish
    → stop transition lease
    → confirm final HOME and request final/post-VSYNC HOME samples
```

## Rejected approaches

### Freeze / visual-hold architecture

Rejected because request/install gates froze the last pre-transition bitmap. Depending on timing, the Dock could freeze on the app, wallpaper, an intermediate animation frame, or icon-flight frame. APP→HOME is a live composition and must not be implemented as a bitmap hold.

### HOME ownership immediately after finger release

Rejected because Launcher ownership can report HOME before the visual APP→HOME transition is actually committed. Using ownership at release caused an immediate wallpaper switch while the app was still shrinking.

### WMShell finish as first HOME source authority

Rejected because it keeps APP/FULL_DISPLAY active through Launcher `FloatingIconView2` animation. The icon-flight pixels are already in Launcher composition by that time.

### Closing APP package-name exclusion

Rejected. The APP package and auxiliary `PreColorStarting`, `Splash Screen`, and `Miui Caption of Task=` exclusions were confirmed to reach SurfaceFlinger, but the visible ghost remained. The residual icon morph was not on those closing-task layers.

### `WindowElement.mFloatingIconLayerLeash`

Rejected as the icon-pixel source. The inspected field is not a reliable independent FloatingIcon pixel surface for the active 4.50 path.

### `FloatingIconLayer2` child-surface exclusion

Rejected for the active device path. Repeated device runs showed `icon=null shader=null`; Launcher 4.50 was using `FloatingIconView2`, a normal Launcher View rendered into the Launcher surface.

## Why the final fix works

The pre-307/advanced-material capture path already had the correct source semantics: APP/RECENTS use live/full-display capture, HOME uses wallpaper capture, and `CaptureSceneState.revision()` rejects stale asynchronous results after a scene transition. The 307-specific implementation restores that semantic model while keeping newer 4.50-specific gesture and WMShell lifecycle handling.

The critical timing observed in the old Launcher 4.50 path was:

```text
performAppToHome
CLOSE_TO_HOME starts
GestureToHome
FloatingIconView2 begins updating
```

Therefore `GestureToHome` is early enough to move LiquidDock to HOME/wallpaper before the icon morph is sampled, while still allowing the preceding APP pull/close animation to remain live.

## CI evidence for archived baseline

GitHub Actions run: `32124464728` (#994)

- `testDebugUnitTest`: success
- `assembleDebug`: success
- artifact upload: success
- artifact ID: `9319864861`
- artifact digest: `sha256:965c03c7c4f38c522f0412c01bfc6ffe00f4661eb3888f1da29129b3995be11a`

Device validation after this build reported no APP→HOME icon-flight ghost.

## Rollback point

For any future renderer experiment, return to:

```text
9ca842752df27e0a229af53fa17b04e12fad6097
```

or branch:

```text
archive/307-capture-stable-20260818
```

Do not reintroduce the rejected freeze, package-exclusion, FloatingIconLayer2, or WMShell-finish-as-HOME-source approaches without new device evidence that the Launcher implementation has changed.
