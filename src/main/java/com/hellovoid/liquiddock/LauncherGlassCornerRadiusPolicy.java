package com.hellovoid.liquiddock;

/** Shared folder-corner override: 0dp preserves native/auto behavior. */
final class LauncherGlassCornerRadiusPolicy {
    private LauncherGlassCornerRadiusPolicy() {}

    static float resolve(
            float overrideDp, float density, float nativeRadiusPx, float fallbackRadiusPx) {
        if (Float.isFinite(overrideDp) && overrideDp > 0f) {
            float safeDensity = Float.isFinite(density) && density > 0f ? density : 1f;
            float safeDp = Math.min(96f, overrideDp);
            return safeDp * safeDensity;
        }
        if (Float.isFinite(nativeRadiusPx) && nativeRadiusPx > 0f) {
            return nativeRadiusPx;
        }
        return Float.isFinite(fallbackRadiusPx) ? Math.max(0f, fallbackRadiusPx) : 0f;
    }
}
