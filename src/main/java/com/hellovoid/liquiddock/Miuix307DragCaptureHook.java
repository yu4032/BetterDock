package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Narrow compatibility bridge for the Dock-drag capture behavior that predates the 307 path.
 *
 * The specialized MiuiX pipeline intentionally bypasses MainHook.installLiquidGlassCaptureHooks,
 * but that also bypasses the proven DragController start/end hooks. During an icon drag the
 * moving icon lives on its own drag Surface, outside the Floating Dock window, so excluding only
 * the Dock window is insufficient. Reuse the original behavior here: resolve that drag Surface
 * name after startDrag(), feed it to DockLiquidGlassView.setDockDragging(), and clear it at
 * endDrag(). No gesture, Recents, lifecycle, or ownership hook is installed by this class.
 */
final class Miuix307DragCaptureHook {
    private static final String TAG = "[DC][MG]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile WeakReference<View> backgroundRef = new WeakReference<>(null);

    private Miuix307DragCaptureHook() {}

    static void bind(View background) {
        backgroundRef = new WeakReference<>(background);
    }

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Class<?> dragController = Class.forName(
                    "com.miui.home.launcher.DragController", false, classLoader);
            Class<?> itemInfo = Class.forName(
                    "com.miui.home.launcher.ItemInfo", false, classLoader);
            Class<?> dragSource = Class.forName(
                    "com.miui.home.launcher.DragSource", false, classLoader);

            HookUtil.hookMethod(dragController, "startDrag",
                    new Class<?>[]{
                            android.graphics.drawable.Drawable.class, boolean.class,
                            itemInfo, int.class, int.class, float.class, dragSource, int.class
                    },
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = currentGlass();
                        if (glass != null) {
                            String dragLayerName = resolveDragSurfaceLayerName(chain.getThisObject());
                            glass.setDockDragging(true, dragLayerName);
                            MainHook.log(TAG + " drag start exclude=" + dragLayerName);
                        }
                        return result;
                    });

            HookUtil.hookMethod(dragController, "endDrag", new Class<?>[0],
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        DockLiquidGlassView glass = currentGlass();
                        if (glass != null) {
                            glass.setDockDragging(false, null);
                            MainHook.log(TAG + " drag end");
                        }
                        return result;
                    });
            MainHook.log(TAG + " drag-only capture hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " drag-only capture hook unavailable: " + error);
        }
    }

    /** Resolve the currently bound Prismal view from the MiuiX background's sibling host. */
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
}
