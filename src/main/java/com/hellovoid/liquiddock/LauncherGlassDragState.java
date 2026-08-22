package com.hellovoid.liquiddock;

import android.graphics.RectF;

/** Immutable type-agnostic state for the single active launcher drag-glass object. */
final class LauncherGlassDragState {
    enum Kind {
        FOLDER,
        WIDGET,
        ICON,
        UNKNOWN
    }

    final Object token;
    final Kind kind;
    final RectF rootBounds;
    final float cornerRadiusPx;
    final float scale;
    final float rotation;
    final float alpha;

    LauncherGlassDragState(
            Object token,
            Kind kind,
            RectF rootBounds,
            float cornerRadiusPx,
            float scale,
            float rotation,
            float alpha) {
        this.token = token;
        this.kind = kind != null ? kind : Kind.UNKNOWN;
        this.rootBounds = rootBounds != null ? new RectF(rootBounds) : new RectF();
        this.cornerRadiusPx = Math.max(0f, finiteOr(cornerRadiusPx, 0f));
        this.scale = finiteOr(scale, 1f);
        this.rotation = finiteOr(rotation, 0f);
        this.alpha = clamp01(finiteOr(alpha, 1f));
    }

    LauncherGlassDragState withGeometry(
            RectF bounds, float nextScale, float nextRotation, float nextAlpha) {
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
