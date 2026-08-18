package com.hellovoid.liquiddock;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * HyperOS 3.0.307 zero-copy backdrop refraction feasibility spike.
 *
 * The framework supplies the live backdrop to the RuntimeShader as the named "content" shader via
 * RenderEffect.createRuntimeShaderEffect(). No screen capture or child Surface is involved.
 */
final class Miuix307BackdropRefractionEffect {
    private static final String TAG = "[DC][ZC][BACKDROP]";
    private static final float EXPERIMENT_STRENGTH_PX = 10.0f;

    private static final String SHADER_SOURCE =
            "uniform shader content;"
          + "uniform float2 size;"
          + "uniform float strengthPx;"
          + "uniform float diagnosticSolid;"
          + "half4 main(float2 p){"
          + "if(diagnosticSolid>0.5){return half4(1.0,0.0,1.0,1.0);}"
          + "float2 halfSize=max(size*0.5,float2(1.0));"
          + "float2 q=(p-halfSize)/halfSize;"
          + "float r=length(q);"
          + "float edge=smoothstep(0.18,1.0,clamp(r,0.0,1.0));"
          + "float2 dir=r>0.001?q/r:float2(0.0);"
          + "float2 displaced=p-dir*(strengthPx*edge*edge);"
          + "return content.eval(displaced);"
          + "}";

    private static WeakReference<View> activeTargetRef = new WeakReference<>(null);
    private static RuntimeShader activeShader;
    private static Method setBackdropRenderEffect;
    private static boolean loggedActive;

    private Miuix307BackdropRefractionEffect() {}

    static synchronized boolean apply(View target) {
        if (target == null) return false;
        if (target.getWidth() <= 0 || target.getHeight() <= 0) {
            target.postOnAnimation(() -> apply(target));
            return false;
        }
        try {
            Method setter = resolveSetter();
            RuntimeShader shader = activeTargetRef.get() == target && activeShader != null
                    ? activeShader : new RuntimeShader(SHADER_SOURCE);
            shader.setFloatUniform("size", (float) target.getWidth(), (float) target.getHeight());
            shader.setFloatUniform("strengthPx", EXPERIMENT_STRENGTH_PX);
            shader.setFloatUniform("diagnosticSolid", 1.0f);
            RenderEffect effect = RenderEffect.createRuntimeShaderEffect(shader, "content");
            setter.invoke(target, effect);

            activeTargetRef = new WeakReference<>(target);
            activeShader = shader;
            if (!loggedActive) {
                loggedActive = true;
                MainHook.log(TAG + " runtime diagnostic active solid=magenta"
                        + " size=" + target.getWidth() + "x" + target.getHeight());
            }
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " runtime refraction unavailable: " + error);
            return false;
        }
    }

    static synchronized void sync(View target) {
        if (target == null) return;
        if (activeTargetRef.get() != target || activeShader == null) {
            apply(target);
            return;
        }
        if (target.getWidth() <= 0 || target.getHeight() <= 0) return;
        try {
            activeShader.setFloatUniform(
                    "size", (float) target.getWidth(), (float) target.getHeight());
            target.invalidate();
        } catch (Throwable error) {
            MainHook.log(TAG + " runtime refraction sync unavailable: " + error);
        }
    }

    static synchronized void clear(View target) {
        View active = activeTargetRef.get();
        if (target != null && active != null && target != active) return;
        if (active != null) {
            try {
                resolveSetter().invoke(active, new Object[]{null});
            } catch (Throwable error) {
                MainHook.log(TAG + " runtime refraction clear unavailable: " + error);
            }
        }
        activeTargetRef = new WeakReference<>(null);
        activeShader = null;
        loggedActive = false;
    }

    private static Method resolveSetter() throws Exception {
        if (setBackdropRenderEffect == null) {
            setBackdropRenderEffect =
                    View.class.getMethod("setBackdropRenderEffect", RenderEffect.class);
        }
        return setBackdropRenderEffect;
    }
}
