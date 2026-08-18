package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Excludes Launcher 4.50's dedicated FloatingIconLayer2 surfaces from APP->HOME mode-1 captures.
 *
 * 4.50 DEX inspection shows WindowElement.mFloatingIconLayerLeash is only the HOME/root leash used
 * as a parent. The actual icon pixels are produced by FloatingIconLayer2's
 * mFloatingIconSurfaceControl and mFloatingIconShaderSurfaceControl, both SurfaceControlCompat
 * wrappers around raw android.view.SurfaceControl handles. Capture those exact child surfaces and
 * append them to DockLiquidGlassView's normal SurfaceControl[] exclusion list while the accepted
 * APP->HOME transition is active. Closing APP, Launcher and wallpaper remain continuously visible.
 */
final class Miuix307IconFlightSurfaceHook {
    private static final String TAG = "[DC][IX]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile SurfaceControl floatingIconSurface;
    private static volatile SurfaceControl floatingIconShaderSurface;
    private static volatile boolean homeTransitionActive;
    private static volatile int lastLoggedExcludedIdentity;

    private Miuix307IconFlightSurfaceHook() {}

    static void install(ClassLoader classLoader) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        installFloatingIconLayerCapture(classLoader);
        installCaptureSurfaceExclusion();
        installTransitionLifecycle();
        Api101Bridge.log(TAG + " 4.50 exact FloatingIconLayer2 Surface exclusion installed");
    }

    private static void installFloatingIconLayerCapture(ClassLoader classLoader) {
        try {
            Class<?> floatingIconLayer = Class.forName(
                    "com.miui.home.recents.views.FloatingIconLayer2", false, classLoader);
            int refreshHooks = 0;
            int clearHooks = 0;
            for (Method method : floatingIconLayer.getDeclaredMethods()) {
                String name = method.getName();
                if (isSurfaceRefreshMethod(name)) {
                    HookUtil.hook(method, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = resolveFloatingIconOwner(
                                floatingIconLayer, method, chain.getThisObject(),
                                chain.getArgs().toArray(new Object[0]));
                        refreshSurfaces(owner, method.getName());
                        return result;
                    });
                    refreshHooks++;
                } else if (isSurfaceClearMethod(name)) {
                    HookUtil.hook(method, chain -> {
                        Object owner = resolveFloatingIconOwner(
                                floatingIconLayer, method, chain.getThisObject(),
                                chain.getArgs().toArray(new Object[0]));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        clearOwnedSurfaces(owner, method.getName());
                        return result;
                    });
                    clearHooks++;
                }
            }
            Api101Bridge.log(TAG + " FloatingIconLayer2 hooks refresh=" + refreshHooks
                    + " clear=" + clearHooks);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " FloatingIconLayer2 Surface hook unavailable", error);
        }
    }

    private static boolean isSurfaceRefreshMethod(String name) {
        return "init".equals(name)
                || "init$lambda$3".equals(name)
                || "draw".equals(name)
                || "drawIcon".equals(name)
                || "showSurfaceControl".equals(name)
                || "bindHomeLeash".equals(name);
    }

    private static boolean isSurfaceClearMethod(String name) {
        return "release".equals(name)
                || "resetMember".equals(name)
                || "release$lambda$6".equals(name);
    }

    private static Object resolveFloatingIconOwner(
            Class<?> floatingIconLayer, Method method, Object thisObject, Object[] args) {
        if (!Modifier.isStatic(method.getModifiers())
                && floatingIconLayer.isInstance(thisObject)) return thisObject;
        if (args != null) {
            for (Object arg : args) {
                if (floatingIconLayer.isInstance(arg)) return arg;
            }
        }
        return null;
    }

    private static void refreshSurfaces(Object owner, String methodName) {
        if (owner == null) return;
        SurfaceControl icon = readRawSurface(owner, "mFloatingIconSurfaceControl");
        SurfaceControl shader = readRawSurface(owner, "mFloatingIconShaderSurfaceControl");

        boolean changed = false;
        if (isValid(icon) && icon != floatingIconSurface) {
            floatingIconSurface = icon;
            changed = true;
        }
        if (isValid(shader) && shader != floatingIconShaderSurface) {
            floatingIconShaderSurface = shader;
            changed = true;
        }
        if (changed) {
            lastLoggedExcludedIdentity = 0;
            Api101Bridge.log(TAG + " FloatingIconLayer2 surfaces captured method=" + methodName
                    + " icon=" + surfaceId(floatingIconSurface)
                    + " shader=" + surfaceId(floatingIconShaderSurface)
                    + " active=" + homeTransitionActive);
        }
    }

    private static SurfaceControl readRawSurface(Object owner, String compatFieldName) {
        try {
            Object compat = HookUtil.getField(owner, compatFieldName);
            if (compat == null) return null;
            Object raw = HookUtil.getField(compat, "mSurfaceControl");
            return raw instanceof SurfaceControl ? (SurfaceControl) raw : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void clearOwnedSurfaces(Object owner, String methodName) {
        if (owner == null) return;
        SurfaceControl icon = readRawSurface(owner, "mFloatingIconSurfaceControl");
        SurfaceControl shader = readRawSurface(owner, "mFloatingIconShaderSurfaceControl");
        boolean cleared = false;
        if (floatingIconSurface != null && (icon == floatingIconSurface || !isValid(floatingIconSurface))) {
            floatingIconSurface = null;
            cleared = true;
        }
        if (floatingIconShaderSurface != null
                && (shader == floatingIconShaderSurface || !isValid(floatingIconShaderSurface))) {
            floatingIconShaderSurface = null;
            cleared = true;
        }
        if (cleared) {
            lastLoggedExcludedIdentity = 0;
            Api101Bridge.log(TAG + " FloatingIconLayer2 surfaces cleared method=" + methodName);
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

                        SurfaceControl icon = floatingIconSurface;
                        SurfaceControl shader = floatingIconShaderSurface;
                        if (!isValid(icon) && !isValid(shader)) return base;

                        ArrayList<SurfaceControl> out = new ArrayList<>(
                                (base == null ? 0 : base.length) + 2);
                        if (base != null) {
                            for (SurfaceControl surface : base) {
                                if (isValid(surface)) out.add(surface);
                            }
                        }
                        addIfValidUnique(out, icon);
                        addIfValidUnique(out, shader);

                        int identity = combinedIdentity(icon, shader);
                        if (identity != lastLoggedExcludedIdentity) {
                            lastLoggedExcludedIdentity = identity;
                            Api101Bridge.log(TAG + " exact FloatingIconLayer2 Surfaces excluded icon="
                                    + surfaceId(icon) + " shader=" + surfaceId(shader)
                                    + " total=" + out.size());
                        }
                        return out.toArray(new SurfaceControl[0]);
                    });
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " capture Surface exclusion hook unavailable", error);
        }
    }

    private static void addIfValidUnique(ArrayList<SurfaceControl> out, SurfaceControl target) {
        if (!isValid(target)) return;
        for (SurfaceControl surface : out) {
            if (surface == target) return;
        }
        out.add(target);
    }

    private static int combinedIdentity(SurfaceControl first, SurfaceControl second) {
        int a = isValid(first) ? System.identityHashCode(first) : 0;
        int b = isValid(second) ? System.identityHashCode(second) : 0;
        return 31 * a + b;
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
                        setHomeTransitionActive(active,
                                String.valueOf(args.length > 2 ? args[2] : ""));
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
                + " reason=" + reason
                + " icon=" + surfaceId(floatingIconSurface)
                + " shader=" + surfaceId(floatingIconShaderSurface));
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
