# HyperOS 3.0.307 TextureView PassBlur Calibration Design

## Status

Approved direction: replace the diagnostic `GLSurfaceView` output with a `TextureView` + EGL output path, keep PassBlur as the live GPU-only backdrop source, and do not re-enable refraction until backdrop orientation and position are accurate.

## Problem

The current HyperOS 3.0.307 feasibility path proves that SurfaceFlinger PassBlur can feed a caller-owned `SurfaceTexture`, which LiquidDock can sample through `GL_TEXTURE_EXTERNAL_OES` without CPU readback. It also proves that producer resize can survive rotation without the earlier native hot-unbind crash.

However, the current output is a `GLSurfaceView`/`SurfaceView`, which creates an independent compositor surface. Device testing now shows two coupled failure classes:

1. output/input feedback can appear as a recursive, spiral-like infinite subdivision in a corner; and
2. the independent SurfaceView's compositor geometry does not share a simple coordinate space with the Floating Dock root during rotation, making backdrop orientation and local position difficult to map reliably.

The existing edge-refraction shader makes these failures harder to diagnose, so refraction must remain disabled until the plain live backdrop is correct.

## Goal

Build a GPU-only HyperOS 3.0.307 calibration backend with this data flow:

```text
SurfaceFlinger PassBlur
        |
        v
caller-owned input SurfaceTexture / OES texture
        |
        v
LiquidDock GLES passthrough shader
        |
        v
TextureView EGL window surface
        |
        v
Floating Dock root window
```

The backend is successful only when the TextureView shows the live background behind the Dock with correct orientation and position across supported rotations, without recursive feedback and without CPU capture/readback.

## Non-goals for the calibration backend

Until backdrop mapping is validated on-device, this backend MUST NOT add:

- rounded-edge lens displacement;
- Snell/IOR refraction;
- RGB dispersion;
- roughness/Fresnel/caustic effects;
- tone/tint layers;
- advanced optical highlights;
- the legacy capture renderer after a zero-copy validation timeout.

The existing safe Dock foreground stroke may remain because it does not alter backdrop sampling.

## Architecture

### 1. PassBlur input remains unchanged

`Miuix307PassBlurBridge` remains the source bridge. It binds a caller-owned `Surface` to `SetPassBlurSurface`, enables texture updates with `setUpdateTextureFlag`, and excludes the Floating Dock root plus system overlays from the PassBlur scene.

The input is still a dedicated `SurfaceTexture` attached to `GL_TEXTURE_EXTERNAL_OES`. No `Bitmap`, `captureScreenAsync`, `ScreenshotHardwareBuffer`, `glReadPixels`, or CPU-side texture upload is allowed on the normal path.

### 2. Replace GLSurfaceView output with TextureView + EGL

The independent `Miuix307PassBlurGpuView extends GLSurfaceView` output is retired from the calibration path.

Create a TextureView-based component that owns two distinct GPU surfaces:

- **input**: the PassBlur producer `SurfaceTexture`, sampled as `samplerExternalOES`;
- **output**: the `TextureView`'s own `SurfaceTexture`, wrapped in a `Surface` and used to create an EGL window surface.

A dedicated render thread owns the EGL display/context/surface, OES texture, program, and input `SurfaceTexture`. `SurfaceTexture.OnFrameAvailableListener` schedules a draw of the newest PassBlur frame.

Because `TextureView` is composed into the existing Floating Dock root instead of publishing a separate SurfaceView child layer, excluding the Floating Dock root from PassBlur must also exclude the rendered calibration output. This removes the feedback loop by construction instead of relying on the child SurfaceView layer name.

### 3. Calibration shader is strict passthrough

The first TextureView shader contains no optical displacement. Its fragment path is conceptually:

```glsl
vec4 sourceUv = uTexMatrix * vec4(mappedBackdropUv, 0.0, 1.0);
gl_FragColor = texture2D(uTexture, sourceUv.xy);
```

There is no `sdRoundRect`, `edgeWeight`, displacement, `uGlassRadius`, or refraction math in this calibration shader.

### 4. Separate feedback validation from backdrop-coordinate validation

