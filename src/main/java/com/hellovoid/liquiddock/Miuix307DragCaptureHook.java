package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceControl;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Narrow compatibility bridge for the Dock-drag capture behavior that predates the 307 path.
 *
 * The specialized MiuiX pipeline intentionally bypasses MainHook.installLiquidGlassCaptureHooks,
 * but that also bypasses the proven DragController start/end hooks. During an icon drag the
 * moving icon lives on its own drag Surface, outside the Floating Dock window, so excluding only
 * the Dock window is insufficient. Reuse the original behavior here: resolve that drag Surface
 * name after the runtime startDrag overload returns, briefly retry on later animation frames when
 * the vendor creates the Surface asynchronously, feed it to DockLiquidGlassView.setDockDragging,
 * then keep the last clean backdrop frozen through MIUI's drop-settling animation. No gesture,
 * Recents, lifecycle, or ownership hook is installed here.
 */
final class Miuix307DragCaptureHook {
    private static final String TAG = "[DC][MG]";
    private static final int DRAG_SURFACE_RETRY_FRAMES = 12;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile WeakReference<View> backgroundRef = new WeakReference<>(null);

    private static volatile boolean dragActive;
    private static volatile String activeDragLayerName;
    private static volatile SurfaceControl activeDragSurface;
    private static volatile long dragSessionId;

    // endDrag is earlier than the real release animation. Keep the captured APP/HOME backdrop
    // frozen until MIUI's DragObject lifecycle completes. A retained DragView may stay attached
    // and report isShown() even after that authoritative callback, so View visibility is only a
    // fallback gate when the vendor finish hook/object is unavailable.
    private static volatile Object settlingDragObject;
    private static volatile List<WeakReference<View>> settlingDragViews = Collections.emptyList();
    private static volatile boolean dropSettling;
    private static volatile boolean dropReleaseScheduled;
    private static volatile boolean settlingDragViewCheckScheduled;
    private static volatile int settlingDropCallbacksRemaining;
    private static volatile boolean dropAnimationFinishHookInstalled;
    private static volatile boolean systemDockDragActive;

    private Miuix307DragCaptureHook() {}

    static void bind(View background) {
        backgroundRef = new WeakReference<>(background);
    }

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> dragController = Class.forName(
                    "com.miui.home.launcher.DragController", false, classLoader);

