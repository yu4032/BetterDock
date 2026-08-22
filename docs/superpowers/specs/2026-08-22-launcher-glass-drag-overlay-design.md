# Unified Launcher Glass Drag Overlay Design

## Goal

Replace per-folder drag-follow rendering with one launcher-root GPU overlay that can render the liquid-glass plate for any dragged launcher object: folders first, then widgets and icons without changing the rendering architecture.

## Constraints

- Folder and launcher drag glass remain GPU-only. No PixelCopy, ImageReader, Bitmap readback, `glReadPixels`, screen recording, or CPU wallpaper capture.
- Dock rendering is independent and must not be modified by launcher drag-overlay lifecycle or producer throttling.
- Reuse the existing launcher wallpaper-only PassBlur/OES source and Prismal optics.
- Keep the existing static folder glass path for non-dragging objects.
- Drag motion must follow the actual drag representation rather than a pre-drag FolderIcon sibling.
- No per-object TextureView creation while dragging. One overlay surface is reused.

## Architecture

`LauncherGlassSession` remains the launcher-root owner of the wallpaper-only OES producer and shared Prismal resources. It gains a single `LauncherGlassDragOverlay` output attached once to the stable Launcher root. The overlay is transparent, non-interactive, and sized to the root.

The overlay owns one active `LauncherGlassDragState` at a time:

```text
LauncherGlassDragState
  token/source identity
  source kind: FOLDER | WIDGET | ICON | UNKNOWN
  source bounds at drag start
  live drag bounds in root coordinates
  corner radius
  interaction/press state
```

The state is type-agnostic at rendering time. Folder/widget/icon-specific hooks only resolve lifecycle and geometry, then call the common coordinator.

## Drag lifecycle

1. Static material is rendered by its existing sink.
2. On MIUI drag activation, the source sink is suppressed and a drag state is opened in the root overlay.
3. During drag, overlay geometry follows the live drag representation. The static sink is not used for motion tracking.
4. Wallpaper sampling uses the existing cached OES frame. Moving the dragged object changes only source UV/atlas geometry; it does not require a new wallpaper producer frame.
5. On drop/cancel, the overlay renders through the final animation while the static sink remains hidden.
6. When the vendor drag state returns to normal, the overlay is cleared and the static sink is restored at its final desktop location.

## MIUI integration

Current reverse-engineering evidence shows FolderIcon background layers expose `onDragContainerBgAnimAlpha(boolean, boolean)`. The second boolean marks return to the normal state. Older recovery code already used this callback to distinguish DragContainer ownership and kept a root proxy visible while MIUI faded the original layer.

Phase 1 uses this proven folder lifecycle signal and discovers the live drag representation from the owner/background ancestry at runtime. The common overlay API does not depend on FolderIcon class names, so widget/icon bridges can later feed the same API.

## Rendering

The drag overlay is one TextureView/EGLSurface. Rendering reuses the launcher's wallpaper OES source and Prismal parameters. A drag move updates only the drag geometry and redraws the glass quad from the cached prepared backdrop. If the requested source rectangle leaves the prepared static atlas, the drag path renders directly from the normalized wallpaper source for that root-space rectangle instead of rebuilding every static folder tile.

The initial implementation may reuse `LauncherGlassSession`'s shared Prismal renderer and output texture. It must not allocate a new PassBlur producer or alter Dock bridge update mode.

## Extensibility

`LauncherGlassDragCoordinator` is the only API launcher object hooks use:

```java
begin(Object token, Kind kind, View source, float cornerRadiusPx)
update(Object token, RectF rootBounds, float scale, float rotation, float alpha)
end(Object token, boolean restoreStatic)
cancel(Object token)
```

`Kind` is metadata only; rendering behavior is driven by geometry. Widget and icon support therefore adds hook adapters, not renderer forks.

## Failure behavior

- If no stable launcher root or overlay surface exists, keep the vendor drag representation and do not suppress the static source.
- If drag geometry cannot be resolved for a frame, preserve the last valid overlay geometry rather than snapping back to the static icon.
- Overlay teardown must never unbind or pause Dock PassBlur.
- Root detach shuts down the overlay with the existing launcher session.

## Verification

- Contract tests verify a single root overlay, generic drag-state API, folder lifecycle bridge, and absence of per-drag TextureView creation.
- Existing folder startup/lifecycle/press/producer tests must remain green.
- `testDebugUnitTest` and `assembleDebug` must pass before device testing.
- Device validation focuses on long-distance drag following, drop/cancel restoration, Recents performance, Dock independence, and repeated drag cycles.