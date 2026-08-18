package com.hellovoid.liquiddock;

import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.View;

import java.lang.reflect.Method;

/**
 * Read-only runtime probe for the compositor refraction entry discovered in HyperOS SystemUI.
 *
 * MiuiShaderChargeView in the device SystemUI reflects two vendor extensions on
 * SurfaceControl.Transaction: setChargeAnim(...) and setChargeAnimProp(...). The latter receives
 * separate General / Refraction / Particles / Lighting float arrays. This probe deliberately only
 * resolves their signatures and inspects SurfaceControl ownership; it never submits either vendor
 * transaction.
 */
final class Miuix307SurfaceRefractionProbe {
    private static final String TAG = "[DC][ZC][REFR]";
    private static boolean logged;
    private static SurfaceControl loggedChildSurface;
    private static int loggedChildWidth = -1;
    private static int loggedChildHeight = -1;

    private Miuix307SurfaceRefractionProbe() {}

    static synchronized void probe(View backdrop, View materialHost) {
        if (logged || backdrop == null || materialHost == null) return;
        logged = true;

        try {
            Class<?> floatType = Float.TYPE;
            Method setChargeAnim = SurfaceControl.Transaction.class.getMethod(
                    "setChargeAnim",
                    SurfaceControl.class,
                    Integer.TYPE,
                    floatType,
                    floatType,
                    floatType,
                    Boolean.TYPE);
            Method setChargeAnimProp = SurfaceControl.Transaction.class.getMethod(
                    "setChargeAnimProp",
                    SurfaceControl.class,
                    float[].class,
                    float[].class,
                    float[].class,
                    float[].class);
            Method surfaceViewGetSurfaceControl =
                    SurfaceView.class.getMethod("getSurfaceControl");

            Object backdropRoot = readViewRoot(backdrop);
            Object materialRoot = readViewRoot(materialHost);
            boolean rootShared = backdropRoot != null && backdropRoot == materialRoot;
            SurfaceControl backdropSurface = readRootSurfaceControl(backdropRoot, "backdrop");
            SurfaceControl materialSurface = readRootSurfaceControl(materialRoot, "materialHost");

            MainHook.log(TAG + " transaction refraction APIs available"
                    + " setChargeAnim=" + setChargeAnim.toGenericString()
                    + " setChargeAnimProp=" + setChargeAnimProp.toGenericString()
                    + " surfaceViewGetSurfaceControl="
                    + surfaceViewGetSurfaceControl.toGenericString()
                    + " backdropRoot=" + describeOwner(backdropRoot)
                    + " materialRoot=" + describeOwner(materialRoot)
                    + " rootShared=" + rootShared
                    + " backdropSurface=" + describeSurface(backdropSurface)
                    + " materialSurface=" + describeSurface(materialSurface));
        } catch (Throwable error) {
            MainHook.log(TAG + " transaction refraction APIs unavailable: " + error);
        }
    }

    static synchronized void probeChildSurface(SurfaceView childView, View materialHost) {
        if (childView == null || materialHost == null) return;
        try {
            SurfaceControl childSurface = childView.getSurfaceControl();
            Object materialRoot = readViewRoot(materialHost);
            SurfaceControl rootSurface = readRootSurfaceControl(materialRoot, "child-materialHost");
            boolean childValid = childSurface != null && childSurface.isValid();
            boolean childIndependent = childValid && rootSurface != null && childSurface != rootSurface;
            int width = childView.getWidth();
            int height = childView.getHeight();

            if (loggedChildSurface != childSurface
                    || loggedChildWidth != width
                    || loggedChildHeight != height) {
                loggedChildSurface = childSurface;
                loggedChildWidth = width;
                loggedChildHeight = height;
                MainHook.log(TAG + " child surface"
                        + " childIndependent=" + childIndependent
                        + " childValid=" + childValid
                        + " childSurface=" + describeSurface(childSurface)
                        + " rootSurface=" + describeSurface(rootSurface)
                        + " size=" + width + "x" + height);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " child surface probe unavailable: " + error);
        }
    }

    static synchronized void noteChildSurfaceDestroyed(SurfaceView childView) {
        SurfaceControl surface = null;
        try {
            if (childView != null) surface = childView.getSurfaceControl();
        } catch (Throwable ignored) {}
        MainHook.log(TAG + " child surface destroyed childSurface=" + describeSurface(surface));
        if (surface == loggedChildSurface) {
            loggedChildSurface = null;
            loggedChildWidth = -1;
            loggedChildHeight = -1;
        }
    }

    private static Object readViewRoot(View view) {
        try {
            Method getViewRootImpl = View.class.getDeclaredMethod("getViewRootImpl");
            getViewRootImpl.setAccessible(true);
            return getViewRootImpl.invoke(view);
        } catch (Throwable error) {
            MainHook.log(TAG + " getViewRootImpl unavailable for "
                    + view.getClass().getSimpleName() + ": " + error);
            return null;
        }
    }

    private static SurfaceControl readRootSurfaceControl(Object viewRoot, String owner) {
        if (viewRoot == null) return null;
        try {
            Method getSurfaceControl = viewRoot.getClass().getMethod("getSurfaceControl");
            Object value = getSurfaceControl.invoke(viewRoot);
            return value instanceof SurfaceControl ? (SurfaceControl) value : null;
        } catch (Throwable error) {
            MainHook.log(TAG + " root SurfaceControl unavailable for " + owner + ": " + error);
            return null;
        }
    }

    private static String describeOwner(Object owner) {
        if (owner == null) return "null";
        return owner.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(owner));
    }

    private static String describeSurface(SurfaceControl surfaceControl) {
        if (surfaceControl == null) return "null";
        return surfaceControl.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(surfaceControl));
    }
}
