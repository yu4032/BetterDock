package com.hellovoid.liquiddock;

/**
 * Holds root-space geometry steady while an ancestor is continuously transforming it.
 * Local material changes and explicitly trusted realtime ancestor motion commit immediately;
 * other ancestor-only motion is committed after the same geometry is observed for two
 * consecutive pre-draws.
 */
final class LauncherGlassGeometryStability {
    private static final int REQUIRED_STABLE_FRAMES = 2;

    private LauncherGlassGeometry.Snapshot pending;
    private int stableFrames;

    LauncherGlassGeometry.Snapshot select(
            LauncherGlassGeometry.Snapshot current,
            LauncherGlassGeometry.Snapshot observed,
            boolean localMaterialChanged) {
        return select(current, observed, localMaterialChanged, false);
    }

    LauncherGlassGeometry.Snapshot select(
            LauncherGlassGeometry.Snapshot current,
            LauncherGlassGeometry.Snapshot observed,
            boolean localMaterialChanged,
            boolean realtimeAncestorMotion) {
        if (same(current, observed)) {
            clearPending();
            return current;
        }
        if (localMaterialChanged || realtimeAncestorMotion || current == null || observed == null) {
            clearPending();
            return observed;
        }
        if (same(pending, observed)) {
            stableFrames++;
        } else {
            pending = observed;
            stableFrames = 1;
        }
        if (stableFrames >= REQUIRED_STABLE_FRAMES) {
            LauncherGlassGeometry.Snapshot committed = observed;
            clearPending();
            return committed;
        }
        return current;
    }

    private void clearPending() {
        pending = null;
        stableFrames = 0;
    }

    private static boolean same(
            LauncherGlassGeometry.Snapshot first,
            LauncherGlassGeometry.Snapshot second) {
        if (first == second) return true;
        return first != null && second != null && first.sameAs(second);
    }
}
