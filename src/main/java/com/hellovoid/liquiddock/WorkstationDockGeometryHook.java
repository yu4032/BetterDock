package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies workstation-only geometry to the visible laptop Dock container.
 *
 * The normal HotSeats blur background is deliberately hidden in workstation mode, so changing
 * HotSeatsListContentBlurBackground2 cannot affect the capsule the user actually sees. The
 * divider holder is a stable runtime anchor inside that visible Dock; walking its parent chain
 * lets us bind the real DockContainer without depending on a concrete vendor subclass name.
 */
final class WorkstationDockGeometryHook {
    private static final String LINE_HOLDER =
            "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder";

    // Weak key alone is insufficient if the value strongly owns the key. Keep both sides weak;
    // the View itself owns the listeners for exactly as long as it is attached.
    private static final WeakHashMap<View, WeakReference<Binding>> bindings = new WeakHashMap<>();
    private static int widthOffsetPx;
    private static boolean unresolvedChainLogged;

    private WorkstationDockGeometryHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig.Workstation config) {
        if (!config.dockEnabled) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        widthOffsetPx = Math.round(config.dockWidthOffset * scale);

        try {
            HookUtil.hookMethod(classLoader, LINE_HOLDER, "bindView", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object content = HookUtil.invoke(chain.getThisObject(), "getContent");
                if (content instanceof View) bindFromAnchor((View) content);
                return result;
            });
            MainHook.log("[DC] workstation visible Dock geometry hook installed widthOffset="
                    + widthOffsetPx);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation visible Dock geometry hook unavailable: " + error);
        }
    }

    static void onWorkstationModeChanged(boolean enabled) {
        ArrayList<Binding> snapshot = new ArrayList<>();
        synchronized (bindings) {
            Iterator<Map.Entry<View, WeakReference<Binding>>> iterator = bindings.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, WeakReference<Binding>> entry = iterator.next();
                Binding binding = entry.getValue() != null ? entry.getValue().get() : null;
                View container = entry.getKey();
                if (binding == null || container == null) {
                    iterator.remove();
                } else {
                    snapshot.add(binding);
                }
            }
        }
        for (Binding binding : snapshot) binding.apply(enabled);
    }

    private static void bindFromAnchor(View anchor) {
        View container = resolveDockContainer(anchor);
        if (container == null) {
            logUnresolvedChain(anchor);
            return;
        }

        Binding binding;
        synchronized (bindings) {
            WeakReference<Binding> bindingRef = bindings.get(container);
            binding = bindingRef != null ? bindingRef.get() : null;
            if (binding == null) {
                binding = new Binding(container);
                bindings.put(container, new WeakReference<>(binding));
                container.addOnLayoutChangeListener(binding);
                container.addOnAttachStateChangeListener(binding);
                MainHook.log("[DC] workstation Dock container resolved class="
                        + container.getClass().getName());
            }
        }
        final Binding bound = binding;
        container.post(() -> bound.apply(MainHook.isWorkstationMode()));
    }

    private static View resolveDockContainer(View anchor) {
        View current = anchor;
        for (int depth = 0; current != null && depth < 16; depth++) {
            String className = current.getClass().getName();
            if (className.contains("DockContainer")) return current;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void logUnresolvedChain(View anchor) {
        if (unresolvedChainLogged) return;
        unresolvedChainLogged = true;
        StringBuilder chain = new StringBuilder();
        View current = anchor;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (chain.length() > 0) chain.append(" <- ");
            chain.append(current.getClass().getName());
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        MainHook.log("[DC] workstation DockContainer not found from divider chain: " + chain);
    }

    private static final class Binding implements View.OnLayoutChangeListener,
            View.OnAttachStateChangeListener {
        private final WeakReference<View> containerRef;
        private final WorkstationDockWidthState widthState = new WorkstationDockWidthState();

        Binding(View container) {
            containerRef = new WeakReference<>(container);
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                   int oldLeft, int oldTop, int oldRight, int oldBottom) {
            apply(MainHook.isWorkstationMode());
        }

        @Override
        public void onViewAttachedToWindow(View v) {}

        @Override
        public void onViewDetachedFromWindow(View v) {
            try { v.removeOnLayoutChangeListener(this); } catch (Throwable ignored) {}
            try { v.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            synchronized (bindings) {
                WeakReference<Binding> current = bindings.get(v);
                if (current != null && current.get() == this) bindings.remove(v);
            }
            containerRef.clear();
        }

        void apply(boolean workstation) {
            View container = containerRef.get();
            if (container == null) return;
            int observedWidth = container.getWidth();
            if (observedWidth <= 0) return;

            int targetWidth = workstation
                    ? widthState.targetWidth(observedWidth, widthOffsetPx)
                    : widthState.restoreWidth(observedWidth);
            if (targetWidth <= 0 || targetWidth == observedWidth) return;

            ViewGroup.LayoutParams lp = container.getLayoutParams();
            if (lp == null) return;
            lp.width = targetWidth;
            container.setLayoutParams(lp);
            container.requestLayout();
            MainHook.log("[DC] workstation Dock width " + observedWidth + " -> " + targetWidth
                    + " active=" + workstation + " class=" + container.getClass().getName());
        }
    }
}
