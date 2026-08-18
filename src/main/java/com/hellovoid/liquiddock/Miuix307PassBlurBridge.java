package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Minimal HyperOS 3.0.307 bridge that asks SurfaceFlinger PassBlur to render into a caller-owned
 * producer Surface. Pixel ownership remains in GPU buffers; this class never captures or maps the
 * backdrop on the CPU.
 */
final class Miuix307PassBlurBridge {
    private static final String TAG = "[DC][PBGL]";
    private static final float DEMO_SCALE = 1.0f;

    static final class Binding {
        final SurfaceControl rootSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final Method setMiBlurWinExc;
        final float scale;
        final String rootName;
        final String outputName;
        boolean bound = true;

        Binding(
                SurfaceControl rootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                Method setMiBlurWinExc,
                float scale,
                String rootName,
                String outputName) {
            this.rootSurface = rootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.setMiBlurWinExc = setMiBlurWinExc;
            this.scale = scale;
            this.rootName = rootName;
            this.outputName = outputName;
        }
    }

    private Miuix307PassBlurBridge() {}

    static Binding bind(
            View materialHost, SurfaceView outputView, Surface producerSurface, float requestedScale) {
        if (materialHost == null || outputView == null || producerSurface == null) return null;
        try {
            Method getViewRootImpl = View.class.getDeclaredMethod("getViewRootImpl");
            getViewRootImpl.setAccessible(true);
            Object viewRoot = getViewRootImpl.invoke(materialHost);
            if (viewRoot == null) {
                MainHook.log(TAG + " PassBlur bind unavailable: ViewRootImpl=null");
                return null;
            }

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object rootValue = getSurfaceControl.invoke(viewRoot);
            if (!(rootValue instanceof SurfaceControl)) {
                MainHook.log(TAG + " PassBlur bind unavailable: root SurfaceControl missing");
                return null;
            }
            SurfaceControl rootSurface = (SurfaceControl) rootValue;
            SurfaceControl outputSurface = outputView.getSurfaceControl();
            if (!rootSurface.isValid() || outputSurface == null || !outputSurface.isValid()) {
                MainHook.log(TAG + " PassBlur bind unavailable: invalid root/output surface");
                return null;
            }

            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            Class<?> transactionClass = SurfaceControl.Transaction.class;
            Method SetPassBlurSurface = transactionClass.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = transactionClass.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);
            Method setMiBlurWinExc = transactionClass.getMethod(
                    "setMiBlurWinExc", SurfaceControl.class, String[].class);

            String rootName = surfaceName(rootSurface);
            String outputName = surfaceName(outputSurface);
            String[] exclusions = new String[]{
                    rootName,
                    outputName,
                    "NavigationBar",
                    "StatusBar",
                    "GestureStub",
                    "DockAssistantView"
            };

            // Deliberately pin the first feasibility demo to full-resolution compositor output.
            float scale = DEMO_SCALE;
            setMiBlurWinExc.invoke(transaction, rootSurface, (Object) exclusions);
            SetPassBlurSurface.invoke(transaction, rootSurface, producerSurface);
            setUpdateTextureFlag.invoke(
                    transaction, rootSurface, Boolean.TRUE, Float.valueOf(scale));
            transaction.apply();

            MainHook.log(TAG + " PassBlur producer bound scale=" + scale
                    + " requestedScale=" + requestedScale
                    + " root=" + rootName
                    + " output=" + outputName
                    + " exclusions=" + Arrays.toString(exclusions));
            return new Binding(
                    rootSurface,
                    SetPassBlurSurface,
                    setUpdateTextureFlag,
                    setMiBlurWinExc,
                    scale,
                    rootName,
                    outputName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur bind unavailable: " + error);
            return null;
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        binding.bound = false;
        try {
            if (!binding.rootSurface.isValid()) return;
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            binding.setPassBlurSurface.invoke(transaction, binding.rootSurface, null);
            binding.setUpdateTextureFlag.invoke(
                    transaction,
                    binding.rootSurface,
                    Boolean.FALSE,
                    Float.valueOf(binding.scale));
            binding.setMiBlurWinExc.invoke(transaction, binding.rootSurface, (Object) new String[0]);
            transaction.apply();
            MainHook.log(TAG + " PassBlur producer unbound root=" + binding.rootName
                    + " output=" + binding.outputName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur unbind failed: " + error);
        }
    }

    private static String surfaceName(SurfaceControl surface) {
        if (surface == null) return "";
        try {
            Method getName = SurfaceControl.class.getDeclaredMethod("getName");
            getName.setAccessible(true);
            Object value = getName.invoke(surface);
            if (value instanceof String) return (String) value;
        } catch (Throwable ignored) {}
        return surface.toString();
    }
}
