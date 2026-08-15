from pathlib import Path


TARGET = Path("src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java")


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected exactly one match, found {count}\n--- old ---\n{old}")
    return text.replace(old, new, 1)


text = TARGET.read_text()

text = replace_once(
    text,
    """    private static final class Style {
        final boolean squircle;
        final boolean fillDiff;
        final float widthPx;
        final float squircleOffsetPx;
        final float squircleCp;
        final float radiusDeltaPx;
        final int color;

        Style(boolean squircle,
              boolean fillDiff,
              float widthPx,
              float squircleOffsetPx,
              float squircleCp,
              float radiusDeltaPx,
              int color) {
            this.squircle = squircle;
            this.fillDiff = fillDiff;
            this.widthPx = widthPx;
            this.squircleOffsetPx = squircleOffsetPx;
            this.squircleCp = squircleCp;
            this.radiusDeltaPx = radiusDeltaPx;
            this.color = color;
        }

        static Style from(LiquidDockConfig.Dock config, View host) {
            float density = host.getResources().getDisplayMetrics().density;
            float dimensionScale = config.dimensionsDp ? density : 1f;
            float cornerScale = config.cornersDp ? density : 1f;

            float width = config.squircle
                    ? config.squircleStrokeWidth
                    : (config.fillDiff
                            ? config.strokeWidth
                            : config.standardStrokeWidth);

            // Preserve the old overlay's visual opacity.
            int legacyBaseAlpha = config.squircle ? 200 : 150;
            int effectiveAlpha = Math.round(
                    legacyBaseAlpha
                            * Math.max(0, Math.min(255, config.strokeAlpha))
                            / 255f);

            // Full customization used:
            // stroke radius = system radius + cornerOffset
            // blur radius   = system radius + blurCornerOffset
            // Liquid-only/native mode simply follows the native radius.
            float radiusDelta = config.enabled
                    ? (config.cornerOffset - config.blurCornerOffset) * cornerScale
                    : 0f;

            return new Style(
                    config.squircle,
                    config.fillDiff,
                    Math.max(0f, width * dimensionScale),
                    config.squircle
                            ? config.squircleStrokeOffset * dimensionScale
                            : 0f,
                    Math.max(0.05f, Math.min(0.95f, config.squircleCp)),
                    radiusDelta,
                    Color.argb(
                            effectiveAlpha,
                            config.strokeR,
                            config.strokeG,
                            config.strokeB));
        }
    }
""",
    """    private static final class Style {
        final boolean squircle;
        final boolean fillDiff;
        final float widthPx;
        final float squircleOffsetPx;
        final float squircleCp;
        final float radiusDeltaPx;
        final int color;
        final boolean shadowEnabled;
        final float shadowRadiusPx;
        final int shadowAlpha;

        Style(boolean squircle,
              boolean fillDiff,
              float widthPx,
              float squircleOffsetPx,
              float squircleCp,
              float radiusDeltaPx,
              int color,
              boolean shadowEnabled,
              float shadowRadiusPx,
              int shadowAlpha) {
            this.squircle = squircle;
            this.fillDiff = fillDiff;
            this.widthPx = widthPx;
            this.squircleOffsetPx = squircleOffsetPx;
            this.squircleCp = squircleCp;
            this.radiusDeltaPx = radiusDeltaPx;
            this.color = color;
            this.shadowEnabled = shadowEnabled;
            this.shadowRadiusPx = shadowRadiusPx;
            this.shadowAlpha = shadowAlpha;
        }

        static Style from(LiquidDockConfig.Dock config, View host) {
            float density = host.getResources().getDisplayMetrics().density;
            float dimensionScale = config.dimensionsDp ? density : 1f;
            float cornerScale = config.cornersDp ? density : 1f;

            float width = config.squircle
                    ? config.squircleStrokeWidth
                    : (config.fillDiff
                            ? config.strokeWidth
                            : config.standardStrokeWidth);

            // Preserve the old overlay's visual opacity.
            int legacyBaseAlpha = config.squircle ? 200 : 150;
            int effectiveAlpha = Math.round(
                    legacyBaseAlpha
                            * Math.max(0, Math.min(255, config.strokeAlpha))
                            / 255f);

            // Full customization used:
            // stroke radius = system radius + cornerOffset
            // blur radius   = system radius + blurCornerOffset
            // Liquid-only/native mode simply follows the native radius.
            float radiusDelta = config.enabled
                    ? (config.cornerOffset - config.blurCornerOffset) * cornerScale
                    : 0f;

            return new Style(
                    config.squircle,
                    config.fillDiff,
                    Math.max(0f, width * dimensionScale),
                    config.squircle
                            ? config.squircleStrokeOffset * dimensionScale
                            : 0f,
                    Math.max(0.05f, Math.min(0.95f, config.squircleCp)),
                    radiusDelta,
                    Color.argb(
                            effectiveAlpha,
                            config.strokeR,
                            config.strokeG,
                            config.strokeB),
                    config.strokeShadow,
                    Math.max(0f, config.strokeShadowRadius * dimensionScale),
                    Math.max(0, Math.min(255, config.strokeShadowAlpha)));
        }
    }
""",
)

