# Liquid Glass Advanced Material Blur Design

## Goal

Add a user-selectable Liquid Glass blur backend while preserving the existing shader blur as the compatibility default:

- `shader`: existing RuntimeShader 40-sample blur.
- `advanced_material`: HyperOS / MIUI SurfaceFlinger self-blur driven directly through `View.setMi*` reflection.

The persisted selection expresses user intent. Runtime capability failure must never rewrite the preference; the current process falls back to shader blur and retries on the next Launcher process start.

## Configuration Contract

Add exported string key `liquid_blur_mode` with default `shader`.

Allowed values:

- `shader`
- `advanced_material`

Unknown or missing values normalize to `shader` at runtime. Config import/export and default presets must preserve string values without changing historical keys or existing defaults.

The Liquid settings page exposes a two-option selector named `模糊方式` before the existing `玻璃模糊` radius control.

## Runtime Backend Selection

`LiquidBlurMode` owns parsing and stable persisted values.

`MiBlurBridge` resolves and caches these public-reflectable hidden MIUI methods once per Launcher process:

- `View.setMiSelfBlur(int, ArrayList)`
- `View.setPassTextureScale(float)`
- `View.setMiSelfBlurEnhanceFlag(int, int)`

`applyContentBlur(view, radiusPx, 0.5f)` invokes self-blur with `colorModes = null`, texture scale `0.5f`, and enhance flag `(0x200, 0x200)`.

Any missing method, reflection exception, or rejected texture-scale call returns failure. `DockLiquidGlassView` then keeps the requested mode as `advanced_material` but sets its active backend to shader for that process. No SharedPreferences write occurs from runtime fallback.

Switching to `shader` clears MIUI self-blur by applying radius 0 and restores texture scale to 1.0 when the reflected API is available.

## Rendering Layers

Liquid Glass becomes one host with two children:

1. `DockLiquidGlassView`: captured backdrop + refraction/tint body.
2. `DockStrokeOverlayView`: crisp Canvas highlight plus `DockStrokeRenderer` foreground border.

`DockLiquidGlassHostView` owns the final Dock geometry clip. The host has the exact Dock width/height/radius and clips its children to the round-rect or squircle outline.

The glass child itself must not use `clipToOutline`. This is deliberate: self-blur must receive rectangular corner content first, and only the parent host clips the already-blurred result back to the Dock shape. This removes the experimentally observed unblurred upper-left rounded-corner region caused by clipping the glass RenderNode before SurfaceFlinger self-blur.

In shader mode, `DockLiquidGlassView` retains its existing shape clip before drawing the body. In active advanced-material mode it draws the refraction/tint body over the full child rectangle; the host applies the final Dock clip after the child self-blur.

The Canvas highlight and configurable Dock stroke never live in the self-blurred child, so both remain sharp.

## Shader Contract

Keep one RuntimeShader. Add a scalar uniform controlling whether `blurred(p)` performs the existing 40-sample kernel.

- active advanced-material backend: `blurred(p)` returns `source(p)` directly and SurfaceFlinger supplies blur.
- standard mode or runtime fallback: existing 40-sample kernel remains active.

This means an advanced-material failure does not need shader reconstruction and can fall back in the same View instance.

## Hot Reload

The existing visible-glass config reload updates:

- requested blur mode;
- blur radius;
- overlay highlight alpha/width;
- overlay Dock stroke style.

Radius changes update either the RuntimeShader uniform or active MIUI self-blur without recreating the glass hierarchy.

## MainHook Integration

Both `Launcher.setupViews()` branches must install the same host/layer assembly:

- liquid-glass-only path;
- full Dock-customization path.

`MainHook.syncAll()` sizes the host to `mBlurBackground2.mWidth/mHeight`, updates host geometry, and invalidates the child/overlay. It no longer needs the glass child to own final outline clipping.

Workstation remains unsupported as a complete mode. When the current workstation guard suspends normal Liquid Glass, the whole host (including overlay) must be hidden/suspended together so no border/highlight remains orphaned.

## Failure and Compatibility Rules

- Default remains `shader`; existing users see no backend change after upgrade.
- Advanced-material reflection failures never crash Launcher.
- Runtime fallback never mutates preferences.
- No dependency on `HyperMaterialUtils.isEnable()` or MIUIX material classes.
- No per-frame method lookup; reflected Methods are cached.
- Existing capture source, scene state, rotation guards, black-frame handling, and capture cadence are unchanged.
- Existing Dock geometry, icon layout, workstation All Apps behavior, and placement algorithms are outside this change.

## Verification

Unit tests must cover:

- string config export/import and default `shader` value;
- default preset string persistence;
- blur-mode parsing and unknown-value fallback;
- runtime backend policy: advanced request + capability failure => shader active backend while requested mode remains advanced;
- advanced rendering contract: shader kernel bypass is controlled by backend state and host owns final clipping;
- both setupViews paths create the same Liquid Glass host/overlay assembly.

CI must run `testDebugUnitTest` and `assembleDebug` on the exact final `api101-migration` HEAD.