# API101 HyperOS 3.0.307+ Compatibility Design

## Goal

Integrate the device-validated HyperOS 3.0.307+ Dock material compatibility into the API101 line without importing the experimental branch history or changing ordinary LiquidDock behavior when compatibility is disabled.

## Baseline

- `v1.3.0` is the released functional baseline and is already an ancestor of current `main`.
- `api101-migration` is an ancestor of `main` and can be fast-forwarded safely.
- Implementation is prepared on an isolated integration branch based on `main`; only the validated result is fast-forwarded into `api101-migration`.

## Feature Gate

Reuse the persisted key `liquid_miuix_307_pipeline`.

Product wording: **HyperOS 3.0.307+ 高级材质兼容**.

- UI default: `false`.
- Runtime default: `false`.
- Export default: `false`.
- User must explicitly enable it.
- When disabled, no 307-specific class hooks, blur suppression, in-place material host, GestureToHome adapter, or 307 drag adapter may be installed.
- Ordinary v1.3.0/API101 Liquid Glass, advanced-material blur, Dock geometry, workstation, and capture paths remain available and unchanged.

## Architecture

`MainHook` owns only feature dispatch. All 307 behavior lives behind one facade:

```text
MainHook
  └─ if liquid glass && 307 compatibility enabled
       └─ Miuix307Compatibility.install(...)
```

`Miuix307Compatibility` is the public internal boundary and delegates to focused components:

- `Miuix307MaterialPipeline`: exact HyperOS 3.0.307+ HotSeats/material lifecycle hooks, geometry callbacks, hierarchy recovery, vendor background-blur suppression, Dock geometry compatibility.
- `MiuixGlassHook`: binds Prismal inside the native material `ViewGroup`, owns geometry-ready handoff, native-radius optics, transparent vendor material body, and keeps the vendor View identity/outline/MiShadow alive.
- `Miuix307DragCaptureHook`: 307 drag lifecycle, valid SurfaceControl exclusion, and freeze-last-clean-backdrop fallback when no excludable surface exists.

Existing generic LiquidDock capture/render code remains shared rather than duplicated.

## Material Ownership

For supported native material backgrounds (`HotSeatsListContentMiuiXBlurBackground` and themed `HotSeatsListContentBlurBackground2`):

1. Preserve the native View instance, layout lifecycle, outline, MiShadow, and geometry callbacks.
2. Disable only vendor pass-window/region/background GPU blur that would post-process Prismal.
3. Replace the visible vendor material body with a transparent rounded shell; do not set the entire View alpha to zero.
4. Insert LiquidDock's glass host as a child of the native material View.
5. Physical refraction, clip, rim, and optical highlight use the native material radius.
6. LiquidDock configurable stroke/corner offset remains a separate decorative geometry and does not drive the optical SDF.
7. Do not preserve a vendor foreground that creates a second visible outline under/over LiquidDock's own stroke.

## Geometry-ready Handoff

Never expose Prismal while the native material is a startup placeholder.

Ready means all are true:

- View is attached.
- Parent is a `ViewGroup`.
- Width > 0.
- Height > 0.
- Native material radius > 0.5 px and finite.

Early `setupViews`/`onAttachedToWindow` callbacks only defer. Existing `setBackgroundRadius` / `triggerMeasure` / hierarchy callbacks retry naturally. No fixed millisecond delay is used.

## Capture and Transition Compatibility

With the compatibility flag enabled:

- HyperOS 3.0 Pad Floating Dock must never be gated by `hasWindowFocus()`; the window is `FLAG_NOT_FOCUSABLE`.
- 307 `GestureToHome` prearms HOME/wallpaper ownership before icon-flight animation.
- Freeform-visible APP scenes use wallpaper capture semantics established by the validated branch.
- Dock drag with a valid SurfaceControl uses live capture plus exclusion.
- Dock drag without an excludable SurfaceControl freezes the last clean backdrop and resumes with a fresh capture on drag end.
- Theme/background replacement rebinds to the current attached HotSeats background.

## Geometry Customization Compatibility

The 307 adapter must preserve normal Dock customization instead of returning before ordinary geometry behavior is represented. Width offset, height offset, spacing, bottom offset, and radius-related synchronization are applied through exact 307 lifecycle hooks, without installing duplicate global hooks.

## Testing

Formal API101 integration requires automated verification, not only device smoke testing:

1. Config schema/default and persistence test for `liquid_miuix_307_pipeline=false`.
2. Feature-dispatch contract proving disabled mode does not install 307 compatibility.
3. Enabled-mode contract proving the 307 facade is reachable only when Liquid Glass and the flag are enabled.
4. Geometry-ready handoff contract, including `0x0/radius=0` startup rejection.
5. Material ownership contract: native View retained, vendor body transparent, native radius drives optics, no full-View alpha hiding.
6. 307 drag/capture regression contracts.
7. Full `testDebugUnitTest`.
8. `assembleDebug`.
9. Final device validation after API101 fast-forward.

## Non-goals

- No automatic OS-version enablement.
- No fixed startup delay.
- No rewrite of the generic Liquid Glass renderer.
- No unrelated refactor of `MainHook` or capture subsystem.
- No change to `main` during this integration.
