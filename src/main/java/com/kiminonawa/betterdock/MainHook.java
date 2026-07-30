package com.kiminonawa.betterdock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderEffect;
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
import java.io.File;
import java.io.FileInputStream;

public class MainHook implements IXposedHookLoadPackage {

    private static View overlay, glassOverlay, oldBg;
    private static int bgW, bgH;
    private static float bgR = 30f;
    private static float gyroX, gyroY, smoothLx, smoothLy;
    private static long gyroTime;
    private static String lightMode = "fixed";
    private static int blurRadius = 100;
    private static int heightOffset, widthOffset, cornerOffset = -1;
    private static int sqStrokeW = 4, sqStrokeOff = 8;
    private static float sqOuterCp = 0.58f;
    private static boolean useSquircle;
    private static boolean liquidGlass;
    private static boolean lgBgOn = true;
    private static boolean fillDiff;
    private static int strokeW = 2, stdStrokeW = 4;
    private static int lgAlpha = 80;
    private static int lgTint = 0x38FFFFFF;
    private static boolean freeWidget;

    private static int readInt(String path, int def) {
        try { File f = new File(path); if (f.exists()) {
            FileInputStream fis = new FileInputStream(f); byte[] b = new byte[16];
            int n = fis.read(b); fis.close(); return Integer.parseInt(new String(b,0,n).trim());
        }} catch (Throwable ignored) {} return def;
    }
    private static String readStr(String path, String def) {
        try { File f = new File(path); if (f.exists()) {
            FileInputStream fis = new FileInputStream(f); byte[] b = new byte[16];
            int n = fis.read(b); fis.close(); return new String(b,0,n).trim();
        }} catch (Throwable ignored) {} return def;
    }
    private static int parseHex(String s, int def) {
        try { return (int) Long.parseLong(s.replace("#",""), 16); } catch (Throwable e) { return def; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;

        lightMode = readStr("/sdcard/dock_light.txt", "fixed");
        blurRadius = readInt("/sdcard/dock_blur_radius.txt", 100);
        heightOffset = readInt("/sdcard/dock_height_offset.txt", 0);
        widthOffset = readInt("/sdcard/dock_width_offset.txt", 0);
        cornerOffset = readInt("/sdcard/dock_corner_offset.txt", -1);
        sqStrokeW = readInt("/sdcard/dock_sq_stroke_w.txt", 4);
        sqStrokeOff = readInt("/sdcard/dock_sq_stroke_off.txt", 8);
        sqOuterCp = readInt("/sdcard/dock_sq_outer_cp.txt", 58) / 100f;
        useSquircle = "1".equals(readStr("/sdcard/dock_squircle.txt", "0"));
        liquidGlass = "1".equals(readStr("/sdcard/dock_lg.txt", "0"));
        lgAlpha = readInt("/sdcard/dock_lg_alpha.txt", 80);
        lgTint = parseHex(readStr("/sdcard/dock_lg_tint.txt", "38FFFFFF"), 0x38FFFFFF);
        lgBgOn = !"0".equals(readStr("/sdcard/dock_lg_bg.txt", "1"));
        fillDiff = "1".equals(readStr("/sdcard/dock_fill_diff.txt", "0"));
        strokeW = readInt("/sdcard/dock_stroke_w.txt", 2);
        stdStrokeW = readInt("/sdcard/dock_std_stroke_w.txt", 4);
        freeWidget = "1".equals(readStr("/sdcard/dock_free_widget.txt", "0"));

        try {
            // Size hooks
            String cls = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundWidth", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (widthOffset != 0) p.args[0] = ((Integer)p.args[0]) + widthOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundHeight", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (heightOffset != 0) p.args[0] = ((Integer)p.args[0]) + heightOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundRadius", float.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (cornerOffset != 0) p.args[0] = ((Float)p.args[0]) + cornerOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        syncAll((View)p.thisObject);
                        if (useSquircle) {
                            View v = (View)p.thisObject;
                            float r = (Float)XposedHelpers.getObjectField(v, "mCornerRadius");
                            if (r > 0) {
                                Path sp = squirclePath(new RectF(0,0,v.getWidth(),v.getHeight()), r);
                                v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                    @Override public void getOutline(View vv, android.graphics.Outline o) { o.setPath(sp); }
                                });
                            }
                        }
                    }
                });

            // Blur radius hook
            try {
                Class<?> bu = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(bu, "setBackgroundBlur", View.class, int.class, float[].class, int[][].class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (blurRadius != 100) p.args[1] = blurRadius;
                        }
                    });
            } catch (Throwable ignored) {}

            // Disable shadow when squircle
            try {
                Class<?> ms = XposedHelpers.findClass("com.miui.home.launcher.common.MiShadowUtils", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(ms, "applyViewShadow", View.class, int.class, float.class, float.class, float.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (useSquircle) p.setResult(null);
                        }
                    });
            } catch (Throwable ignored) {}

            // === Widget free placement: remove MIUI grid restrictions ===
            // LayoutDropRuleForSwapPlaces.isLegalXY enforces:
            //   spanX==4 → cellX must be 0 (locks 4-wide widgets to left edge)
            //   spanY==4 → cellY must be 0 or 2
            //   spanX<=1 → cellX must be even (odd columns blocked for 1x1)
            // Hook to always return true — like AOSP Launcher3 free placement.
            if (freeWidget) {
                try {
                    XposedHelpers.findAndHookMethod(
                        "com.miui.home.launcher.compat.LayoutDropRuleForSwapPlaces",
                        lpparam.classLoader,
                        "isLegalXY",
                        int.class, int.class, int.class, int.class,
                        new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                p.setResult(true);
                            }
                        });
                    XposedBridge.log("[DC] Widget free placement: OK");
                } catch (Throwable e) {
                    XposedBridge.log("[DC] Widget free placement: " + e.getClass().getSimpleName());
                }
            }

            // setupViews
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader,
                "setupViews", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object hotSeats = XposedHelpers.getObjectField(param.thisObject, "mHotSeats");
                            if (hotSeats == null) return;
                            oldBg = (View)XposedHelpers.getObjectField(hotSeats, "mBlurBackground2");
                            if (oldBg == null) return;
                            ViewGroup parent = (ViewGroup)oldBg.getParent();
                            if (parent == null) return;

                            // --- Trigger HyperLight via stubbed MiuixMaterialBlurUtilities ---
                            if ("1".equals(readStr("/sdcard/dock_use_hl.txt", "0"))) {
                                try {
                                    Class<?> mmbu = lpparam.classLoader.loadClass(
                                        "com.miui.home.common.utils.MiuixMaterialBlurUtilities");
                                    mmbu.getMethod("applyMaterialBlur", View.class, Runnable.class, Runnable.class)
                                        .invoke(null, oldBg, null, null);
                                    XposedBridge.log("[DC] HyperLight proxy: OK");
                                    return;
                                } catch (Throwable e) {
                                    XposedBridge.log("[DC] HyperLight proxy: " + e.getClass().getSimpleName());
                                }
                            }

                            int gravity = ((FrameLayout.LayoutParams)oldBg.getLayoutParams()).gravity;

                            // Liquid Glass overlay (behind bloom, above blur bg)
                            if (liquidGlass && glassOverlay == null && lgBgOn) {
                                glassOverlay = new View(oldBg.getContext()) {
                                    @Override protected void onDraw(Canvas canvas) {
                                        // GPU blur handles rendering — just draw tint
                                        if (bgW < 1 || bgH < 1) return;
                                        float w = bgW, h = bgH, r = Math.max(0, bgR);
                                        int a = Color.alpha(lgTint) * lgAlpha / 255;
                                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        p.setColor(Color.argb(a, Color.red(lgTint), Color.green(lgTint), Color.blue(lgTint)));
                                        if (useSquircle) canvas.drawPath(squirclePath(new RectF(0,0,w,h), r), p);
                                        else canvas.drawRoundRect(0,0,w,h, r,r, p);
                                    }
                                };
                                // GPU anisotropic blur (HyperLight-style chromium look)
                                int blurH = blurRadius / 6;
                                int blurV = blurRadius / 4;
                                glassOverlay.setRenderEffect(
                                    RenderEffect.createBlurEffect(blurH, blurV, Shader.TileMode.CLAMP));
                                glassOverlay.setId(View.generateViewId());
                                parent.addView(glassOverlay, parent.indexOfChild(oldBg)+1,
                                    new FrameLayout.LayoutParams(-1, -1, gravity));
                                XposedBridge.log("[DC] GPU blur: " + blurH + "x" + blurV);
                            }

                            // Bloom overlay
                            if (overlay != null) return;
                            overlay = new View(oldBg.getContext()) {
                                @Override protected void onDraw(Canvas canvas) {
                                    if (bgW < 1 || bgH < 1) return;
                                    float w = bgW, h = bgH;
                                    float r = Math.max(0, useSquircle ? bgR + sqStrokeOff : bgR - 1f);
                                    float maxDim = Math.max(w,h);

                                    if (useSquircle) {
                                        Path outer = squirclePath(new RectF(-sqStrokeOff,-sqStrokeOff,w+sqStrokeOff,h+sqStrokeOff), r, sqOuterCp);
                                        Path inner = squirclePath(new RectF(-sqStrokeOff+sqStrokeW,-sqStrokeOff+sqStrokeW,w+sqStrokeOff-sqStrokeW,h+sqStrokeOff-sqStrokeW), r-sqStrokeW*0.5f, 0.65f);
                                        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setStyle(Paint.Style.FILL);

                                        if ("none".equals(lightMode)) {
                                            fill.setColor(Color.argb(200,255,255,255)); canvas.drawPath(outer,fill);
                                            fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill); return;
                                        }
                                        fill.setColor(Color.argb(120,255,255,255)); canvas.drawPath(outer,fill);
                                        fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill);

                                        boolean dyn = "dynamic".equals(lightMode);
                                        if (dyn) { smoothLx+=(gyroY-smoothLx)*0.06f; smoothLy+=(gyroX-smoothLy)*0.06f; }
                                        float lx = dyn?smoothLx:0, ly = dyn?smoothLy:0;
                                        float ang = (float)Math.atan2(ly,lx), cs=(float)Math.cos(ang), sn=(float)Math.sin(ang);
                                        float d2=maxDim*0.6f, cx2=w*0.5f, cy2=h*0.5f;
                                        Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG); s1p.setStyle(Paint.Style.FILL);
                                        s1p.setShader(new LinearGradient(cx2-cs*d2, cy2-sn*d2, cx2+cs*d2, cy2+sn*d2,
                                            new int[]{Color.argb(0,255,255,255),Color.argb(60,255,255,255),Color.argb(220,255,255,255),Color.argb(60,255,255,255)},
                                            new float[]{0f,0.3f,0.5f,1f}, Shader.TileMode.CLAMP));
                                        canvas.drawPath(outer,s1p);
                                        fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill); return;
                                    }

                                    if ("none".equals(lightMode)) {
                                        if (!useSquircle && fillDiff) {
                                            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setStyle(Paint.Style.FILL);
                                            s.setColor(Color.argb(150,255,255,255));
                                            canvas.drawRoundRect(0,0,w,h, r,r, s);
                                            s.setColor(Color.argb(0,255,255,255)); s.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                                            canvas.drawRoundRect(strokeW,strokeW,w-strokeW,h-strokeW, r-strokeW,r-strokeW, s); return;
                                        } else {
                                            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setStyle(Paint.Style.STROKE);
                                            s.setStrokeWidth((float)stdStrokeW); s.setColor(Color.argb(150,255,255,255));
                                            canvas.drawRoundRect(1,1,w-1,h-1,r,r,s); return;
                                        }
                                    }

                                    boolean dyn = "dynamic".equals(lightMode);
                                    if (dyn) { smoothLx+=(gyroY-smoothLx)*0.06f; smoothLy+=(gyroX-smoothLy)*0.06f; }
                                    float lx = dyn?smoothLx:0, ly = dyn?smoothLy:0;
                                    float ang = (float)Math.atan2(ly,lx), cs=(float)Math.cos(ang), sn=(float)Math.sin(ang);
                                    float d2=maxDim*0.6f, cx2=w*0.5f, cy2=h*0.5f;
                                    Paint dSpec = new Paint(Paint.ANTI_ALIAS_FLAG); dSpec.setStyle(Paint.Style.FILL);
                                    dSpec.setShader(new LinearGradient(cx2-cs*d2, cy2-sn*d2, cx2+cs*d2, cy2+sn*d2,
                                        new int[]{Color.argb(0,255,255,255),Color.argb(60,255,255,255),Color.argb(220,255,255,255),Color.argb(60,255,255,255)},
                                        new float[]{0f,0.3f,0.5f,1f}, Shader.TileMode.CLAMP));

                                    if (!useSquircle && fillDiff) {
                                        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG); base.setStyle(Paint.Style.FILL);
                                        base.setColor(Color.argb(120,255,255,255));
                                        canvas.drawRoundRect(0,0,w,h, r,r, base);
                                        Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG); clear.setStyle(Paint.Style.FILL);
                                        clear.setColor(Color.argb(0,255,255,255)); clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                                        canvas.drawRoundRect(strokeW,strokeW,w-strokeW,h-strokeW, r-strokeW,r-strokeW, clear);
                                        if ("dynamic".equals(lightMode)||"fixed".equals(lightMode)) {
                                            canvas.drawRoundRect(0,0,w,h,r,r,dSpec);
                                            canvas.drawRoundRect(strokeW,strokeW,w-strokeW,h-strokeW,r-strokeW,r-strokeW,dSpec);
                                        }
                                    } else {
                                        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG); base.setStyle(Paint.Style.STROKE);
                                        base.setStrokeWidth((float)stdStrokeW); base.setColor(Color.argb(120,255,255,255));
                                        canvas.drawRoundRect(1,1,w-1,h-1,r,r,base);
                                        if ("dynamic".equals(lightMode)||"fixed".equals(lightMode))
                                            canvas.drawRoundRect(1,1,w-1,h-1,r,r,dSpec);
                                    }
                                }
                            };
                            overlay.setId(View.generateViewId());
                            parent.addView(overlay, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, gravity));
                            syncAll(oldBg);

                            if ("dynamic".equals(lightMode)) {
                                try {
                                    SensorManager sm = (SensorManager)oldBg.getContext().getSystemService(android.content.Context.SENSOR_SERVICE);
                                    Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                                    if (gyro != null) {
                                        sm.registerListener(new SensorEventListener() {
                                            @Override public void onSensorChanged(SensorEvent e) {
                                                if (e.values.length < 3) return;
                                                long now = e.timestamp;
                                                if (gyroTime > 0) { float dt = (now-gyroTime)/1e9f;
                                                    if (dt>0 && dt<0.1f) {
                                                        // Landscape tablet: swap X/Y for correct axis mapping
                                                        gyroX=clamp(gyroX+e.values[1]*dt*2.4f,-1.35f,1.35f);
                                                        gyroY=clamp(gyroY+e.values[0]*dt*2.4f,-1.35f,1.35f);
                                                    }}
                                                gyroTime=now;
                                                if (overlay!=null) overlay.postInvalidate();
                                                if (glassOverlay!=null) glassOverlay.postInvalidate();
                                            }
                                            @Override public void onAccuracyChanged(Sensor s,int a){}
                                        }, gyro, SensorManager.SENSOR_DELAY_GAME);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] err: "+e.getClass().getSimpleName()+" "+e.getMessage());
                        }
                    }
                });
        } catch (Throwable e) {
            XposedBridge.log("[DC] init err: "+e.getClass().getSimpleName()+" "+e.getMessage());
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Path squirclePath(RectF rect, float radius) { return squirclePath(rect, radius, 0.65f); }
    private static Path squirclePath(RectF rect, float radius, float cp) {
        Path p = new Path();
        if (radius <= 1) { p.addRect(rect, Path.Direction.CW); return p; }
        float r = radius, c = r*cp;
        float l=rect.left, t=rect.top, ri=rect.right, b=rect.bottom;
        p.moveTo(l, t+r); p.cubicTo(l, t+r-c, l+r-c, t, l+r, t); p.lineTo(ri-r, t);
        p.cubicTo(ri-r+c, t, ri, t+r-c, ri, t+r); p.lineTo(ri, b-r);
        p.cubicTo(ri, b-r+c, ri-r+c, b, ri-r, b); p.lineTo(l+r, b);
        p.cubicTo(l+r-c, b, l, b-r+c, l, b-r); p.close();
        return p;
    }

    private static void syncAll(View bg) {
        if (overlay == null || bg == null) return;
        try {
            bgW = XposedHelpers.getIntField(bg, "mWidth"); bgH = XposedHelpers.getIntField(bg, "mHeight");
            Object r = XposedHelpers.getObjectField(bg, "mCornerRadius");
            if (r instanceof Float) bgR = (Float)r;
            if (bgW <= 0) return;
            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
            if (lp != null) { lp.width = bgW; lp.height = bgH; overlay.setLayoutParams(lp); }
            overlay.invalidate();
            if (glassOverlay != null) {
                ViewGroup.LayoutParams glp = glassOverlay.getLayoutParams();
                if (glp != null) { glp.width = bgW; glp.height = bgH; glassOverlay.setLayoutParams(glp); }
                glassOverlay.invalidate();
            }
        } catch (Throwable ignored) {}
    }
}
