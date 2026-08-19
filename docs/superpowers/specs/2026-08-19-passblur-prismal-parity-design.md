# PassBlur Prismal Parity Design

## Goal

Make the HyperOS 3.0.307 zero-copy PassBlur path visually and mathematically match the current upstream Prismal material as closely as possible while retaining the existing GPU-only SurfaceFlinger PassBlur -> SurfaceTexture -> EGL pipeline.

## Constraints

- Keep zero-copy/GPU-only behavior: no Bitmap capture, `captureScreenAsync`, `glReadPixels`, or CPU texture upload.
- Keep the dedicated TextureView/EGL output path and PassBlur producer bridge.
- Treat upstream Prismal `master` as the optical reference: same base parameter semantics, same main glass shader equations, and the same half-resolution two-pass Gaussian blur topology.
- Keep Stage-B solely as the adapter between Dock-local coordinates and the PassBlur OES producer texture.
- Do not fabricate samples outside the producer domain by clamping an invalid Dock-to-producer rectangle to an edge texel.

## Root causes confirmed from device logs and source comparison

1. `Miuix307PassBlurTextureView.updateBackdropMapping()` can produce a negative normalized Y while the Dock exit/relayout animation moves the material partly below `mWinFrameInScreen`. With config rotation 3, this becomes an OES X coordinate greater than 1. The shader currently clamps those coordinates, collapsing a region to the edge texel.
2. `Miuix307PrismalMaterial.defaults()` and `fromConfig()` do not preserve the current upstream Prismal calibrated base. Several values differ materially, including IOR, normal/displacement, height transition, refraction inset, light direction, rim/specular/shininess, and shadow parameters.
3. The adapter approximates Prismal blur with a small set of in-shader OES taps. Upstream Prismal uses a 0.5x horizontal Gaussian FBO followed by a vertical Gaussian FBO and samples the resulting blurred texture from the glass shader.
4. The adapter changes upstream shader equations (notably edge-refraction/drop-lens behavior), adds LiquidDock-only edge/highlight modifiers, and clamps final color. These changes prevent parity even with matching uniforms.
5. The TextureView draw path does not clear the transparent EGL target before a shader that uses `discard`, allowing stale framebuffer pixels around animated/resized rounded corners.

## Architecture

### Stage A: PassBlur OES normalization

`Miuix307PassBlurTextureView` continues to consume the SurfaceFlinger-owned PassBlur producer as `samplerExternalOES`. A small normalization pass renders only the Dock-local producer region into an RGBA `GL_TEXTURE_2D` FBO at the TextureView output size. Stage-B mapping, config rotation and the SurfaceTexture transform exist only in this pass.

The normalization pass also receives a producer-domain validity rectangle. Fragments whose Dock-local source coordinates lie outside the available PassBlur window are written transparent rather than clamped to the nearest OES texel. This avoids edge-smear during Dock exit/relayout animations without pretending unavailable backdrop pixels exist.

### Stage B: Upstream Prismal blur

The normalized 2D texture is fed into two half-resolution FBOs. The first executes the current upstream Prismal horizontal Gaussian shader and the second the vertical Gaussian shader. Blur radius/sigma uses the same `blurRadius * 0.5` semantics as upstream.

### Stage C: Upstream Prismal glass

The final material pass samples both the normalized raw texture and the blurred texture using the current upstream Prismal fragment equations. Prismal optical UVs stay Dock-local. No Stage-B or OES matrix logic exists inside the glass shader.

LiquidDock configuration remains live, but values map one-to-one to Prismal semantics. Defaults are updated to the calibrated `PrismalLiquidGlass.applyBase()` values. The legacy `lensRefraction` value is interpreted as the upstream user scale directly; `lensDepthEffect` follows upstream `min(1, normalStrength * 0.9)` unless an explicit future Prismal-compatible control is introduced.

## Producer-domain handling

A pure Java helper computes:

- normalized Dock-local `backdropRect` relative to `mWinFrameInScreen`;
- normalized valid Dock-local Y interval corresponding to the intersection between the host rect and the producer window;
- whether the host is fully covered, partially covered, or fully outside.

No clamping is applied to `backdropRect`; clamping would hide the source-domain error. The normalization shader uses the valid interval to output transparency for unavailable source rows. Device diagnostics log coverage state and the unclamped mapping.

## Rendering hygiene

Every final TextureView frame clears to transparent before material drawing. FBO passes also clear their targets. All FBO/texture resources are recreated on output size changes and deleted during renderer shutdown.

## Verification

Unit/contract tests must prove:

- mapping math reports partial/outside producer coverage instead of edge clamping;
- the OES matrix/rotation logic lives only in the normalization shader;
- the material shader no longer contains OES sampling or Stage-B mapping;
- two half-resolution Gaussian FBO passes are present;
- upstream calibrated defaults are preserved;
- upstream drop-lens and output equations are not replaced by LiquidDock compatibility modifiers;
- every final frame clears transparent before drawing;
- no CPU capture/readback APIs are reintroduced.

GitHub Actions on the `feat/**` branch must pass both `./gradlew testDebugUnitTest --stacktrace` and `./gradlew assembleDebug --stacktrace`.