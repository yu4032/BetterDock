# Official Prismal Framebuffer Boundary Design

## Goal

Restore the upstream `styropyr0/Prismal` optical model as an isolated black box. LiquidDock must adapt HyperOS PassBlur/OES into the framebuffer domain Prismal expects, then crop the visible Dock for the TextureView without modifying Prismal's internal optical equations.

## Current problem

LiquidDock currently renders Prismal directly into the visible Dock TextureView while sampling a larger overscan texture. It uploads `u_resolution = outputWidth x outputHeight` and `u_glassSize = outputWidth x outputHeight`, then patches the upstream fragment shader with `u_dockUvRect`, `mapDockUvToBackdrop()`, Y-vector conversion, legacy S-curve logic, and other LiquidDock-specific optical changes. This mixes the compositor mapping domain with Prismal's optical domain.

Upstream Prismal instead distinguishes:

- `u_resolution`: complete background framebuffer size;
- `u_glassSize`: glass rectangle size;
- `u_mousePos`: glass center in framebuffer coordinates;
- `a_position` / `v_shapeCoord`: glass-local `[-0.5,+0.5]` coordinates;
- `v_screenTexCoord`: framebuffer-space texture coordinates with the upstream vertex Y convention.

## Architecture

The zero-copy pipeline becomes:

1. HyperOS PassBlur producer -> external OES texture.
2. Existing Stage-A normalization applies SurfaceTexture affine transform, validated quarter-turn mapping, coverage masking, and real overscan into `rawTexture` of `fboWidth x fboHeight`.
3. Existing two-pass blur produces a blurred texture in the same framebuffer domain.
4. A new `materialTexture/materialFramebuffer` of `fboWidth x fboHeight` receives the official Prismal glass pass. Prismal uses its official vertex and fragment shaders and receives:
   - `u_resolution = fboWidth, fboHeight`;
   - `u_glassSize = outputWidth, outputHeight`;
   - `u_mousePos = visible Dock center in Prismal framebuffer coordinates`;
   - official optical uniforms only.
5. A separate LiquidDock crop/composite pass samples the visible Dock rectangle from `materialTexture` into the final `outputWidth x outputHeight` TextureView.

## Ownership boundary

### LiquidDock owns

- PassBlur binding and producer lifecycle;
- `configRot` and SurfaceTexture transform normalization;
- partial-coverage validity;
- overscan sizing and `GL_MAX_TEXTURE_SIZE` guard;
- Gaussian background blur resources;
- mapping the visible Dock rectangle into the normalized framebuffer;
- final crop/composite to TextureView;
- mapping diagnostics.

### Prismal owns

- the exact upstream vertex shader;
- the exact upstream fragment shader;
- SDF shape, height field, meniscus, normals, lens, Snell refraction, bulge, chromatic aberration, reflection, lighting, caustics, and transmission;
- official uniform meanings.

No LiquidDock coordinate transform or legacy optical path may be inserted inside the official Prismal shader.

## Mapping diagnostics

Add a one-shot `[DC][PRISMAL-MAP]` log after the first stable normalized frame and again when producer geometry/rotation changes. It reports:

- root surface, producer surface/buffer, `configRot`, SurfaceTexture matrix;
- visible output size, raw/blur/material FBO sizes;
- Dock rectangle in normalized FBO pixels and UV;
- valid Dock rectangle and coverage;
- expected Prismal `u_resolution`, `u_glassSize`, `u_mousePos`;
- Dock +X/+Y and Prismal +X/+Y basis directions;
- TL/TR/BL/BR/center anchor mapping from Dock-local coordinates to normalized FBO pixels and upstream screen UV.

The log is diagnostic only and must not change rendering.

## Shader parity

`Miuix307PrismalShader` must contain the current upstream `vertex_shader.glsl` and `fragment_shader.glsl` without LiquidDock optical edits. Permanent parity tests should reject LiquidDock-only uniforms/functions such as `u_dockUvRect`, `mapDockUvToBackdrop`, `upstreamOffsetToLocalTextureUv`, and legacy S-curve uniforms.

## Parameter policy

`Miuix307PrismalMaterial` remains the Java-side adapter from LiquidDock controls to official Prismal uniforms. Remove legacy S-curve parameters and any guard-budget terms that only exist for the legacy model. Preserve literal current Prismal controls and official lens calculation semantics.

## Validation

Per user instruction, do not require a RED/GREEN test cycle for this refactor. Validate by:

- source parity/contract tests against the official shader structure;
- existing mapping/rotation/overscan unit tests;
- complete `testDebugUnitTest`;
- complete `assembleDebug`;
- target-device `[DC][PRISMAL-MAP]` logs and visual inspection after installing the resulting APK.

The work stays isolated on `refactor/prismal-official-model`; no automatic merge.