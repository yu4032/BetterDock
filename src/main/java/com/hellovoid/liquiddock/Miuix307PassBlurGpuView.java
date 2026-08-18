package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Diagnostic GPU-only consumer for HyperOS PassBlur output.
 *
 * The left half samples the live compositor texture without displacement. The right half adds a
 * deliberately obvious sinusoidal horizontal offset so device testing can prove that LiquidDock's
 * own shader is spatially transforming a live SurfaceFlinger backdrop.
 */
final class Miuix307PassBlurGpuView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "[DC][PBGL]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;

    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n"
            + "attribute vec2 aUv;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vUv = aUv;\n"
            + "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
            + "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES uTexture;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "uniform vec4 uCrop;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec2 rootUv = vec2(uCrop.x + vUv.x * uCrop.z,\n"
            + "                     uCrop.y + vUv.y * uCrop.w);\n"
            + "  if (vUv.x > 0.5) {\n"
            + "    rootUv.x += sin(vUv.y * 42.0) * 0.028;\n"
            + "  }\n"
            + "  vec4 transformed = uTexMatrix * vec4(rootUv, 0.0, 1.0);\n"
            + "  gl_FragColor = texture2D(uTexture, transformed.xy);\n"
            + "}\n";

    private final WeakReference<View> materialHostRef;
    private final FloatBuffer quadBuffer;
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];

    private volatile boolean gpuBackdropActive;
    private volatile boolean activationExhausted;
    private volatile boolean shuttingDown;
    private volatile boolean hasConsumedFrame;
    private volatile float cropX;
    private volatile float cropY;
    private volatile float cropW = 1f;
    private volatile float cropH = 1f;
    private volatile float glassRadiusPx;

    private int program;
    private int oesTexture;
    private SurfaceTexture surfaceTexture;
    private Surface producerSurface;
    private Miuix307PassBlurBridge.Binding binding;
    private boolean firstFrameLogged;
    private boolean firstDrawLogged;
    private ViewTreeObserver preDrawObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    Miuix307PassBlurGpuView(Context context, View materialHost) {
        super(context);
        materialHostRef = new WeakReference<>(materialHost);
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        setZOrderOnTop(false);
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    boolean isGpuBackdropActive() {
        return gpuBackdropActive;
    }

    boolean isActivationExhausted() {
        return activationExhausted;
    }

    void setGlassRadius(float radiusPx) {
        glassRadiusPx = Math.max(0f, radiusPx);
        post(this::applyOutputCornerRadius);
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        gpuBackdropActive = false;
        removeCropObserver();

        Miuix307PassBlurBridge.Binding currentBinding = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(currentBinding);

        if (producerSurface != null) {
            producerSurface.release();
            producerSurface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }

        try {
            queueEvent(() -> {
                if (oesTexture != 0) {
                    int[] texture = new int[]{oesTexture};
                    GLES20.glDeleteTextures(1, texture, 0);
                    oesTexture = 0;
                }
                if (program != 0) {
                    GLES20.glDeleteProgram(program);
                    program = 0;
                }
            });
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installCropObserver();
        updateCrop();
    }

    @Override
    protected void onDetachedFromWindow() {
        shutdown();
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if (shuttingDown) return;
        try {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (program == 0) throw new IllegalStateException("shader program=0");

            int[] texture = new int[1];
            GLES20.glGenTextures(1, texture, 0);
            oesTexture = texture[0];
            if (oesTexture == 0) throw new IllegalStateException("OES texture=0");
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);

            surfaceTexture = new SurfaceTexture(oesTexture);
            producerSurface = new Surface(surfaceTexture);
            surfaceTexture.setOnFrameAvailableListener(textureSource -> {
                if (shuttingDown) return;
                frameAvailable.set(true);
                requestRender();
            });
            post(() -> bindProducerWhenReady(0));
            GLES20.glClearColor(0f, 0f, 0f, 0f);
        } catch (Throwable error) {
            fail("GL input setup", error);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
        post(this::updateCrop);
        post(this::applyOutputCornerRadius);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (shuttingDown || program == 0 || oesTexture == 0) return;
        SurfaceTexture textureSource = surfaceTexture;
        if (textureSource == null) return;

        try {
            if (frameAvailable.getAndSet(false)) {
                textureSource.updateTexImage();
                textureSource.getTransformMatrix(textureMatrix);
                hasConsumedFrame = true;
                if (!firstFrameLogged) {
                    firstFrameLogged = true;
                    MainHook.log(TAG + " first OES frame");
                }
            }
            if (!hasConsumedFrame) return;

            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aUv");
            int texture = GLES20.glGetUniformLocation(program, "uTexture");
            int matrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
            int crop = GLES20.glGetUniformLocation(program, "uCrop");
            if (position < 0 || uv < 0 || texture < 0 || matrix < 0 || crop < 0) {
                throw new IllegalStateException("shader location unavailable");
            }

            quadBuffer.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
            quadBuffer.position(2);
            GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
            GLES20.glUniform1i(texture, 0);
            GLES20.glUniformMatrix4fv(matrix, 1, false, textureMatrix, 0);
            GLES20.glUniform4f(crop, cropX, cropY, cropW, cropH);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(uv);

            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("GLES error=0x" + Integer.toHexString(error));
            }
            gpuBackdropActive = binding != null;
            if (gpuBackdropActive && !firstDrawLogged) {
                firstDrawLogged = true;
                MainHook.log(TAG + " first GLES backdrop draw crop=["
                        + cropX + "," + cropY + "," + cropW + "," + cropH + "]");
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || !isAttachedToWindow() || surfaceTexture == null || producerSurface == null) {
            retryBind(attempt, "views/input not ready");
            return;
        }
        View root = materialHost.getRootView();
        int rootWidth = root != null ? root.getWidth() : 0;
        int rootHeight = root != null ? root.getHeight() : 0;
        SurfaceControl outputSurface = getSurfaceControl();
        if (rootWidth <= 0 || rootHeight <= 0
                || outputSurface == null || !outputSurface.isValid()) {
            retryBind(attempt, "geometry/output surface not ready");
            return;
        }

        try {
            surfaceTexture.setDefaultBufferSize(rootWidth, rootHeight);
            updateCrop();
            Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(
                    materialHost, this, producerSurface, 1.0f);
            if (next == null) {
                retryBind(attempt, "framework bind returned null");
                return;
            }
            binding = next;
            activationExhausted = false;
            applyOutputCornerRadius();
        } catch (Throwable error) {
            if (attempt < MAX_BIND_RETRY_FRAMES) {
                retryBind(attempt, error.getClass().getSimpleName());
            } else {
                fail("producer bind", error);
            }
        }
    }

    private void retryBind(int attempt, String reason) {
        if (shuttingDown) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            activationExhausted = true;
            MainHook.log(TAG + " PassBlur GPU activation exhausted reason=" + reason);
            return;
        }
        postOnAnimation(() -> bindProducerWhenReady(attempt + 1));
    }

    private void installCropObserver() {
        removeCropObserver();
        View root = getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            updateCrop();
            return true;
        };
        observer.addOnPreDrawListener(listener);
        preDrawObserver = observer;
        preDrawListener = listener;
    }

    private void removeCropObserver() {
        ViewTreeObserver observer = preDrawObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        preDrawObserver = null;
        preDrawListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private void updateCrop() {
        View materialHost = materialHostRef.get();
        View root = materialHost != null ? materialHost.getRootView() : getRootView();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0
                || getWidth() <= 0 || getHeight() <= 0) return;
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        getLocationInWindow(viewLocation);

        float rootWidth = root.getWidth();
        float rootHeight = root.getHeight();
        float left = (viewLocation[0] - rootLocation[0]) / rootWidth;
        float top = (viewLocation[1] - rootLocation[1]) / rootHeight;
        float width = getWidth() / rootWidth;
        float height = getHeight() / rootHeight;

        cropX = clamp01(left);
        cropW = Math.max(0.0001f, Math.min(1f - cropX, width));
        // Shader UV uses bottom-left origin before the SurfaceTexture transform matrix.
        cropY = clamp01(1f - (top + height));
        cropH = Math.max(0.0001f, Math.min(1f - cropY, height));
    }

    private void applyOutputCornerRadius() {
        if (shuttingDown || glassRadiusPx <= 0f) return;
        try {
            SurfaceControl output = getSurfaceControl();
            if (output == null || !output.isValid()) return;
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            Method setCornerRadius = SurfaceControl.Transaction.class.getMethod(
                    "setCornerRadius", SurfaceControl.class, Float.TYPE);
            setCornerRadius.invoke(transaction, output, Float.valueOf(glassRadiusPx));
            transaction.apply();
        } catch (Throwable error) {
            MainHook.log(TAG + " output corner radius unavailable: " + error);
        }
    }

    private void fail(String stage, Throwable error) {
        activationExhausted = true;
        gpuBackdropActive = false;
        MainHook.log(TAG + " PassBlur GPU " + stage + " failed: " + error);
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) GLES20.glDeleteShader(vertex);
            if (fragment != 0) GLES20.glDeleteShader(fragment);
            return 0;
        }
        int result = GLES20.glCreateProgram();
        GLES20.glAttachShader(result, vertex);
        GLES20.glAttachShader(result, fragment);
        GLES20.glLinkProgram(result);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            MainHook.log(TAG + " program link failed: " + GLES20.glGetProgramInfoLog(result));
            GLES20.glDeleteProgram(result);
            return 0;
        }
        return result;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            MainHook.log(TAG + " shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
