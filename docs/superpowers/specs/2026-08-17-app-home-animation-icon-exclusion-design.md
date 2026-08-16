# APP → HOME Floating Icon Capture Exclusion Design

## Goal

Prevent the APP → HOME `CLOSE_TO_HOME` live backdrop capture from baking HyperOS Launcher’s animated return icon (`FloatingIconView2`) into the Dock glass, while preserving the already-correct live positional continuity until animation end.

## Reverse-engineered basis

On this Launcher build, `com.miui.home.recents.views.FloatingIconView2` is a normal Launcher View, not an independent SurfaceFlinger layer. `com.miui.home.recents.anim.WindowElement` owns a field named `mFloatingIconLayerLeash` whose DEX-declared type is `android.view.SurfaceControl`; its no-argument `bindIconLayerLeashIfNeeded()` binds that leash from the Launcher root surface. HyperOS also contains a separate `FloatingIconLayer2` implementation with dedicated buffer layers, but the device logs for the affected path show `FloatingIconView2`.

## Design

- Keep the existing APP → HOME animation lifecycle fix unchanged: APP/full-display capture remains active until the exact `CLOSE_TO_HOME` animation end.
- Add a small `AppHomeAnimationLayerExclusion` adapter that hooks `WindowElement.bindIconLayerLeashIfNeeded()` and caches the valid `mFloatingIconLayerLeash` SurfaceControl after the vendor method returns.
- Expose the existing APP → HOME pending state from `CaptureSceneState` as a package-private read-only query.
- In `DockLiquidGlassView`, only when a capture request is `CaptureScene.APP` and the APP → HOME handoff is pending, append the cached Launcher-root leash to the existing full-display `setExcludeLayers()` array alongside the Floating Dock leash.
- Do not exclude the Launcher root during ordinary APP capture, Recents, All Apps, HOME, workstation mode, or after the APP → HOME animation has ended.
- If the Launcher-root leash is absent or invalid, preserve the current capture behavior rather than changing source semantics or hiding the real Launcher view.

## Non-goals

No changes to SystemUI HOME/APP ownership authority, wallpaper cache, capture cadence, capture modes, Recents exit handling, freeform exclusion policy, Floating Dock focus handling, or Launcher rendering state (`setVisibility`, `setAlpha`, `setIsDrawIcon`).

## Verification

- RED source/wiring contract must fail before the new adapter exists.
- GREEN tests must require the exact WindowElement class/method/field names, pending-state gate, and Dock exclusion wiring.
- Existing `CaptureSceneStateTest` APP → HOME and Recents tests must remain green.
- `testDebugUnitTest` and `assembleDebug` must both pass on GitHub Actions before producing a device-test APK.
