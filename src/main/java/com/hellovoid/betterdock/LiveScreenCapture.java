package com.hellovoid.betterdock;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wallpaper-only capture for HyperOS Launcher.
 *
 * HyperOS Home's own Utilities.captureWallpaperBitmap() uses captureDisplay() with
 * vendor captureMode(2) and the layer-name selector "Wallpaper BBQ wrapper".  We
 * reproduce that path, but additionally provide sourceCrop + frameScale so SurfaceFlinger
 * only reads back the lower strip needed by the Dock.  If this optimized variant is not
 * accepted by a particular framework build, we fall back to Launcher Utilities' exact
 * full-frame implementation and crop/scale it immediately on the worker thread.
 */
final class LiveScreenCapture {
    private static final String TAG = "BetterDock";
    private static final String[] DEFAULT_WALLPAPER_LAYER_NAMES = {
            "Wallpaper BBQ wrapper"
    };

    private final Object windowManager;
    private final Constructor<?> captureBuilderConstructor;
    private final Method setSourceCrop;
    private final Method setFrameScale;
    private final Method setCaptureMode;
    private final Method setExcludeOrIncludeLayerNames;
    private final Method build;
    private final Method createSyncCaptureListener;
    private final Method captureDisplay;
    private final Method getBuffer;
    private final Method asBitmap;
    private final Method getHardwareBuffer;

    private final Method launcherCaptureWallpaperBitmap;
    private final String[] wallpaperLayerNames;

    private boolean optimizedFailureLogged;
    private boolean launcherFallbackFailureLogged;

    LiveScreenCapture(float probeScale, ClassLoader launcherClassLoader) throws Exception {
        if (!(probeScale > 0f)) throw new IllegalArgumentException("probeScale must be > 0");

        Class<?> windowManagerGlobal = Class.forName("android.view.WindowManagerGlobal");
        windowManager = windowManagerGlobal.getMethod("getWindowManagerService").invoke(null);
        if (windowManager == null) throw new IllegalStateException("Window manager unavailable");

        Class<?> screenCaptureClass = Class.forName("android.window.ScreenCapture");
        Class<?> captureArgsClass = Class.forName("android.window.ScreenCapture$CaptureArgs");
        Class<?> captureBuilderClass = Class.forName("android.window.ScreenCapture$CaptureArgs$Builder");
        Class<?> listenerClass = Class.forName("android.window.ScreenCapture$ScreenCaptureListener");
        Class<?> syncListenerClass = Class.forName(
                "android.window.ScreenCapture$SynchronousScreenCaptureListener");
        Class<?> screenshotBufferClass = Class.forName(
                "android.window.ScreenCapture$ScreenshotHardwareBuffer");

        captureBuilderConstructor = captureBuilderClass.getDeclaredConstructor();
        captureBuilderConstructor.setAccessible(true);
        setSourceCrop = captureBuilderClass.getMethod("setSourceCrop", Rect.class);
        setFrameScale = captureBuilderClass.getMethod("setFrameScale", float.class, float.class);
        setCaptureMode = captureBuilderClass.getDeclaredMethod("setCaptureMode", int.class);
        setCaptureMode.setAccessible(true);
        setExcludeOrIncludeLayerNames = captureBuilderClass.getDeclaredMethod(
                "setExcludeOrIncludeLayerNames", String[].class);
        setExcludeOrIncludeLayerNames.setAccessible(true);
        build = captureBuilderClass.getMethod("build");

        createSyncCaptureListener = screenCaptureClass.getMethod("createSyncCaptureListener");
        getBuffer = syncListenerClass.getMethod("getBuffer");
        asBitmap = screenshotBufferClass.getMethod("asBitmap");
        getHardwareBuffer = screenshotBufferClass.getMethod("getHardwareBuffer");

        Method capture = null;
        for (Method method : windowManager.getClass().getMethods()) {
            if (!"captureDisplay".equals(method.getName())) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 3 && p[0] == int.class
                    && captureArgsClass.isAssignableFrom(p[1])
                    && listenerClass.isAssignableFrom(p[2])) {
                capture = method;
                break;
            }
        }
        if (capture == null) {
            Class<?> iWindowManagerClass = Class.forName("android.view.IWindowManager");
            capture = iWindowManagerClass.getMethod(
                    "captureDisplay", int.class, captureArgsClass, listenerClass);
        }
        capture.setAccessible(true);
        captureDisplay = capture;

