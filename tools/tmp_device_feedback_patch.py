from pathlib import Path


def patch(path, old, new, count=1):
    p = Path(path)
    s = p.read_text()
    found = s.count(old)
    if found != count:
        raise SystemExit(f"{path}: expected {count} matches, found {found}")
    p.write_text(s.replace(old, new, count))


schema = 'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java'
patch(schema, '"sq_stroke_w", 1, 4, 4, 1, 10,', '"sq_stroke_w", 1, 4, 4, 0, 10,')
patch(schema, '"stroke_w", 1, 2, 2, 1, 6,', '"stroke_w", 1, 2, 2, 0, 6,')
patch(schema, '"std_stroke_w", 1, 4, 4, 1, 10,', '"std_stroke_w", 1, 4, 4, 0, 10,')

# Keep the launcher-owned drag SurfaceControl and pass it to the capture layer.
drag = 'src/main/java/com/hellovoid/liquiddock/Miuix307DragCaptureHook.java'
patch(drag, 'import android.view.ViewGroup;\n', 'import android.view.ViewGroup;\nimport android.view.SurfaceControl;\n')
patch(drag,
      '    private static volatile boolean dragActive;\n'
      '    private static volatile String activeDragLayerName;\n'
      '    private static volatile long dragSessionId;\n',
      '    private static volatile boolean dragActive;\n'
      '    private static volatile String activeDragLayerName;\n'
      '    private static volatile SurfaceControl activeDragSurface;\n'
      '    private static volatile long dragSessionId;\n')
patch(drag,
      '        DockLiquidGlassView glass = currentGlass();\n'
      '        String dragLayerName = resolveDragSurfaceLayerName(dragController);\n',
      '        DockLiquidGlassView glass = currentGlass();\n'
      '        SurfaceControl dragSurface = resolveDragSurfaceControl(dragController);\n'
      '        String dragLayerName = surfaceLayerName(dragSurface);\n')
patch(drag,
      '        boolean betterLayer = dragLayerName != null && !dragLayerName.isEmpty()\n'
      '                && !Objects.equals(activeDragLayerName, dragLayerName);\n'
      '        if (!firstCallback && !betterLayer) return;\n',
      '        boolean betterLayer = dragLayerName != null && !dragLayerName.isEmpty()\n'
      '                && !Objects.equals(activeDragLayerName, dragLayerName);\n'
      '        boolean betterSurface = isValidSurface(dragSurface)\n'
      '                && activeDragSurface != dragSurface;\n'
      '        if (!firstCallback && !betterLayer && !betterSurface) return;\n')
patch(drag,
      '        if (betterLayer) activeDragLayerName = dragLayerName;\n'
      '        glass.setDockDragging(true, activeDragLayerName);\n',
      '        if (betterLayer) activeDragLayerName = dragLayerName;\n'
      '        if (betterSurface) activeDragSurface = dragSurface;\n'
      '        glass.setDockDragging(true, activeDragLayerName, activeDragSurface);\n')
patch(drag,
      '        if (firstCallback && activeDragLayerName == null) {\n',
      '        if (firstCallback && !isValidSurface(activeDragSurface)) {\n')
patch(drag,
      '            String dragLayerName = resolveDragSurfaceLayerName(dragController);\n'
      '            if (dragLayerName != null && !dragLayerName.isEmpty()) {\n'
      '                activeDragLayerName = dragLayerName;\n'
      '                DockLiquidGlassView glass = currentGlass();\n'
      '                if (glass != null) glass.setDockDragging(true, dragLayerName);\n'
      '                MainHook.log(TAG + " drag surface retry attempt=" + attempt\n'
      '                        + " exclude=" + dragLayerName);\n'
      '                return;\n'
      '            }\n',
      '            SurfaceControl dragSurface = resolveDragSurfaceControl(dragController);\n'
      '            String dragLayerName = surfaceLayerName(dragSurface);\n'
      '            if (isValidSurface(dragSurface)) {\n'
      '                activeDragSurface = dragSurface;\n'
      '                if (dragLayerName != null && !dragLayerName.isEmpty()) {\n'
      '                    activeDragLayerName = dragLayerName;\n'
      '                }\n'
      '                DockLiquidGlassView glass = currentGlass();\n'
      '                if (glass != null) {\n'
      '                    glass.setDockDragging(true, activeDragLayerName, activeDragSurface);\n'
      '                }\n'
      '                MainHook.log(TAG + " drag surface retry attempt=" + attempt\n'
      '                        + " exclude=" + activeDragLayerName + " handle=true");\n'
      '                return;\n'
      '            }\n')
patch(drag,
      '        if (glass != null) glass.setDockDragging(false, null);\n'
      '        dragActive = false;\n'
      '        activeDragLayerName = null;\n',
      '        if (glass != null) glass.setDockDragging(false, null, null);\n'
      '        dragActive = false;\n'
      '        activeDragLayerName = null;\n'
      '        activeDragSurface = null;\n')
