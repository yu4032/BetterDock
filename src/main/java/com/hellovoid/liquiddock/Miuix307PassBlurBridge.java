package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;
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
        boolean bound = true;

        Binding(
                SurfaceControl rootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                Method setMiBlurWinExc,
                float scale,
                String rootName) {
            this.rootSurface = rootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.setMiBlurWinExc = setMiBlurWinExc;
            this.scale = scale;
            this.rootName = rootName;
        }
    }

    private Miuix307PassBlurBridge() {}

    static Binding bind(View materialHost, Surface producerSurface, float requestedScale) {
        if (materialHost == null || producerSurface == null) return null;
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
            if (!rootSurface.isValid()) {
                MainHook.log(TAG + " PassBlur bind unavailable: invalid root surface");
                return null;
            }

            Class<?> transactionClass = SurfaceControl.Transaction.class;
            Method setPassBlurSurface = transactionClass.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = transactionClass.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);
            Method setMiBlurWinExc = transactionClass.getMethod(
                    "setMiBlurWinExc", SurfaceControl.class, String[].class);

            String rootName = surfaceName(rootSurface);
            String[] exclusions = new String[]{
                    rootName,
                    "NavigationBar",
                    "StatusBar",
                    "GestureStub",
                    "DockAssistantView"
            };

            // Keep the calibration producer at full resolution. TextureView output is composited
            // into the already-excluded Floating Dock root, so no child-layer exclusion is required.
            float scale = DEMO_SCALE;
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                setMiBlurWinExc.invoke(transaction, rootSurface, (Object) exclusions);
                setPassBlurSurface.invoke(transaction, rootSurface, producerSurface);
                setUpdateTextureFlag.invoke(
                        transaction, rootSurface, Boolean.TRUE, Float.valueOf(scale));
                transaction.apply();
            }

            MainHook.log(TAG + " PassBlur producer bound scale=" + scale
                    + " requestedScale=" + requestedScale
                    + " root=" + rootName
                    + " output=TextureView-in-root"
                    + " exclusions=" + Arrays.toString(exclusions));
            return new Binding(
                    rootSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    setMiBlurWinExc,
                    scale,
                    rootName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur bind unavailable: " + error);
            return null;
        }
    }

    /** Compatibility overload for the retired diagnostic view; output identity is intentionally ignored. */
    static Binding bind(
            View materialHost, View ignoredOutputView, Surface producerSurface, float requestedScale) {
        return bind(materialHost, producerSurface, requestedScale);
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        binding.bound = false;
        try {
            if (!binding.rootSurface.isValid()) return;
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                binding.setPassBlurSurface.invoke(transaction, binding.rootSurface, null);
                binding.setUpdateTextureFlag.invoke(
                        transaction,
                        binding.rootSurface,
                        Boolean.FALSE,
                        Float.valueOf(binding.scale));
                binding.setMiBlurWinExc.invoke(
                        transaction, binding.rootSurface, (Object) new String[0]);
                transaction.apply();
            }
            MainHook.log(TAG + " PassBlur producer unbound root=" + binding.rootName);
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