        Method launcherCapture = null;
        String[] names = null;
        try {
            ClassLoader cl = launcherClassLoader != null
                    ? launcherClassLoader : Thread.currentThread().getContextClassLoader();
            Class<?> utilities = Class.forName(
                    "com.miui.home.launcher.common.Utilities", false, cl);
            launcherCapture = utilities.getDeclaredMethod("captureWallpaperBitmap");
            launcherCapture.setAccessible(true);
            try {
                Field namesField = utilities.getDeclaredField("EXCLUDE_OR_INCLUDE_LAYER_NAMES");
                namesField.setAccessible(true);
                Object value = namesField.get(null);
                if (value instanceof String[] && ((String[]) value).length > 0) {
                    names = ((String[]) value).clone();
                }
            } catch (Throwable ignored) {
                // The exact HyperOS 3 build decompiled for this device uses the fallback below.
            }
        } catch (Throwable error) {
            Log.w(TAG, "Launcher Utilities.captureWallpaperBitmap unavailable", error);
        }
        launcherCaptureWallpaperBitmap = launcherCapture;
        wallpaperLayerNames = names != null ? names : DEFAULT_WALLPAPER_LAYER_NAMES.clone();
    }

    Bitmap captureWallpaper(Rect sourceCrop, float scale, int displayId) throws Exception {
        if (sourceCrop == null || sourceCrop.isEmpty()) {
            throw new IllegalArgumentException("sourceCrop must be non-empty");
        }
        if (!(scale > 0f)) throw new IllegalArgumentException("scale must be > 0");

        Throwable optimizedFailure = null;
        try {
            Log.i(TAG, "captureMode(2) vendor wallpaper call: display=" + displayId
                    + " crop=" + sourceCrop + " scale=" + scale);
            Bitmap result = captureVendorWallpaperStrip(sourceCrop, scale, displayId);
            if (result != null) {
                Log.i(TAG, "captureMode(2) vendor wallpaper frame="
                        + result.getWidth() + "x" + result.getHeight());
                return result;
            }
            Log.w(TAG, "captureMode(2) vendor call returned null; trying Launcher Utilities fallback");
        } catch (Throwable error) {
            optimizedFailure = error;
            if (!optimizedFailureLogged) {
                optimizedFailureLogged = true;
                Log.w(TAG, "HyperOS captureMode(2) cropped wallpaper capture failed; "
                        + "trying Launcher Utilities fallback", error);
            }
        }

        try {
            Log.i(TAG, "Utilities.captureWallpaperBitmap() fallback attempt");
            Bitmap result = captureViaLauncherUtilities(sourceCrop, scale);
            if (result != null) {
                Log.i(TAG, "Utilities wallpaper fallback frame="
                        + result.getWidth() + "x" + result.getHeight());
                return result;
            }
            Log.w(TAG, "Utilities.captureWallpaperBitmap() fallback returned null");
        } catch (Throwable error) {
            if (!launcherFallbackFailureLogged) {
                launcherFallbackFailureLogged = true;
                Log.e(TAG, "Launcher Utilities wallpaper fallback failed", error);
            }
            if (optimizedFailure != null) error.addSuppressed(optimizedFailure);
            if (error instanceof Exception) throw (Exception) error;
            throw new RuntimeException(error);
        }

        if (optimizedFailure != null) {
            if (optimizedFailure instanceof Exception) throw (Exception) optimizedFailure;
            throw new RuntimeException(optimizedFailure);
        }
        return null;
    }

    /**
     * Full-display content capture: no layer-name filter, no vendor wallpaper mode.
     *
     * The Dock is a floating overlay window (type 2997) that can be summoned over any app,
     * so refracting only the wallpaper layer is wrong there.  This path asks SurfaceFlinger
     * for the composited display content (the "below" graphics the native floating Dock blurs
     * in real time via blur-behind).  setExcludeOrIncludeLayerNames is intentionally omitted
     * (null = capture all layers); captureMode stays at the default so no wallpaper-only
     * semantics are applied.
     */
    Bitmap captureScreen(Rect sourceCrop, float scale, int displayId) throws Exception {
        if (sourceCrop == null || sourceCrop.isEmpty()) {
            throw new IllegalArgumentException("sourceCrop must be non-empty");
        }
        if (!(scale > 0f)) throw new IllegalArgumentException("scale must be > 0");

        Throwable fullFailure = null;
        try {
            Log.i(TAG, "fullscreen capture call: display=" + displayId
                    + " crop=" + sourceCrop + " scale=" + scale);
            Bitmap result = captureFullDisplayStrip(sourceCrop, scale, displayId);
            if (result != null) {
                Log.i(TAG, "fullscreen capture frame="
                        + result.getWidth() + "x" + result.getHeight());
                return result;
            }
            Log.w(TAG, "fullscreen capture returned null");
        } catch (Throwable error) {
            fullFailure = error;
            if (!optimizedFailureLogged) {
                optimizedFailureLogged = true;
                Log.w(TAG, "fullscreen capture failed", error);
            }
        }
        if (fullFailure != null) {
            if (fullFailure instanceof Exception) throw (Exception) fullFailure;
            throw new RuntimeException(fullFailure);
        }
        return null;
    }

    /** HyperOS Home's own wallpaper-selector semantics, with compositor-side crop/scale added. */
    private Bitmap captureVendorWallpaperStrip(Rect sourceCrop, float scale, int displayId)
            throws Exception {
        Object builder = captureBuilderConstructor.newInstance();
        setSourceCrop.invoke(builder, new Rect(sourceCrop));
        setFrameScale.invoke(builder, scale, scale);

        // Verified from HyperOS Home Utilities.captureWallpaperBitmap():
        //   setExcludeOrIncludeLayerNames({"Wallpaper BBQ wrapper"})
        //   setCaptureMode(2)
        //   captureDisplay(...)
        setExcludeOrIncludeLayerNames.invoke(builder, (Object) wallpaperLayerNames.clone());
        setCaptureMode.invoke(builder, 2);

        Object args = build.invoke(builder);
        Object listener = createSyncCaptureListener.invoke(null);
        captureDisplay.invoke(windowManager, displayId, args, listener);
        Object screenshotBuffer = getBuffer.invoke(listener);
        if (screenshotBuffer == null) return null;

        Bitmap result = null;
        Object hardwareBuffer = null;
        try {
            Object bitmap = asBitmap.invoke(screenshotBuffer);
            if (bitmap instanceof Bitmap) result = (Bitmap) bitmap;
            try {
                hardwareBuffer = getHardwareBuffer.invoke(screenshotBuffer);
            } catch (Throwable ignored) {
            }
        } finally {
            closeHardwareBuffer(hardwareBuffer);
        }
        return result;
    }

    /**
     * Full-display content capture: capture everything EXCEPT the Dock's own layer.
     *
     * The native floating Dock uses SurfaceFlinger blur-behind (BackdropBlurFrameLayout +
     * setBackgroundBlurRadius), which blurs the layers BELOW the Dock window, never the Dock
     * itself.  A plain full-display capture would include the Dock's own layer (its icons end
     * up inside the glass background).  So we exclude the "Floating Dock" layer by name:
     * with the default capture mode (no vendor captureMode(2)), setExcludeOrIncludeLayerNames
     * acts as an EXCLUDE filter — the captured strip is the composited content underneath the
     * Dock (app + wallpaper), matching the native blur-behind source.
     */
    private Bitmap captureFullDisplayStrip(Rect sourceCrop, float scale, int displayId)
            throws Exception {
        Object builder = captureBuilderConstructor.newInstance();
        setSourceCrop.invoke(builder, new Rect(sourceCrop));
        setFrameScale.invoke(builder, scale, scale);

        // Exclude the Dock overlay's own layer(s) so its icons never bleed into the glass.
        // Layer names are matched as prefixes ("Floating Dock" matches "Floating Dock#7382").
        // No setCaptureMode call: default mode treats these names as exclusions.
        setExcludeOrIncludeLayerNames.invoke(builder,
                (Object) new String[]{"Floating Dock"});

        Object args = build.invoke(builder);
        Object listener = createSyncCaptureListener.invoke(null);
        captureDisplay.invoke(windowManager, displayId, args, listener);
        Object screenshotBuffer = getBuffer.invoke(listener);
        if (screenshotBuffer == null) return null;

        Bitmap result = null;
        Object hardwareBuffer = null;
        try {
            Object bitmap = asBitmap.invoke(screenshotBuffer);
            if (bitmap instanceof Bitmap) result = (Bitmap) bitmap;
            try {
                hardwareBuffer = getHardwareBuffer.invoke(screenshotBuffer);
            } catch (Throwable ignored) {
            }
        } finally {
            closeHardwareBuffer(hardwareBuffer);
        }
        return result;
    }

    /**
     * Exact stock-Launcher correctness fallback.  It captures the full wallpaper first because
     * that is what Utilities.captureWallpaperBitmap() does, then discards everything outside the
     * requested strip immediately.  This path should only be used if the optimized vendor call
     * rejects sourceCrop/frameScale on a future build.
     */
    private Bitmap captureViaLauncherUtilities(Rect sourceCrop, float scale) throws Exception {
        Method method = launcherCaptureWallpaperBitmap;
        if (method == null) return null;
        Object value = method.invoke(null);
        if (!(value instanceof Bitmap)) return null;

        Bitmap full = (Bitmap) value;
        if (full.isRecycled() || full.getWidth() <= 0 || full.getHeight() <= 0) {
            if (!full.isRecycled()) full.recycle();
            return null;
        }

        Rect safe = new Rect(sourceCrop);
        if (!safe.intersect(0, 0, full.getWidth(), full.getHeight()) || safe.isEmpty()) {
            full.recycle();
            return null;
        }

        Bitmap cropped = Bitmap.createBitmap(full, safe.left, safe.top, safe.width(), safe.height());
        if (cropped != full && !full.isRecycled()) full.recycle();

        int targetWidth = Math.max(1, Math.round(safe.width() * scale));
        int targetHeight = Math.max(1, Math.round(safe.height() * scale));
        if (cropped.getWidth() == targetWidth && cropped.getHeight() == targetHeight) {
            return cropped;
        }

        Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true);
        if (scaled != cropped && !cropped.isRecycled()) cropped.recycle();
        return scaled;
    }

    private static void closeHardwareBuffer(Object hardwareBuffer) {
        if (hardwareBuffer == null) return;
        try {
            if (hardwareBuffer instanceof AutoCloseable) {
                ((AutoCloseable) hardwareBuffer).close();
                return;
            }
            hardwareBuffer.getClass().getMethod("close").invoke(hardwareBuffer);
        } catch (Throwable ignored) {
        }
    }
}
