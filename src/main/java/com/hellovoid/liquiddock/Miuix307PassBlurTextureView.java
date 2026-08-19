package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feedback-safe Stage-A HyperOS 3.0.307 PassBlur calibration backend.
 *
 * PassBlur renders into a caller-owned input SurfaceTexture attached to an external OES texture.
 * A dedicated EGL thread samples that texture and renders strict full-domain passthrough into this
 * TextureView. Because TextureView is composited into the already-excluded Floating Dock root, the
 * output does not publish an independent SurfaceView layer back into the PassBlur scene.
 */
final class Miuix307PassBlurTextureView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private static final String TAG = "[DC][PBTX]";
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
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec4 transformed = uTexMatrix * vec4(vUv, 0.0, 1.0);\n"
            + "  gl_FragColor = texture2D(uTexture, transformed.xy);\n"
            + "}\n";

    private static final class ProducerGeometry {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int configRotation;
        final SurfaceControl rootSurface;

        ProducerGeometry(
                int surfaceWidth, int surfaceHeight,
                int bufferWidth, int bufferHeight,
                int configRotation, SurfaceControl rootSurface) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.configRotation = configRotation;
            this.rootSurface = rootSurface;
        }
    }

    private final WeakReference<View> materialHostRef;
    private final FloatBuffer quadBuffer;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];

    private volatile boolean shuttingDown;
    private volatile boolean gpuBackdropActive;
    private volatile boolean activationExhausted;
    private volatile boolean hasConsumedFrame;
    private volatile int configRotation;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile SurfaceTexture outputSurfaceTexture;
    private volatile Surface outputWindowSurface;
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile int outputWidth;
    private volatile int outputHeight;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglWindowSurface = EGL14.EGL_NO_SURFACE;
    private int program;
    private int oesTexture;
    private int boundSurfaceWidth;
    private int boundSurfaceHeight;
    private int boundConfigRotation = -1;
    private boolean firstFrameLogged;
    private boolean firstDrawLogged;
    private boolean firstMatrixLogged;
    private ViewTreeObserver preDrawObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    Miuix307PassBlurTextureView(Context context, View materialHost) {
        super(context);
        materialHostRef = new WeakReference<>(materialHost);
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        setOpaque(false);
        setSurfaceTextureListener(this);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        renderThread = new HandlerThread("LiquidDock-PassBlur-EGL");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
    }

    boolean isGpuBackdropActive() {
        return gpuBackdropActive;
    }

    boolean isActivationExhausted() {
        return activationExhausted;
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        gpuBackdropActive = false;
        removeGeometryObserver();

        Miuix307PassBlurBridge.Binding currentBinding = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(currentBinding);
        resetBoundGeometry();

        renderHandler.post(this::releaseRenderResources);
        renderThread.quitSafely();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installGeometryObserver();
        if (isAvailable() && getSurfaceTexture() != null && outputWindowSurface == null) {
            onSurfaceTextureAvailable(getSurfaceTexture(), getWidth(), getHeight());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        shutdown();
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (shuttingDown || surface == null) return;
        outputSurfaceTexture = surface;
        outputWidth = Math.max(1, width);
        outputHeight = Math.max(1, height);
        Surface window = new Surface(surface);
        Surface stale = outputWindowSurface;
        outputWindowSurface = window;
        renderHandler.post(() -> attachOutputWindow(stale, window, outputWidth, outputHeight));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (shuttingDown || surface != outputSurfaceTexture) return;
        outputWidth = Math.max(1, width);
        outputHeight = Math.max(1, height);
        renderHandler.post(() -> {
            if (eglWindowSurface != EGL14.EGL_NO_SURFACE) {
                makeCurrent();
                GLES20.glViewport(0, 0, outputWidth, outputHeight);
                drawLatestFrame(false);
            }
        });
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (surface == outputSurfaceTexture) outputSurfaceTexture = null;
        Surface stale = outputWindowSurface;
        outputWindowSurface = null;
        outputWidth = 0;
        outputHeight = 0;
        if (stale != null) renderHandler.post(() -> destroyOutputWindow(stale));
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // TextureView output update callback; input frames are driven by the separate PassBlur ST.
    }

    private void attachOutputWindow(Surface stale, Surface window, int width, int height) {
        if (shuttingDown) {
            if (window != null) window.release();
            return;
        }
        try {
            if (stale != null && stale != window) destroyOutputWindow(stale);
            ensureEglContext();
            destroyEglWindowSurfaceOnly();
            int[] attrs = new int[]{EGL14.EGL_NONE};
            eglWindowSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, window, attrs, 0);
            checkEglHandle("eglCreateWindowSurface", eglWindowSurface != EGL14.EGL_NO_SURFACE);
            makeCurrent();
            GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            ensureGlInput();
            drawLatestFrame(false);
        } catch (Throwable error) {
            fail("output attach", error);
        }
    }

    private void destroyOutputWindow(Surface stale) {
        try {
            destroyEglWindowSurfaceOnly();
        } catch (Throwable error) {
            MainHook.log(TAG + " output EGL surface destroy failed: " + error);
        }
        try {
            if (stale != null) stale.release();
        } catch (Throwable ignored) {}
    }

    private void ensureEglContext() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY
                && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null) {
            return;
        }

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEglHandle("eglGetDisplay", display != EGL14.EGL_NO_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw new IllegalStateException("eglInitialize error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }

        int[] configAttrs = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0)
                || count[0] <= 0 || configs[0] == null) {
            throw new IllegalStateException("eglChooseConfig error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }

        int[] contextAttrs = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0);
        checkEglHandle("eglCreateContext", context != EGL14.EGL_NO_CONTEXT);

        eglDisplay = display;
        eglConfig = configs[0];
        eglContext = context;
    }

    private void ensureGlInput() {
        if (program != 0 && oesTexture != 0
                && inputSurfaceTexture != null && inputProducerSurface != null) {
            return;
        }

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (program == 0) throw new IllegalStateException("shader program=0");

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        oesTexture = textures[0];
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

        SurfaceTexture input = new SurfaceTexture(oesTexture);
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);
            drawLatestFrame(true);
        }, renderHandler);
        post(() -> bindProducerWhenReady(0));
    }

    private void drawLatestFrame(boolean fromFrameCallback) {
        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE
                || program == 0 || oesTexture == 0) {
            return;
        }
        SurfaceTexture input = inputSurfaceTexture;
        if (input == null) return;

        try {
            makeCurrent();
            if (frameAvailable.getAndSet(false)) {
                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                hasConsumedFrame = true;
                if (!firstFrameLogged) {
                    firstFrameLogged = true;
                    MainHook.log(TAG + " first OES frame configRot=" + configRotation);
                }
                if (!firstMatrixLogged) {
                    firstMatrixLogged = true;
                    MainHook.log(TAG + " texture matrix=" + formatTextureMatrix(textureMatrix)
                            + " stage=full-domain configRot=" + configRotation);
                }
            }
            if (!hasConsumedFrame) return;

            GLES20.glViewport(0, 0, Math.max(1, outputWidth), Math.max(1, outputHeight));
            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aUv");
            int texture = GLES20.glGetUniformLocation(program, "uTexture");
            int matrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
            if (position < 0 || uv < 0 || texture < 0 || matrix < 0) {
                throw new IllegalStateException("shader location unavailable");
            }

            quadBuffer.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(
                    position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
            quadBuffer.position(2);
            GLES20.glEnableVertexAttribArray(uv);
            GLES20.glVertexAttribPointer(
                    uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
            GLES20.glUniform1i(texture, 0);
            GLES20.glUniformMatrix4fv(matrix, 1, false, textureMatrix, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(position);
            GLES20.glDisableVertexAttribArray(uv);

            int glError = GLES20.glGetError();
            if (glError != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("GLES error=0x" + Integer.toHexString(glError));
            }
            if (!EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)) {
                throw new IllegalStateException("eglSwapBuffers error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }

            Miuix307PassBlurBridge.Binding currentBinding = binding;
            gpuBackdropActive = currentBinding != null && currentBinding.bound;
            if (gpuBackdropActive && !firstDrawLogged) {
                firstDrawLogged = true;
                MainHook.log(TAG + " first EGL passthrough draw"
                        + " textureDomain=full"
                        + " output=" + outputWidth + "x" + outputHeight
                        + " producerSurface=" + boundSurfaceWidth + "x" + boundSurfaceHeight
                        + " configRot=" + configRotation
                        + " frameCallback=" + fromFrameCallback);
                post(this::logStageADiagnostics);
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null) return;
        View materialHost = materialHostRef.get();
        Surface producer = inputProducerSurface;
        SurfaceTexture input = inputSurfaceTexture;
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || !isAttachedToWindow() || !isAvailable()
                || producer == null || input == null) {
            retryBind(attempt, "views/input not ready");
            return;
        }

        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        if (geometry == null || geometry.bufferWidth <= 0 || geometry.bufferHeight <= 0
                || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryBind(attempt, "producer geometry not ready");
            return;
        }

        renderHandler.post(() -> {
            SurfaceTexture currentInput = inputSurfaceTexture;
            if (shuttingDown || currentInput == null || currentInput != input) return;
            try {
                currentInput.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                post(() -> finishBindProducer(geometry, producer, attempt));
            } catch (Throwable error) {
                post(() -> retryBind(attempt, error.getClass().getSimpleName()));
            }
        });
    }

    private void finishBindProducer(ProducerGeometry geometry, Surface producer, int attempt) {
        if (shuttingDown || binding != null || producer != inputProducerSurface) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return;

        ProducerGeometry current = readSurfaceGeometry(materialHost);
        if (current == null || !isSameSurface(current.rootSurface, geometry.rootSurface)) {
            retryBind(attempt, "root changed before bind");
            return;
        }

        Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(
                materialHost, producer, 1.0f);
        if (next == null) {
            retryBind(attempt, "framework bind returned null");
            return;
        }

        binding = next;
        configRotation = current.configRotation;
        boundSurfaceWidth = current.surfaceWidth;
        boundSurfaceHeight = current.surfaceHeight;
        boundConfigRotation = current.configRotation;
        activationExhausted = false;
        MainHook.log(TAG + " producer geometry surface="
                + current.surfaceWidth + "x" + current.surfaceHeight
                + " buffer=" + current.bufferWidth + "x" + current.bufferHeight
                + " configRot=" + current.configRotation
                + " output=TextureView");
    }

    private void retryBind(int attempt, String reason) {
        if (shuttingDown || binding != null) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            activationExhausted = true;
            MainHook.log(TAG + " PassBlur TextureView activation exhausted reason=" + reason);
            return;
        }
        postOnAnimation(() -> bindProducerWhenReady(attempt + 1));
    }

    private void installGeometryObserver() {
        removeGeometryObserver();
        View root = getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            refreshProducerGeometryInPlace();
            return true;
        };
        observer.addOnPreDrawListener(listener);
        preDrawObserver = observer;
        preDrawListener = listener;
    }

    private void removeGeometryObserver() {
        ViewTreeObserver observer = preDrawObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        preDrawObserver = null;
        preDrawListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private void refreshProducerGeometryInPlace() {
        if (shuttingDown || binding == null) return;
        View materialHost = materialHostRef.get();
        SurfaceTexture input = inputSurfaceTexture;
        if (materialHost == null || input == null) return;

        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            return;
        }
        if (!isSameSurface(binding.rootSurface, geometry.rootSurface)) return;
        if (geometry.surfaceWidth == boundSurfaceWidth
                && geometry.surfaceHeight == boundSurfaceHeight
                && geometry.configRotation == boundConfigRotation) {
            return;
        }

        configRotation = geometry.configRotation;
        boundSurfaceWidth = geometry.surfaceWidth;
        boundSurfaceHeight = geometry.surfaceHeight;
        boundConfigRotation = geometry.configRotation;
        hasConsumedFrame = false;
        frameAvailable.set(false);
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        renderHandler.post(() -> {
            SurfaceTexture currentInput = inputSurfaceTexture;
            if (!shuttingDown && currentInput == input) {
                currentInput.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
            }
        });
        MainHook.log(TAG + " producer geometry updated in place surface="
                + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                + " configRot=" + geometry.configRotation);
    }

    private ProducerGeometry readSurfaceGeometry(View materialHost) {
        if (materialHost == null) return null;
        try {
            Object viewRoot = getViewRootImpl(materialHost);
            if (viewRoot == null) return null;
            Field sizeField = findField(viewRoot.getClass(), "mSurfaceSize");
            sizeField.setAccessible(true);
            Object value = sizeField.get(viewRoot);
            if (!(value instanceof Point)) return null;
            Point surfaceSize = (Point) value;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;

            int configRotation = readConfigRotation(materialHost);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(viewRoot);
            SurfaceControl rootSurface = surface instanceof SurfaceControl
                    ? (SurfaceControl) surface : null;
            return new ProducerGeometry(
                    surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight,
                    configRotation, rootSurface);
        } catch (Throwable error) {
            MainHook.log(TAG + " producer geometry unavailable: " + error);
            return null;
        }
    }

    private void logStageADiagnostics() {
        if (shuttingDown) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return;
        int[] hostScreen = new int[2];
        materialHost.getLocationOnScreen(hostScreen);
        MainHook.log(TAG + " stage-A diagnostic hostScreen=["
                + hostScreen[0] + "," + hostScreen[1] + "]"
                + " hostSize=" + materialHost.getWidth() + "x" + materialHost.getHeight()
                + " producerSurface=" + boundSurfaceWidth + "x" + boundSurfaceHeight
                + " configRot=" + configRotation
                + " texture matrix=" + formatTextureMatrix(textureMatrix));
    }

    private void makeCurrent() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY
                || eglContext == EGL14.EGL_NO_CONTEXT
                || eglWindowSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL output unavailable");
        }
        if (!EGL14.eglMakeCurrent(
                eglDisplay, eglWindowSurface, eglWindowSurface, eglContext)) {
            throw new IllegalStateException("eglMakeCurrent error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void destroyEglWindowSurfaceOnly() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
        try {
            EGL14.eglMakeCurrent(
                    eglDisplay,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
        } catch (Throwable ignored) {}
        EGL14.eglDestroySurface(eglDisplay, eglWindowSurface);
        eglWindowSurface = EGL14.EGL_NO_SURFACE;
    }

    private void releaseRenderResources() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY
                    && eglWindowSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) {
                makeCurrent();
            }
        } catch (Throwable ignored) {}

        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        if (producer != null) {
            try { producer.release(); } catch (Throwable ignored) {}
        }
        SurfaceTexture input = inputSurfaceTexture;
        inputSurfaceTexture = null;
        if (input != null) {
            try { input.release(); } catch (Throwable ignored) {}
        }

        if (oesTexture != 0) {
            try {
                int[] textures = new int[]{oesTexture};
                GLES20.glDeleteTextures(1, textures, 0);
            } catch (Throwable ignored) {}
            oesTexture = 0;
        }
        if (program != 0) {
            try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) {}
            program = 0;
        }

        Surface output = outputWindowSurface;
        outputWindowSurface = null;
        destroyEglWindowSurfaceOnly();
        if (output != null) {
            try { output.release(); } catch (Throwable ignored) {}
        }

        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try { EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        }
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
        outputSurfaceTexture = null;
    }

    private void resetBoundGeometry() {
        boundSurfaceWidth = 0;
        boundSurfaceHeight = 0;
        boundConfigRotation = -1;
    }

    private void fail(String stage, Throwable error) {
        activationExhausted = true;
        gpuBackdropActive = false;
        MainHook.log(TAG + " PassBlur TextureView " + stage + " failed: " + error);
    }

    private static int readConfigRotation(View materialHost) {
        Display display = materialHost != null ? materialHost.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        try {
            Method method = Display.class.getMethod("getInstallOrientation");
            Object value = method.invoke(display);
            if (value instanceof Number) installOrientation = ((Number) value).intValue();
        } catch (Throwable ignored) {}
        int rotation = display.getRotation();
        int result = (installOrientation + rotation) % 4;
        return result < 0 ? result + 4 : result;
    }

    private static Object getViewRootImpl(View view) throws Exception {
        Method method = View.class.getDeclaredMethod("getViewRootImpl");
        method.setAccessible(true);
        return method.invoke(view);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            Method method = SurfaceControl.class.getMethod("isSameSurface", SurfaceControl.class);
            Object value = method.invoke(first, second);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return first.equals(second);
        }
    }

    private static void checkEglHandle(String stage, boolean ok) {
        if (!ok) {
            throw new IllegalStateException(stage + " error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
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

    private static String formatTextureMatrix(float[] matrix) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) value.append(',');
            value.append(matrix[i]);
        }
        return value.append(']').toString();
    }
}
