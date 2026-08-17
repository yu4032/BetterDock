package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Keeps the whole-Dock background shadow independent from Dock size/blur customization.
 *
 * <p>The normal MainHook path already owns this shadow while {@code dock_customization}
 * is enabled. This compatibility hook intentionally activates only when that switch is off,
 * so the Shadow page remains functional without duplicating the existing full-customization
 * renderer. The shadow stays in an expanded software View because drawing it inside the Dock
 * foreground would clip the blur at the host View bounds.</p>
 */
final class DockShadowIndependenceHook {
    private static boolean installed;
    private static LiquidDockConfig.Dock dockConfig;
    private static View background;
    private static ShadowView shadowView;
    private static View nativeShadowTarget;
    private static View observedBackground;

    private static final View.OnLayoutChangeListener LAYOUT_LISTENER =
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    syncFromBackground(v);

    private DockShadowIndependenceHook() {}

    static void install(ClassLoader classLoader) {
        LiquidDockConfig config = LiquidDockConfig.load();
        if (!config.enabled) return;
        if (config.dock.enabled) return;
        if (installed) return;
        installed = true;
        dockConfig = config.dock;

        installNativeShadowSuppression(classLoader);
        installShadowHost(classLoader);
        installGeometrySync(classLoader);
        installWorkstationVisibilitySync(classLoader);
        MainHook.log("[DC] independent Dock shadow ownership installed");
    }