Calibration proceeds in two observable stages.

#### Stage A: feedback-free full-domain probe

Render the PassBlur input through the TextureView backend with no manual local crop. This stage answers only:

- Does the recursive/spiral feedback disappear?
- Does the TextureView survive attach/detach and rotation without crashing?
- Does the PassBlur producer continue to deliver live OES frames?

A full-domain probe is diagnostic and is not expected to match the Dock-local background yet.

#### Stage B: accurate Dock-local backdrop mapping

After Stage A is feedback-free, map the Dock-local output to the correct region of the PassBlur input.

The mapping must be derived from one coordinate model only:

1. the final visible screen-space bounds of the material host / Dock visual owner;
2. the display or ViewRoot surface dimensions that correspond to the PassBlur scene;
3. the actual `SurfaceTexture.getTransformMatrix()` returned for the current PassBlur buffer.

Do not reuse the retired SurfaceView `mScreenRect`, `mRTLastReportedPosition`, output `SurfaceControl` position, or a second independent SurfaceView crop transform.

The implementation must log enough information to reconstruct the mapping numerically: material-host screen rect, display/root surface dimensions, configuration rotation, input texture matrix, normalized pre-matrix backdrop rect, and all four post-matrix corners.

### 5. Rotation and lifecycle

Rotation must not reintroduce the old native hot-unbind path.

For the same PassBlur root producer:

- resize the existing input `SurfaceTexture` buffer in place when the producer dimensions change;
- update orientation/mapping metadata;
- recreate only the TextureView EGL window surface when the TextureView output surface itself is destroyed/recreated;
- do not call `SetPassBlurSurface(null)` merely because width, height, or rotation changed.

A true root-surface identity change or complete component teardown may perform the normal final unbind.

### 6. Visual shell during calibration

The calibration output must remain optically neutral:

- no tone/tint overlay;
- no advanced optical highlight;
- no old refraction shader;
- no validation-timeout switch to the legacy capture renderer.

The existing safe configured Dock foreground stroke may remain. Any rounded clipping needed for presentation should be done by the normal Dock/View shell, not by reintroducing an independent SurfaceView SurfaceControl crop.

## Success criteria

The TextureView calibration backend is considered accurate only when all of the following are observed on-device:

1. No recursive, spiral, hall-of-mirrors, or infinite-subdivision artifact appears.
2. The live backdrop continues to update during workspace motion and after repeated portrait/landscape rotations.
3. Launcher PID remains stable through repeated rotations.
4. Background orientation is correct in both supported orientations.
5. A recognizable feature immediately outside the Dock continues through the Dock at the correct location and scale.
6. The center passthrough has no intentional optical displacement, tint, blur, or highlight.
7. Normal operation uses no CPU readback or capture fallback.

Only after these criteria pass may a follow-up change reintroduce edge refraction and later the thick-glass optical model.

## Testing strategy

TDD remains mandatory.

Static/source contracts should verify that the calibration path:

- uses `TextureView` and EGL rather than `GLSurfaceView`/`SurfaceView` for output;
- keeps a separate OES input `SurfaceTexture` and TextureView output surface;
- has no feedback-sensitive child SurfaceView output exclusion dependency;
- uses a passthrough shader with no displacement/refraction terms;
- keeps PassBlur producer resize in place across rotation;
- contains no capture/readback APIs in the normal backend;
- keeps legacy capture disabled on validation timeout;
- logs the Stage B coordinate model when local mapping is enabled.

Every implementation task must first produce a failing test, then the minimal production change, and use GitHub CI for Android unit tests and APK assembly. No local Android/Gradle build is used.

## Rollout

1. Introduce TextureView + EGL backend with full-domain passthrough and remove GLSurfaceView from the active calibration path.
2. Validate on-device that recursive feedback is gone and lifecycle/rotation is stable.
3. Add the single-coordinate-model Dock-local mapping and validate exact orientation/position.
4. Freeze the accurate passthrough path as a regression baseline.
5. In a separate follow-up, reintroduce refraction incrementally, starting with a small edge-only displacement and preserving exact center passthrough.