old_resolver = '''    /** Extract the original "drag surface#..." SurfaceFlinger layer name. */
    private static String resolveDragSurfaceLayerName(Object dragController) {
        try {
            Object dragObject = HookUtil.getField(dragController, "mDragObject");
            if (dragObject == null) return null;
            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (!(views instanceof List) || ((List<?>) views).isEmpty()) return null;
            Object dragView = ((List<?>) views).get(0);
            if (!(dragView instanceof View)) return null;

            Method getSurfaceControl = View.class.getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(dragView);
            if (surface == null) return null;

            String value = surface.toString();
            int start = value.indexOf("name=");
            int end = value.indexOf(')', start);
            if (start < 0 || end <= start) return null;
            return value.substring(start + 5, end);
        } catch (Throwable error) {
            MainHook.log(TAG + " drag surface resolve failed: " + error);
            return null;
        }
    }
'''
new_resolver = '''    /** Resolve the launcher-owned drag SurfaceControl without taking ownership of it. */
    private static SurfaceControl resolveDragSurfaceControl(Object dragController) {
        try {
            Object dragObject = HookUtil.getField(dragController, "mDragObject");
            if (dragObject == null) return null;
            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (!(views instanceof List) || ((List<?>) views).isEmpty()) return null;
            Object dragView = ((List<?>) views).get(0);
            if (!(dragView instanceof View)) return null;

            Method getSurfaceControl = View.class.getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(dragView);
            return surface instanceof SurfaceControl && isValidSurface((SurfaceControl) surface)
                    ? (SurfaceControl) surface : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " drag surface resolve failed: " + error);
            return null;
        }
    }

    private static boolean isValidSurface(SurfaceControl surface) {
        if (surface == null) return false;
        try { return surface.isValid(); }
        catch (Throwable ignored) { return false; }
    }

    private static String surfaceLayerName(SurfaceControl surface) {
        if (!isValidSurface(surface)) return null;
        String value = surface.toString();
        int start = value.indexOf("name=");
        int end = value.indexOf(')', start);
        if (start < 0 || end <= start) return null;
        return value.substring(start + 5, end);
    }
'''
patch(drag, old_resolver, new_resolver)

# Preserve the legacy 2-arg entry, add a strong SurfaceControl entry for 307.
glass = 'src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java'
patch(glass,
      '    private volatile boolean dockDragging = false;\n'
      '    private volatile String dragLayerName = null;\n',
      '    private volatile boolean dockDragging = false;\n'
      '    private volatile String dragLayerName = null;\n'
      '    private volatile android.view.SurfaceControl dragSurfaceControl = null;\n')
old_set_drag = '''    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {
        dockDragging = dragging;
        dragLayerName = dragging ? dragSurfaceLayerName : null;
        if (dragging) {
            resetCaptureCircuit("drag-start");
            beginObservationBurst();
            observationValid = false;
            requestStateCapture("drag-start");
        }
    }
'''
new_set_drag = '''    void setDockDragging(boolean dragging, String dragSurfaceLayerName) {
        setDockDragging(dragging, dragSurfaceLayerName, null);
    }

    void setDockDragging(boolean dragging, String dragSurfaceLayerName,
                         android.view.SurfaceControl dragSurface) {
        dockDragging = dragging;
        dragLayerName = dragging ? dragSurfaceLayerName : null;
        dragSurfaceControl = dragging && isValidCaptureSurface(dragSurface) ? dragSurface : null;
        if (dragging) {
            resetCaptureCircuit("drag-start");
            beginObservationBurst();
            observationValid = false;
            requestStateCapture("drag-start");
        }
    }

    private static boolean isValidCaptureSurface(android.view.SurfaceControl surface) {
        if (surface == null) return false;
        try { return surface.isValid(); }
        catch (Throwable ignored) { return false; }
    }

    private android.view.SurfaceControl[] buildFullDisplaySurfaceExcludes() {
        java.util.ArrayList<android.view.SurfaceControl> out = new java.util.ArrayList<>(2);
        if (isValidCaptureSurface(dockWindowSurface)) out.add(dockWindowSurface);
        android.view.SurfaceControl drag = dragSurfaceControl;
        if (isValidCaptureSurface(drag) && drag != dockWindowSurface) out.add(drag);
        return out.isEmpty() ? null : out.toArray(new android.view.SurfaceControl[0]);
    }
'''
patch(glass, old_set_drag, new_set_drag)
old_excludes = '''                    android.view.SurfaceControl[] excludes = null;
                    if ((requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                            || (workstationMode
                                && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                            && dockWindowSurface != null) {
                        excludes = new android.view.SurfaceControl[]{dockWindowSurface};
                    }
'''
new_excludes = '''                    android.view.SurfaceControl[] excludes =
                            (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                                    || (workstationMode
                                        && requestedSource == CaptureSourcePolicy.Source.LOCAL_LAYER))
                                    ? buildFullDisplaySurfaceExcludes() : null;
'''
patch(glass, old_excludes, new_excludes)

