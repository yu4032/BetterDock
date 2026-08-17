package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

/**
 * Hook coordinator for HyperOS 3.0.307+ HotSeatsListContentMiuiXBlurBackground.
 *
 * The MiuiX background stays installed and remains the backdrop-blur/gradient owner.
 * MiuixGlassHook places the existing LiquidDock Prismal glass stack directly above it.
 */
final class Miuix307MaterialPipeline {
    static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";

    private static boolean installed;
    private static View workspaceRef;

    private Miuix307MaterialPipeline() {}

    static boolean isInstalled() {
        return installed;
    }

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        final Class<?> backgroundClass;
        try {
            backgroundClass = Class.forName(BACKGROUND_CLASS, false, classLoader);
        } catch (Throwable unavailable) {
            MainHook.log("[DC] MiuiX 307 material unavailable: background class missing");
            return false;
        }

        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (MainHook.isWorkstationMode()) return result;
                        try {
                            Object launcher = chain.getThisObject();
                            Object hotSeats = HookUtil.getField(launcher, "mHotSeats");
                            View background = resolveBackground(hotSeats);
                            if (background == null) {
                                MainHook.log("[DC] MiuiX 307 background not found in setupViews");
                                return result;
                            }

                            View workspace = null;
                            try {
                                Object value = HookUtil.getField(launcher, "mWorkspace");
                                if (value instanceof View) workspace = (View) value;
                            } catch (Throwable ignored) {}
                            if (workspace != null) workspaceRef = workspace;

                            if (!ensureGlassBound(background, config, classLoader)) {
                                MainHook.log("[DC] MiuiX 307 real glass install returned false");
                            }
                        } catch (Throwable error) {
                            MainHook.log("[DC] MiuiX 307 real glass bind failed: " + error);
                        }
                        return result;
                    });

            // setupViews can run before the vendor has its final dimensions. It can also keep
            // the Launcher hierarchy while replacing only the MiuiX background during APP->HOME.
            // Treat each geometry callback instance as authoritative: bind first, then sync.
            HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        View background = (View) chain.getThisObject();
                        ensureGlassBound(background, config, classLoader);
                        MiuixGlassHook.syncSize(background);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        View background = (View) chain.getThisObject();
                        ensureGlassBound(background, config, classLoader);
                        MiuixGlassHook.syncSize(background);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                    new Class<?>[]{float.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        View background = (View) chain.getThisObject();
                        ensureGlassBound(background, config, classLoader);
                        MiuixGlassHook.syncGeometry(background, config);
                        return result;
                    });

            // Decompiled 307 Launcher emits StateNotifyUtils.sendStateBroadcast(..., "toHome",
            // ...) before the APP->HOME icon-flight animation. Feed only that native boundary
            // into DockLiquidGlassView's existing HOME gesture target so the old APP capture is
            // scene-stale before animation pixels can be installed. This is optional/fail-open:
            // a vendor signature change must not disable the entire 307 material pipeline.
            try {
                HookUtil.hookMethod(classLoader,
                        "com.miui.home.recents.util.StateNotifyUtils", "sendStateBroadcast",
                        chain -> {
                            Object[] args = chain.getArgs().toArray(new Object[0]);
                            for (Object arg : args) {
                                if ("toHome".equals(arg)) {
                                    MiuixGlassHook.onHomeTransitionStart();
                                    break;
                                }
                            }
                            return chain.proceed(args);
                        }, android.content.Context.class,
                        String.class, String.class, String.class);
                MainHook.log("[DC] MiuiX 307 native toHome backdrop hook installed");
            } catch (Throwable error) {
                MainHook.log("[DC] MiuiX 307 native toHome hook unavailable: " + error);
            }

            installed = true;
            MainHook.log("[DC] MiuiX 307 real glass pipeline hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 material hook install failed: " + error);
            return false;
        }
    }

    /**
     * Self-heal when HyperOS replaces only the MiuiX background view. setupViews is not a
     * reliable per-instance boundary on 307, but the background geometry callbacks are.
     */
    private static boolean ensureGlassBound(
            View background, LiquidDockConfig config, ClassLoader classLoader) {
        if (background == null) return false;
        if (MiuixGlassHook.isBoundTo(background)) return true;

        MainHook.log("[DC] MiuiX 307 background instance changed; rebinding Prismal glass"
                + " instance=" + Integer.toHexString(System.identityHashCode(background)));
        boolean installedNow = MiuixGlassHook.install(
                background, workspaceRef, config, null, classLoader);
        if (!installedNow) {
            // Width may arrive before the new background is parented. Height/radius callbacks
            // will retry naturally, so do not add a polling loop or delayed lifecycle guess.
            MainHook.log("[DC] MiuiX 307 background rebind deferred; parent not ready");
        }
        return installedNow;
    }

    private static View resolveBackground(Object hotSeats) {
        if (hotSeats == null) return null;

        // New Launcher exposes the active dock background through this accessor. Prefer it
        // over old field names so the 307 path never accidentally binds mBlurBackground2.
        try {
            Object value = HookUtil.invoke(hotSeats, "getHotSeatsBackground");
            if (value instanceof View
                    && BACKGROUND_CLASS.equals(value.getClass().getName())) {
                MainHook.log("[DC] getHotSeatsBackground returned " + value.getClass().getName());
                return (View) value;
            }
        } catch (Throwable ignored) {}

        return hotSeats instanceof View ? findBackground((View) hotSeats) : null;
    }

    private static View findBackground(View root) {
        if (root == null) return null;
        if (BACKGROUND_CLASS.equals(root.getClass().getName())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findBackground(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
