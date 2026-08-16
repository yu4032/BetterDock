package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Controls workstation/laptop Dock divider lines independently from Dock geometry. */
final class DockDividerHook {
    private DockDividerHook() {}

    private static final WeakHashMap<View, View.OnLayoutChangeListener> pendingGeometry =
            new WeakHashMap<>();

    private static int channel(int v) { return Math.max(0, Math.min(v, 255)); }

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder",
                    "bindView",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        LiquidDockConfig.Divider cfg = LiquidDockConfig.load().divider;
                        if (!cfg.enabled) return result;

                        View line = (View) HookUtil.invoke(chain.getThisObject(), "getContent");
                        if (line != null) applyDivider(line, cfg, true);
                        return result;
                    });
            MainHook.log("[DC] dock divider hook installed");
        } catch (Throwable e) {
            MainHook.log("[DC] dock divider hook unavailable: " + e);
        }
    }

    /**
     * Applies values that are independent of layout immediately. Height and Y position are
     * derived only from a real parent height; the divider's own transient size is never used as
     * a substitute because the first RecyclerView bind can happen before its parent is laid out.
     */
    private static void applyDivider(View line, LiquidDockConfig.Divider cfg,
                                     boolean allowDeferredGeometry) {
        if (line == null || cfg == null || !cfg.enabled) return;
        if (!(line.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;

        ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) line.getLayoutParams();
        float density = line.getResources().getDisplayMetrics().density;

        if (cfg.explicitMode) {
            // Explicit mode: zero is a literal width/height/color value.
            lp.width = Math.max(0, Math.round(cfg.widthDp * density));
        } else if (cfg.widthDp != 0f) {
            lp.width = Math.round(cfg.widthDp * density);
        }

        boolean heightNeedsParent = cfg.explicitMode || cfg.heightPercent != 0f;
        if (heightNeedsParent) {
            int parentH = parentHeight(line);
            if (parentH > 0) {
                int targetH = Math.max(0,
                        Math.round(parentH * cfg.heightPercent / 100f));
                lp.topMargin = (parentH - targetH) / 2
                        + Math.round(cfg.yOffsetDp * density);
                lp.height = targetH;
            } else if (allowDeferredGeometry) {
                scheduleGeometryAfterLayout(line);
            }
        } else if (cfg.yOffsetDp != 0f) {
            // Legacy height=0 means keep MIUI's own height; offset that native geometry once.
            lp.topMargin += Math.round(cfg.yOffsetDp * density);
        }
        line.setLayoutParams(lp);

        if (cfg.explicitMode) {
            line.setBackgroundColor(Color.argb(channel(cfg.alpha),
                    channel(cfg.colorR), channel(cfg.colorG), channel(cfg.colorB)));
        } else {
            boolean hasColor = cfg.colorR != 0 || cfg.colorG != 0 || cfg.colorB != 0;
            boolean hasAlpha = cfg.alpha != 0;
            if (hasColor || hasAlpha) {
                int color = Color.rgb(hasColor ? channel(cfg.colorR) : 255,
                        hasColor ? channel(cfg.colorG) : 255,
                        hasColor ? channel(cfg.colorB) : 255);
                if (hasAlpha) color = Color.argb(channel(cfg.alpha),
                        Color.red(color), Color.green(color), Color.blue(color));
                line.setBackgroundColor(color);
            }
        }
    }

    private static int parentHeight(View line) {
        ViewParent parent = line.getParent();
        return parent instanceof View ? ((View) parent).getHeight() : 0;
    }

    /** One deferred retry per divider View, completed as soon as its parent has real bounds. */
    private static void scheduleGeometryAfterLayout(View line) {
        ViewParent rawParent = line.getParent();
        if (!(rawParent instanceof View)) return;
        View parent = (View) rawParent;

        synchronized (pendingGeometry) {
            if (pendingGeometry.containsKey(line)) return;
        }

        WeakReference<View> lineRef = new WeakReference<>(line);
        final View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom - top <= 0) return;
                finishDeferredGeometry(v, lineRef, this);
            }
        };
        synchronized (pendingGeometry) {
            // A bind may race between the first containsKey() and listener construction.
            if (pendingGeometry.containsKey(line)) return;
            pendingGeometry.put(line, listener);
        }
        parent.addOnLayoutChangeListener(listener);

        // Covers the race where the parent gained its final bounds just before listener install.
        parent.post(() -> {
            if (parent.getHeight() > 0) finishDeferredGeometry(parent, lineRef, listener);
        });
    }

    private static void finishDeferredGeometry(View parent, WeakReference<View> lineRef,
                                               View.OnLayoutChangeListener listener) {
        View line = lineRef.get();
        parent.removeOnLayoutChangeListener(listener);
        if (line == null) return;
        synchronized (pendingGeometry) {
            View.OnLayoutChangeListener current = pendingGeometry.get(line);
            if (current != listener) return;
            pendingGeometry.remove(line);
        }
        applyDivider(line, LiquidDockConfig.load().divider, false);
    }
}