# Theme/icon-pack rebuilds can span multiple loop turns; use attachment + real layout boundary.
pipeline = 'src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java'
patch(pipeline, 'import android.view.ViewGroup;\n',
      'import android.view.ViewGroup;\nimport android.view.ViewTreeObserver;\n')
patch(pipeline,
      '    private static View.OnAttachStateChangeListener hierarchyListener;\n'
      '    private static boolean hierarchyRebindPosted;\n',
      '    private static View.OnAttachStateChangeListener hierarchyListener;\n'
      '    private static boolean hierarchyRebindPosted;\n'
      '    private static ViewTreeObserver hierarchyRecoveryObserver;\n'
      '    private static ViewTreeObserver.OnGlobalLayoutListener hierarchyRecoveryListener;\n')
old_schedule = '''    /**
     * Coalesce a theme/hierarchy burst into one next-main-turn repair. If the vendor replacement
     * background is not parented yet, stop here; setupViews or a real geometry callback will
     * retry later. This deliberately has no delayed polling loop.
     */
    private static void scheduleHierarchyRebind(
            LiquidDockConfig config, ClassLoader classLoader) {
        if (hierarchyRebindPosted) return;
        hierarchyRebindPosted = true;
        if (MAIN_HANDLER == null) {
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
        }
        MAIN_HANDLER.post(() -> {
            hierarchyRebindPosted = false;
            Object hotSeats = hotSeatsRef.get();
            if (hotSeats == null) {
                MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; HotSeats owner gone");
                return;
            }
            View currentBackground = resolveBackground(hotSeats);
            if (currentBackground == null || !(currentBackground.getParent() instanceof ViewGroup)) {
                MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; background not ready");
                return;
            }
            if (!ensureGlassBound(currentBackground, config, classLoader)) {
                MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; install not ready");
            }
        });
    }
'''
new_schedule = '''    /**
     * Coalesce a theme/hierarchy burst into one next-main-turn repair. Theme/icon changes can
     * leave the old background discoverable with a parent while it is already detached, so a
     * parent check alone is not authoritative. If the new hierarchy is not attached yet, wait
     * for a real global-layout event instead of polling with an arbitrary delay.
     */
    private static void scheduleHierarchyRebind(
            LiquidDockConfig config, ClassLoader classLoader) {
        if (hierarchyRebindPosted) return;
        hierarchyRebindPosted = true;
        if (MAIN_HANDLER == null) {
            MAIN_HANDLER = new Handler(Looper.getMainLooper());
        }
        MAIN_HANDLER.post(() -> {
            hierarchyRebindPosted = false;
            if (tryHierarchyRebind(config, classLoader)) {
                clearHierarchyLayoutRecovery();
                return;
            }
            armHierarchyLayoutRecovery(config, classLoader);
        });
    }

    private static boolean tryHierarchyRebind(
            LiquidDockConfig config, ClassLoader classLoader) {
        Object hotSeats = hotSeatsRef.get();
        if (hotSeats == null) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; HotSeats owner gone");
            return false;
        }
        View currentBackground = resolveBackground(hotSeats);
        if (currentBackground == null || !currentBackground.isAttachedToWindow()
                || !(currentBackground.getParent() instanceof ViewGroup)) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; background not attached");
            return false;
        }
        if (!ensureGlassBound(currentBackground, config, classLoader)) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; install not ready");
            return false;
        }
        View host = resolveBoundHost(currentBackground);
        if (host == null || !host.isAttachedToWindow()) {
            MainHook.log("[DC] MiuiX 307 hierarchy rebind deferred; host not attached");
            return false;
        }
        MainHook.log("[DC] MiuiX 307 hierarchy rebind complete after theme/layout change");
        return true;
    }

    private static void armHierarchyLayoutRecovery(
            LiquidDockConfig config, ClassLoader classLoader) {
        Object hotSeats = hotSeatsRef.get();
        if (!(hotSeats instanceof View)) return;
        View owner = (View) hotSeats;
        View root = owner.getRootView();
        ViewTreeObserver observer = (root != null ? root : owner).getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        if (hierarchyRecoveryObserver == observer && hierarchyRecoveryListener != null) return;

        clearHierarchyLayoutRecovery();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            if (tryHierarchyRebind(config, classLoader)) {
                clearHierarchyLayoutRecovery();
            }
        };
        observer.addOnGlobalLayoutListener(listener);
        hierarchyRecoveryObserver = observer;
        hierarchyRecoveryListener = listener;
        MainHook.log("[DC] MiuiX 307 hierarchy recovery armed for next real layout");
    }

    private static void clearHierarchyLayoutRecovery() {
        ViewTreeObserver observer = hierarchyRecoveryObserver;
        ViewTreeObserver.OnGlobalLayoutListener listener = hierarchyRecoveryListener;
        hierarchyRecoveryObserver = null;
        hierarchyRecoveryListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Throwable ignored) {}
    }
'''
patch(pipeline, old_schedule, new_schedule)
