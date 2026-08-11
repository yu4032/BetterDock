package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Wallpaper-only capture for HyperOS Launcher.
 *
 * The wallpaper-only path uses captureDisplay() with vendor captureMode(2) and the
 * layer-name selector "Wallpaper BBQ wrapper".  We use that same vendor mode, but
 * additionally provide sourceCrop + frameScale so SurfaceFlinger only reads back the
 * lower strip needed by the Dock.  If this optimized variant is not
 * accepted by a particular framework build, we fall back to Launcher Utilities' exact
 * full-frame implementation and crop/scale it immediately on the worker thread.
 */
final class LiveScreenCapture {
    private static final String TAG = "LiquidDock";
    private static final String[] DEFAULT_WALLPAPER_LAYER_NAMES = {
            "Wallpaper BBQ wrapper"
    };

    private final Object windowManager;
    private final Constructor<?> captureBuilderConstructor;
    private final Method setSourceCrop;
    private final Method setFrameScale;
    private final Method setCaptureMode;
    private final Method setExcludeOrIncludeLayerNames;
    private final Method setExcludeLayers;
    private final Method build;
    private final Method createSyncCaptureListener;
    private final Method captureDisplay;
    private final Method getBuffer;
    private final Method asBitmap;
    private final Method getHardwareBuffer;
    // Async path: on HyperOS, ScreenCaptureListener is a CLASS (not the AOSP interface)
    // constructed with a java.util.function.ObjIntConsumer<ScreenshotHardwareBuffer> —
    // the consumer receives (buffer, status) when SurfaceFlinger completes the capture.
    // There is no onCaptureComplete method on this build, so the async listener is built
    // from that constructor instead of a dynamic Proxy.
    private final Constructor<?> asyncListenerConstructor;

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
        setExcludeLayers = captureBuilderClass.getDeclaredMethod(
                "setExcludeLayers", android.view.SurfaceControl[].class);
        setExcludeLayers.setAccessible(true);
        build = captureBuilderClass.getMethod("build");

