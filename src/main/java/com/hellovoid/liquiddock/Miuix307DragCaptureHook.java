package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * name after the runtime startDrag overload returns, feed it to DockLiquidGlassView.setDockDragging,
 * and clear it at endDrag(). No gesture, Recents, lifecycle, or ownership hook is installed here.
 */
final class Miuix307DragCaptureHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile WeakReference<View> backgroundRef = new WeakReference<>(null);

    // A vendor startDrag overload may delegate to another startDrag overload. Treat those nested
    // callbacks as one drag session, but allow a later callback to upgrade a temporarily-null
    // Surface name to the concrete "drag surface#..." name.
    private static volatile boolean dragActive;
    private static volatile String activeDragLayerName;

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

            // The no-arg endDrag callback is device-proven to execute on 307. Keep that precise
            // hook instead of broadening the end side unnecessarily.
            HookUtil.hookMethod(dragController, "endDrag", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        onEndDrag();
                        return result;
                    });
            MainHook.log(TAG + " drag-only capture hook installed startOverloads=" + startHooks);
        } catch (Throwable error) {
            MainHook.log(TAG + " drag-only capture hook unavailable: " + error);
        }
    }

    private static void onStartDrag(Object dragController, String signature) {
        DockLiquidGlassView glass = currentGlass();
        String dragLayerName = resolveDragSurfaceLayerName(dragController);
        if (glass == null) {
            MainHook.log(TAG + " drag start method=" + signature
                    + " glass=null exclude=" + dragLayerName);
            return;
        }

        boolean firstCallback = !dragActive;
        boolean betterLayer = dragLayerName != null && !dragLayerName.isEmpty()
                && !Objects.equals(activeDragLayerName, dragLayerName);
        if (!firstCallback && !betterLayer) return;

        dragActive = true;
        if (betterLayer) activeDragLayerName = dragLayerName;
        glass.setDockDragging(true, activeDragLayerName);
        MainHook.log(TAG + " drag start method=" + signature
                + " exclude=" + activeDragLayerName);
    }

    private static void onEndDrag() {
        if (!dragActive && activeDragLayerName == null) return;
        DockLiquidGlassView glass = currentGlass();
        if (glass != null) glass.setDockDragging(false, null);
        dragActive = false;
        activeDragLayerName = null;
        MainHook.log(TAG + " drag end");
    }

    /** Resolve the currently bound Prismal view from the MiuiX background's sibling host. */
    static DockLiquidGlassView currentGlass() {
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

    /** Extract the original "drag surface#..." SurfaceFlinger layer name. */
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
