package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Constructor;
import java.util.WeakHashMap;

/**
 * Read-only runtime probe for large-folder and widget hosts.
 *
 * This class deliberately observes only ownership, lifecycle and geometry. It does not alter
 * any View property, hierarchy or compositor state, so probe output can be trusted before the
 * visual prototype is enabled.
 */
final class FolderWidgetGlassProbe {
    private static final String TAG = "[DC][FWGLASS][PROBE]";

    private static final String[] LARGE_FOLDER_CLASSES = new String[]{
            "com.miui.home.launcher.folder.FolderIcon4x4_16",
            "com.miui.home.launcher.folder.FolderIcon3x3_9",
            "com.miui.home.launcher.folder.FolderIcon2x2_4",
            "com.miui.home.launcher.folder.FolderIcon2x2_9",
            "com.miui.home.launcher.folder.FolderIcon2x2"
    };

    private static final String[] WIDGET_HOST_CLASSES = new String[]{
            "com.miui.home.launcher.LauncherAppWidgetHostView",
            "com.miui.home.launcher.maml.MaMlHostView"
    };

    private static final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();
    private static boolean installed;

    private FolderWidgetGlassProbe() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;

        for (String name : LARGE_FOLDER_CLASSES) {
            hookConstructors(classLoader, name, "folder");
        }
        for (String name : WIDGET_HOST_CLASSES) {
            hookConstructors(classLoader, name, "widget");
        }
        MainHook.log(TAG + " hooks installed");
    }

    private static void hookConstructors(ClassLoader classLoader, String className, String kind) {
        try {
            Class<?> target = Class.forName(className, false, classLoader);
            int count = 0;
            for (Constructor<?> constructor : target.getDeclaredConstructors()) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (!(owner instanceof View) && result instanceof View) owner = result;
                    if (owner instanceof View) register((View) owner, kind);
                    return result;
                });
                count++;
            }
            MainHook.log(TAG + " constructor hooks class=" + className + " count=" + count);
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable class=" + className + " error=" + error);
        }
    }

    private static void register(View view, String kind) {
        synchronized (observed) {
            if (observed.containsKey(view)) return;
            observed.put(view, Boolean.TRUE);
        }

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                logState(v, kind, "attach");
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                logState(v, kind, "detach");
            }
        });
        view.addOnLayoutChangeListener((v, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                logState(v, kind, "layout");
            }
        });

        logState(view, kind, view.isAttachedToWindow() ? "register-attached" : "register");
    }

    private static void logState(View view, String kind, String event) {
        int[] location = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE};
        try {
            view.getLocationOnScreen(location);
        } catch (Throwable ignored) {}

        int width = view.getWidth();
        int height = view.getHeight();
        int right = location[0] == Integer.MIN_VALUE ? Integer.MIN_VALUE : location[0] + width;
        int bottom = location[1] == Integer.MIN_VALUE ? Integer.MIN_VALUE : location[1] + height;
        ViewParent parent = view.getParent();
        float radius = resolveRadius(view);

        MainHook.log(TAG
                + " kind=" + kind
                + " event=" + event
                + " class=" + view.getClass().getName()
                + " parent=" + (parent == null ? "null" : parent.getClass().getName())
                + " attached=" + view.isAttachedToWindow()
                + " size=" + width + "x" + height
                + " screenRect=[" + location[0] + "," + location[1]
                + "-" + right + "," + bottom + "]"
                + " radius=" + radius);
    }

    private static float resolveRadius(View view) {
        // Both LauncherAppWidgetHostView and MaMlHostView expose computeRoundedCornerRadius().
        Object value = HookUtil.invoke(view, "computeRoundedCornerRadius");
        if (value instanceof Number) return ((Number) value).floatValue();

        for (String field : new String[]{"mCornerRadius", "mRadius"}) {
            try {
                Object candidate = HookUtil.getField(view, field);
                if (candidate instanceof Number) return ((Number) candidate).floatValue();
            } catch (Throwable ignored) {}
        }
        return Float.NaN;
    }
}