            int startHooks = 0;
            Class<?> cursor = dragController;
            while (cursor != null && cursor != Object.class) {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (!"startDrag".equals(method.getName())
                            || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    final String signature = methodSignature(method);
                    HookUtil.hook(method, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onStartDrag(chain.getThisObject(), signature);
                        return result;
                    });
                    startHooks++;
                }
                cursor = cursor.getSuperclass();
            }
            if (startHooks == 0) {
                throw new IllegalStateException("no instance startDrag overloads");
            }

            installDropAnimationFinishHook(classLoader);

            HookUtil.hookMethod(dragController, "endDrag", new Class<?>[0],
                    chain -> {
                        Object dragObject = currentDragObject(chain.getThisObject());
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onEndDrag(dragObject);
                        return result;
                    });
            installSystemDockDragHooks(classLoader);
            MainHook.log(TAG + " drag-only capture hook installed startOverloads=" + startHooks
                    + " dropAnimationHook=" + dropAnimationFinishHookInstalled);
        } catch (Throwable error) {
            MainHook.log(TAG + " drag-only capture hook unavailable: " + error);
        }
    }

    private static void installDropAnimationFinishHook(ClassLoader classLoader) {
        try {
            Class<?> dragObjectClass = Class.forName(
                    "com.miui.home.launcher.DragObject", false, classLoader);
            HookUtil.hookMethod(dragObjectClass, "onDropAnimationFinished", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onDropAnimationFinished(chain.getThisObject());
                        return result;
                    });
            dropAnimationFinishHookInstalled = true;
            MainHook.log(TAG + " drop animation lifecycle hook installed");
        } catch (Throwable error) {
            dropAnimationFinishHookInstalled = false;
            MainHook.log(TAG + " drop animation lifecycle hook unavailable: " + error);
        }
    }

    private static void installSystemDockDragHooks(ClassLoader classLoader) {
        try {
            Class<?> content = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeatsListContent", false, classLoader);
            HookUtil.hookMethod(content, "startDragInDockForSystem", new Class<?>[0], chain -> {
                setSystemDockDragActive(true);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } catch (Throwable error) {
                    setSystemDockDragActive(false);
                    throw error;
                }
            });
            HookUtil.hookMethod(content, "resetDraggingView", new Class<?>[0], chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                setSystemDockDragActive(false);
                onHotseatDragCleanup();
                return result;
            });

            Class<?> listenerInterface = Class.forName(
                    "android.view.IMiuiDragListener", false, classLoader);
            int listenerHooks = 0;
            for (int i = 1; i <= 16; i++) {
                try {
                    Class<?> candidate = Class.forName(
                            "com.miui.home.launcher.hotseats.HotSeatsListContent$" + i,
                            false, classLoader);
                    if (!listenerInterface.isAssignableFrom(candidate)) continue;
                    HookUtil.hookMethod(candidate, "onStart", new Class<?>[0], chain -> {
                        setSystemDockDragActive(true);
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
                    HookUtil.hookMethod(candidate, "onEnd", new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        setSystemDockDragActive(false);
                        return result;
                    });
                    listenerHooks++;
                } catch (ClassNotFoundException ignored) {
                }
            }
            MainHook.log(TAG + " system Dock drag freeze hooks installed listeners=" + listenerHooks);
        } catch (Throwable error) {
            MainHook.log(TAG + " system Dock drag freeze hook unavailable: " + error);
        }
    }

    private static void setSystemDockDragActive(boolean active) {
        if (systemDockDragActive == active) return;
        systemDockDragActive = active;
        DockLiquidGlassView glass = currentGlass();
        if (glass != null) glass.setSystemDockDragActive(active);
        MainHook.log(TAG + (active
                ? " system Dock drag start -> capture frozen"
                : " system Dock drag end -> capture resumed"));
    }

    private static void onStartDrag(Object dragController, String signature) {
        DockLiquidGlassView glass = currentGlass();
        SurfaceControl dragSurface = resolveDragSurfaceControl(dragController);
        String dragLayerName = surfaceLayerName(dragSurface);
        if (glass == null) {
            MainHook.log(TAG + " drag start method=" + signature
                    + " glass=null exclude=" + dragLayerName);
            return;
        }

        boolean firstCallback = !dragActive;
        boolean betterLayer = dragLayerName != null && !dragLayerName.isEmpty()
                && !Objects.equals(activeDragLayerName, dragLayerName);
        boolean betterSurface = isValidSurface(dragSurface)
                && activeDragSurface != dragSurface;
        if (!firstCallback && !betterLayer && !betterSurface) return;

        if (firstCallback) {
            dropSettling = false;
            dropReleaseScheduled = false;
            settlingDragViewCheckScheduled = false;
            settlingDragObject = null;
            settlingDragViews = Collections.emptyList();
            settlingDropCallbacksRemaining = 0;
            dragActive = true;
            dragSessionId++;
        }
        if (betterLayer) activeDragLayerName = dragLayerName;
        if (betterSurface) activeDragSurface = dragSurface;
        glass.setDockDragging(true, activeDragLayerName, activeDragSurface);
        MainHook.log(TAG + " drag start method=" + signature
                + " exclude=" + activeDragLayerName);

        if (firstCallback && !isValidSurface(activeDragSurface)) {
            scheduleDragSurfaceRetry(dragController, dragSessionId, 1);
        }
    }

    private static void scheduleDragSurfaceRetry(Object dragController, long sessionId,
                                                 int attempt) {
        if (!dragActive || activeDragLayerName != null || sessionId != dragSessionId) return;
        if (attempt > DRAG_SURFACE_RETRY_FRAMES) {
            MainHook.log(TAG + " drag surface retry exhausted attempts="
                    + DRAG_SURFACE_RETRY_FRAMES + " exclude=null");
            return;
        }

        View scheduler = backgroundRef.get();
        if (scheduler == null) return;
        scheduler.postOnAnimation(() -> {
            if (!dragActive || activeDragLayerName != null || sessionId != dragSessionId) return;

            SurfaceControl dragSurface = resolveDragSurfaceControl(dragController);
            String dragLayerName = surfaceLayerName(dragSurface);
            if (isValidSurface(dragSurface)) {
                activeDragSurface = dragSurface;
                if (dragLayerName != null && !dragLayerName.isEmpty()) {
                    activeDragLayerName = dragLayerName;
                }
                DockLiquidGlassView glass = currentGlass();
                if (glass != null) {
                    glass.setDockDragging(true, activeDragLayerName, activeDragSurface);
                }
                MainHook.log(TAG + " drag surface retry attempt=" + attempt
                        + " exclude=" + activeDragLayerName + " handle=true");
                return;
            }
            scheduleDragSurfaceRetry(dragController, sessionId, attempt + 1);
        });
    }

    /** Logical drag end. Physical drop settling may still be running after this boundary. */
    private static void onEndDrag(Object dragObject) {
        if (!dragActive && activeDragLayerName == null && activeDragSurface == null) return;

        dragActive = false;
        activeDragLayerName = null;
        activeDragSurface = null;
        dragSessionId++;

        settlingDragObject = dragObject;
        settlingDragViews = snapshotDragViews(dragObject);
        dropSettling = true;
        dropReleaseScheduled = false;
        settlingDragViewCheckScheduled = false;
        settlingDropCallbacksRemaining = Math.max(1, settlingDragViews.size());

        DockLiquidGlassView glass = currentGlass();
        if (glass != null) {
            glass.setDockDragging(true, null, null);
        }

        int dropAnimationCounter = dropAnimationFinishHookInstalled
                ? readDropAnimationCounter(dragObject) : 0;
        MainHook.log(TAG + " drag end -> drop settling counter=" + dropAnimationCounter
                + " callbacks=" + settlingDropCallbacksRemaining
                + " dragViews=" + settlingDragViews.size()
                + "; capture remains frozen");
    }

    /** Called after MIUI has updated mDropAnimationCounter for one DragView. */
    private static void onDropAnimationFinished(Object dragObject) {
        if (!dropSettling || dragObject == null || settlingDragObject != dragObject) return;

        if (settlingDropCallbacksRemaining > 0) {
            settlingDropCallbacksRemaining--;
        }
        int dropAnimationCounter = readDropAnimationCounter(dragObject);
        if (dropAnimationCounter > 0 || settlingDropCallbacksRemaining > 0) {
            MainHook.log(TAG + " drop animation finished; counter=" + dropAnimationCounter
                    + " callbacksRemaining=" + settlingDropCallbacksRemaining);
            return;
        }
        finishDropSettling("drag release anim end", true);
    }

    /**
     * resetDraggingView() is fallback-only when the real DragObject finish hook/object exists.
     * On 307 it can run before the release animation is actually complete.
     */
    private static void onHotseatDragCleanup() {
        if (!dropSettling || dragActive) return;
        if (dropAnimationFinishHookInstalled && settlingDragObject != null) {
            MainHook.log(TAG + " hotseat drag cleanup observed; waiting for DragObject animation end");
            return;
        }
        finishDropSettling("hotseat drag cleanup fallback", false);
    }

    /**
     * The real DragObject completion callback is authoritative even if its DragView remains
     * attached/shown for bookkeeping. That exact condition caused a permanent freeze when Dock
     * width changed during icon removal. Fallback cleanup still uses real View visibility before
     * crossing the final compositor frame.
     */
    private static void finishDropSettling(String reason, boolean vendorFinished) {
        if (!dropSettling || dropReleaseScheduled) return;

        if (!vendorFinished && hasVisibleSettlingDragView()) {
            scheduleSettlingDragViewCheck(reason);
            return;
        }

        final long releaseSession = dragSessionId;
        View scheduler = backgroundRef.get();
        if (scheduler == null) {
            MainHook.log(TAG + " " + reason + " -> compositor barrier unavailable; stay frozen");
            return;
        }

        dropReleaseScheduled = true;
        MainHook.log(TAG + " " + reason + " -> compositor barrier armed session="
                + releaseSession + " vendorFinished=" + vendorFinished);
        scheduler.postOnAnimation(() -> {
            if (!dropSettling || dragActive || releaseSession != dragSessionId) return;

            dropReleaseScheduled = false;
            settlingDragViewCheckScheduled = false;
            dropSettling = false;
            settlingDragObject = null;
            settlingDragViews = Collections.emptyList();
            settlingDropCallbacksRemaining = 0;
            MainHook.log(TAG + " compositor barrier passed session=" + releaseSession);
            finishDockDragCapture(reason);
        });
    }

    /** Recheck retained DragViews only for the lifecycle fallback path. */
    private static void scheduleSettlingDragViewCheck(String reason) {
        if (!dropSettling || dragActive || dropReleaseScheduled || settlingDragViewCheckScheduled) {
            return;
        }

        final long releaseSession = dragSessionId;
        View scheduler = backgroundRef.get();
        if (scheduler == null) {
            MainHook.log(TAG + " " + reason + " -> DragView visual check unavailable; stay frozen");
            return;
        }

        settlingDragViewCheckScheduled = true;
        MainHook.log(TAG + " " + reason + " -> DragView still visible; wait VSYNC session="
                + releaseSession);
        scheduler.postOnAnimation(() -> {
            if (!dropSettling || dragActive || releaseSession != dragSessionId) return;

            settlingDragViewCheckScheduled = false;
            if (hasVisibleSettlingDragView()) {
                scheduleSettlingDragViewCheck(reason);
                return;
            }

            MainHook.log(TAG + " " + reason + " -> DragView visually absent session="
                    + releaseSession);
            finishDropSettling(reason, false);
        });
    }

    private static boolean hasVisibleSettlingDragView() {
        List<WeakReference<View>> snapshot = settlingDragViews;
        for (WeakReference<View> reference : snapshot) {
            View view = reference != null ? reference.get() : null;
            if (view == null) continue;
            if (view.isAttachedToWindow()
                    && view.getVisibility() == View.VISIBLE
                    && view.isShown()
                    && view.getAlpha() > 0.01f) {
                return true;
            }
        }
        return false;
    }

    private static void finishDockDragCapture(String reason) {
        DockLiquidGlassView glass = currentGlass();
        if (glass != null) glass.setDockDragging(false, null, null);
        MainHook.log(TAG + " " + reason);
    }

    private static Object currentDragObject(Object dragController) {
        if (dragController == null) return null;
        try {
            return HookUtil.getField(dragController, "mDragObject");
        } catch (Throwable error) {
            MainHook.log(TAG + " current DragObject unavailable: " + error);
            return null;
        }
    }

    private static int readDropAnimationCounter(Object dragObject) {
        if (dragObject == null) return 0;
        try {
            Object value = HookUtil.getField(dragObject, "mDropAnimationCounter");
            return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
        } catch (Throwable error) {
            MainHook.log(TAG + " drop animation counter unavailable: " + error);
            return 0;
        }
    }

    private static List<WeakReference<View>> snapshotDragViews(Object dragObject) {
        if (dragObject == null) return Collections.emptyList();
        ArrayList<WeakReference<View>> result = new ArrayList<>();
        try {
            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (views instanceof List) {
                for (Object item : (List<?>) views) {
                    if (item instanceof View) result.add(new WeakReference<>((View) item));
                }
            } else if (views != null && views.getClass().isArray()) {
                int length = Array.getLength(views);
                for (int i = 0; i < length; i++) {
                    Object item = Array.get(views, i);
                    if (item instanceof View) result.add(new WeakReference<>((View) item));
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " drag view snapshot unavailable: " + error);
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }

    private static int countDragViews(Object dragObject) {
        if (dragObject == null) return 0;
        try {
            Object views = HookUtil.getField(dragObject, "mDragViews");
            if (views instanceof List) return ((List<?>) views).size();
            if (views != null && views.getClass().isArray()) return Array.getLength(views);
        } catch (Throwable error) {
            MainHook.log(TAG + " drag view count unavailable: " + error);
        }
        return 0;
    }

    private static DockLiquidGlassView currentGlass() {
        View background = backgroundRef.get();
        if (background == null) return null;
        android.view.ViewParent parent = background.getParent();
        if (!(parent instanceof View)) return null;
        return findGlass((View) parent);
    }

    private static DockLiquidGlassView findGlass(View root) {
        if (root instanceof DockLiquidGlassView) return (DockLiquidGlassView) root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            DockLiquidGlassView found = findGlass(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /** Resolve the launcher-owned drag SurfaceControl without taking ownership of it. */
    private static SurfaceControl resolveDragSurfaceControl(Object dragController) {
        try {
            Object dragObject = currentDragObject(dragController);
            if (dragObject == null) return null;
            Object views = HookUtil.getField(dragObject, "mDragViews");
            Object dragView;
            if (views instanceof List) {
                if (((List<?>) views).isEmpty()) return null;
                dragView = ((List<?>) views).get(0);
            } else if (views != null && views.getClass().isArray()) {
                if (Array.getLength(views) == 0) return null;
                dragView = Array.get(views, 0);
            } else {
                return null;
            }
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

    private static String methodSignature(Method method) {
        StringBuilder out = new StringBuilder();
        out.append(method.getDeclaringClass().getSimpleName())
                .append('#').append(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) out.append(',');
            out.append(parameters[i].getSimpleName());
        }
        return out.append(')').toString();
    }
}