        createSyncCaptureListener = screenCaptureClass.getMethod("createSyncCaptureListener");
        getBuffer = syncListenerClass.getMethod("getBuffer");
        asBitmap = screenshotBufferClass.getMethod("asBitmap");
        getHardwareBuffer = screenshotBufferClass.getMethod("getHardwareBuffer");
        Constructor<?> asyncCtor = null;
        try {
            asyncCtor = listenerClass.getConstructor(java.util.function.ObjIntConsumer.class);
        } catch (Throwable t) {
            try {
                asyncCtor = listenerClass.getDeclaredConstructor(java.util.function.ObjIntConsumer.class);
                asyncCtor.setAccessible(true);
            } catch (Throwable t2) {
                Log.w(TAG, "ScreenCaptureListener(ObjIntConsumer) ctor unavailable: " + t2);
            }
        }
        asyncListenerConstructor = asyncCtor;

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
    Bitmap captureScreen(Rect sourceCrop, float scale, int displayId,
                         android.view.SurfaceControl[] excludeLayers, String excludeLayerName)
            throws Exception {
        if (sourceCrop == null || sourceCrop.isEmpty()) {
            throw new IllegalArgumentException("sourceCrop must be non-empty");
        }
        if (!(scale > 0f)) throw new IllegalArgumentException("scale must be > 0");

        Throwable fullFailure = null;
        try {
            Log.i(TAG, "fullscreen capture call: display=" + displayId
                    + " crop=" + sourceCrop + " scale=" + scale
                    + " excludeLayers=" + (excludeLayers == null ? 0 : excludeLayers.length)
                    + " excludeName=" + excludeLayerName);
            Bitmap result = captureFullDisplayStrip(sourceCrop, scale, displayId,
                    excludeLayers, excludeLayerName);
            if (result != null) {
                Log.i(TAG, "fullscreen capture frame="
                        + result.getWidth() + "x" + result.getHeight());
                dumpFrameForDebug(result, sourceCrop, scale);
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

    /** Result sink for async captures; called on the SurfaceFlinger callback thread. */
    interface CaptureCallback {
        void onResult(Bitmap bitmap);
        void onError(Throwable error);
    }

    /**
     * Asynchronous full-display capture: submits the request and returns immediately; the
     * result arrives via {@link CaptureCallback} on the SF callback thread.  The caller
     * thread never blocks on the Binder round-trip (the sync path waits inside getBuffer).
     * Falls back to the synchronous path when the async listener cannot be built.
     *
     * @param captureMode 1 = layer-name exclusion mode (excludeLayerNames honored),
     *                    2 = MIUI vendor wallpaper-only mode (SF captures just the
     *                    wallpaper layer; exclusion lists are irrelevant).
     */
    void captureScreenAsync(Rect sourceCrop, float scale, int displayId,
                            android.view.SurfaceControl[] excludeLayers, String[] excludeLayerNames,
                            int captureMode, CaptureCallback callback) {
        try {
            if (sourceCrop == null || sourceCrop.isEmpty()) throw new IllegalArgumentException(
                    "sourceCrop must be non-empty");
            if (!(scale > 0f)) throw new IllegalArgumentException("scale must be > 0");
            if (callback == null) throw new IllegalArgumentException("callback must not be null");

            Object builder = captureBuilderConstructor.newInstance();
            setSourceCrop.invoke(builder, new Rect(sourceCrop));
            setFrameScale.invoke(builder, scale, scale);
            if (excludeLayers != null && excludeLayers.length > 0) {
                setExcludeLayers.invoke(builder, (Object) excludeLayers);
            }
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            if (captureMode == 2) {
                // MIUI vendor wallpaper mode: setExcludeOrIncludeLayerNames is INCLUDE
                // semantics — SF captures ONLY the listed layers.  The launcher's wallpaper
                // selector passes ["Wallpaper BBQ wrapper"] (Launcher.Utilities.
                // EXCLUDE_OR_INCLUDE_LAYER_NAMES).  We ALSO include the wallpaper service
                // layer: while the Dock animates (icon fly-in/out) its blur pass reuses
                // the BBQ wrapper, whose captured content then contains Dock icons.
                // The service layer is pure wallpaper in every state.
                names.add("Wallpaper BBQ wrapper");
                names.add("com.miui.miwallpaper.wallpaperservice.ImageWallpaper");
            } else {
                // Mode 1: exclusion by layer NAME (the historical working path).
                names.add("Floating Dock");
                if (excludeLayerNames != null) {
                    for (String n : excludeLayerNames) {
                        if (n != null && !n.isEmpty() && !names.contains(n)) names.add(n);
                    }
                }
            }
            if (!names.isEmpty()) {
                setExcludeOrIncludeLayerNames.invoke(builder, (Object) names.toArray(new String[0]));
            }
            setCaptureMode.invoke(builder, captureMode);
            Object args = build.invoke(builder);

            if (asyncListenerConstructor == null) {
                // No async listener on this build: fall back to the synchronous path.
                String firstName = (excludeLayerNames != null && excludeLayerNames.length > 0)
                        ? excludeLayerNames[0] : null;
                Bitmap result = captureScreen(sourceCrop, scale, displayId,
                        excludeLayers, firstName);
                if (result != null) callback.onResult(result);
                else callback.onError(new RuntimeException("sync fallback returned null"));
                return;
            }
            Object listener = asyncListenerConstructor.newInstance(
                    (java.util.function.ObjIntConsumer<Object>) (buffer, status) -> {
                        Log.i(TAG, "async capture callback: buffer=" + buffer + " status=" + status);
                        Object hardwareBuffer = null;
                        try {
                            if (buffer == null) {
                                callback.onError(new RuntimeException(
                                        "async capture: null buffer status=" + status));
                                return;
                            }
                            Object bitmap = asBitmap.invoke(buffer);
                            if (bitmap instanceof Bitmap) {
                                callback.onResult((Bitmap) bitmap);
                            } else {
                                callback.onError(new RuntimeException(
                                        "async capture: asBitmap returned non-Bitmap"));
                            }
                            try {
                                hardwareBuffer = getHardwareBuffer.invoke(buffer);
                            } catch (Throwable ignored) {
                            }
                        } catch (Throwable e) {
                            callback.onError(e);
                        } finally {
                            closeHardwareBuffer(hardwareBuffer);
                        }
                    });
            captureDisplay.invoke(windowManager, displayId, args, listener);
            Log.i(TAG, "async fullscreen capture submitted: display=" + displayId
                    + " crop=" + sourceCrop + " scale=" + scale);
        } catch (Throwable error) {
            callback.onError(error);
        }
    }

    private int dumpCounter;
    private void dumpFrameForDebug(Bitmap frame, Rect crop, float scale) {
        try {
            if (++dumpCounter > 3) return;
            // App-private dir: com.miui.home can write here, root can read for analysis.
            String path = "/data/data/com.miui.home/files/bd_frame_" + dumpCounter + ".png";
            java.io.FileOutputStream fos = new java.io.FileOutputStream(path);
            frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.i(TAG, "dump frame -> " + path + " " + frame.getWidth() + "x" + frame.getHeight()
                    + " crop=" + crop + " scale=" + scale);
        } catch (Throwable e) {
            Log.w(TAG, "frame dump failed: " + e);
        }
    }

    /** Wallpaper-selector semantics (vendor captureMode 2), with compositor-side crop/scale added. */
    private Bitmap captureVendorWallpaperStrip(Rect sourceCrop, float scale, int displayId)
            throws Exception {
        Object builder = captureBuilderConstructor.newInstance();
        setSourceCrop.invoke(builder, new Rect(sourceCrop));
        setFrameScale.invoke(builder, scale, scale);

        // Wallpaper-only selector:
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
     * up inside the glass background).  We therefore exclude the Dock window's SurfaceControl
     * explicitly: setExcludeLayers() removes exactly the Dock's own layer from the capture,
     * matching the native blur-behind source (app + wallpaper underneath the Dock).
     */
    private Bitmap captureFullDisplayStrip(Rect sourceCrop, float scale, int displayId,
                                           android.view.SurfaceControl[] excludeLayers,
                                           String excludeLayerName)
            throws Exception {
        Object builder = captureBuilderConstructor.newInstance();
        setSourceCrop.invoke(builder, new Rect(sourceCrop));
        setFrameScale.invoke(builder, scale, scale);

        // Exclude the Dock overlay's own window layer(s) so its icons never bleed into the
        // glass.  Key insight from framework decompile: mExcludeOrIncludeLayerNames is only
        // read by SurfaceFlinger when captureMode != 0 (Parcel <init> checks mCaptureMode
        // before reading the string array).  Mode 2 = include (wallpaper capture); try
        // mode 1 here — treat layer names as EXCLUDE for full-display capture.
        if (excludeLayers != null && excludeLayers.length > 0) {
            setExcludeLayers.invoke(builder, (Object) excludeLayers);
        }
        // Non-zero captureMode so the layer-name filter is honored.  HyperOS layer names
        // carry a dynamic "#handle" suffix ("Floating Dock#14717"), so the exact name is
        // re-resolved from the dock window's SurfaceControl each capture.  Pass both the
        // bare name (in case SF does prefix matching) and the exact current name.
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        names.add("Floating Dock");
        if (excludeLayerName != null && !excludeLayerName.isEmpty()) {
            names.add(excludeLayerName);
        }
        setExcludeOrIncludeLayerNames.invoke(builder,
                (Object) names.toArray(new String[0]));
        setCaptureMode.invoke(builder, 1);

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
