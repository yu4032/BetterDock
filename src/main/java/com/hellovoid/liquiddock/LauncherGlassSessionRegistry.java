package com.hellovoid.liquiddock;

import android.view.View;

import java.util.WeakHashMap;

/** One shared GPU glass session per stable Launcher ViewRoot. */
final class LauncherGlassSessionRegistry {
    private static final WeakHashMap<View, LauncherGlassSession> SESSIONS = new WeakHashMap<>();

    private LauncherGlassSessionRegistry() {}

    static synchronized LauncherGlassSession acquire(
            View materialHost, LiquidDockConfig.Glass glassConfig) {
        View root = resolveStableRoot(materialHost);
        if (root == null) return null;
        LauncherGlassSession current = SESSIONS.get(root);
        if (current != null && !current.isShutdown()) {
            current.setGlassConfig(glassConfig);
            return current;
        }
        LauncherGlassSession created = new LauncherGlassSession(root, glassConfig);
        SESSIONS.put(root, created);
        return created;
    }

    static View resolveStableRoot(View materialHost) {
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || materialHost.getWidth() <= 0 || materialHost.getHeight() <= 0) return null;
        View root = materialHost.getRootView();
        if (root == null || root == materialHost || !root.isAttachedToWindow()
                || root.getWidth() <= 0 || root.getHeight() <= 0 || root.getWindowToken() == null) {
            return null;
        }
        return root;
    }

    static synchronized void forget(View root, LauncherGlassSession session) {
        if (root == null || session == null) return;
        if (SESSIONS.get(root) == session) SESSIONS.remove(root);
    }
}
