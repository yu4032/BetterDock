package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.WeakHashMap;

/**
 * Shared Dock border renderer for Liquid Glass and the native blur Dock.
 *
 * Design constraints:
 *  - no independent overlay View / RenderNode;
 *  - no Path.op();
 *  - no Paint.Style.STROKE for the configurable Dock border;
 *  - the center of the Dock is geometrically excluded from every border draw.
 *
 * A border is built from a validated outer path and inner path.  The canvas is
 * clipped to the outer path and then clipOutPath(inner) removes the complete
 * Dock interior.  If the inner contour is ever invalid during an animation,
 * that one border frame is skipped instead of degrading into a filled mask.
 */
final class DockStrokeRenderer {
    private static final float MAX_THICKNESS_FRACTION = 0.20f;
    private static final float MIN_INTERIOR_FRACTION = 0.35f;
    private static final long NATIVE_CONFIG_REFRESH_NS = 1_000_000_000L;

    private static final WeakHashMap<View, StrokeDrawable> INSTALLED =
            new WeakHashMap<>();

    private static boolean nativeHookInstalled;
    private static volatile LiquidDockConfig.Dock cachedNativeConfig;
    private static volatile long nativeConfigReadNanos;

    private DockStrokeRenderer() {}

    /**
     * Install the same renderer on HotSeatsListContentBlurBackground2.
     * This gives the native MIUI blur Dock a border without adding a child/overlay View.
     */
    static void installNativeHook(ClassLoader classLoader,
                                  LiquidDockConfig.Dock initialConfig) {
        if (nativeHookInstalled) return;
        nativeHookInstalled = true;
        cachedNativeConfig = initialConfig;
        nativeConfigReadNanos = System.nanoTime();

        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    "setBackgroundRadius",
                    chain -> {
                        Object result =
                                chain.proceed(chain.getArgs().toArray(new Object[0]));

                        if (MainHook.isWorkstationMode()) return result;

                        View background = (View) chain.getThisObject();
                        float radius = readNativeRadius(background);
                        LiquidDockConfig.Dock config = currentNativeConfig();

                        configure(background, config, radius);
                        return result;
                    },
                    float.class);
            MainHook.log("[DC] native blur Dock stroke renderer installed");
        } catch (Throwable error) {
            MainHook.log("[DC] native blur Dock stroke renderer unavailable: " + error);
        }
    }

    /**
     * Configure a View's foreground border.  For Liquid Glass this View is the glass
     * itself; for native mode it is HotSeatsListContentBlurBackground2.
     */
    static void configure(View host, LiquidDockConfig.Dock config, float radius) {
        if (host == null) return;

        synchronized (INSTALLED) {
            StrokeDrawable installed = INSTALLED.get(host);

            if (config == null || !config.strokeEnabled) {
                if (installed != null && host.getForeground() == installed) {
                    host.setForeground(installed.baseForeground());
                }
                INSTALLED.remove(host);
                return;
            }

            Style style = Style.from(config, host);
            Drawable current = host.getForeground();

            if (installed == null && current instanceof StrokeDrawable) {
                installed = (StrokeDrawable) current;
                INSTALLED.put(host, installed);
            }

            if (installed == null) {
                installed = new StrokeDrawable(current);
                INSTALLED.put(host, installed);
                host.setForeground(installed);
            }

            installed.setStyle(style);
            installed.setRadius(radius);
            host.invalidate();
        }
    }

    static void updateRadius(View host, float radius) {
        if (host == null) return;
        synchronized (INSTALLED) {
            StrokeDrawable drawable = INSTALLED.get(host);
            if (drawable == null && host.getForeground() instanceof StrokeDrawable) {
                drawable = (StrokeDrawable) host.getForeground();
                INSTALLED.put(host, drawable);
            }
            if (drawable != null) drawable.setRadius(radius);
        }
    }

    private static float readNativeRadius(View background) {
        try {
            Object value = HookUtil.getField(background, "mCornerRadius");
            if (value instanceof Number) {
                return Math.max(0f, ((Number) value).floatValue());
            }
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    private static LiquidDockConfig.Dock currentNativeConfig() {
        long now = System.nanoTime();
        if (now - nativeConfigReadNanos < NATIVE_CONFIG_REFRESH_NS) {
            return cachedNativeConfig;
        }
        synchronized (DockStrokeRenderer.class) {
            now = System.nanoTime();
            if (now - nativeConfigReadNanos >= NATIVE_CONFIG_REFRESH_NS) {
                try {
                    cachedNativeConfig = LiquidDockConfig.load().dock;
                } catch (Throwable ignored) {
                }
                nativeConfigReadNanos = now;
            }
            return cachedNativeConfig;
        }
    }

    private static final class Style {
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

    private static final class StrokeDrawable extends Drawable {
        private final Drawable baseForeground;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outer = new Path();
        private final Path inner = new Path();
        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();

        private Style style;
        private float radius;
        private int drawableAlpha = 255;
        private ColorFilter colorFilter;

        StrokeDrawable(Drawable baseForeground) {
            this.baseForeground = baseForeground;
            paint.setStyle(Paint.Style.FILL);
        }

        Drawable baseForeground() {
            return baseForeground;
        }

        void setStyle(Style style) {
            this.style = style;
            invalidateSelf();
        }

        void setRadius(float radius) {
            radius = Math.max(0f, radius);
            if (Float.floatToIntBits(this.radius)
                    == Float.floatToIntBits(radius)) {
                return;
            }
            this.radius = radius;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            if (baseForeground != null) {
                baseForeground.draw(canvas);
            }

            Style s = style;
            Rect bounds = getBounds();
            if (s == null
                    || s.widthPx <= 0f
                    || Color.alpha(s.color) <= 0
                    || bounds.width() <= 2
                    || bounds.height() <= 2) {
                return;
            }

            float width = bounds.width();
            float height = bounds.height();
            float minDim = Math.min(width, height);

            // A user-configured border is never allowed to consume the Dock body.
            float thickness = Math.min(
                    s.widthPx,
                    Math.max(1f, minDim * MAX_THICKNESS_FRACTION));
            if (!(thickness > 0f)) return;

            float effectiveRadius = Math.max(0f, radius + s.radiusDeltaPx);

            float outerInset;
            float innerInset;
            float outerRadius;
            float innerRadius;
            float innerCp;

            if (s.squircle) {
                // Preserve the old squircle offset semantics: the outer contour may
                // extend outside the host and is naturally clipped by the View.
                outerInset = -s.squircleOffsetPx;
                innerInset = outerInset + thickness;
                outerRadius =
                        Math.max(0f, effectiveRadius + s.squircleOffsetPx);
                innerRadius =
                        Math.max(0f, outerRadius - thickness * 0.5f);
                innerCp = 0.65f;
            } else if (s.fillDiff) {
                // Legacy fillDiff occupied the edge-to-width interval.
                outerInset = 0f;
                innerInset = thickness;
                outerRadius = Math.max(0f, effectiveRadius - 1f);
                innerRadius = Math.max(0f, outerRadius - thickness);
                innerCp = s.squircleCp;
            } else {
                // Legacy standard stroke was centered ~1 px inside the View edge.
                float half = thickness * 0.5f;
                outerInset = 1f - half;
                innerInset = 1f + half;
                float centerRadius = Math.max(0f, effectiveRadius - 1f);
                outerRadius = centerRadius + half;
                innerRadius = Math.max(0f, centerRadius - half);
                innerCp = s.squircleCp;
            }

            outerRect.set(
                    bounds.left + outerInset,
                    bounds.top + outerInset,
                    bounds.right - outerInset,
                    bounds.bottom - outerInset);

            innerRect.set(
                    bounds.left + innerInset,
                    bounds.top + innerInset,
                    bounds.right - innerInset,
                    bounds.bottom - innerInset);

            // Fail closed.  If animation-time geometry is bad, skip this border
            // frame.  Never fall back to painting the outer shape alone.
            if (outerRect.width() <= 1f
                    || outerRect.height() <= 1f
                    || innerRect.width() <= 1f
                    || innerRect.height() <= 1f
                    || innerRect.width()
                            < width * MIN_INTERIOR_FRACTION
                    || innerRect.height()
                            < height * MIN_INTERIOR_FRACTION) {
                return;
            }

            outer.rewind();
            inner.rewind();
            buildShape(
                    outer,
                    outerRect,
                    outerRadius,
                    s.squircle,
                    s.squircleCp);
            buildShape(
                    inner,
                    innerRect,
                    innerRadius,
                    s.squircle,
                    innerCp);

            int alpha = Math.round(
                    Color.alpha(s.color) * drawableAlpha / 255f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColorFilter(colorFilter);
            paint.setColor(Color.argb(
                    alpha,
                    Color.red(s.color),
                    Color.green(s.color),
                    Color.blue(s.color)));

            // The central Dock body is removed from the clip before any color is
            // drawn.  This is the invariant that prevents the old "whole Dock mask".
            int save = canvas.save();
            canvas.clipPath(outer);
            canvas.clipOutPath(inner);
            canvas.drawPath(outer, paint);
            canvas.restoreToCount(save);
        }

        private static void buildShape(
                Path out,
                RectF rect,
                float requestedRadius,
                boolean squircle,
                float cp) {
            float maxRadius =
                    Math.max(0f, Math.min(rect.width(), rect.height()) * 0.5f);
            float r =
                    Math.max(0f, Math.min(requestedRadius, maxRadius));

            if (!squircle || r <= 1f) {
                out.addRoundRect(
                        rect,
                        r,
                        r,
                        Path.Direction.CW);
                return;
            }

            float control =
                    r * Math.max(0.05f, Math.min(0.95f, cp));
            float left = rect.left;
            float top = rect.top;
            float right = rect.right;
            float bottom = rect.bottom;

            out.moveTo(left, top + r);
            out.cubicTo(
                    left,
                    top + r - control,
                    left + r - control,
                    top,
                    left + r,
                    top);
            out.lineTo(right - r, top);
            out.cubicTo(
                    right - r + control,
                    top,
                    right,
                    top + r - control,
                    right,
                    top + r);
            out.lineTo(right, bottom - r);
            out.cubicTo(
                    right,
                    bottom - r + control,
                    right - r + control,
                    bottom,
                    right - r,
                    bottom);
            out.lineTo(left + r, bottom);
            out.cubicTo(
                    left + r - control,
                    bottom,
                    left,
                    bottom - r + control,
                    left,
                    bottom - r);
            out.close();
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (baseForeground != null) {
                baseForeground.setBounds(bounds);
            }
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean changed =
                    baseForeground != null
                            && baseForeground.setState(state);
            if (changed) invalidateSelf();
            return changed;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean changed =
                    baseForeground != null
                            && baseForeground.setLevel(level);
            if (changed) invalidateSelf();
            return changed;
        }

        @Override
        public boolean isStateful() {
            return baseForeground != null
                    && baseForeground.isStateful();
        }

        @Override
        public void setAlpha(int alpha) {
            drawableAlpha = Math.max(0, Math.min(255, alpha));
            if (baseForeground != null) {
                baseForeground.setAlpha(alpha);
            }
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            colorFilter = filter;
            if (baseForeground != null) {
                baseForeground.setColorFilter(filter);
            }
            invalidateSelf();
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void jumpToCurrentState() {
            if (baseForeground != null) {
                baseForeground.jumpToCurrentState();
            }
        }
    }
}
