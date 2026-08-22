package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Replaces MIUI desktop FolderIcon advanced material with output-only shared glass sinks. */
final class MiuixFolderGlassHook {
    private static final String TAG = "[DC][FolderGlass]";
    private static final String ITEM_ICON = "com.miui.home.launcher.ItemIcon";
    private static final String FOLDER_ICON = "com.miui.home.launcher.FolderIcon";
    private static final int MAX_STARTUP_RECOVERY_FRAMES = 24;
    private static final Map<View, WeakReference<LauncherGlassSinkView>> CLAIMED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, View.OnAttachStateChangeListener> FOLDER_ATTACH_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ViewGroup, Boolean> FOLDER_RECOVERY_PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private MiuixFolderGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
            Class<?> itemIcon = Class.forName(ITEM_ICON, false, classLoader);
            Method setIconImageView = HookUtil.findMethodExact(itemIcon, "setIconImageView",
                    new Class<?>[]{Drawable.class, android.graphics.Bitmap.class});
            HookUtil.hook(setIconImageView, chain -> {
                Object icon = chain.getThisObject();
                boolean folder = icon instanceof ViewGroup
                        && (FOLDER_ICON.equals(icon.getClass().getName())
                        || icon.getClass().getName().endsWith(".FolderIcon"));
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (folder) attachFromFolderIcon((ViewGroup) icon, glassConfig);
                return result;
            });

            Class<?> utilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, classLoader);
            Class<?>[] params = new Class<?>[]{View.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class, int.class};
            HookUtil.hook(HookUtil.findMethodExact(utilities, "setFolderIconBlur", params), chain -> {
                Object target = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (target instanceof View) {
                    View material = (View) target;
                    LauncherGlassSinkView sink = claimedSink(material);
                    if (sink == null) sink = attachMaterial(material, glassConfig);
                    if (sink != null) {
                        clearVendorBlur(material);
                        return null;
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
            MainHook.log(TAG + " shared FolderIcon glass hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        try {
            Object value = HookUtil.getField(icon, "mIconImageView");
            if (value instanceof View) {
                LauncherGlassSinkView sink = attachMaterial((View) value, glassConfig);
                // Launcher restart can call setIconImageView after FolderIcon is attached but
                // before its real ViewRoot/Surface is stable. Adding an attach listener at that
                // point does not replay onViewAttachedToWindow, so recover on later UI frames.
                if (sink == null && icon.isAttachedToWindow()) {
                    scheduleFolderRecovery(icon, glassConfig, 0);
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " material resolve failed: " + error);
        }
    }


    private static void scheduleFolderRecovery(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (icon == null) return;
        if (attempt == 0) {
            synchronized (FOLDER_RECOVERY_PENDING) {
                if (FOLDER_RECOVERY_PENDING.containsKey(icon)) return;
                FOLDER_RECOVERY_PENDING.put(icon, Boolean.TRUE);
            }
        }
        if (attempt >= MAX_STARTUP_RECOVERY_FRAMES) {
            FOLDER_RECOVERY_PENDING.remove(icon);
            MainHook.log(TAG + " startup recovery exhausted for "
                    + icon.getClass().getSimpleName());
            return;
        }
        WeakReference<ViewGroup> iconRef = new WeakReference<>(icon);
        icon.postOnAnimation(() -> {
            ViewGroup current = iconRef.get();
            if (current == null || !current.isAttachedToWindow()) {
                if (current != null) FOLDER_RECOVERY_PENDING.remove(current);
                return;
            }
            LauncherGlassSinkView sink = null;
            try {
                Object value = HookUtil.getField(current, "mIconImageView");
                if (value instanceof View) {
                    sink = attachMaterial((View) value, glassConfig);
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " startup material recovery failed: " + error);
            }
            if (sink == null && attempt < MAX_STARTUP_RECOVERY_FRAMES) {
                scheduleFolderRecovery(current, glassConfig, attempt + 1);
            } else {
                FOLDER_RECOVERY_PENDING.remove(current);
            }
        });
    }


    private static void observeFolderIconAttach(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (icon == null || FOLDER_ATTACH_LISTENERS.containsKey(icon)) return;
        WeakReference<ViewGroup> iconRef = new WeakReference<>(icon);
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                ViewGroup folder = iconRef.get();
                if (folder == null) return;
                // DragContainer -> desktop reparent does not necessarily call setIconImageView or
                // setFolderIconBlur again. Recover the material on the first normal UI frame.
                folder.postOnAnimation(() -> {
                    ViewGroup current = iconRef.get();
                    if (current != null && current.isAttachedToWindow()) {
                        attachFromFolderIcon(current, glassConfig);
                    }
                });
            }

            @Override public void onViewDetachedFromWindow(View v) {
                ViewGroup folder = iconRef.get();
                if (folder == null) return;
                try {
                    Object value = HookUtil.getField(folder, "mIconImageView");
                    if (value instanceof View) {
                        LauncherGlassSinkView sink = claimedSink((View) value);
                        if (sink != null) sink.requestLifecycleRefresh();
                    }
                } catch (Throwable ignored) {}
            }
        };
        FOLDER_ATTACH_LISTENERS.put(icon, listener);
        icon.addOnAttachStateChangeListener(listener);
    }

    private static LauncherGlassSinkView attachMaterial(
            View material, LiquidDockConfig.Glass glassConfig) {
        if (material == null || !(material.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) material.getParent();
        if (parent.getClass().getName().endsWith(".CellLayout")) {
            MainHook.log(TAG + " refusing direct CellLayout sink insertion");
            return null;
        }
        LauncherGlassSinkView existing = claimedSink(material);
        if (existing != null && existing.getParent() == parent) {
            // MIUI can repopulate mIconImageView after our sink already exists. The material
            // is above the sink in child order, so a restored drawable would cover Prismal.
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            return existing;
        }
        float radius = readMaterialRadius(material);
        if (!Float.isFinite(radius) || radius <= 0f) {
            radius = Math.min(Math.max(1, material.getWidth()),
                    Math.max(1, material.getHeight())) * 0.22f;
        }
        LauncherGlassSinkView sink = LauncherGlassSinkView.attachToMaterial(
                material, radius, glassConfig);
        if (sink != null) {
            CLAIMED.put(material, new WeakReference<>(sink));
            clearVendorBlur(material);
            makeMaterialTransparent(material);
            MainHook.log(TAG + " FolderIcon material joined shared launcher session");
        }
        return sink;
    }

    private static LauncherGlassSinkView claimedSink(View material) {
        WeakReference<LauncherGlassSinkView> reference = CLAIMED.get(material);
        return reference != null ? reference.get() : null;
    }


    private static float readMaterialRadius(View material) {
        if (material == null) return Float.NaN;
        try {
            Field field = findField(material.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(material);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        try {
            Field field = findField(material.getClass(), "mBackground");
            field.setAccessible(true);
            Object value = field.get(material);
            if (value instanceof GradientDrawable) {
                return Math.max(0f, ((GradientDrawable) value).getCornerRadius());
            }
        } catch (Throwable ignored) {}
        Drawable drawable = material.getBackground();
        if (drawable instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) drawable).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static void makeMaterialTransparent(View material) {
        if (material instanceof ImageView) {
            ((ImageView) material).setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        } else {
            material.setBackground(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private static void clearVendorBlur(View material) {
        MiBlurBridge.clearContentBlur(material);
        try { View.class.getMethod("clearMiBackgroundBlendColor").invoke(material); }
        catch (Throwable ignored) {}
    }
}
