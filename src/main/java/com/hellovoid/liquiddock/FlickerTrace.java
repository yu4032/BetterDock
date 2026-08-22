package com.hellovoid.liquiddock;

import android.os.Process;
import android.os.SystemClock;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Dedicated low-volume visual-state trace for transient Dock flicker diagnosis. */
final class FlickerTrace {
    static final String PRIMARY_PATH = "/sdcard/Download/liquiddock_flicker.log";
    static final String FALLBACK_PATH = "/data/local/tmp/liquiddock_flicker.log";
    private static final String THEMED_BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final long MAX_BYTES = 4L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static String activePath;
    private static String lastFrameSignature;

    private FlickerTrace() {}

    static void startSession() {
        synchronized (LOCK) {
            lastFrameSignature = null;
            writeLocked("SESSION pid=" + Process.myPid()
                    + " uptimeMs=" + SystemClock.elapsedRealtime()
                    + " thread=" + Thread.currentThread().getName());
        }
    }

    static void event(String event, View background, DockLiquidGlassHostView host) {
        writeState(event, background, host, false);
    }

    static void sampleHostFrame(DockLiquidGlassHostView host) {
        View background = host != null && host.getParent() instanceof View
                ? (View) host.getParent() : null;
        writeState("FRAME", background, host, true);
    }

    private static void writeState(
            String event, View background, DockLiquidGlassHostView host, boolean dedupe) {
        boolean zeroInstalled = Miuix307ZeroCopyRenderer.isInstalled();
        boolean zeroActive = Miuix307ZeroCopyRenderer.isActive();
        boolean exhausted = Miuix307ZeroCopyRenderer.isActivationExhausted();
        boolean themed = background != null
                && THEMED_BACKGROUND_CLASS.equals(background.getClass().getName());
        boolean bound = host != null && host.getParent() == background;
        boolean hostReady = host != null && host.isAttachedToWindow() && host.isShown()
                && host.getWidth() > 1 && host.getHeight() > 1;
        boolean gap = themed && (!bound || !hostReady || !zeroActive);

        String signature = id(background)
                + ":" + bool(background != null && background.isAttachedToWindow())
                + ":" + bool(background != null && background.isShown())
                + ":" + id(host)
                + ":" + bool(bound)
                + ":" + bool(hostReady)
                + ":" + bool(zeroInstalled)
                + ":" + bool(zeroActive)
                + ":" + bool(exhausted)
                + ":" + Miuix307ZeroCopyRenderer.activeWidth()
                + "x" + Miuix307ZeroCopyRenderer.activeHeight()
                + ":" + bool(gap);

        synchronized (LOCK) {
            if (dedupe && signature.equals(lastFrameSignature)) return;
            if (dedupe) lastFrameSignature = signature;
            StringBuilder line = new StringBuilder();
            if (gap) line.append("FLICKER_GAP ");
            line.append("event=").append(event)
                    .append(" uptimeMs=").append(SystemClock.elapsedRealtime())
                    .append(" thread=").append(Thread.currentThread().getName())
                    .append(" bg=").append(viewState(background))
                    .append(" host=").append(viewState(host))
                    .append(" bound=").append(bound)
                    .append(" zcInstalled=").append(zeroInstalled)
                    .append(" zcActive=").append(zeroActive)
                    .append(" zcExhausted=").append(exhausted)
                    .append(" zcSize=").append(Miuix307ZeroCopyRenderer.activeWidth())
                    .append('x').append(Miuix307ZeroCopyRenderer.activeHeight());
            writeLocked(line.toString());
        }
    }

    private static String viewState(View view) {
        if (view == null) return "null";
        return view.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(view))
                + "{attached=" + view.isAttachedToWindow()
                + ",shown=" + view.isShown()
                + ",vis=" + view.getVisibility()
                + ",alpha=" + view.getAlpha()
                + ",size=" + view.getWidth() + "x" + view.getHeight()
                + ",children=" + (view instanceof android.view.ViewGroup
                    ? ((android.view.ViewGroup) view).getChildCount() : -1)
                + "}";
    }

    private static String id(View view) {
        return view == null ? "null"
                : view.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(view));
    }

    private static int bool(boolean value) { return value ? 1 : 0; }

    private static void writeLocked(String message) {
        String stamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(new Date());
        String line = stamp + " " + message + "\n";
        if (append(PRIMARY_PATH, line)) {
            activePath = PRIMARY_PATH;
            return;
        }
        if (append(FALLBACK_PATH, line)) activePath = FALLBACK_PATH;
    }

    private static boolean append(String path, String line) {
        try {
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (file.exists() && file.length() > MAX_BYTES) {
                try (FileOutputStream truncate = new FileOutputStream(file, false)) {
                    truncate.write(("--- trace truncated at " + System.currentTimeMillis() + " ---\n")
                            .getBytes(StandardCharsets.UTF_8));
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
