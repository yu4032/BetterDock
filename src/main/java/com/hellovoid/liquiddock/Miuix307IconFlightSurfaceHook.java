package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Excludes Launcher 4.50's dedicated floating-icon Surface from APP->HOME mode-1 captures.
 *
 * Direct 4.50 DEX inspection shows WindowElement owns mFloatingIconLayerLeash as a real
 * SurfaceControl.  The CLOSE_TO_HOME FloatingIconView2 is attached to that layer, so excluding
 * the exact handle removes only the icon-flight overlay while the closing APP, Launcher and
 * wallpaper remain live in the compositor capture.
 */
final class Miuix307IconFlightSurfaceHook {
    private static final String TAG = "[DC][IX]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile SurfaceControl floatingIconLeash;
    private static volatile boolean homeTransitionActive;
    private static volatile int lastLoggedExcludedIdentity;

    private Miuix307IconFlightSurfaceHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installWindowElementLeashCapture(classLoader);
        installCaptureSurfaceExclusion();
        installTransitionLifecycle();
        Api101Bridge.log(TAG + " 4.50 exact floating-icon Surface exclusion installed");
    }

    private static void installWindowElementLeashCapture(ClassLoader classLoader) {
        try {
            Class<?> windowElement = Class.forName(
                    "com.miui.home.recents.anim.WindowElement", false, classLoader);
            int captureHooks = 0;
            int resetHooks = 0;
            for (Method method : windowElement.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                String name = method.getName();
                if ("bindIconLayerLeashIfNeeded".equals(name)
                        || "earlyInitFloatingIconLayer".equals(name)) {
                    HookUtil.hook(method, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        captureLeash(chain.getThisObject(), method.getName());
                        return result;
                    });
                    captureHooks++;
                } else if ("resetFloatingIcon".equals(name)) {
                    HookUtil.hook(method, chain -> {
                        Object owner = chain.getThisObject();
                        SurfaceControl before = readLeash(owner);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        SurfaceControl current = floatingIconLeash;
                        if (before != null && current == before) {
                            floatingIconLeash = null;
                            lastLoggedExcludedIdentity = 0;
                            Api101Bridge.log(TAG + " floating icon leash cleared by reset");
                        }
                        return result;
                    });
                    resetHooks++;
                }
            }
            Api101Bridge.log(TAG + " WindowElement leash hooks capture=" + captureHooks
                    + " reset=" + resetHooks);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " WindowElement floating-icon leash hook unavailable", error);
        }
    }

    private static void installCaptureSurfaceExclusion() {
        try {
            HookUtil.hookMethod(DockLiquidGlassView.class, "buildFullDisplaySurfaceExcludes",
                    new Class<?>[0], chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        SurfaceControl[] base = result instanceof SurfaceControl[]
                                ? (SurfaceControl[]) result : null;
                        if (!homeTransitionActive) return base;

                        SurfaceControl icon = floatingIconLeash;
                        if (!isValid(icon)) return base;
                        if (containsIdentity(base, icon)) return base;

                        ArrayList<SurfaceControl> out = new ArrayList<>(
                                base == null ? 1 : base.length + 1);
                        if (base != null) {
                            for (SurfaceControl surface : base) {
                                if (isValid(surface)) out.add(surface);
                            }
                        }
                        out.add(icon);

                        int identity = System.identityHashCode(icon);
                        if (identity != lastLoggedExcludedIdentity) {
                            lastLoggedExcludedIdentity = identity;
                            Api101Bridge.log(TAG + " exact floating icon Surface excluded surface="
                                    + surfaceId(icon) + " total=" + out.size());
                        }
                        return out.toArray(new SurfaceControl[0]);
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " capture Surface exclusion hook unavailable", error);
        }
    }

    private static void installTransitionLifecycle() {
        try {
            HookUtil.hookMethod(Miuix307GestureBackdropHoldHook.class,
                    "setSystemUiTransitionActive",
                    new Class<?>[]{DockLiquidGlassView.class, boolean.class, String.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        boolean active = args.length > 1 && Boolean.TRUE.equals(args[1]);
                        Object result = chain.proceed(args);
                        setHomeTransitionActive(active, String.valueOf(args.length > 2 ? args[2] : ""));
                        return result;
                    });
            HookUtil.hookMethod(Miuix307GestureBackdropHoldHook.class,
                    "stopAllTransitionCapture", new Class<?>[]{String.class}, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        setHomeTransitionActive(false,
                                String.valueOf(args.length > 0 ? args[0] : "stop-all"));
                        return result;
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " transition lifecycle hook unavailable", error);
        }
    }

    private static void setHomeTransitionActive(boolean active, String reason) {
        if (homeTransitionActive == active) return;
        homeTransitionActive = active;
        if (!active) lastLoggedExcludedIdentity = 0;
        Api101Bridge.log(TAG + " exact floating icon exclusion active=" + active
                + " reason=" + reason + " leash=" + surfaceId(floatingIconLeash));
    }

    private static void captureLeash(Object owner, String methodName) {
        SurfaceControl leash = readLeash(owner);
        if (!isValid(leash)) return;
        SurfaceControl previous = floatingIconLeash;
        floatingIconLeash = leash;
        if (previous != leash) {
            lastLoggedExcludedIdentity = 0;
            Api101Bridge.log(TAG + " floating icon leash captured method=" + methodName
                    + " surface=" + surfaceId(leash)
                    + " active=" + homeTransitionActive);
        }
    }

    private static SurfaceControl readLeash(Object owner) {
        if (owner == null) return null;
        try {
            Object value = HookUtil.getField(owner, "mFloatingIconLayerLeash");
            if (value instanceof SurfaceControl) return (SurfaceControl) value;
        } catch (Throwable ignored) {}
        try {
            Object value = HookUtil.invoke(owner, "getMFloatingIconLayerLeash");
            return value instanceof SurfaceControl ? (SurfaceControl) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean containsIdentity(SurfaceControl[] surfaces, SurfaceControl target) {
        if (surfaces == null || target == null) return false;
        for (SurfaceControl surface : surfaces) {
            if (surface == target) return true;
        }
        return false;
    }

    private static boolean isValid(SurfaceControl surface) {
        if (surface == null) return false;
        try {
            return surface.isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String surfaceId(SurfaceControl surface) {
        if (surface == null) return "null";
        return "SurfaceControl@" + Integer.toHexString(System.identityHashCode(surface))
                + "/valid=" + isValid(surface);
    }
}
