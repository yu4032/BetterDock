package com.hellovoid.betterdock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static View overlay, shadowView, oldBg, nativeShadowTarget;
    private static int bgW, bgH, shadowPad;
    private static int strokeBaseR = 255, strokeBaseG = 255, strokeBaseB = 255, strokeBaseAlpha = 255;
    private static float bgR = 30f;
    private static float strokeR = 30f;
    private static float gyroX, gyroY, smoothLx, smoothLy;
    private static SensorManager motionSensorManager;
    private static SensorEventListener motionSensorListener;

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    private static Path squirclePath(RectF r, float rad) { return squirclePath(r, rad, 0.65f); }
    private static Path squirclePath(RectF r, float rad, float cp) {
        Path p = new Path(); if (rad <= 1) { p.addRect(r, Path.Direction.CW); return p; }
        float a = rad, c = a * cp, l = r.left, t = r.top, ri = r.right, b = r.bottom;
        p.moveTo(l, t + a); p.cubicTo(l, t + a - c, l + a - c, t, l + a, t); p.lineTo(ri - a, t);
        p.cubicTo(ri - a + c, t, ri, t + a - c, ri, t + a); p.lineTo(ri, b - a);
        p.cubicTo(ri, b - a + c, ri - a + c, b, ri - a, b); p.lineTo(l + a, b);
        p.cubicTo(l + a - c, b, l, b - a + c, l, b - a); p.close(); return p;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;

        ConfigReader cfg = ConfigReader.load();
        if (cfg.b("home_grid_8x4", true)) HomeGridHook.install(lpparam.classLoader,
            cfg.i("grid_margin_left", 0), cfg.i("grid_margin_right", 0),
            cfg.i("grid_margin_top", 0), cfg.i("grid_margin_bottom", 0));
        XposedBridge.log("[DC] init: bl=" + cfg.i("blur_radius", -1) + " lm=" + cfg.s("light_mode", "?") + " sq=" + cfg.b("squircle", false));

        String lm = cfg.s("light_mode", "fixed");
        boolean sq = cfg.b("squircle", false), fd = cfg.b("fill_diff", false);
        int wo = cfg.i("width_offset", 0), ho = cfg.i("height_offset", 0), co = cfg.i("corner_offset", -1), br = cfg.i("blur_radius", 100);
        int blurCo = cfg.i("blur_corner_offset", 0);
        int spacing = cfg.i("dock_spacing", 0);
        int bottomOffset = cfg.i("dock_bottom_offset", 0);
        ClassLoader cl = lpparam.classLoader;

        try {
            String hsc = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";

            if (bottomOffset != 0) { try {
                Class<?> deviceConfig = XposedHelpers.findClass("com.miui.home.launcher.DeviceConfig", cl);
                XposedHelpers.findAndHookMethod(deviceConfig, "getHotSeatsMarginBottom",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            p.setResult((Integer) p.getResult() + bottomOffset);
                        }
                    });
            } catch (Throwable e) { XposedBridge.log("[DC] bottom offset hook unavailable: " + e); } }

            if (spacing != 0) { try {
                Class<?> recyclerView = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView", cl);
                Class<?> recyclerState = XposedHelpers.findClass("androidx.recyclerview.widget.RecyclerView$State", cl);
                Class<?> layoutManager = XposedHelpers.findClass(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager", cl);
                XposedHelpers.findAndHookMethod(
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    cl, "getItemOffsets", Rect.class, View.class, recyclerView, recyclerState,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Rect out = (Rect) p.args[0];
                            out.left += spacing;
                            out.right += spacing;
                        }
                    });
                XposedHelpers.findAndHookMethod(layoutManager, "updateBackgroundView",
                    FrameLayout.class, int.class, int.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            int itemCount = (Integer) XposedHelpers.callMethod(p.thisObject, "getItemCount");
                            if (itemCount > 0)
                                p.args[1] = (Integer) p.args[1] + spacing * 2 * itemCount;
                        }
                    });
            } catch (Throwable e) { XposedBridge.log("[DC] spacing hook unavailable: " + e); } }

            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundWidth", int.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (wo != 0) p.args[0] = (int) p.args[0] + wo; }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundHeight", int.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (ho != 0) p.args[0] = (int) p.args[0] + ho; }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }});
            XposedHelpers.findAndHookMethod(hsc, cl, "setBackgroundRadius", float.class,
                new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) {
                        float systemRadius = (Float) p.args[0];
                        strokeR = Math.max(0f, systemRadius + co);
                        p.args[0] = Math.max(0f, systemRadius + blurCo);
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject);
                        if (sq) { View v = (View) p.thisObject; float r = (Float) XposedHelpers.getObjectField(v, "mCornerRadius");
                            if (r > 0) v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                @Override public void getOutline(View vv, android.graphics.Outline o) { o.setPath(squirclePath(new RectF(0, 0, v.getWidth(), v.getHeight()), r)); }}); } }});

            try { Class<?> bu = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", cl);
                XposedHelpers.findAndHookMethod(bu, "setBackgroundBlur", View.class, int.class, float[].class, int[][].class,
                    new XC_MethodHook() { @Override protected void beforeHookedMethod(MethodHookParam p) { if (br != 100) p.args[1] = br; }});
            } catch (Throwable ignored) {}
            try {
                XposedHelpers.findAndHookMethod("com.miui.home.launcher.hotseats.HotSeats", cl,
                    "getMingouStaticDockBlurShadowTarget", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            Object target = p.getResult();
                            if (target instanceof View) nativeShadowTarget = (View) target;
                        }
                    });
                Class<?> ms = XposedHelpers.findClass("com.miui.home.launcher.common.MiShadowUtils", cl);
                XposedHelpers.findAndHookMethod(ms, "applyViewShadow", View.class, int.class,
                    float.class, float.class, float.class, float.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (p.args[0] != nativeShadowTarget) return;
                            p.args[1] = Color.TRANSPARENT;
                            p.args[2] = 0f;
                            p.args[3] = 0f;
                            p.args[4] = 0f;
                        }
                    });
            } catch (Throwable e) {
                XposedBridge.log("[DC] native Dock shadow hook unavailable: " + e);
            }

            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher", cl, "setupViews",
                new XC_MethodHook() { @Override protected void afterHookedMethod(MethodHookParam param) {
                    try { ConfigReader c2 = ConfigReader.load();
                        Object hs = XposedHelpers.getObjectField(param.thisObject, "mHotSeats"); if (hs == null) return;
                        try {
                            Object target = XposedHelpers.callMethod(hs, "getMingouStaticDockBlurShadowTarget");
                            if (target instanceof View) {
                                nativeShadowTarget = (View) target;
                                Class<?> ms = XposedHelpers.findClass(
                                    "com.miui.home.launcher.common.MiShadowUtils", cl);
                                XposedHelpers.callStaticMethod(ms, "applyViewShadow",
                                    nativeShadowTarget, Color.TRANSPARENT, 0f, 0f, 0f, 1f);
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] native Dock shadow clear failed: " + e);
                        }
                        oldBg = (View) XposedHelpers.getObjectField(hs, "mBlurBackground2"); if (oldBg == null) return;
                        ViewGroup parent = (ViewGroup) oldBg.getParent(); if (parent == null) return;
                        int gv = ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity;
                        String lm2 = c2.s("light_mode", "fixed"); boolean strokeEnabled = c2.b("dock_stroke", true), sq2 = c2.b("squircle", false), fd2 = c2.b("fill_diff", false);
                        strokeBaseR = c2.i("stroke_base_r", 255);
                        strokeBaseG = c2.i("stroke_base_g", 255);
                        strokeBaseB = c2.i("stroke_base_b", 255);
                        strokeBaseAlpha = c2.i("stroke_base_alpha", 255);
                        int sqW = c2.i("sq_stroke_w", 4), sqOff = c2.i("sq_stroke_off", 8);
                        float sqCp = c2.i("sq_outer_cp", 58) / 100f;
                        int sw = c2.i("stroke_w", 2), stdSw = c2.i("std_stroke_w", 4);
                        boolean shadow = c2.b("stroke_shadow", false);
                        int shadowRadius = c2.i("shadow_radius", 8), shadowAlpha = c2.i("shadow_alpha", 70);
                        boolean dockShadow = c2.b("dock_shadow", true);
                        int dockShadowRadius = c2.i("dock_shadow_radius", 42);
                        int dockShadowSize = c2.i("dock_shadow_size", 52);
                        int dockShadowAlpha = c2.i("dock_shadow_alpha", 140);
                        int dockShadowY = c2.i("dock_shadow_y", 12);
                        if (overlay != null) return;
                        if (dockShadow) {
                            shadowView = makeDockShadow(sq2, sqOff, sqCp, dockShadowRadius, dockShadowSize,
                                dockShadowAlpha, dockShadowY);
                            shadowView.setId(View.generateViewId());
                            int bgIndex = parent.indexOfChild(oldBg);
                            parent.addView(shadowView, Math.max(0, bgIndex),
                                new FrameLayout.LayoutParams(1, 1));
                            ViewGroup unclipped = parent;
                            for (int level = 0; level < 4 && unclipped != null; level++) {
                                unclipped.setClipChildren(false);
                                unclipped.setClipToPadding(false);
                                android.view.ViewParent next = unclipped.getParent();
                                unclipped = next instanceof ViewGroup ? (ViewGroup) next : null;
                            }
                        }
                        overlay = makeOverlay(oldBg, strokeEnabled, lm2, sq2, sqOff, sqW, sqCp, fd2, sw, stdSw,
                            shadow, shadowRadius, shadowAlpha);
                        overlay.setId(View.generateViewId()); parent.addView(overlay, new FrameLayout.LayoutParams(-1, -1, gv));
                        syncAll(oldBg); if (strokeEnabled && "dynamic".equals(lm2)) startMotionSensor(oldBg);
                    } catch (Throwable e) { XposedBridge.log("[DC] err: " + e); }
                }});
        } catch (Throwable e) { XposedBridge.log("[DC] init err: " + e); }
    }

    private static View makeDockShadow(boolean sq, int sqOff, float sqCp,
                                       int radius, int size, int alpha, int offsetY) {
        final int maxDistance = Math.max(1, size);
        final int blurRadius = Math.min(Math.max(1, radius), maxDistance);
        final int spread = Math.max(0, maxDistance - blurRadius);
        shadowPad = Math.max(4, maxDistance + Math.abs(offsetY) + 4);
        View view = new View(oldBg.getContext()) {
            @Override protected void onDraw(Canvas canvas) {
                if (bgW <= 0 || bgH <= 0) return;
                float left = shadowPad;
                float top = shadowPad;
                RectF bounds;
                float corner;
                if (sq) {
                    bounds = new RectF(left - sqOff - spread, top - sqOff - spread,
                        left + bgW + sqOff + spread, top + bgH + sqOff + spread);
                    corner = Math.max(0, strokeR + sqOff + spread);
                } else {
                    bounds = new RectF(left + 1f - spread, top + 1f - spread,
                        left + bgW - 1f + spread, top + bgH - 1f + spread);
                    corner = Math.max(0, strokeR - 1f + spread);
                }
                Path shape = sq ? squirclePath(bounds, corner, sqCp) : new Path();
                if (!sq) shape.addRoundRect(bounds, corner, corner, Path.Direction.CW);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(Color.argb(1, 0, 0, 0));
                paint.setShadowLayer(blurRadius, 0, offsetY, Color.argb(alpha, 0, 0, 0));
                canvas.drawPath(shape, paint);
            }
            @Override protected void onDetachedFromWindow() {
                if (shadowView == this) shadowView = null;
                super.onDetachedFromWindow();
            }
        };
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        return view;
    }

    private static View makeOverlay(View bg, boolean strokeEnabled, String lm, boolean sq, int sqOff, int sqW, float sqCp,
                                    boolean fd, int sw, int stdSw, boolean shadow,
                                    int shadowRadius, int shadowAlpha) {
        return new View(bg.getContext()) {
            @Override protected void onDraw(Canvas c) {
                if (!strokeEnabled || bgW < 1 || bgH < 1) return;
                float w = bgW, h = bgH, r = Math.max(0, sq ? strokeR + sqOff : strokeR - 1f), md = Math.max(w, h);
                if (sq) {
                    if (shadow) drawSqShadow(c, w, h, r, sqOff, sqCp, shadowRadius, shadowAlpha);
                    drawSq(c, w, h, r, sqOff, sqW, sqCp, lm, md); return;
                }
                if (shadow) drawRoundShadow(c, w, h, r, shadowRadius, shadowAlpha);
                if ("none".equals(lm)) {
                    if (fd) c.drawPath(roundRectRing(w, h, r, sw), noc(150));
                    else {
                        Paint stroke = noc(150); stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeWidth(stdSw);
                        c.drawRoundRect(1, 1, w - 1, h - 1, r, r, stroke);
                    }
                    return;
                }
                drawDyn(c, w, h, r, fd, sw, stdSw, md, lm);
            }
            @Override protected void onWindowVisibilityChanged(int visibility) {
                super.onWindowVisibilityChanged(visibility);
                if (!strokeEnabled || !"dynamic".equals(lm)) return;
                if (visibility == View.VISIBLE) startMotionSensor(bg); else stopMotionSensor();
            }
            @Override protected void onDetachedFromWindow() {
                if (strokeEnabled && "dynamic".equals(lm)) stopMotionSensor();
                if (overlay == this) overlay = null;
                if (oldBg == bg) oldBg = null;
                super.onDetachedFromWindow();
            }
        };
    }

    private static Path roundRectRing(float w, float h, float r, float inset) {
        Path outer = new Path(); outer.addRoundRect(new RectF(0, 0, w, h), r, r, Path.Direction.CW);
        Path inner = new Path();
        float ir = Math.max(0, r - inset);
        inner.addRoundRect(new RectF(inset, inset, w - inset, h - inset), ir, ir, Path.Direction.CW);
        outer.op(inner, Path.Op.DIFFERENCE);
        return outer;
    }

    private static void drawRoundShadow(Canvas c, float w, float h, float r, int radius, int alpha) {
        int steps = Math.max(1, Math.min(radius, 40));
        for (int i = steps; i >= 1; i--) {
            float outerInset = i - 1f, innerInset = i;
            Path band = new Path();
            float outerR = Math.max(0, r - outerInset), innerR = Math.max(0, r - innerInset);
            band.addRoundRect(new RectF(outerInset, outerInset, w - outerInset, h - outerInset),
                outerR, outerR, Path.Direction.CW);
            Path inner = new Path();
            inner.addRoundRect(new RectF(innerInset, innerInset, w - innerInset, h - innerInset),
                innerR, innerR, Path.Direction.CW);
            band.op(inner, Path.Op.DIFFERENCE);
            float strength = 1f - (i - 1f) / steps;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.argb(Math.round(alpha * strength * strength), 0, 0, 0));
            c.drawPath(band, paint);
        }
    }

    private static void drawSqShadow(Canvas c, float w, float h, float r, int sqOff,
                                     float sqCp, int radius, int alpha) {
        int steps = Math.max(1, Math.min(radius, 40));
        for (int i = steps; i >= 1; i--) {
            float oi = i - 1f, ii = i;
            Path band = squirclePath(new RectF(-sqOff + oi, -sqOff + oi,
                w + sqOff - oi, h + sqOff - oi), Math.max(0, r - oi), sqCp);
            Path inner = squirclePath(new RectF(-sqOff + ii, -sqOff + ii,
                w + sqOff - ii, h + sqOff - ii), Math.max(0, r - ii), sqCp);
            band.op(inner, Path.Op.DIFFERENCE);
            float strength = 1f - (i - 1f) / steps;
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.argb(Math.round(alpha * strength * strength), 0, 0, 0));
            c.drawPath(band, paint);
        }
    }

    private static void drawSq(Canvas c, float w, float h, float r, int sqOff, int sqW, float sqCp, String lm, float md) {
        Path outer = squirclePath(new RectF(-sqOff, -sqOff, w + sqOff, h + sqOff), r, sqCp);
        Path inner = squirclePath(new RectF(-sqOff + sqW, -sqOff + sqW, w + sqOff - sqW, h + sqOff - sqW),
            Math.max(0, r - sqW * .5f), .65f);
        outer.op(inner, Path.Op.DIFFERENCE);
        Paint base = noc("none".equals(lm) ? 200 : 120);
        c.drawPath(outer, base);
        if (!"none".equals(lm)) c.drawPath(outer, grad(w, h, lm, md));
    }

    private static void drawDyn(Canvas c, float w, float h, float r, boolean fd, int sw, int stdSw, float md, String lm) {
        Paint gradient = grad(w, h, lm, md);
        if (fd) {
            Path ring = roundRectRing(w, h, r, sw);
            c.drawPath(ring, noc(120));
            c.drawPath(ring, gradient);
        } else {
            Paint base = noc(120); base.setStyle(Paint.Style.STROKE); base.setStrokeWidth(stdSw);
            c.drawRoundRect(1, 1, w - 1, h - 1, r, r, base);
            gradient.setStyle(Paint.Style.STROKE); gradient.setStrokeWidth(stdSw);
            c.drawRoundRect(1, 1, w - 1, h - 1, r, r, gradient);
        }
    }

    private static Paint noc(int a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int alpha = Math.round(a * clamp(strokeBaseAlpha, 0, 255) / 255f);
        p.setColor(Color.argb(alpha, Math.max(0, Math.min(255, strokeBaseR)),
            Math.max(0, Math.min(255, strokeBaseG)), Math.max(0, Math.min(255, strokeBaseB))));
        return p;
    }
    private static void clear(Paint p) { p.setColor(0); p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); }
    private static Paint grad(float w, float h, String lm, float md) {
        boolean dyn = "dynamic".equals(lm); if (dyn) { smoothLx += (gyroY - smoothLx) * .06f; smoothLy += (gyroX - smoothLy) * .06f; }
        float lx = dyn ? smoothLx : 0, ly = dyn ? smoothLy : 0, ang = (float) Math.atan2(ly, lx), cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
        Paint p = new Paint(1); p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(w * .5f - cs * md * .6f, h * .5f - sn * md * .6f, w * .5f + cs * md * .6f, h * .5f + sn * md * .6f,
            new int[]{Color.argb(0, 255, 255, 255), Color.argb(60, 255, 255, 255), Color.argb(220, 255, 255, 255), Color.argb(60, 255, 255, 255)},
            new float[]{0f, .3f, .5f, 1f}, Shader.TileMode.CLAMP)); return p;
    }

    private static void startMotionSensor(View bg) {
        try {
            SensorManager sm = (SensorManager) bg.getContext().getSystemService(android.content.Context.SENSOR_SERVICE);
            if (sm == null) return;
            stopMotionSensor();
            Sensor sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if (sensor == null) sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (sensor == null) { XposedBridge.log("[DC] dynamic light: no motion sensor"); return; }
            motionSensorManager = sm;
            motionSensorListener = new SensorEventListener() {
                @Override public void onSensorChanged(SensorEvent e) {
                    if (e.values.length < 2) return;
                    gyroX = clamp(-e.values[0] / SensorManager.GRAVITY_EARTH * 1.35f, -1.35f, 1.35f);
                    gyroY = clamp(e.values[1] / SensorManager.GRAVITY_EARTH * 1.35f, -1.35f, 1.35f);
                    View v = overlay; if (v != null) v.postInvalidateOnAnimation();
                }
                @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
            };
            boolean ok = sm.registerListener(motionSensorListener, sensor, SensorManager.SENSOR_DELAY_GAME);
            XposedBridge.log("[DC] dynamic light sensor=" + sensor.getName() + " registered=" + ok);
        } catch (Throwable e) { XposedBridge.log("[DC] dynamic light sensor error: " + e); }
    }


    private static synchronized void stopMotionSensor() {
        if (motionSensorManager != null && motionSensorListener != null) {
            try { motionSensorManager.unregisterListener(motionSensorListener); }
            catch (Throwable e) { XposedBridge.log("[DC] unregister motion sensor error: " + e); }
        }
        motionSensorManager = null;
        motionSensorListener = null;
    }

    private static void syncShadowGeometry() {
        View shadow = shadowView, stroke = overlay;
        if (shadow == null || stroke == null || bgW <= 0 || bgH <= 0) return;
        ViewGroup.LayoutParams lp = shadow.getLayoutParams();
        if (lp != null) {
            lp.width = bgW + shadowPad * 2;
            lp.height = bgH + shadowPad * 2;
            shadow.setLayoutParams(lp);
        }
        shadow.setX(stroke.getX() - shadowPad);
        shadow.setY(stroke.getY() - shadowPad);
        shadow.invalidate();
    }

    private static void syncAll(View bg) { if (overlay == null || bg == null) return;
        try { bgW = XposedHelpers.getIntField(bg, "mWidth"); bgH = XposedHelpers.getIntField(bg, "mHeight");
            Object r = XposedHelpers.getObjectField(bg, "mCornerRadius"); if (r instanceof Float) bgR = (Float) r;
            if (bgW <= 0) return; ViewGroup.LayoutParams lp = overlay.getLayoutParams();
            if (lp != null) { lp.width = bgW; lp.height = bgH; overlay.setLayoutParams(lp); }
            overlay.invalidate();
            if (shadowView != null) {
                syncShadowGeometry();
                overlay.post(MainHook::syncShadowGeometry);
            }
        } catch (Throwable ignored) {} }

}
