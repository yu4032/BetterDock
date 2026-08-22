package com.hellovoid.liquiddock;

import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnostic-only probe for transient whole-Dock shadow flashes during Dock resize. */
final class DockShadowFlickerProbe {
    private static final String PRIMARY_PATH =
            "/sdcard/Download/liquiddock_shadow_flicker.log";
    private static final String FALLBACK_PATH =
            "/data/user/0/com.miui.home/files/liquiddock_shadow_flicker.log";
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final Object LOCK = new Object();

    private static WeakReference<View> observedBackgroundRef = new WeakReference<>(null);
    private static WeakReference<ViewTreeObserver> observerRef = new WeakReference<>(null);
    private static ViewTreeObserver.OnPreDrawListener preDrawListener;
    private static WeakReference<View> observedShadowRef = new WeakReference<>(null);
    private static View.OnAttachStateChangeListener shadowAttachListener;
    private static String lastFrameSignature;
    private static String activePath;
    private static boolean enabled;

    private DockShadowFlickerProbe() {}

    static void install(ClassLoader classLoader) {
        LiquidDockConfig config = LiquidDockConfig.load();
        enabled = config.enabled && config.dock.enabled && config.dock.shadowEnabled;
        write("SESSION pid=" + Process.myPid()
                + " uptimeMs=" + SystemClock.elapsedRealtime()
                + " enabled=" + enabled);
        Api101Bridge.log("[DC][SHADOW-FLICKER] trace="
                + (activePath == null ? PRIMARY_PATH : activePath)
                + " enabled=" + enabled);
        if (!enabled) return;

        installLauncherSetupProbe(classLoader);
        installGeometryProbe(classLoader,
                "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2");
        installGeometryProbe(classLoader,
                "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground");
    }

