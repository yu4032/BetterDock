package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;

/**
 * 10x6-only correction for DragController's stale screen clamp after rotation.
 *
 * Launcher DEX inspection shows DragController.onTouchEvent clamps MotionEvent X/Y against
 * Launcher.getScreenWidthForDragController()/getScreenHeightForDragController() before writing
 * DragObject.x/y.  Those callbacks normally delegate to DeviceConfig screen dimensions.  On the
 * affected portrait rotation DeviceConfig reports the stale 1880px landscape height while the live
 * Launcher/Workspace is 1880x3008, so Y is capped at 1879 before CellScreen or CellLayout sees it.
 *
 * Keep the fix at the callback boundary: return the live Launcher root/Workspace dimensions only
 * for the experimental 10x6 profile.  Do not mutate DeviceConfig, DragObject, occupancy, or DB.
 */
final class HomeGridDragBoundsHook {
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";

    private static boolean installed;
    private static int lastLoggedWidthFrom = Integer.MIN_VALUE;
    private static int lastLoggedWidthTo = Integer.MIN_VALUE;
    private static int lastLoggedHeightFrom = Integer.MIN_VALUE;
    private static int lastLoggedHeightTo = Integer.MIN_VALUE;

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
            MainHook.log("[DC][GRID10] live DragController bounds installed");
        } catch (Throwable error) {
            MainHook.log("[DC][GRID10] live DragController bounds unavailable: " + error);
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

                    int current = (Integer) result;
                    View root = liveRoot(chain.getThisObject());
                    if (root == null) return result;
                    int actual = heightAxis ? root.getHeight() : root.getWidth();
                    if (actual <= 0 || actual == current) return result;

                    logRewriteOnce(methodName, heightAxis, current, actual, root);
                    return actual;
                });
    }

    /**
     * Workspace is the DragController target coordinate domain and is known to fill the Launcher
     * root on the affected tablet. Prefer it when attached/measured; fall back to Launcher root.
     */
    private static View liveRoot(Object launcher) {
        try {
            Object workspace = HookUtil.getField(launcher, "mWorkspace");
            if (workspace instanceof View) {
                View view = (View) workspace;
                if (view.getWidth() > 0 && view.getHeight() > 0) return view;
            }
        } catch (Throwable ignored) {}

        try {
            Object rootValue = HookUtil.invoke(launcher, "getRootView");
            if (rootValue instanceof View) {
                View root = (View) rootValue;
                if (root.getWidth() > 0 && root.getHeight() > 0) return root;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void logRewriteOnce(String methodName, boolean heightAxis,
                                       int current, int actual, View root) {
        if (heightAxis) {
            if (lastLoggedHeightFrom == current && lastLoggedHeightTo == actual) return;
            lastLoggedHeightFrom = current;
            lastLoggedHeightTo = actual;
        } else {
            if (lastLoggedWidthFrom == current && lastLoggedWidthTo == actual) return;
            lastLoggedWidthFrom = current;
            lastLoggedWidthTo = actual;
        }
        MainHook.log("[DC][GRID10] drag bounds " + methodName + " "
                + current + "->" + actual + " live="
                + root.getWidth() + "x" + root.getHeight());
    }
}
