# MiuiX 307 Zero-Copy Glass Design

## Goal

Replace the MiuiX 307 backdrop readback path with a SurfaceFlinger pass-window blur child so the normal 307 glass path can render without `captureScreenAsync`, Bitmap readback, or texture upload. Preserve the already device-validated Launcher 4.50 geometry, HOME/APP/RECENTS behavior, drag/drop compatibility, and a safe fallback to the archived capture implementation.

## Baseline and rollback

The device-validated capture baseline is commit `9ca842752df27e0a229af53fa17b04e12fad6097` and branch `archive/307-capture-stable-20260818`. The zero-copy work lives only on `feat/miuix307-zero-copy-glass`.

## Architecture

The MiuiX HotSeats background remains the geometry/outline/MiShadow owner. LiquidDock injects a `DockLiquidGlassHostView` into that background. In zero-copy mode the host contains a new `Miuix307ZeroCopyBackdropView` instead of `DockLiquidGlassView`.

The backdrop child enables MIUI pass-window blur on its own RenderNode using `View.setPassWindowBlurEnabled(true)`, `setMiViewBlurMode(1)`, and `setMiBackgroundBlurRadius(radius)`. This child is the bottom layer inside the host. `DockLiquidGlassHostView` keeps final shape clipping and the existing sharp ADVANCED optical highlight shader above it. The vendor HotSeats material body remains transparent so there is no second fill edge, and vendor blur on the parent background stays disabled so it cannot post-process the whole host.

The first-stage zero-copy renderer intentionally does not provide true background-UV refraction because SurfaceFlinger does not expose its backdrop texture to `RuntimeShader`. It preserves the existing specular/rim/caustic/edge-band optical overlay and stroke/MiShadow. A future compositor-refraction phase may replace this limitation.

## Fallback

`MiuixGlassHook.install()` first tries zero-copy. If the pass-window blur API is unavailable or cannot be activated after the zero-copy child is attached, it removes the experimental child/host and installs the existing `DockLiquidGlassView` capture renderer unchanged. This means capture lifecycle hooks may stay installed globally; with no `DockLiquidGlassView` bound they are inert, while they remain available for fallback.

## Capture invariant

When zero-copy is active, the 307 material binding must not create `DockLiquidGlassView`, call `LiquidGlassFactory.create`, bind `HomeOwnershipRuntime`, or issue `captureScreenAsync`. APP/HOME/RECENTS changes are handled by SurfaceFlinger automatically because pass-window blur samples the composited content behind the Dock each frame.

## Geometry and theme lifecycle

`Miuix307MaterialPipeline` continues to own native/themed background discovery, geometry callbacks, theme-instance rebinding, width/height/radius offsets, and Dock customization. `MiuixGlassHook.syncSize()` and `syncGeometry()` update the zero-copy host/backdrop when active and continue to support the capture fallback.

## Diagnostics

Successful zero-copy activation logs `[DC][ZC] zero-copy active ...`. Fallback logs `[DC][ZC] zero-copy unavailable; capture fallback ...`. The active renderer is queryable from `MiuixGlassHook` for host-side contract tests.

## Success criteria

1. `testDebugUnitTest` and `assembleDebug` pass.
2. Source contracts prove zero-copy creates a pass-window child and does not create `DockLiquidGlassView` on its successful path.
3. On Launcher 4.50 device, `[DC][ZC] zero-copy active` appears.
4. During repeated HOME, APP, APP→HOME, APP→RECENTS, and RECENTS→HOME transitions there are no LiquidDock `captureScreenAsync`/`SF-MICapture` readbacks while zero-copy remains active.
5. Dock geometry, theme rebinding, resize, drag/drop completion, outline, stroke, and MiShadow remain visually correct.
6. If pass-window blur activation fails, the archived capture behavior remains available as fallback.