    private static void installLauncherSetupProbe(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            Object hotSeats = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            View background = resolveBackground(hotSeats);
                            if (background != null) {
                                armPreDraw(background);
                                sample("SETUP_VIEWS", background, false);
                            }
                        } catch (Throwable error) {
                            write("PROBE_ERROR setupViews " + error);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            write("PROBE_ERROR install setupViews " + error);
        }
    }

    private static void installGeometryProbe(ClassLoader classLoader, String className) {
        tryHook(classLoader, className, "setBackgroundWidth", int.class);
        tryHook(classLoader, className, "setBackgroundHeight", int.class);
        tryHook(classLoader, className, "setBackgroundRadius", float.class);
        tryHook(classLoader, className, "updateBackgroundSize", int.class, int.class, float.class);
    }

    private static void tryHook(
            ClassLoader classLoader, String className, String method, Class<?>... params) {
        try {
            HookUtil.hookMethod(classLoader, className, method,
                    chain -> {
                        View background = chain.getThisObject() instanceof View
                                ? (View) chain.getThisObject() : null;
                        if (background != null) sample("BEFORE_" + method, background, false);
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (background != null) {
                            armPreDraw(background);
                            sample("AFTER_" + method, background, false);
                        }
                        return result;
                    }, params);
        } catch (Throwable ignored) {
            // APIs differ between the native and themed material classes; missing overloads are normal.
        }
    }

    private static void armPreDraw(View background) {
        if (background == null) return;
        View current = observedBackgroundRef.get();
        if (current == background && preDrawListener != null) return;
        removePreDraw();

        View root = background.getRootView();
        ViewTreeObserver observer = (root != null ? root : background).getViewTreeObserver();
        if (observer == null || !observer.isAlive()) return;
        WeakReference<View> watched = new WeakReference<>(background);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            View bg = watched.get();
            if (bg != null) sample("PREDRAW", bg, true);
            return true;
        };
        observer.addOnPreDrawListener(listener);
        observedBackgroundRef = new WeakReference<>(background);
        observerRef = new WeakReference<>(observer);
        preDrawListener = listener;
        sample("PREDRAW_ARMED", background, false);
    }

    private static void removePreDraw() {
        ViewTreeObserver observer = observerRef.get();
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        observerRef = new WeakReference<>(null);
        preDrawListener = null;
        if (observer != null && listener != null) {
            try {
                if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
            } catch (Throwable ignored) {}
        }
    }

    private static void sample(String event, View background, boolean dedupe) {
        View shadow = resolveShadow();
        observeShadow(shadow);
        ViewGroup parent = background != null && background.getParent() instanceof ViewGroup
                ? (ViewGroup) background.getParent() : null;
        int backgroundIndex = parent == null ? -1 : parent.indexOfChild(background);
        int shadowIndex = parent == null || shadow == null ? -1 : parent.indexOfChild(shadow);
        boolean animating = isAnimating(background);
        boolean parentMatch = shadow != null && shadow.getParent() == parent;
        boolean shadowAttached = shadow != null && shadow.isAttachedToWindow();
        boolean shadowShown = shadow != null && shadow.isShown()
                && shadow.getVisibility() == View.VISIBLE && shadow.getAlpha() > 0f;
        boolean zOrderValid = parent != null && shadowIndex >= 0 && backgroundIndex >= 0
                && shadowIndex < backgroundIndex;
        boolean risk = background != null && background.isAttachedToWindow()
                && !MainHook.isWorkstationMode()
                && (shadow == null || !parentMatch || !shadowAttached || !shadowShown || !zOrderValid);

        String signature = id(background) + ':' + id(shadow)
                + ':' + bool(animating)
                + ':' + bool(parentMatch)
                + ':' + bool(shadowAttached)
                + ':' + bool(shadowShown)
                + ':' + shadowIndex + ':' + backgroundIndex
                + ':' + bool(risk)
                + ':' + size(background) + ':' + size(shadow);
        synchronized (LOCK) {
            if (dedupe && signature.equals(lastFrameSignature)) return;
            if (dedupe) lastFrameSignature = signature;
        }

        String prefix = risk ? "FLASH_RISK " : "";
        write(prefix + "event=" + event
                + " uptimeMs=" + SystemClock.elapsedRealtime()
                + " anim=" + animating
                + " bg=" + state(background)
                + " shadow=" + state(shadow)
                + " parentMatch=" + parentMatch
                + " z=" + shadowIndex + '<' + backgroundIndex
                + " zValid=" + zOrderValid);
    }

    private static void observeShadow(View shadow) {
        View previous = observedShadowRef.get();
        if (previous == shadow && shadowAttachListener != null) return;
        if (previous != null && shadowAttachListener != null) {
            try { previous.removeOnAttachStateChangeListener(shadowAttachListener); }
            catch (Throwable ignored) {}
        }
        observedShadowRef = new WeakReference<>(shadow);
        shadowAttachListener = null;
        if (shadow == null) return;

        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                View bg = resolveCurrentBackground();
                write("SHADOW_ATTACHED uptimeMs=" + SystemClock.elapsedRealtime()
                        + " shadow=" + state(v) + " bg=" + state(bg));
            }

            @Override public void onViewDetachedFromWindow(View v) {
                View bg = resolveCurrentBackground();
                boolean risk = bg != null && bg.isAttachedToWindow()
                        && !MainHook.isWorkstationMode();
                write((risk ? "FLASH_RISK " : "")
                        + "SHADOW_DETACHED uptimeMs=" + SystemClock.elapsedRealtime()
                        + " anim=" + isAnimating(bg)
                        + " shadow=" + state(v) + " bg=" + state(bg));
            }
        };
        shadow.addOnAttachStateChangeListener(listener);
        shadowAttachListener = listener;
        write("SHADOW_TRACKED uptimeMs=" + SystemClock.elapsedRealtime()
                + " shadow=" + state(shadow));
    }

    private static View resolveShadow() {
        return resolveWeakViewField("shadowViewRef");
    }

    private static View resolveCurrentBackground() {
        View current = observedBackgroundRef.get();
        return current != null ? current : resolveWeakViewField("oldBgRef");
    }

    private static View resolveWeakViewField(String name) {
        try {
            Field field = MainHook.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof WeakReference) {
                Object view = ((WeakReference<?>) value).get();
                return view instanceof View ? (View) view : null;
            }
        } catch (Throwable error) {
            write("PROBE_ERROR reflect " + name + ' ' + error);
        }
        return null;
    }

    private static View resolveBackground(Object hotSeats) {
        if (hotSeats == null) return null;
        try {
            Object active = HookUtil.invoke(hotSeats, "getHotSeatsBackground");
            if (active instanceof View) return (View) active;
        } catch (Throwable ignored) {}
        try {
            Object compat = HookUtil.getField(hotSeats, "mBlurBackground2");
            return compat instanceof View ? (View) compat : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isAnimating(View view) {
        if (view == null) return false;
        try { return Boolean.TRUE.equals(HookUtil.invoke(view, "isAnimating")); }
        catch (Throwable ignored) { return false; }
    }

    private static String state(View view) {
        if (view == null) return "null";
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        return id(view)
                + "{attached=" + view.isAttachedToWindow()
                + ",shown=" + view.isShown()
                + ",vis=" + view.getVisibility()
                + ",alpha=" + view.getAlpha()
                + ",view=" + view.getWidth() + 'x' + view.getHeight()
                + ",lp=" + (lp == null ? "null" : lp.width + "x" + lp.height)
                + ",xy=" + Math.round(view.getX()) + ',' + Math.round(view.getY())
                + '}';
    }

    private static String size(View view) {
        return view == null ? "null" : view.getWidth() + "x" + view.getHeight();
    }

    private static String id(View view) {
        return view == null ? "null" : view.getClass().getSimpleName() + '@'
                + Integer.toHexString(System.identityHashCode(view));
    }

    private static int bool(boolean value) { return value ? 1 : 0; }

    private static void write(String message) {
        synchronized (LOCK) {
            String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
            String line = stamp + ' ' + message + '\n';
            if (append(PRIMARY_PATH, line)) {
                activePath = PRIMARY_PATH;
                return;
            }
            if (append(FALLBACK_PATH, line)) activePath = FALLBACK_PATH;
        }
    }

    private static boolean append(String path, String line) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (file.exists() && file.length() > MAX_BYTES) {
                try (FileOutputStream out = new FileOutputStream(file, false)) {
                    out.write(("--- trace truncated ---\n").getBytes(StandardCharsets.UTF_8));
                }
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
