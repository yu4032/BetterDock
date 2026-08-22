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
    private static final int INITIAL_UPDATE_FRAMES = 4;

    static final class Binding {
        final SurfaceControl rootSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final Method setMiBlurWinExc;
        final float scale;
        final String rootName;
        final boolean callerManagedUpdates;
        boolean bound = true;
        boolean updatesEnabled = true;

        Binding(
                SurfaceControl rootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                Method setMiBlurWinExc,
                float scale,
                String rootName,
                boolean callerManagedUpdates) {
            this.rootSurface = rootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.setMiBlurWinExc = setMiBlurWinExc;
            this.scale = scale;
            this.rootName = rootName;
            this.callerManagedUpdates = callerManagedUpdates;
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

            // LauncherGlassSession deliberately passes its stable Launcher root as materialHost and
            // owns its own refresh cadence. Dock's zero-copy renderer passes the actual Dock material
            // view instead, so its historical continuous producer remains completely independent.
            boolean callerManagedUpdates = materialHost.getRootView() == materialHost;

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

            Binding binding = new Binding(
                    rootSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    setMiBlurWinExc,
                    scale,
                    rootName,
                    callerManagedUpdates);
            if (binding.callerManagedUpdates) {
                schedulePauseUpdates(materialHost, binding, INITIAL_UPDATE_FRAMES);
            }

            MainHook.log(TAG + " PassBlur producer bound scale=" + scale
                    + " requestedScale=" + requestedScale
                    + " root=" + rootName
                    + " output=TextureView-in-root"
                    + " mode=" + (callerManagedUpdates ? "caller-managed" : "continuous")
                    + " exclusions=" + Arrays.toString(exclusions));
            return binding;
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

    static void requestSingleUpdate(Binding binding, View host) {
        if (binding == null || host == null || !binding.bound || !binding.callerManagedUpdates) return;
        setUpdatesEnabled(binding, true);
        schedulePauseUpdates(host, binding, INITIAL_UPDATE_FRAMES);
    }

    static void pauseUpdates(Binding binding) {
        if (binding == null || !binding.callerManagedUpdates) return;
        setUpdatesEnabled(binding, false);
    }

    private static void schedulePauseUpdates(View host, Binding binding, int framesLeft) {
        if (host == null || binding == null || !binding.bound || !binding.callerManagedUpdates) return;
        if (framesLeft <= 0) {
            pauseUpdates(binding);
            return;
        }
        host.postOnAnimation(() -> schedulePauseUpdates(host, binding, framesLeft - 1));
    }

    private static void setUpdatesEnabled(Binding binding, boolean enabled) {
        if (binding == null || !binding.bound || !binding.rootSurface.isValid()) return;
        if (binding.updatesEnabled == enabled) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(
                    transaction,
                    binding.rootSurface,
                    Boolean.valueOf(enabled),
                    Float.valueOf(binding.scale));
            transaction.apply();
            binding.updatesEnabled = enabled;
            MainHook.log(TAG + " PassBlur producer updates=" + enabled
                    + " root=" + binding.rootName);
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur update toggle failed: " + error);
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        try {
            if (!binding.rootSurface.isValid()) {
                binding.bound = false;
                binding.updatesEnabled = false;
                return;
            }
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
            binding.bound = false;
            binding.updatesEnabled = false;
            MainHook.log(TAG + " PassBlur producer unbound root=" + binding.rootName);
        } catch (Throwable error) {
            binding.bound = false;
            binding.updatesEnabled = false;
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
