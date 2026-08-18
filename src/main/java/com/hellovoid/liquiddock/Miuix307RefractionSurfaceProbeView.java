package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * Transparent SurfaceView used only to prove that the Dock can own a compositor child layer
 * independent from its shared ViewRoot SurfaceControl.
 *
 * This diagnostic view never applies the HyperOS charge/refraction shader. It creates one
 * transparent buffer so the SurfaceView has a real compositor surface, then hands only the child
 * SurfaceControl identity to Miuix307SurfaceRefractionProbe.
 */
final class Miuix307RefractionSurfaceProbeView extends SurfaceView
        implements SurfaceHolder.Callback {
    private final WeakReference<View> materialHostRef;

    Miuix307RefractionSurfaceProbeView(Context context, View materialHost) {
        super(context);
        materialHostRef = new WeakReference<>(materialHost);

        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
        setZOrderOnTop(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        clearTransparent(holder);
        // getSurfaceControl() is the independent child candidate; never target the shared root.
        getSurfaceControl();
        Miuix307SurfaceRefractionProbe.probeChildSurface(this, materialHostRef.get());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Miuix307SurfaceRefractionProbe.probeChildSurface(this, materialHostRef.get());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Miuix307SurfaceRefractionProbe.noteChildSurfaceDestroyed(this);
    }

    private static void clearTransparent(SurfaceHolder holder) {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
        } catch (Throwable error) {
            MainHook.log("[DC][ZC][REFR] child transparent clear unavailable: " + error);
        } finally {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (Throwable error) {
                    MainHook.log("[DC][ZC][REFR] child transparent post unavailable: " + error);
                }
            }
        }
    }
}
