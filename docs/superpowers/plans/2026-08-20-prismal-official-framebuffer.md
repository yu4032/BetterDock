# Official Prismal Framebuffer Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore upstream Prismal as an unmodified optical model behind a clean LiquidDock framebuffer adapter.

**Architecture:** HyperOS PassBlur/OES is normalized into an overscan 2D framebuffer owned by LiquidDock. Official Prismal renders into an overscan-sized material framebuffer using official `u_resolution`, `u_glassSize`, and `u_mousePos`, after which a separate crop pass copies the visible Dock rectangle into the TextureView.

**Tech Stack:** Android Java, OpenGL ES 2.0, SurfaceTexture/OES, HyperOS PassBlur, JUnit source/behavior contracts, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-20-prismal-official-framebuffer-design.md`

## Global Constraints

- Do not merge automatically.
- Do not restore Bitmap/screenshot capture fallback.
- Preserve device-validated Stage-A quarter-turn and SurfaceTexture normalization behavior.
- Official Prismal shader equations must not contain LiquidDock coordinate mapping or legacy S-curve logic.
- Per user instruction, verification does not require a RED/GREEN cycle; run direct parity, unit, and build verification.

---

### Task 1: Freeze official Prismal shader boundary and mapping log

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307PrismalOfficialShaderContractTest.java`

**Interfaces:**
- Consumes: normalized FBO geometry (`fboWidth`, `fboHeight`, Dock UV rect, output size, producer geometry).
- Produces: exact upstream shader strings and `[DC][PRISMAL-MAP]` domain diagnostics.

- [ ] Replace `Miuix307PrismalShader.VERTEX_SHADER` with current upstream `vertex_shader.glsl` semantics including `a_position`, `u_resolution`, `u_mousePos`, and `u_glassSize`.
- [ ] Replace fragment shader with current upstream `fragment_shader.glsl`, removing `u_dockUvRect`, `mapDockUvToBackdrop`, offset-basis adapter, legacy S-curve uniforms/path, and LiquidDock-only optical equation changes.
- [ ] Add a source contract test asserting official uniforms/functions exist and LiquidDock-only optical mapping symbols do not.
- [ ] Add `logPrismalMappingDomains()` to `Miuix307PassBlurTextureView` reporting producer, normalized FBO, expected Prismal uniforms, basis directions, and five anchors.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace` and commit.

### Task 2: Render official Prismal in the overscan framebuffer domain

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307OfficialFramebufferDomainTest.java`

**Interfaces:**
- Consumes: `rawTexture`, `blurTextureV`, `fboWidth`, `fboHeight`, visible Dock output dimensions/insets.
- Produces: `materialTexture/materialFramebuffer` matching `fboWidth x fboHeight`; official glass quad placement.

- [ ] Allocate/release `materialTexture` and `materialFramebuffer` with the raw FBO size.
- [ ] Add a dedicated upstream glass position buffer `[-0.5,-0.5 ... +0.5,+0.5]` and bind only `a_position` for the official Prismal program.
- [ ] Compute Dock center in Prismal framebuffer coordinates from actual sampling insets: `centerX = leftInset + outputWidth/2`; `centerY` converted to the official `u_mousePos` convention exactly once.
- [ ] Render Prismal to `materialFramebuffer` using `glViewport(0,0,fboWidth,fboHeight)`, `u_resolution=fboWidth/fboHeight`, `u_glassSize=outputWidth/outputHeight`, and the Dock center.
- [ ] Change `Miuix307PrismalMaterial.applyUniforms` to accept framebuffer resolution, glass size, and mouse position and upload only official uniforms.
- [ ] Remove legacy S-curve fields and legacy-only guard-budget contributions from `Params`.
- [ ] Run full unit tests and commit.

### Task 3: Add LiquidDock-only crop/composite stage

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurShaders.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/Miuix307PrismalCropCompositeTest.java`

**Interfaces:**
- Consumes: `materialTexture`, Dock normalized-FBO pixel/UV rectangle, valid Dock coverage.
- Produces: final `outputWidth x outputHeight` TextureView image.

- [ ] Add a simple 2D crop shader that maps output UV into the visible Dock rectangle in `materialTexture`; it performs no optical distortion.
- [ ] Compile a separate `cropProgram` and render it to framebuffer 0 after the official material pass.
- [ ] Keep partial-coverage scissor/transparent behavior in the final output stage.
- [ ] Remove `u_dockUvRect` uploads from the official material program.
- [ ] Run full unit tests and commit.

### Task 4: Reconcile overscan guard with official-only optics

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java`
- Modify: related overscan tests under `src/test/java/com/hellovoid/liquiddock/`

**Interfaces:**
- Consumes: official Prismal parameters and visible glass dimensions.
- Produces: conservative `requiredSampleGuardPx()` without legacy-model terms.

- [ ] Delete legacy lens/parallax/Snell/bulge guard terms.
- [ ] Keep conservative reach for official lens, parallax, Snell, official bulge, chromatic aberration, reflection, backdrop scaling, and blur halo.
- [ ] Update existing source-shape tests only where they explicitly reference removed legacy symbols; preserve behavior checks for GL texture-limit and asymmetric insets.
- [ ] Run full unit tests and commit.

### Task 5: Final verification and artifact

**Files:**
- Update: Draft PR description only after verification.

**Interfaces:**
- Consumes: final branch HEAD.
- Produces: verified debug APK and target-device diagnostic instructions.

- [ ] Run standard GitHub Actions workflow on final HEAD.
- [ ] Require `testDebugUnitTest` success.
- [ ] Require `assembleDebug` success.
- [ ] Require artifact upload success.
- [ ] Download artifact, extract APK, compute SHA-256.
- [ ] Keep architectural PR Draft and unmerged; report exact `[DC][PRISMAL-MAP]` lines needed for target-device validation.