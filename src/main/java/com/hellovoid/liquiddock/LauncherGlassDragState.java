package com.hellovoid.liquiddock;

import java.util.Objects;

/** Immutable type-agnostic state for the single active launcher drag-glass object. */
final class LauncherGlassDragState {
    enum Kind {
        FOLDER,
        WIDGET,
        ICON,
        UNKNOWN
    }

    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() { return right - left; }
        float height() { return bottom - top; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds)) return false;
            Bounds value = (Bounds) other;
            return Float.compare(left, value.left) == 0
                    && Float.compare(top, value.top) == 0
                    && Float.compare(right, value.right) == 0
                    && Float.compare(bottom, value.bottom) == 0;
        }

        @Override public int hashCode() {
            return Objects.hash(left, top, right, bottom);
        }
    }

    final Object token;
    final Kind kind;
    final Bounds rootBounds;
    final float cornerRadiusPx;
    final float scale;
    final float rotation;
    final float alpha;

    LauncherGlassDragState(
            Object token,
            Kind kind,
            Bounds rootBounds,
            float cornerRadiusPx,
            float scale,
            float rotation,
            float alpha) {
        this.token = token;
        this.kind = kind != null ? kind : Kind.UNKNOWN;
        this.rootBounds = rootBounds;
        this.cornerRadiusPx = Math.max(0f, finiteOr(cornerRadiusPx, 0f));
        this.scale = finiteOr(scale, 1f);
        this.rotation = finiteOr(rotation, 0f);
        this.alpha = clamp01(finiteOr(alpha, 1f));
    }

    LauncherGlassDragState withGeometry(
            Bounds bounds, float nextScale, float nextRotation, float nextAlpha) {
        return new LauncherGlassDragState(
                token, kind, bounds, cornerRadiusPx, nextScale, nextRotation, nextAlpha);
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