text = replace_once(
    text,
    """        private final Path outer = new Path();
        private final Path inner = new Path();
        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();

        private Style style;
        private float radius;
        private boolean geometryDirty = true;
        private boolean geometryValid;
""",
    """        private final Path outer = new Path();
        private final Path inner = new Path();
        private final Path shadowOuter = new Path();
        private final Path shadowInner = new Path();
        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();
        private final RectF shadowRect = new RectF();

        private Style style;
        private float radius;
        private float geometryThickness;
        private float outerRadius;
        private float innerRadius;
        private float innerCp;
        private boolean geometryDirty = true;
        private boolean geometryValid;
""",
)

text = replace_once(
    text,
    """            if (!ensureGeometry(s, bounds)) return;

            int alpha = Math.round(
""",
    """            if (!ensureGeometry(s, bounds)) return;

            drawStrokeShadow(canvas, s);

            int alpha = Math.round(
""",
)

text = replace_once(
    text,
    """        private boolean ensureGeometry(Style s, Rect bounds) {
""",
    """        private void drawStrokeShadow(Canvas canvas, Style s) {
            if (!s.shadowEnabled
                    || s.shadowRadiusPx <= 0f
                    || s.shadowAlpha <= 0
                    || geometryThickness <= 0f) {
                return;
            }

            // The historical stroke shadow faded inward from the outer contour. Keep that
            // visual model, but clamp it to the current border ring so the Dock body remains
            // geometrically excluded just like the foreground stroke.
            float reach = Math.min(s.shadowRadiusPx, geometryThickness);
            int steps = Math.max(1, Math.min(40, (int) Math.ceil(reach)));

            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColorFilter(colorFilter);

            for (int i = steps; i >= 1; i--) {
                float outerDistance = reach * (i - 1f) / steps;
                float innerDistance = reach * i / steps;
                float outerT = Math.min(1f, outerDistance / geometryThickness);
                float innerT = Math.min(1f, innerDistance / geometryThickness);

                buildInterpolatedContour(shadowOuter, outerT, s);
                buildInterpolatedContour(shadowInner, innerT, s);

                float strength = 1f - (i - 1f) / steps;
                int alpha = Math.round(
                        s.shadowAlpha * strength * strength * drawableAlpha / 255f);
                if (alpha <= 0) continue;
                paint.setColor(Color.argb(alpha, 0, 0, 0));

                int save = canvas.save();
                canvas.clipPath(shadowOuter);
                canvas.clipOutPath(shadowInner);
                canvas.drawPath(shadowOuter, paint);
                canvas.restoreToCount(save);
            }
        }

        private void buildInterpolatedContour(Path out, float t, Style s) {
            float clamped = Math.max(0f, Math.min(1f, t));
            shadowRect.set(
                    lerp(outerRect.left, innerRect.left, clamped),
                    lerp(outerRect.top, innerRect.top, clamped),
                    lerp(outerRect.right, innerRect.right, clamped),
                    lerp(outerRect.bottom, innerRect.bottom, clamped));
            float contourRadius = lerp(outerRadius, innerRadius, clamped);
            float contourCp = lerp(s.squircleCp, innerCp, clamped);
            out.rewind();
            buildShape(out, shadowRect, contourRadius, s.squircle, contourCp);
        }

        private static float lerp(float start, float end, float t) {
            return start + (end - start) * t;
        }

        private boolean ensureGeometry(Style s, Rect bounds) {
""",
)

text = replace_once(
    text,
    """            outer.rewind();
            inner.rewind();
            buildShape(outer, outerRect, outerRadius, s.squircle, s.squircleCp);
            buildShape(inner, innerRect, innerRadius, s.squircle, innerCp);
            geometryValid = true;
""",
    """            geometryThickness = thickness;
            this.outerRadius = outerRadius;
            this.innerRadius = innerRadius;
            this.innerCp = innerCp;

            outer.rewind();
            inner.rewind();
            buildShape(outer, outerRect, outerRadius, s.squircle, s.squircleCp);
            buildShape(inner, innerRect, innerRadius, s.squircle, innerCp);
            geometryValid = true;
""",
)

TARGET.write_text(text)
