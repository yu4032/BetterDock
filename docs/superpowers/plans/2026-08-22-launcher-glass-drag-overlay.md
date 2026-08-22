# Launcher Glass Drag Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one launcher-root GPU drag overlay that can carry liquid glass for folders now and widgets/icons later, without making static folder sinks chase MIUI drag transforms.

**Architecture:** `LauncherGlassSession` owns one reusable `LauncherGlassDragOverlay`; launcher object hooks feed a generic `LauncherGlassDragCoordinator`. Folder integration uses the already-observed `onDragContainerBgAnimAlpha(boolean, boolean)` lifecycle and resolves live drag geometry separately from the static material. The overlay reuses cached launcher wallpaper/Prismal resources and never changes Dock PassBlur ownership.

**Tech Stack:** Android Java, LSPosed-style reflection hooks, TextureView/EGL ES 2.0, SurfaceTexture OES, existing Prismal renderer, JUnit contract/unit tests, GitHub Actions Gradle CI.

**Spec:** `docs/superpowers/specs/2026-08-22-launcher-glass-drag-overlay-design.md`

## Global Constraints

- GPU-only wallpaper path: no PixelCopy, ImageReader, Bitmap readback, `glReadPixels`, or screen capture.
- One reusable drag overlay per Launcher root; no per-object drag TextureView creation.
- Dock producer/rendering remains independent.
- Static folder glass remains unchanged outside drag.
- Rendering API is generic across folder/widget/icon sources.

---

### Task 1: Generic drag state and coordinator contract

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassDragState.java`
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassDragCoordinator.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassDragCoordinatorTest.java`

**Interfaces:**
- Produces: `LauncherGlassDragState.Kind`, immutable geometry snapshot, `begin`, `update`, `end`, `cancel`, `current`.

- [ ] Write tests proving token ownership, kind-agnostic geometry updates, stale-token rejection, and end/cancel clearing.
- [ ] Run `./gradlew testDebugUnitTest --tests '*LauncherGlassDragCoordinatorTest'` and verify RED because classes are missing.
- [ ] Implement only the state/coordinator logic with no Android rendering dependency beyond `RectF`.
- [ ] Run focused test and verify GREEN.

### Task 2: Single root drag overlay lifecycle

**Files:**
- Create: `src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSessionRegistry.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassDragOverlayContractTest.java`

**Interfaces:**
- Consumes: `LauncherGlassDragCoordinator`.
- Produces: exactly one overlay attached to stable root and owned/shut down with session.

- [ ] Write contract tests requiring one session-owned overlay, root-sized transparent/non-clickable TextureView, and no new PassBlur binding inside overlay.
- [ ] Verify RED.
- [ ] Implement overlay creation/attachment, surface lifecycle, and session delegation. Initial renderer may draw through session-owned GL resources; overlay must not create a second producer.
- [ ] Verify focused tests GREEN.

### Task 3: Drag-only render path using cached wallpaper

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java`
- Test: `src/test/java/com/hellovoid/liquiddock/LauncherGlassDragRenderContractTest.java`

**Interfaces:**
- Consumes: current drag geometry.
- Produces: `updateDragGlass`, `clearDragGlass`; drag moves request redraw without `requestSingleUpdate` and without static `LauncherGlassGeometryStability`.

- [ ] Write RED contracts that drag updates bypass static geometry stability, do not request a new producer frame, and render into one overlay EGLSurface.
- [ ] Implement minimal drag render path using the last consumed wallpaper texture and current Prismal params.
- [ ] Keep static atlas/backdrop caching intact; drag movement only changes sample/crop geometry.
- [ ] Verify focused tests GREEN.

### Task 4: Folder lifecycle bridge to generic overlay

**Files:**
- Modify: `src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java`
- Modify: `src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java`
- Test: `src/test/java/com/hellovoid/liquiddock/FolderDragOverlayContractTest.java`

**Interfaces:**
- Uses: `onDragContainerBgAnimAlpha(boolean, boolean)` when available; fallback remains existing attach/recovery lifecycle.
- Produces: begin/update/end calls keyed by FolderIcon owner; static sink suppressed only while overlay is active.

- [ ] Write RED contracts requiring folder hook to install drag lifecycle hook and route through generic coordinator rather than moving the static sink.
- [ ] Implement lifecycle bridge: begin on drag state, continuously resolve root-space live drag bounds, end when normal state returns, restore static sink after final placement.
- [ ] Remove DragContainer-specific “treat every preDraw as local static movement” behavior from `LauncherGlassSinkView`; the overlay now owns drag following.
- [ ] Verify focused tests GREEN.

### Task 5: Extensibility seam and full regression

**Files:**
- Create: `src/test/java/com/hellovoid/liquiddock/LauncherGlassDragExtensibilityContractTest.java`
- Modify only if required by tests: coordinator/state files.

**Interfaces:**
- Ensures `Kind.WIDGET` and `Kind.ICON` use the same coordinator/render API and no folder-only renderer exists.

- [ ] Add tests constructing folder/widget/icon states through the same API.
- [ ] Verify focused tests GREEN.
- [ ] Run `./gradlew testDebugUnitTest --stacktrace`.
- [ ] Run `./gradlew assembleDebug --stacktrace`.
- [ ] Confirm existing Dock/PassBlur/static folder contracts remain green and inspect PR diff for accidental Dock changes.