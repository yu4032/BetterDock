package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/** Keeps DragController bounds aligned with the live Launcher workspace for the 10x6 profile. */
final class HomeGridDragBoundsHook {
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static boolean installed;

    private HomeGridDragBoundsHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || !customGridEnabled || selectedProfile != HomeGridProfile.GRID_10X6) {
            return;
        }
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            hookDimension(launcher, "getScreenWidthForDragController", false);
            hookDimension(launcher, "getScreenHeightForDragController", true);
            installed = true;
        } catch (Throwable error) {
            MainHook.log("[DC] 10x6 DragController bounds unavailable: " + error);
        }
    }

    private static void hookDimension(Class<?> launcher, String methodName, boolean heightAxis)
            throws NoSuchMethodException {
        Method method = HookUtil.findMethodExact(launcher, methodName, new Class<?>[0]);
        Api101Bridge.module().hook(method)
                .setPriority(XposedInterface.PRIORITY_HIGHEST)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!(result instanceof Integer) || MainHook.isWorkstationMode()) return result;

                    View root = liveRoot(chain.getThisObject());
                    if (root == null) return result;
                    int actual = heightAxis ? root.getHeight() : root.getWidth();
                    return actual > 0 ? actual : result;
                });
    }

    private static View liveRoot(Object launcher) {
        try {
            Object workspace = HookUtil.getField(launcher, "mWorkspace");
            if (workspace instanceof View) {
                View view = (View) workspace;
                if (view.getWidth() > 0 && view.getHeight() > 0) return view;
            }
        } catch (Throwable ignored) {}

        try {
            Object root = HookUtil.invoke(launcher, "getRootView");
            if (root instanceof View) {
                View view = (View) root;
                if (view.getWidth() > 0 && view.getHeight() > 0) return view;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
