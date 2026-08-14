# Dynamic Liquid Highlight Design

Date: 2026-08-14
Branch: `api101-migration`

## Goal

Replace the current static Canvas `LinearGradient` highlight on `DockStrokeOverlayView` with the liquid-glass shader's original per-pixel geometric lighting (`specular + rim + caustics`) while preserving the two existing blur backends:

- `shader`: keep the highlight inside `DockLiquidGlassView.REFRACTION_SHADER` so the existing path remains a single RuntimeShader pass.
- `advanced_material`: suppress highlight contribution inside the self-blurred glass RenderNode and draw the same geometric highlight in the independent sharp overlay after SurfaceFlinger self-blur.

The result must preserve the current `liquid_blur_mode` fallback contract, Dock shape clipping, stroke foreground, configuration keys, rotation behavior, and live configuration reload.

## Non-goals

- Do not add Prismal-style background reflection sampling (`reflSample`) in this change.
- Do not add another blur pass to the standard Shader backend.
- Do not alter capture scheduling, grid, widget placement, Dock sizing, or workstation behavior.
- Do not reintroduce the old overlay/shadow layout animation path.

## Current Problem

`DockLiquidGlassView.REFRACTION_SHADER` already computes dynamic specular, rim, and caustic lighting after background blur/refraction. This is correct for the standard Shader backend because the blur operation affects the input samples, not the final highlight contribution.

For `advanced_material`, however, `setMiSelfBlur(...)` blurs the complete `DockLiquidGlassView` RenderNode. The highlight currently produced inside that RenderNode is therefore blurred together with the glass body. The temporary replacement in `DockStrokeOverlayView` is only a static Canvas diagonal gradient and no longer reproduces the original geometric lighting.

## Architecture

### Standard Shader backend

```text
DockLiquidGlassHostView
├── DockLiquidGlassView
│   ├── 40-sample Shader blur
│   ├── refraction / chromatic aberration / tint
│   └── dynamic spec + rim + caustics
└── DockStrokeOverlayView
    └── DockStrokeRenderer foreground only
```

`DockStrokeOverlayView` does not run the dynamic highlight shader in this mode. This avoids duplicate geometric calculations and prevents double highlights.

### Advanced material backend

```text
DockLiquidGlassHostView
├── DockLiquidGlassView
│   ├── refraction / chromatic aberration / tint
│   ├── shader blur disabled
│   └── internal highlight contribution disabled
│        ↓
│     SurfaceFlinger setMiSelfBlur(...)
└── DockStrokeOverlayView
    ├── dynamic highlight RuntimeShader
    └── DockStrokeRenderer foreground
```

The host remains responsible for the final round/squircle clip. The overlay also clips its own RuntimeShader draw to the same `DockShapePath` so shader output outside the glass geometry cannot leak into the rectangular View bounds.

## Shared Highlight Model

The advanced overlay RuntimeShader copies only the geometric lighting part of `REFRACTION_SHADER`:

- `sdRound` / `gradRound`
- height field and dome blend
- `gradH`
- surface normal `N`
- primary specular `specP`
- lit and opposite rim terms
- caustic term

It does not sample the captured background and therefore does not need `content`, capture size/scale, screen offset, blur radius, chromatic aberration, IOR/refraction offsets, or brightness.

The formulas and constants for `specP`, `rimLitSide`, `rimOpposite`, and `caust` must remain aligned between the standard glass shader and the advanced overlay shader.

## Highlight Controls

Existing preference keys remain unchanged.

### `liquid_highlight_alpha`

This becomes the final dynamic-highlight intensity multiplier for both backends:

- Shader backend: the combined `spec + rim + caustic` contribution inside `REFRACTION_SHADER` is multiplied by this value.
- Advanced backend: the same combined contribution in the overlay RuntimeShader is multiplied by this value.

This keeps the existing GUI control meaningful after the static Canvas gradient is removed.

### `liquid_highlight_width`

This remains an input to the existing glass shader Fresnel/edge geometry exactly as it is today. It is not removed from the glass path.

