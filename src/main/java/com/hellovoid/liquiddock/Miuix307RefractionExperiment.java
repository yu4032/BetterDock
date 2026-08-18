package com.hellovoid.liquiddock;

import android.view.SurfaceControl;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Feasibility-only compositor refraction experiment for HyperOS 3.0.307.
 *
 * The shared Floating Dock root SurfaceControl is never accepted here. The only target is the
 * independent SurfaceView child that was already device-validated. Parameters are taken from the
 * decompiled SystemUI MiuiShaderChargeView, but lighting intensity and particles are held at the
 * native inactive values so the visible variable is spatial refraction.
 */
final class Miuix307RefractionExperiment {
    private static final String TAG = "[DC][ZC][REFR]";
    private static final int SHADER_TYPE_ACTIVE = 1000;

    private static WeakReference<SurfaceView> activeViewRef = new WeakReference<>(null);
    private static long startTimeMs;
    private static SurfaceControl.Transaction transaction;
    private static Method setChargeAnim;
    private static Method setChargeAnimProp;
    private static boolean appliedLogged;

    private Miuix307RefractionExperiment() {}

    static synchronized void apply(SurfaceView childView) {
        if (childView == null) return;
        try {
            SurfaceControl childSurface = childView.getSurfaceControl();
            if (childSurface == null || !childSurface.isValid()) {
                MainHook.log(TAG + " experiment skipped invalid child surface");
                return;
            }

            SurfaceControl.Transaction localTransaction = new SurfaceControl.Transaction();
            Method localSetChargeAnimProp = SurfaceControl.Transaction.class.getMethod(
                    "setChargeAnimProp",
                    SurfaceControl.class,
                    float[].class,
                    float[].class,
                    float[].class,
                    float[].class);
            Class<?> floatType = Float.TYPE;
            Method localSetChargeAnim = SurfaceControl.Transaction.class.getMethod(
                    "setChargeAnim",
                    SurfaceControl.class,
                    Integer.TYPE,
                    floatType,
                    floatType,
                    floatType,
                    Boolean.TYPE);

            float[] general = new float[]{1.5f, 0.08f, 0.23f, 0.0f, 0.0f};
            float[] refraction = new float[]{0.5f, 0.2f, 0.7f, 8.0f};
            float[] particles = new float[]{0.0f, 0.0f};
            float[] lighting = new float[]{0.0f, 0.0f, -1.06f, -0.31f, -1.23f};

            localSetChargeAnimProp.invoke(
                    localTransaction, childSurface, general, refraction, particles, lighting);

            activeViewRef = new WeakReference<>(childView);
            transaction = localTransaction;
            setChargeAnim = localSetChargeAnim;
            setChargeAnimProp = localSetChargeAnimProp;
            startTimeMs = System.currentTimeMillis();
            appliedLogged = false;
            postNextFrame(childView);
        } catch (Throwable error) {
            MainHook.log(TAG + " child refraction experiment unavailable: " + error);
            stop(childView);
        }
    }

    static synchronized void stop(SurfaceView childView) {
        SurfaceView active = activeViewRef.get();
        if (childView != null && active != null && childView != active) return;
        activeViewRef = new WeakReference<>(null);
        transaction = null;
        setChargeAnim = null;
        setChargeAnimProp = null;
        startTimeMs = 0L;
        appliedLogged = false;
    }

    private static void postNextFrame(SurfaceView childView) {
        childView.postOnAnimation(() -> renderFrame(childView));
    }

    private static synchronized void renderFrame(SurfaceView childView) {
        if (activeViewRef.get() != childView || transaction == null || setChargeAnim == null) return;
        try {
            SurfaceControl childSurface = childView.getSurfaceControl();
            if (childSurface == null || !childSurface.isValid()) {
                stop(childView);
                return;
            }

            float seconds = (System.currentTimeMillis() - startTimeMs) / 1000.0f;
            float phase = (seconds * 0.6f) % 1.0f;
            setChargeAnim.invoke(
                    transaction,
                    childSurface,
                    Integer.valueOf(SHADER_TYPE_ACTIVE),
                    Float.valueOf(phase),
                    Float.valueOf(0.0f),
                    Float.valueOf(0.0f),
                    Boolean.FALSE);
            transaction.apply();

            if (!appliedLogged) {
                appliedLogged = true;
                MainHook.log(TAG + " child compositor refraction active"
                        + " refraction=[0.5,0.2,0.7,8.0]"
                        + " particles=[0,0] lightingIntensity=[0,0]"
                        + " useBlackBackground=false"
                        + " childSurface=" + Integer.toHexString(System.identityHashCode(childSurface)));
            }
            postNextFrame(childView);
        } catch (Throwable error) {
            MainHook.log(TAG + " child refraction frame failed: " + error);
            stop(childView);
        }
    }
}
