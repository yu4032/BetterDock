package com.hellovoid.liquiddock;

/** Owns the single active launcher drag-glass state independently of source type. */
final class LauncherGlassDragCoordinator {
    private LauncherGlassDragState current;

    synchronized boolean begin(
            Object token,
            LauncherGlassDragState.Kind kind,
            LauncherGlassDragState.Bounds rootBounds,
            float cornerRadiusPx) {
        if (!valid(token, rootBounds)) return false;
        current = new LauncherGlassDragState(
                token, kind, rootBounds, cornerRadiusPx, 1f, 0f, 1f);
        return true;
    }

    synchronized boolean update(
            Object token,
            LauncherGlassDragState.Bounds rootBounds,
            float scale,
            float rotation,
            float alpha) {
        if (current == null || current.token != token || !valid(token, rootBounds)) return false;
        current = current.withGeometry(rootBounds, scale, rotation, alpha);
        return true;
    }

    synchronized boolean end(Object token) {
        return clear(token);
    }

    synchronized boolean cancel(Object token) {
        return clear(token);
    }

    synchronized LauncherGlassDragState current() {
        return current;
    }

    private boolean clear(Object token) {
        if (current == null || current.token != token) return false;
        current = null;
        return true;
    }

    private static boolean valid(Object token, LauncherGlassDragState.Bounds bounds) {
        return token != null && bounds != null && bounds.width() > 0f && bounds.height() > 0f;
    }
}
