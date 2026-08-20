package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Low-risk large-folder material prototype backed by ViewRootImpl BackgroundBlurDrawable.
 *
 * It intentionally does not own a SurfaceFlinger PassBlur producer. The stock folder plate is
 * kept alive as an immediate fallback for edit/open transitions and any hidden-API failure.
 */
final class LargeFolderNativeBlurPrototype {
    private static final String TAG = "[DC][FWGLASS][FOLDER]";
    private static final int BLUR_RADIUS = 60;

    private static final String[] LARGE_FOLDER_CLASSES = new String[]{
            "com.miui.home.launcher.folder.FolderIcon4x4_16",
            "com.miui.home.launcher.folder.FolderIcon3x3_9",
            "com.miui.home.launcher.folder.FolderIcon2x2_4",
            "com.miui.home.launcher.folder.FolderIcon2x2_9",
            "com.miui.home.launcher.folder.FolderIcon2x2"
    };

    private static final WeakHashMap<View, Entry> entries = new WeakHashMap<>();
    private static boolean installed;
    private static boolean editMode;
    private static boolean folderOpen;

    private LargeFolderNativeBlurPrototype() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;

        installFolderInflateHooks(classLoader);
        installLauncherLifecycleHooks(classLoader);
        MainHook.log(TAG + " native-blur prototype hooks installed");
    }

    private static void installFolderInflateHooks(ClassLoader classLoader) {
        Set<Method> hooked = new HashSet<>();
        for (String name : LARGE_FOLDER_CLASSES) {
            try {
                Class<?> target = Class.forName(name, false, classLoader);
                Method onFinishInflate = HookUtil.findMethodExact(
                        target, "onFinishInflate", new Class<?>[0]);
                String owner = onFinishInflate.getDeclaringClass().getName();
                if (!owner.startsWith("com.miui.home.launcher.folder.")) {
                    MainHook.log(TAG + " skip unsafe inflate owner=" + owner + " target=" + name);
                    continue;
                }
                if (!hooked.add(onFinishInflate)) continue;

                HookUtil.hook(onFinishInflate, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object ownerView = chain.getThisObject();
                    if (ownerView instanceof View && isLargeFolderInstance((View) ownerView)) {
                        attachPrototype((View) ownerView);
                    }
                    return result;
                });
                MainHook.log(TAG + " inflate hook owner=" + owner + " target=" + name);
            } catch (Throwable error) {
                MainHook.log(TAG + " folder class unavailable=" + name + " error=" + error);
            }
        }
    }

    private static boolean isLargeFolderInstance(View view) {
        String name = view.getClass().getName();
        for (String supported : LARGE_FOLDER_CLASSES) {
            if (supported.equals(name)) return true;
        }
        return false;
    }

    private static void installLauncherLifecycleHooks(ClassLoader classLoader) {
        try {
            Class<?> launcher = Class.forName("com.miui.home.launcher.Launcher", false, classLoader);
            HookUtil.hookMethod(launcher, "showEditPanel", new Class<?>[]{boolean.class}, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                editMode = Boolean.TRUE.equals(chain.getArgs().get(0));
                refreshAll();
                return result;
            });

            Class<?> folderInfo = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, classLoader);
            HookUtil.hookMethod(launcher, "openFolder",
                    new Class<?>[]{folderInfo, View.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        folderOpen = true;
                        refreshAll();
                        return result;
                    });
            HookUtil.hookMethod(launcher, "closeFolder",
                    new Class<?>[]{boolean.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        folderOpen = false;
                        refreshAll();
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " launcher lifecycle hooks unavailable: " + error);
        }
    }

    private static void attachPrototype(View folder) {
        synchronized (entries) {
            if (entries.containsKey(folder)) return;
        }

        try {
            Object stockObject = HookUtil.getField(folder, "mIconImageView");
            if (!(stockObject instanceof View)) {
                MainHook.log(TAG + " stock mIconImageView missing class=" + folder.getClass().getName());
                return;
            }
            View stockImage = (View) stockObject;
            if (!(stockImage.getParent() instanceof FrameLayout)) {
                MainHook.log(TAG + " stock parent unsupported class=" + folder.getClass().getName());
                return;
            }
            FrameLayout parent = (FrameLayout) stockImage.getParent();
            FrameLayout blurLayer = new FrameLayout(stockImage.getContext());
            blurLayer.setClickable(false);
            blurLayer.setFocusable(false);
            blurLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            ViewGroup.LayoutParams raw = stockImage.getLayoutParams();
            FrameLayout.LayoutParams layoutParams;
            if (raw instanceof FrameLayout.LayoutParams) {
                layoutParams = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) raw);
            } else if (raw != null) {
                layoutParams = new FrameLayout.LayoutParams(raw.width, raw.height, Gravity.CENTER);
            } else {
                layoutParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
            }

            Entry entry = new Entry(folder, stockImage, blurLayer, stockImage.getAlpha());
            synchronized (entries) {
                entries.put(folder, entry);
            }

            blurLayer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    installBlurDrawable(entry);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    entry.backgroundReady = false;
                    entry.drawable = null;
                    blurLayer.setBackground(null);
                    restoreStock(entry);
                }
            });
            blurLayer.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                  oldLeft, oldTop, oldRight, oldBottom) -> {
                if (entry.backgroundReady && (right - left > 0) && (bottom - top > 0)) {
                    updateCornerRadius(entry);
                }
            });

            parent.addView(blurLayer, 0, layoutParams);
            if (blurLayer.isAttachedToWindow()) installBlurDrawable(entry);
            setPrototypeVisible(entry, shouldShow(entry));
            MainHook.log(TAG + " prototype attached class=" + folder.getClass().getName());
        } catch (Throwable error) {
            MainHook.log(TAG + " prototype attach failed class="
                    + folder.getClass().getName() + " error=" + error);
        }
    }

    private static void installBlurDrawable(Entry entry) {
        if (entry.backgroundReady || entry.installing || !entry.blurLayer.isAttachedToWindow()) return;
        entry.installing = true;
        try {
            Object viewRoot = HookUtil.invoke(entry.blurLayer, "getViewRootImpl");
            if (viewRoot == null) throw new IllegalStateException("ViewRootImpl unavailable");

            Object value = HookUtil.invoke(viewRoot, "createBackgroundBlurDrawable");
            if (!(value instanceof Drawable)) {
                throw new IllegalStateException("createBackgroundBlurDrawable returned " + value);
            }
            Drawable drawable = (Drawable) value;
            HookUtil.invoke(drawable, "setBlurRadius", BLUR_RADIUS);
            entry.drawable = drawable;
            entry.blurLayer.setBackground(drawable);
            entry.backgroundReady = true;
            updateCornerRadius(entry);
            setPrototypeVisible(entry, shouldShow(entry));
            MainHook.log(TAG + " native blur ready class=" + entry.folder.getClass().getName()
                    + " size=" + entry.blurLayer.getWidth() + "x" + entry.blurLayer.getHeight());
        } catch (Throwable error) {
            entry.backgroundReady = false;
            entry.drawable = null;
            entry.blurLayer.setBackground(null);
            restoreStock(entry);
            MainHook.log(TAG + " native blur unavailable; stock restored: " + error);
        } finally {
            entry.installing = false;
        }
    }

    private static void updateCornerRadius(Entry entry) {
        Drawable drawable = entry.drawable;
        if (drawable == null) return;
        float radius = resolveCornerRadius(entry);
        HookUtil.invoke(drawable, "setCornerRadius", radius);
    }

    private static float resolveCornerRadius(Entry entry) {
        for (View candidate : new View[]{entry.folder, entry.stockImage}) {
            Object value = HookUtil.invoke(candidate, "computeRoundedCornerRadius");
            if (value instanceof Number && ((Number) value).floatValue() > 0f) {
                return ((Number) value).floatValue();
            }
            try {
                Object field = HookUtil.getField(candidate, "mCornerRadius");
                if (field instanceof Number && ((Number) field).floatValue() > 0f) {
                    return ((Number) field).floatValue();
                }
            } catch (Throwable ignored) {}
        }
        int width = entry.blurLayer.getWidth();
        int height = entry.blurLayer.getHeight();
        int min = Math.min(width, height);
        if (min > 0) return min * 0.18f;
        return 22f * entry.blurLayer.getResources().getDisplayMetrics().density;
    }

    private static boolean shouldShow(Entry entry) {
        return entry.backgroundReady
                && !editMode
                && !folderOpen
                && entry.folder.isAttachedToWindow();
    }

    private static void refreshAll() {
        synchronized (entries) {
            for (Entry entry : entries.values()) {
                if (entry != null) setPrototypeVisible(entry, shouldShow(entry));
            }
        }
    }

    private static void setPrototypeVisible(Entry entry, boolean visible) {
        if (!visible) {
            restoreStock(entry);
            return;
        }
        entry.blurLayer.setVisibility(View.VISIBLE);
        entry.stockImage.setAlpha(0f);
    }

    private static void restoreStock(Entry entry) {
        entry.blurLayer.setVisibility(View.INVISIBLE);
        entry.stockImage.setAlpha(entry.stockAlpha);
    }

    private static final class Entry {
        final View folder;
        final View stockImage;
        final FrameLayout blurLayer;
        final float stockAlpha;
        Drawable drawable;
        boolean backgroundReady;
        boolean installing;

        Entry(View folder, View stockImage, FrameLayout blurLayer, float stockAlpha) {
            this.folder = folder;
            this.stockImage = stockImage;
            this.blurLayer = blurLayer;
            this.stockAlpha = stockAlpha;
        }
    }
}