The advanced highlight renderer may use the same value only where it can reproduce the existing edge-band semantics without changing the shared spec/rim/caustic model. It must not invent a second unrelated line-width effect.

### Existing dynamic controls

The overlay receives and hot-reloads:

- `normalStrength`
- `dome`
- `specularSharp`
- `specularStrength`
- `rimLight`
- `caustics`
- `edgeBand`
- `highlightAlpha`
- geometry: radius, squircle flag, squircle control point

## Runtime Backend State

The decision to draw the overlay highlight must use the **active** blur backend, not merely the requested preference.

Rules:

1. Requested `shader` → glass highlight enabled, overlay highlight disabled.
2. Requested `advanced_material`, MIUI self-blur succeeds → glass highlight disabled, overlay highlight enabled.
3. Requested `advanced_material`, MIUI self-blur fails → runtime falls back to Shader; glass highlight enabled, overlay highlight disabled; saved preference remains `advanced_material`.
4. If self-blur state changes after attach/radius reconfiguration, overlay enablement is updated immediately so there is never a frame with both highlight paths active.

`DockLiquidGlassView` therefore exposes active-backend changes to `DockLiquidGlassHostView`/overlay through a small internal callback or equivalent package-private synchronization point. No SharedPreferences write occurs.

## AGSL Output and Blend

The advanced highlight overlay uses `BlendMode.PLUS`.

The RuntimeShader output must obey Android premultiplied-alpha rules. Do **not** output non-zero RGB with alpha 0. The renderer computes the highlight RGB, clamps it, derives an alpha that is at least the maximum RGB component, and returns premultiplied-compatible `half4`. With an opaque glass destination, PLUS keeps the resulting alpha saturated while adding the highlight RGB.

The shader source must avoid `//` line comments inside concatenated one-line AGSL strings; use block comments or Java-side comments.

## Shape and Layering

- `DockLiquidGlassView` stays rectangular in advanced mode so self-blur receives corner source pixels.
- `DockLiquidGlassHostView.dispatchDraw()` remains the final round/squircle clip.
- `DockStrokeOverlayView` clips its own dynamic highlight draw with `DockShapePath` before drawing the shader rectangle.
- `DockStrokeRenderer` remains the overlay foreground and is not blurred.
- No overlay code changes Dock/icon `LayoutParams`.

## Hot Reload

The existing config reload path must update both glass and overlay highlight parameters without recreating the View hierarchy.

Setters must early-return when values are unchanged and invalidate only when a value actually changes. Changes to `specularSharp`, `specularStrength`, `rimLight`, `caustics`, `edgeBand`, `highlightAlpha`, and relevant geometry should become visible on the existing ~1 s config reload cadence.

## Testing

Add regression tests that verify:

1. Standard Shader mode keeps internal dynamic highlight and disables the overlay highlight path.
2. Active advanced-material mode disables the glass highlight contribution and enables the overlay RuntimeShader.
3. Advanced-material fallback re-enables the glass highlight and disables the overlay path without modifying the requested mode.
4. `DockStrokeOverlayView` no longer uses `LinearGradient` for liquid highlight.
5. Overlay uses `RuntimeShader`, `BlendMode.PLUS`, and `DockShapePath` clipping.
6. Overlay shader includes specular/rim/caustic formulas and does not sample captured background.
7. `liquid_highlight_alpha` participates in both backend highlight paths.
8. Hot reload forwards the dynamic highlight parameters to the overlay.
9. Existing `testDebugUnitTest` and `assembleDebug` remain green.

## Acceptance Criteria

- Standard Shader appearance retains dynamic spec/rim/caustic without a second highlight pass.
- Advanced material blur shows the same class of sharp, per-pixel geometric highlights above self-blur.
- No double highlight occurs during successful self-blur or fallback.
- `liquid_highlight_alpha` remains functional.
- Stroke remains sharp and independent.
- Round/squircle clipping remains correct through resize and rotation.
- No preference key or JSON compatibility change is introduced.
- No changes are made to capture scheduling or grid/widget behavior.
