# MiuiX 307 Refraction Highlight Design

## Goal

Add real refraction and highlight to the opt-in HyperOS 3.0.307+ material pipeline without restoring LiquidDock's legacy high-frequency full-screen capture state machine.

## Confirmed rendering model

The 307 native `HotSeatsListContentMiuiXBlurBackground` remains the owner of blur, tint and its SurfaceFlinger-driven refresh. LiquidDock adds a foreground refraction overlay only.

The overlay samples the **original, unblurred composited background below the Floating Dock**. It does not attempt to sample the MiuiX blurred result.

```text
background/app/wallpaper
        |
        +--> MiuiX native pass blur --> native Dock material
        |
        +--> local GPU capture (Dock rect + bleed, mode 1, Floating Dock excluded)
                                |
                         Hardware Bitmap
                                |
                          BitmapShader
                                |
                    RuntimeShader refraction
                                |
                       refraction + highlight
```

## Capture boundary

The new 307 overlay may reuse the low-level `LiveScreenCapture.captureScreenAsync()` primitive, but must not reuse:

- `CaptureSceneState`
- `BackdropTransitionPolicy`
- wallpaper-strip caching
- APP/HOME/RECENTS ownership logic
- launcher lifecycle capture hooks
- the legacy `DockLiquidGlassView` capture scheduler

Each request captures only the overlay's current screen rectangle expanded by a bounded refraction bleed. The rectangle is clamped to the physical display. The returned `ScreenshotHardwareBuffer.asBitmap()` is kept as a hardware-backed `Bitmap` and used directly by `BitmapShader`; no CPU pixel readback or software bitmap conversion is introduced.

## Performance limits

- Capture source is `FULL_DISPLAY`/mode 1 so the shader sees the actual unblurred content below the Dock.
- `Floating Dock` is excluded through the existing mode-1 layer-name path.
- Maximum capture cadence is 30 FPS even if the legacy `liquid_capture_power_limit_fps` is higher.
- Capture scale is clamped to 0.25-0.50; an existing user value inside that range is preserved.
- While detached, hidden, zero-sized, or not shown, the capture loop stops.
- Only one capture request may be in flight. Requests coalesce while one is active.

## Refraction rendering

Create `Miuix307RefractionView` and replace the current `Miuix307HighlightView` overlay in the 307 material pipeline.

The RuntimeShader receives:

- `content`: latest hardware-backed `BitmapShader`
- overlay `size`
- effective `captureScale`
- crop-relative inset from the overlay origin to the captured buffer origin
- corner radius
- glass thickness
- IOR
- normal strength
- dome amount
- lens refraction pixels
- chromatic aberration

The shader uses a rounded-rectangle signed-distance field and its local gradient to displace backdrop sampling near the glass boundary, with a smaller dome component through the interior. RGB channels sample at slightly different offsets when chromatic aberration is non-zero.

The refracted raw backdrop is intentionally partially transparent. The native MiuiX blur remains visible underneath; the raw sample becomes strongest near high-curvature/edge regions instead of replacing the whole native material.

After the RuntimeShader pass, the View draws the existing white directional edge highlight. If no backdrop frame is available yet, the View still draws highlight-only, matching the current 307 behavior instead of flashing blank.

## Geometry

`Miuix307MaterialPipeline` continues to own geometry synchronization from `mWidth`, `mHeight` and `mBackground.getCornerRadius()`. It sends the same geometry to `Miuix307RefractionView` and continues to use `DockStrokeRenderer` on the native background.

Changing width, height or radius requests an immediate fresh local backdrop frame but does not create a second scheduler.

## Failure behavior

Any ScreenCapture initialization/submission failure disables only the 307 refraction sampler for that overlay instance. MiuiX native blur, highlight fallback and Dock stroke remain active. The global 307 material pipeline does not fall back to the legacy capture pipeline merely because refraction capture failed.

## Compatibility

The `liquid_miuix_307_pipeline` switch remains opt-in and defaults off. No low-version path changes. The existing issue that 307 mode and the old Advanced Material backend cannot be used together is explicitly out of scope for this change.
