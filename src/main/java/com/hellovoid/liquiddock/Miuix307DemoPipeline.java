package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Opt-in demo adapter for HyperOS 3.0.307+ HotSeatsListContentMiuiXBlurBackground.
 *
 * The native MiuiBlurUiHelper remains the blur owner. LiquidDock only adds a lightweight
 * highlight and the existing foreground stroke, and intentionally installs no capture state.
 */
final class Miuix307DemoPipeline {
    static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";

    private static final WeakHashMap<View, Miuix307HighlightView> OVERLAYS = new WeakHashMap<>();
    private static boolean installed;

    private Miuix307DemoPipeline() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        if (installed) return true;
        final Class<?> backgroundClass;
        try {
            backgroundClass = Class.forName(BACKGROUND_CLASS, false, classLoader);
        } catch (Throwable unavailable) {
            MainHook.log("[DC] MiuiX 307 demo unavailable: background class missing");
            return false;
        }

        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (MainHook.isWorkstationMode()) return result;
                        try {
                            Object hotSeats = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hotSeats instanceof View) {
                                View background = findBackground((View) hotSeats);
                                if (background != null) bind(background, config);
                            }
                        } catch (Throwable error) {
                            MainHook.log("[DC] MiuiX 307 demo bind failed: " + error);
                        }
                        return result;
                    });

            HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        sync((View) chain.getThisObject(), config, true);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        sync((View) chain.getThisObject(), config, true);
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                    new Class<?>[]{float.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        sync((View) chain.getThisObject(), config, true);
                        return result;
                    });

            installed = true;
            MainHook.log("[DC] MiuiX 307 demo pipeline hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 demo hook install failed: " + error);
            return false;
        }
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

    private static void bind(View background, LiquidDockConfig config) {
        if (background == null) return;
        ViewGroup parent = background.getParent() instanceof ViewGroup
                ? (ViewGroup) background.getParent() : null;
        if (parent == null) return;

        Miuix307HighlightView overlay = OVERLAYS.get(background);
        if (overlay == null || overlay.getParent() != parent) {
            if (overlay != null && overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            overlay = new Miuix307HighlightView(background.getContext());
            OVERLAYS.put(background, overlay);
            int index = Math.max(0, parent.indexOfChild(background));
            parent.addView(overlay, Math.min(parent.getChildCount(), index + 1), copyLayoutParams(background));
            MainHook.log("[DC] MiuiX 307 demo overlay attached");
        }
        sync(background, config, true);
    }

    private static void sync(View background, LiquidDockConfig config, boolean refreshBlur) {
        if (background == null || MainHook.isWorkstationMode()) return;
        Miuix307HighlightView overlay = OVERLAYS.get(background);
        if (overlay == null) {
            bind(background, config);
            return;
        }

        ViewGroup.LayoutParams source = background.getLayoutParams();
        ViewGroup.LayoutParams target = overlay.getLayoutParams();
        int width = readIntField(background, "mWidth", source != null ? source.width : background.getWidth());
        int height = readIntField(background, "mHeight", source != null ? source.height : background.getHeight());
        if (width > 0) target.width = width;
        if (height > 0) target.height = height;
        if (source instanceof FrameLayout.LayoutParams && target instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams from = (FrameLayout.LayoutParams) source;
            FrameLayout.LayoutParams to = (FrameLayout.LayoutParams) target;
            to.gravity = from.gravity;
            to.leftMargin = from.leftMargin;
            to.topMargin = from.topMargin;
            to.rightMargin = from.rightMargin;
            to.bottomMargin = from.bottomMargin;
        }
        overlay.setLayoutParams(target);
        overlay.setVisibility(background.getVisibility());

        float radius = readRadius(background);
        overlay.setMaterialGeometry(radius, config.glass.highlightAlpha, config.glass.highlightWidth);
        DockStrokeRenderer.configure(background, config.dock, radius);
        if (refreshBlur) refreshNativeBlur(background);
    }

    private static ViewGroup.LayoutParams copyLayoutParams(View background) {
        ViewGroup.LayoutParams source = background.getLayoutParams();
        if (source instanceof FrameLayout.LayoutParams) {
            return new FrameLayout.LayoutParams((FrameLayout.LayoutParams) source);
        }
        int width = source != null ? source.width : ViewGroup.LayoutParams.MATCH_PARENT;
        int height = source != null ? source.height : ViewGroup.LayoutParams.MATCH_PARENT;
        return new FrameLayout.LayoutParams(width, height);
    }

    private static int readIntField(Object owner, String name, int fallback) {
        try {
            Field field = findField(owner.getClass(), name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static float readRadius(View background) {
        try {
            Field field = findField(background.getClass(), "mBackground");
            field.setAccessible(true);
            Object value = field.get(background);
            if (value instanceof GradientDrawable) {
                return ((GradientDrawable) value).getCornerRadius();
            }
        } catch (Throwable ignored) {}
        Drawable drawable = background.getBackground();
        if (drawable instanceof GradientDrawable) {
            return ((GradientDrawable) drawable).getCornerRadius();
        }
        return Math.max(0f, Math.min(background.getWidth(), background.getHeight()) * .22f);
    }

    private static void refreshNativeBlur(View background) {
        try {
            Field field = findField(background.getClass(), "mBlurUiHelper");
            field.setAccessible(true);
            Object helper = field.get(background);
            if (helper == null) return;
            Method refreshBlur = helper.getClass().getDeclaredMethod("refreshBlur");
            refreshBlur.setAccessible(true);
            refreshBlur.invoke(helper);
            MainHook.log("[DC] MiuiX 307 native refreshBlur");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 refreshBlur unavailable: " + error);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
