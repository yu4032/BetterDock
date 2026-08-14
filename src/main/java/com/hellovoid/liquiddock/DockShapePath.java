package com.hellovoid.liquiddock;

import android.graphics.Path;
import android.graphics.RectF;

/** Shared round-rect/squircle geometry for Liquid Glass host and sharp overlay. */
final class DockShapePath {
    private DockShapePath() {}

    static void build(Path out, float width, float height, float radius,
                      boolean squircle, float cp) {
        out.reset();
        if (width <= 1f || height <= 1f) return;
        RectF bounds = new RectF(.5f, .5f, width - .5f, height - .5f);
        float safeRadius = Math.max(0f,
                Math.min(radius, Math.min(bounds.width(), bounds.height()) * .5f));
        if (!squircle || safeRadius <= 1f) {
            out.addRoundRect(bounds, safeRadius, safeRadius, Path.Direction.CW);
            return;
        }

        float a = safeRadius;
        float c = a * Math.max(0.05f, Math.min(.95f, cp));
        float l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom;
        out.moveTo(l, t + a);
        out.cubicTo(l, t + a - c, l + a - c, t, l + a, t);
        out.lineTo(r - a, t);
        out.cubicTo(r - a + c, t, r, t + a - c, r, t + a);
        out.lineTo(r, b - a);
        out.cubicTo(r, b - a + c, r - a + c, b, r - a, b);
        out.lineTo(l + a, b);
        out.cubicTo(l + a - c, b, l, b - a + c, l, b - a);
        out.close();
    }
}