    private static void installNativeShadowSuppression(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeats",
                    "getMingouStaticDockBlurShadowTarget",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (result instanceof View) nativeShadowTarget = (View) result;
                        return result;
                    });

            Class<?> shadowUtils = Class.forName(
                    "com.miui.home.launcher.common.MiShadowUtils", false, classLoader);
            HookUtil.hookMethod(shadowUtils, "applyViewShadow",
                    new Class<?>[]{View.class, int.class, float.class, float.class,
                            float.class, float.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (MainHook.isWorkstationMode() || args[0] != nativeShadowTarget) {
                            return chain.proceed(args);
                        }
                        args[1] = Color.TRANSPARENT;
                        args[2] = 0f;
                        args[3] = 0f;
                        args[4] = 0f;
                        return chain.proceed(args);
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] independent native Dock shadow suppression unavailable: " + error);
        }
    }

    private static void installShadowHost(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher", "setupViews",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            Object hotSeats = HookUtil.getField(chain.getThisObject(), "mHotSeats");
                            if (hotSeats == null) return result;

                            clearNativeShadow(hotSeats);
                            View nextBackground = (View) HookUtil.getField(hotSeats, "mBlurBackground2");
                            if (nextBackground == null) return result;
                            bindBackground(nextBackground);

                            removeShadowView();
                            if (!dockConfig.shadowEnabled) return result;

                            ViewGroup parent = (ViewGroup) nextBackground.getParent();
                            if (parent == null) return result;
                            shadowView = new ShadowView(nextBackground, dockConfig);
                            shadowView.setId(View.generateViewId());
                            int backgroundIndex = parent.indexOfChild(nextBackground);
                            parent.addView(shadowView, Math.max(0, backgroundIndex),
                                    new FrameLayout.LayoutParams(1, 1));
                            disableAncestorClipping(parent);
                            syncFromBackground(nextBackground);
                        } catch (Throwable error) {
                            MainHook.log("[DC] independent Dock shadow init failed: " + error);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] independent Dock shadow setup hook unavailable: " + error);
        }
    }

    private static void installGeometrySync(ClassLoader classLoader) {
        try {
            Class<?> backgroundClass = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    false, classLoader);
            HookUtil.hookMethod(backgroundClass, "setBackgroundWidth",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        syncFromBackground((View) chain.getThisObject());
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundHeight",
                    new Class<?>[]{int.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        syncFromBackground((View) chain.getThisObject());
                        return result;
                    });
            HookUtil.hookMethod(backgroundClass, "setBackgroundRadius",
                    new Class<?>[]{float.class}, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        syncFromBackground((View) chain.getThisObject());
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log("[DC] independent Dock shadow geometry hook unavailable: " + error);
        }
    }

    private static void installWorkstationVisibilitySync(ClassLoader classLoader) {
        try {
            Class<?> stateManager = Class.forName(
                    "com.miui.home.launcher.laptop.LaptopStateManager", false, classLoader);
            HookUtil.hookMethod(stateManager, "onLaptopModeChanged",
                    new Class<?>[]{boolean.class}, chain -> {
                        boolean entering = Boolean.TRUE.equals(chain.getArgs().get(0));
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        refreshWorkstationVisibility(entering);
                        return result;
                    });
        } catch (Throwable ignored) {
            // MainHook has a legacy workstation-state fallback; geometry callbacks and draw()
            // still fail closed through MainHook.isWorkstationMode() on those builds.
        }
    }

    private static void clearNativeShadow(Object hotSeats) {
        if (MainHook.isWorkstationMode()) return;
        try {
            Object target = HookUtil.invoke(hotSeats, "getMingouStaticDockBlurShadowTarget");
            if (!(target instanceof View)) return;
            nativeShadowTarget = (View) target;
            HookUtil.invokeStatic("com.miui.home.launcher.common.MiShadowUtils",
                    "applyViewShadow", nativeShadowTarget,
                    Color.TRANSPARENT, 0f, 0f, 0f, 1f);
        } catch (Throwable error) {
            MainHook.log("[DC] independent native Dock shadow clear failed: " + error);
        }
    }

    private static void bindBackground(View nextBackground) {
        background = nextBackground;
        if (observedBackground == nextBackground) return;
        if (observedBackground != null) {
            try { observedBackground.removeOnLayoutChangeListener(LAYOUT_LISTENER); }
            catch (Throwable ignored) {}
        }
        observedBackground = nextBackground;
        nextBackground.addOnLayoutChangeListener(LAYOUT_LISTENER);
    }

    private static void removeShadowView() {
        ShadowView old = shadowView;
        shadowView = null;
        if (old == null) return;
        try {
            if (old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
        } catch (Throwable ignored) {}
    }

    private static void disableAncestorClipping(ViewGroup parent) {
        ViewGroup current = parent;
        for (int level = 0; level < 4 && current != null; level++) {
            current.setClipChildren(false);
            current.setClipToPadding(false);
            android.view.ViewParent next = current.getParent();
            current = next instanceof ViewGroup ? (ViewGroup) next : null;
        }
    }

    private static void syncFromBackground(View source) {
        ShadowView shadow = shadowView;
        if (shadow == null || source == null || source != background) return;
        try {
            int width = HookUtil.getIntField(source, "mWidth");
            int height = HookUtil.getIntField(source, "mHeight");
            if (width <= 0) width = source.getWidth();
            if (height <= 0) height = source.getHeight();
            float radius = 0f;
            Object value = HookUtil.getField(source, "mCornerRadius");
            if (value instanceof Number) radius = ((Number) value).floatValue();
            shadow.updateGeometry(width, height, radius);
        } catch (Throwable ignored) {
            shadow.updatePositionOnly();
        }
    }

    private static void refreshWorkstationVisibility(boolean entering) {
        ShadowView shadow = shadowView;
        if (shadow == null) return;
        shadow.setVisibility(entering ? View.GONE : View.VISIBLE);
        if (!entering && background != null) syncFromBackground(background);
    }

    private static Path squirclePath(RectF rect, float radius, float controlPoint) {
        Path path = new Path();
        if (radius <= 1f) {
            path.addRect(rect, Path.Direction.CW);
            return path;
        }
        float c = radius * controlPoint;
        float left = rect.left, top = rect.top, right = rect.right, bottom = rect.bottom;
        path.moveTo(left, top + radius);
        path.cubicTo(left, top + radius - c, left + radius - c, top,
                left + radius, top);
        path.lineTo(right - radius, top);
        path.cubicTo(right - radius + c, top, right, top + radius - c,
                right, top + radius);
        path.lineTo(right, bottom - radius);
        path.cubicTo(right, bottom - radius + c, right - radius + c, bottom,
                right - radius, bottom);
        path.lineTo(left + radius, bottom);
        path.cubicTo(left + radius - c, bottom, left, bottom - radius + c,
                left, bottom - radius);
        path.close();
        return path;
    }

    private static final class ShadowView extends View {
        private final View dockBackground;
        private final LiquidDockConfig.Dock config;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int dockWidth;
        private int dockHeight;
        private float nativeRadius;
        private final int blurRadius;
        private final int spread;
        private final int offsetY;
        private final int alpha;
        private final int pad;
        private final int squircleOffset;

        ShadowView(View background, LiquidDockConfig.Dock config) {
            super(background.getContext());
            dockBackground = background;
            this.config = config;

            float shadowScale = background.getResources().getDisplayMetrics().density;
            int radius = Math.max(1, Math.round(config.shadowRadius * shadowScale));
            int size = Math.max(1, Math.round(config.shadowSize * shadowScale));
            offsetY = Math.round(config.shadowY * shadowScale);
            alpha = Math.max(0, Math.min(255, config.shadowAlpha));
            blurRadius = Math.min(radius, size);
            spread = Math.max(0, size - blurRadius);
            pad = Math.max(4, size + Math.abs(offsetY) + 4);

            float strokeGeometryScale = config.dimensionsDp ? shadowScale : 1f;
            squircleOffset = Math.round(config.squircleStrokeOffset * strokeGeometryScale);
            paint.setColor(Color.argb(1, 0, 0, 0));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void updateGeometry(int width, int height, float radius) {
            dockWidth = Math.max(0, width);
            dockHeight = Math.max(0, height);
            nativeRadius = Math.max(0f, radius);
            setVisibility(MainHook.isWorkstationMode() ? View.GONE : View.VISIBLE);
            ViewGroup.LayoutParams params = getLayoutParams();
            if (params != null && dockWidth > 0 && dockHeight > 0) {
                int targetWidth = dockWidth + pad * 2;
                int targetHeight = dockHeight + pad * 2;
                if (params.width != targetWidth || params.height != targetHeight) {
                    params.width = targetWidth;
                    params.height = targetHeight;
                    setLayoutParams(params);
                }
            }
            updatePositionOnly();
            invalidate();
        }

        void updatePositionOnly() {
            setX(dockBackground.getX() - pad);
            setY(dockBackground.getY() - pad);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (MainHook.isWorkstationMode() || dockWidth <= 0 || dockHeight <= 0
                    || alpha <= 0) return;

            float configuredRadius = DockStrokeRenderer.resolveConfiguredRadius(
                    dockBackground, config, nativeRadius);
            float left = pad;
            float top = pad;
            RectF bounds;
            float corner;
            if (config.squircle) {
                bounds = new RectF(
                        left - squircleOffset - spread,
                        top - squircleOffset - spread,
                        left + dockWidth + squircleOffset + spread,
                        top + dockHeight + squircleOffset + spread);
                corner = Math.max(0f, configuredRadius + squircleOffset + spread);
            } else {
                bounds = new RectF(
                        left + 1f - spread,
                        top + 1f - spread,
                        left + dockWidth - 1f + spread,
                        top + dockHeight - 1f + spread);
                corner = Math.max(0f, configuredRadius - 1f + spread);
            }

            Path shape = config.squircle
                    ? squirclePath(bounds, corner,
                            Math.max(0.05f, Math.min(0.95f, config.squircleCp)))
                    : new Path();
            if (!config.squircle) {
                shape.addRoundRect(bounds, corner, corner, Path.Direction.CW);
            }
            paint.setShadowLayer(blurRadius, 0f, offsetY, Color.argb(alpha, 0, 0, 0));
            canvas.drawPath(shape, paint);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (shadowView == this) shadowView = null;
            super.onDetachedFromWindow();
        }
    }
}
