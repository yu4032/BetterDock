# MiuiX 307 PassBlur GPU Backdrop Demo Design

## Goal

Prove a general-purpose, GPU-only live-backdrop pipeline on HyperOS 3.0.307 without using the fixed `setChargeAnim*` / water-wave effects and without using LiquidDock's capture/Bitmap renderer.

## Device evidence

The device framework's `ViewRootImpl` creates a `SurfaceTexture`, wraps it in a `Surface`, sends it to the root `SurfaceControl` through `SurfaceControl.Transaction.SetPassBlurSurface(...)`, and enables production through `setUpdateTextureFlag(...)`. `SurfaceFlinger` renders selected layers into that producer Surface. The output therefore arrives through a BufferQueue rather than `ScreenshotHardwareBuffer.asBitmap()`.

The existing charge-shader probe is not part of this design. It remains useful only as evidence that the compositor contains a real refraction shader; its center-origin water-wave geometry is not a reusable Dock material.

## Architecture

For the 307 `HotSeatsListContentBlurBackground2` path, replace the current charge-refraction/background-blur calibration composition with a diagnostic GPU backdrop view:

```text
Wallpaper / APP / Recents layers
        |
        v
SurfaceFlinger PassBlur
        |
        | SetPassBlurSurface(rootSC, Surface)
        | setUpdateTextureFlag(rootSC, true, 1.0f)
        v
SurfaceTexture / GL_TEXTURE_EXTERNAL_OES
        |
        v
Miuix307PassBlurGpuView GLES shader
        |
        v
Floating Dock child Surface
        |
        +-- tone View
        +-- sharp LiquidDock optics
```

The PassBlur source transaction targets the Floating Dock root `SurfaceControl`. The source excludes the Floating Dock root, the demo child Surface, NavigationBar, StatusBar, GestureStub, and DockAssistantView to avoid feedback and system-overlay contamination.

## Demo shader

The demo is intentionally diagnostic, not final glass. It displays the same live backdrop on both halves of the Dock. The left side is sampled without displacement. The right side adds an obvious static sinusoidal horizontal UV displacement. If the right side bends live APP/wallpaper detail while the left side remains aligned, the generic SF -> BufferQueue -> OES -> LiquidDock shader path is proven.

The shader must not contain a captured Bitmap, BitmapShader, screenshot input, charge-animation uniforms, or water-wave phase semantics.

## Geometry

The PassBlur producer buffer is sized to the Floating Dock root window at `sfScale=1.0`. The GPU view maps its own location in that root window into normalized crop coordinates and applies the `SurfaceTexture` transform matrix before sampling. Crop coordinates refresh on pre-draw so Dock width/position animation can move without rebuilding the renderer.

The output child `SurfaceControl` receives the native Dock corner radius so the diagnostic Surface does not escape the glass shape.

## Lifecycle and validation

`Miuix307PassBlurGpuView` is active only after all of the following are true:

1. an independent output Surface exists;
2. `SetPassBlurSurface` and `setUpdateTextureFlag` were successfully submitted to the Floating Dock root;
3. at least one `SurfaceTexture` frame arrived;
4. `updateTexImage()` and one GLES draw completed successfully.

Installation waits for this state before declaring `[DC][ZC] zero-copy active`. Method lookup/binding/GL failures mark activation exhausted and trigger the unchanged capture fallback. Validation gets a longer startup allowance than the old blur-child path because a BufferQueue producer/consumer and EGL surface must both become ready.

On clear/detach, the transaction sends `SetPassBlurSurface(rootSC, null)` and `setUpdateTextureFlag(rootSC, false, 1.0f)`, then releases the producer `Surface`, input `SurfaceTexture`, and GL objects.

## Constraints

- Android/Gradle builds are GitHub CI only.
- No local Android build.
- No `captureScreenAsync` on the successful demo path.
- No `ScreenshotHardwareBuffer`, `Bitmap`, `BitmapShader`, CPU pixel readback, or CPU texture upload on the successful demo path.
- No `setChargeAnim*`, `setChargeAnimProp`, `WaterWave`, or wallpaper-runtime-shader binding on the successful demo path.
- Use `sfScale=1.0f` for the first demo to avoid the compositor's 1/4 better-down path.
- Preserve the existing capture renderer only as runtime fallback.

## Success criteria

A successful device run logs the PassBlur transaction binding, first OES frame, first GLES draw, and `[DC][ZC] zero-copy active backend=passblur-gles`, with no capture fallback. Visually, live content behind the Dock appears raw on the left and spatially displaced on the right. HOME, APP, and RECENTS transitions should update without a capture request.
