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

                            if (!MiuixGlassHook.install(
                                    background, workspace, config, launcher, classLoader)) {
                                MainHook.log("[DC] MiuiX 307 real glass install returned false");
                            }
                        } catch (Throwable error) {
                            MainHook.log("[DC] MiuiX 307 real glass bind failed: " + error);
                        }
                        return result;
                    });

            // setupViews can run before the vendor has its final dimensions. These callbacks
            // are the authoritative geometry boundary on 307 and keep the glass host aligned.
            // Reuse the install-time config here: this path can be called every animation
            // frame, while DockLiquidGlassView already owns the slow appearance hot-reload.
            HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        MiuixGlassHook.sync((View) chain.getThisObject(), config);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        MiuixGlassHook.sync((View) chain.getThisObject(), config);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                    new Class<?>[]{float.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        MiuixGlassHook.sync((View) chain.getThisObject(), config);
                        return result;
                    });

            installed = true;
            MainHook.log("[DC] MiuiX 307 real glass pipeline hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 material hook install failed: " + error);
            return false;
        }
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
