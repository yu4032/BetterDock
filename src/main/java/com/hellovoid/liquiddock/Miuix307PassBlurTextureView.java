package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
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
 * Feedback-safe HyperOS 3.0.307 PassBlur -> OES -> 2D -> Prismal -> TextureView renderer.
 *
 * Stage A normalizes SurfaceFlinger's external-OES PassBlur producer into Dock-local RGBA while
 * applying the validated HyperOS Stage-B coordinate transform. Stage B runs Prismal's original
 * half-resolution horizontal/vertical Gaussian blur. Stage C runs the upstream Prismal optical
 * shader over ordinary 2D textures. Pixel data never crosses to the CPU.
 */
final class Miuix307PassBlurTextureView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private static final String TAG = "[DC][PBTX]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
    private static final float BLUR_FBO_SCALE = 0.5f;
    private static final int BLUR_KERNEL_RADIUS_TEXELS = 15;
    // Left/right keep the fixed 32dp GPU overscan as a compatibility baseline and can add
    // independent GUI pixel extras. Top/bottom remain fully controlled by their historical pixel
    // values. Output remains clipped to the visible Dock itself.
    private static final float EDGE_OVERSCAN_DP = 32f;

    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class SamplingInsets {
        final int left;
        final int right;
        final int top;
        final int bottom;

        SamplingInsets(int left, int right, int top, int bottom) {
            this.left = Math.max(0, left);
            this.right = Math.max(0, right);
            this.top = Math.max(0, top);
            this.bottom = Math.max(0, bottom);
        }
    }

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
    private final Handler mainHandler;
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];

    private volatile boolean shuttingDown;
    private volatile boolean gpuBackdropActive;
    private volatile boolean activationExhausted;
    private volatile boolean hasConsumedFrame;
    private volatile boolean producerRebindPending;
    private volatile int configRotation;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile SurfaceTexture outputSurfaceTexture;
    private volatile Surface outputWindowSurface;
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile Miuix307PrismalMaterial.Params opticalParams;
    private volatile int outputWidth;
    private volatile int outputHeight;
    private volatile int maxTextureSize;
    private volatile int topOverscanPx = 48;
    private volatile int bottomOverscanPx = 16;
    private volatile int leftExtraOverscanPx;
    private volatile int rightExtraOverscanPx;

    // Stage A samples a real overscan ring around the visible Dock. The sample-valid
    // rectangle is used only by the normalization mirror guard; Dock validity remains separate
    // so half-pulled animations are still clipped to pixels that are actually on-screen.
    private volatile float backdropX;
    private volatile float backdropY;
    private volatile float backdropW = 1f;
    private volatile float backdropH = 1f;
    private volatile float validSampleLeft;
    private volatile float validSampleBottom;
    private volatile float validSampleRight = 1f;
    private volatile float validSampleTop = 1f;
    private volatile float validDockLeft;
    private volatile float validDockBottom;
    private volatile float validDockRight = 1f;
    private volatile float validDockTop = 1f;
    // Visible Dock coordinates inside the larger overscan texture: x, y, width, height.
    private volatile float dockUvLeft;
    private volatile float dockUvBottom;
    private volatile float dockUvWidth = 1f;
    private volatile float dockUvHeight = 1f;
    private volatile Miuix307BackdropMapping.Coverage producerCoverage =
            Miuix307BackdropMapping.Coverage.FULL;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglWindowSurface = EGL14.EGL_NO_SURFACE;

    private int normalizeProgram;
    private int blurProgram;
    private int materialProgram;
    private int oesTexture;

    private int rawTexture;
    private int rawFramebuffer;
    private int blurTextureH;
    private int blurFramebufferH;
    private int blurTextureV;
    private int blurFramebufferV;
    private int fboWidth;
    private int fboHeight;
    private int blurWidth;
    private int blurHeight;

    private int boundSurfaceWidth;
    private int boundSurfaceHeight;
    private int boundBufferWidth;
    private int boundBufferHeight;
    private int boundConfigRotation = -1;
    private boolean firstFrameLogged;
    private boolean firstDrawLogged;
    private boolean firstMatrixLogged;
    private boolean stageBDiagnosticsLogged;
    private ViewTreeObserver preDrawObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    Miuix307PassBlurTextureView(Context context, View materialHost) {
        super(context);
        materialHostRef = new WeakReference<>(materialHost);
        opticalParams = Miuix307PrismalMaterial.defaults(
                context.getResources().getDisplayMetrics().density);
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
        mainHandler = new Handler(context.getMainLooper());
    }

    boolean isGpuBackdropActive() {
        return gpuBackdropActive;
    }

    boolean isActivationExhausted() {
        return activationExhausted;
    }

    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {
        if (glassConfig == null || shuttingDown) return;
        opticalParams = Miuix307PrismalMaterial.fromConfig(
                glassConfig, getResources().getDisplayMetrics().density);
        topOverscanPx = Math.max(0, glassConfig.captureBleedTopPx);
        bottomOverscanPx = Math.max(0, glassConfig.captureBleedBottomPx);
        leftExtraOverscanPx = Math.max(0, glassConfig.captureBleedLeftPx);
        rightExtraOverscanPx = Math.max(0, glassConfig.captureBleedRightPx);
        updateBackdropMapping();
        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));
    }

    /**
     * Reconnect SurfaceFlinger's PassBlur producer without rebuilding the attached TextureView.
     * The framework Binding can remain stale-true after its BufferQueue has disconnected.
     */
    void rebindProducer(String reason) {
        if (shuttingDown) return;
        if (producerRebindPending) return;
        producerRebindPending = true;
        Miuix307PassBlurBridge.Binding stale = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(stale);
        gpuBackdropActive = false;
        activationExhausted = false;
        hasConsumedFrame = false;
        frameAvailable.set(false);
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        stageBDiagnosticsLogged = false;
        resetBoundGeometry();
        MainHook.log(TAG + " producer rebind requested reason=" + reason);
        renderHandler.post(() -> recreateInputProducer(reason));
    }

    /**
     * SetPassBlurSurface marks the producer Binder before parceling it to SurfaceFlinger. A
     * producer that already crossed that boundary must not be reused; create a new BufferQueue.
     */
    private void recreateInputProducer(String reason) {
        Surface staleProducer = inputProducerSurface;
        SurfaceTexture staleInput = inputSurfaceTexture;
        try {
            makeCurrent();
            inputProducerSurface = null;
            inputSurfaceTexture = null;

            if (staleProducer != null) staleProducer.release();
            if (staleInput != null) {
                try { staleInput.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}
                staleInput.release();
            }
            if (oesTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
                oesTexture = 0;
            }

            createInputProducer();
            if (inputProducerSurface == staleProducer || inputSurfaceTexture == staleInput) {
                throw new IllegalStateException("PassBlur input producer was not replaced");
            }
            MainHook.log(TAG + " input producer recreated reason=" + reason);
            post(() -> bindProducerWhenReady(0));
        } catch (Throwable error) {
            producerRebindPending = false;
            fail("producer recreate", error);
        }
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        producerRebindPending = false;
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
        updateBackdropMapping();
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
        updateBackdropMapping();
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
        updateBackdropMapping();
        renderHandler.post(() -> {
            if (eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
            try {
                makeCurrent();
                ensureFboSize(outputWidth, outputHeight);
                drawLatestFrame(false);
            } catch (Throwable error) {
                fail("output resize", error);
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
        // Output callback only. PassBlur input frames are driven by the independent input ST.
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
            queryMaxTextureSize();
            // Mapping is View/screen geometry and therefore belongs on the UI thread. Do not
            // allocate the first FBO until that mapping has been recomputed with the real GPU
            // texture limit; otherwise one first frame could use capped FBOs with uncapped UVs.
            mainHandler.post(() -> {
                if (shuttingDown || outputWindowSurface != window) return;
                updateBackdropMapping();
                renderHandler.post(() -> finishOutputAttach(window, width, height));
            });
        } catch (Throwable error) {
            fail("output attach", error);
        }
    }

    private void finishOutputAttach(Surface window, int width, int height) {
        if (shuttingDown || outputWindowSurface != window
                || eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
        try {
            makeCurrent();
            ensureGlResources();
            ensureFboSize(Math.max(1, width), Math.max(1, height));
            drawLatestFrame(false);
        } catch (Throwable error) {
            fail("output attach finish", error);
        }
    }

    private void queryMaxTextureSize() {
        if (maxTextureSize > 0) return;
        int[] value = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0);
        if (value[0] <= 0) {
            throw new IllegalStateException("GL_MAX_TEXTURE_SIZE unavailable");
        }
        maxTextureSize = value[0];
        MainHook.log(TAG + " GL_MAX_TEXTURE_SIZE=" + maxTextureSize);
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

    private void ensureGlResources() {
        if (normalizeProgram != 0 && blurProgram != 0 && materialProgram != 0
                && oesTexture != 0 && inputSurfaceTexture != null && inputProducerSurface != null) {
            return;
        }

        normalizeProgram = createProgram(
                Miuix307PassBlurShaders.QUAD_VERTEX,
                Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        blurProgram = createProgram(
                Miuix307PassBlurShaders.QUAD_VERTEX,
                Miuix307PassBlurShaders.GAUSSIAN_BLUR_FRAGMENT);
        materialProgram = createProgram(
                Miuix307PrismalShader.VERTEX_SHADER,
                Miuix307PrismalShader.FRAGMENT_SHADER);
        if (normalizeProgram == 0 || blurProgram == 0 || materialProgram == 0) {
            throw new IllegalStateException("one or more Prismal pipeline programs failed");
        }

        createInputProducer();
        post(() -> bindProducerWhenReady(0));
    }

    private void createInputProducer() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        oesTexture = textures[0];
        if (oesTexture == 0) throw new IllegalStateException("OES texture=0");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        SurfaceTexture input = new SurfaceTexture(oesTexture);
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);
            drawLatestFrame(true);
        }, renderHandler);
    }

    private void ensureFboSize(int width, int height) {
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("FBO allocation before GL_MAX_TEXTURE_SIZE query");
        }
        if (width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalStateException("visible material exceeds GL_MAX_TEXTURE_SIZE "
                    + width + "x" + height + " max=" + maxTextureSize);
        }
        SamplingInsets insets = resolveSamplingInsets(width, height);
        int nextWidth = Math.max(1, width + insets.left + insets.right);
        int nextHeight = Math.max(1, height + insets.top + insets.bottom);
        int nextBlurWidth = Math.max(1, Math.round(nextWidth * BLUR_FBO_SCALE));
        int nextBlurHeight = Math.max(1, Math.round(nextHeight * BLUR_FBO_SCALE));
        if (rawFramebuffer != 0 && blurFramebufferH != 0 && blurFramebufferV != 0
                && fboWidth == nextWidth && fboHeight == nextHeight
                && blurWidth == nextBlurWidth && blurHeight == nextBlurHeight) {
            return;
        }

        releaseFbos();
        rawTexture = createTexture2D(nextWidth, nextHeight);
        rawFramebuffer = createFramebuffer(rawTexture);
        blurTextureH = createTexture2D(nextBlurWidth, nextBlurHeight);
        blurFramebufferH = createFramebuffer(blurTextureH);
        blurTextureV = createTexture2D(nextBlurWidth, nextBlurHeight);
        blurFramebufferV = createFramebuffer(blurTextureV);
        fboWidth = nextWidth;
        fboHeight = nextHeight;
        blurWidth = nextBlurWidth;
        blurHeight = nextBlurHeight;
    }

    private void drawLatestFrame(boolean fromFrameCallback) {
        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE
                || normalizeProgram == 0 || blurProgram == 0 || materialProgram == 0
                || oesTexture == 0) {
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
                            + " stage=normalize-only configRot=" + configRotation);
                }
            }
            if (!hasConsumedFrame) return;

            ensureFboSize(Math.max(1, outputWidth), Math.max(1, outputHeight));
            renderNormalizationPass();
            renderBlurPasses();
            renderMaterialPass();

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
                MainHook.log(TAG + " first EGL material draw"
                        + " textureDomain=normalized-2d"
                        + " material=prismal-upstream"
                        + " blur=two-pass-0.5x"
                        + " coverage=" + producerCoverage
                        + " backdropRect=[" + backdropX + "," + backdropY + ","
                        + backdropW + "," + backdropH + "]"
                        + " validDockRect=[" + validDockLeft + "," + validDockBottom + ","
                        + validDockRight + "," + validDockTop + "]"
                        + " output=" + outputWidth + "x" + outputHeight
                        + " producerSurface=" + boundSurfaceWidth + "x" + boundSurfaceHeight
                        + " producerBuffer=" + boundBufferWidth + "x" + boundBufferHeight
                        + " configRot=" + configRotation
                        + " frameCallback=" + fromFrameCallback);
            }
            if (gpuBackdropActive && !stageBDiagnosticsLogged) {
                stageBDiagnosticsLogged = true;
                float[] matrixSnapshot = textureMatrix.clone();
                post(() -> logStageBDiagnostics(matrixSnapshot));
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private void renderNormalizationPass() {
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rawFramebuffer);
        GLES20.glViewport(0, 0, fboWidth, fboHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(
                requireUniform(normalizeProgram, "uTexMatrix"), 1, false, textureMatrix, 0);
        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uBackdropRect"),
                backdropX, backdropY, backdropW, backdropH);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), configRotation);
        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uValidDockRect"),
                validSampleLeft, validSampleBottom, validSampleRight, validSampleTop);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
    }

    private void renderBlurPasses() {
        Miuix307PrismalMaterial.Params params = opticalParams;
        float sigma = Miuix307PrismalMaterial.blurSigma(params);
        float texelX = 1f / Math.max(1, blurWidth);
        float texelY = 1f / Math.max(1, blurHeight);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glUseProgram(blurProgram);
        bindQuad(blurProgram);
        GLES20.glUniform1f(requireUniform(blurProgram, "uSigma"), sigma);
        GLES20.glUniform2f(requireUniform(blurProgram, "uTexelSize"), texelX, texelY);
        GLES20.glUniform1i(requireUniform(blurProgram, "uTexture"), 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebufferH);
        GLES20.glViewport(0, 0, blurWidth, blurHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rawTexture);
        GLES20.glUniform2f(requireUniform(blurProgram, "uDirection"), 1f, 0f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFramebufferV);
        GLES20.glViewport(0, 0, blurWidth, blurHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureH);
        GLES20.glUniform2f(requireUniform(blurProgram, "uDirection"), 0f, 1f);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(blurProgram);
    }

    private void renderMaterialPass() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, Math.max(1, outputWidth), Math.max(1, outputHeight));
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (producerCoverage == Miuix307BackdropMapping.Coverage.OUTSIDE
                || validDockRight <= validDockLeft || validDockTop <= validDockBottom) {
            return;
        }

        // Do not invent pixels outside the Floating Dock PassBlur producer. Partial animation
        // coverage stays transparent instead of repeating the nearest producer edge texel.
        if (producerCoverage == Miuix307BackdropMapping.Coverage.PARTIAL) {
            int left = Math.max(0, Math.round(validDockLeft * outputWidth));
            int bottom = Math.max(0, Math.round(validDockBottom * outputHeight));
            int right = Math.min(outputWidth, Math.round(validDockRight * outputWidth));
            int top = Math.min(outputHeight, Math.round(validDockTop * outputHeight));
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(materialProgram);
        bindQuad(materialProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, rawTexture);
        GLES20.glUniform1i(requireUniform(materialProgram, "u_backgroundTexture"), 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTextureV);
        GLES20.glUniform1i(requireUniform(materialProgram, "u_blurredTexture"), 1);
        GLES20.glUniform1i(requireUniform(materialProgram, "u_useBlurredTexture"), 1);

        float cornerRadiusPx = Math.max(1f, outputHeight * 0.44f);
        View materialHost = materialHostRef.get();
        if (materialHost != null) {
            float nativeRadius = MiuixGlassHook.readNativeOpticsRadius(materialHost);
            if (!Float.isNaN(nativeRadius) && !Float.isInfinite(nativeRadius) && nativeRadius > 0f) {
                cornerRadiusPx = nativeRadius;
            }
        }
        Miuix307PrismalMaterial.applyUniforms(
                materialProgram, opticalParams, cornerRadiusPx, outputWidth, outputHeight);
        int uDockUvRect = requireUniform(materialProgram, "u_dockUvRect");
        GLES20.glUniform4f(
                uDockUvRect, dockUvLeft, dockUvBottom, dockUvWidth, dockUvHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(materialProgram);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void bindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) throw new IllegalStateException("quad attribute unavailable");
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(
                position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(
                uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
    }

    private void unbindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
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
        producerRebindPending = false;
        configRotation = current.configRotation;
        boundSurfaceWidth = current.surfaceWidth;
        boundSurfaceHeight = current.surfaceHeight;
        boundBufferWidth = current.bufferWidth;
        boundBufferHeight = current.bufferHeight;
        boundConfigRotation = current.configRotation;
        activationExhausted = false;
        stageBDiagnosticsLogged = false;
        updateBackdropMapping();
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
            producerRebindPending = false;
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
            updateBackdropMapping();
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
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) return;
        if (!binding.rootSurface.isValid()
                || !isSameSurface(binding.rootSurface, geometry.rootSurface)) {
            rebindProducer("producer-root-changed");
            return;
        }
        if (geometry.surfaceWidth == boundSurfaceWidth
                && geometry.surfaceHeight == boundSurfaceHeight
                && geometry.bufferWidth == boundBufferWidth
                && geometry.bufferHeight == boundBufferHeight
                && geometry.configRotation == boundConfigRotation) {
            return;
        }

        configRotation = geometry.configRotation;
        boundSurfaceWidth = geometry.surfaceWidth;
        boundSurfaceHeight = geometry.surfaceHeight;
        boundBufferWidth = geometry.bufferWidth;
        boundBufferHeight = geometry.bufferHeight;
        boundConfigRotation = geometry.configRotation;
        hasConsumedFrame = false;
        frameAvailable.set(false);
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        stageBDiagnosticsLogged = false;
        updateBackdropMapping();
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

    private int horizontalOverscanPx() {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));
    }

    private int blurSamplingGuardPx() {
        return Math.max(0, (int) Math.ceil(
                BLUR_KERNEL_RADIUS_TEXELS / Math.max(BLUR_FBO_SCALE, 0.0001f)));
    }

    private SamplingInsets resolveSamplingInsets(int width, int height) {
        int opticalX = Miuix307PrismalMaterial.requiredSampleGuardPx(
                opticalParams, width, height, true);
        int opticalY = Miuix307PrismalMaterial.requiredSampleGuardPx(
                opticalParams, width, height, false);
        int blurGuard = blurSamplingGuardPx();
        opticalX += blurGuard;
        opticalY += blurGuard;

        int left = Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX);
        int right = Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX);
        int top = Math.max(Math.max(0, topOverscanPx), opticalY);
        int bottom = Math.max(Math.max(0, bottomOverscanPx), opticalY);

        int[] horizontal = fitInsetPairToTextureLimit(width, left, right, maxTextureSize);
        int[] vertical = fitInsetPairToTextureLimit(height, top, bottom, maxTextureSize);
        return new SamplingInsets(horizontal[0], horizontal[1], vertical[0], vertical[1]);
    }

    private static int[] fitInsetPairToTextureLimit(
            int visible, int before, int after, int maxTextureSize) {
        int safeVisible = Math.max(1, visible);
        int safeBefore = Math.max(0, before);
        int safeAfter = Math.max(0, after);
        if (maxTextureSize <= 0) return new int[]{safeBefore, safeAfter};

        int available = Math.max(0, maxTextureSize - safeVisible);
        long desired = (long) safeBefore + safeAfter;
        if (desired <= available) return new int[]{safeBefore, safeAfter};
        if (available <= 0 || desired <= 0) return new int[]{0, 0};

        int fittedBefore = (int) Math.round(safeBefore * (available / (double) desired));
        fittedBefore = Math.max(0, Math.min(available, fittedBefore));
        int fittedAfter = available - fittedBefore;
        return new int[]{fittedBefore, fittedAfter};
    }

    private void updateBackdropMapping() {
        if (shuttingDown || !isAttachedToWindow()) return;
        int visibleWidth = outputWidth > 0 ? outputWidth : getWidth();
        int visibleHeight = outputHeight > 0 ? outputHeight : getHeight();
        if (visibleWidth <= 0 || visibleHeight <= 0) return;

        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");
        if (winFrame == null || winFrame.width() <= 0 || winFrame.height() <= 0) return;
        int[] viewScreen = new int[2];
        getLocationOnScreen(viewScreen);

        SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight);
        int sampleWidth = visibleWidth + insets.left + insets.right;
        int sampleHeight = visibleHeight + insets.top + insets.bottom;
        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(
                viewScreen[0] - insets.left, viewScreen[1] - insets.top,
                sampleWidth, sampleHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());
        Miuix307BackdropMapping.Result dock = Miuix307BackdropMapping.compute(
                viewScreen[0], viewScreen[1], visibleWidth, visibleHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());

        float nextDockUvLeft = insets.left / (float) sampleWidth;
        float nextDockUvBottom = insets.bottom / (float) sampleHeight;
        float nextDockUvWidth = visibleWidth / (float) sampleWidth;
        float nextDockUvHeight = visibleHeight / (float) sampleHeight;

        boolean unchanged = Float.compare(backdropX, sample.backdropX) == 0
                && Float.compare(backdropY, sample.backdropY) == 0
                && Float.compare(backdropW, sample.backdropW) == 0
                && Float.compare(backdropH, sample.backdropH) == 0
                && Float.compare(validSampleLeft, sample.validLeft) == 0
                && Float.compare(validSampleBottom, sample.validBottom) == 0
                && Float.compare(validSampleRight, sample.validRight) == 0
                && Float.compare(validSampleTop, sample.validTop) == 0
                && Float.compare(validDockLeft, dock.validLeft) == 0
                && Float.compare(validDockBottom, dock.validBottom) == 0
                && Float.compare(validDockRight, dock.validRight) == 0
                && Float.compare(validDockTop, dock.validTop) == 0
                && Float.compare(dockUvLeft, nextDockUvLeft) == 0
                && Float.compare(dockUvBottom, nextDockUvBottom) == 0
                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && producerCoverage == dock.coverage;
        if (unchanged) return;

        backdropX = sample.backdropX;
        backdropY = sample.backdropY;
        backdropW = sample.backdropW;
        backdropH = sample.backdropH;
        validSampleLeft = sample.validLeft;
        validSampleBottom = sample.validBottom;
        validSampleRight = sample.validRight;
        validSampleTop = sample.validTop;
        validDockLeft = dock.validLeft;
        validDockBottom = dock.validBottom;
        validDockRight = dock.validRight;
        validDockTop = dock.validTop;
        dockUvLeft = nextDockUvLeft;
        dockUvBottom = nextDockUvBottom;
        dockUvWidth = nextDockUvWidth;
        dockUvHeight = nextDockUvHeight;
        producerCoverage = dock.coverage;
        stageBDiagnosticsLogged = false;
        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));
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

            int nextRotation = readConfigRotation(materialHost);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (nextRotation == 1 || nextRotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(viewRoot);
            SurfaceControl rootSurface = surface instanceof SurfaceControl
                    ? (SurfaceControl) surface : null;
            return new ProducerGeometry(
                    surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight,
                    nextRotation, rootSurface);
        } catch (Throwable error) {
            MainHook.log(TAG + " producer geometry unavailable: " + error);
            return null;
        }
    }

    private void logStageBDiagnostics(float[] matrixSnapshot) {
        if (shuttingDown) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return;
        View root = materialHost.getRootView();
        if (root == null) return;

        int[] viewScreen = new int[2];
        int[] hostScreen = new int[2];
        int[] rootScreen = new int[2];
        getLocationOnScreen(viewScreen);
        materialHost.getLocationOnScreen(hostScreen);
        root.getLocationOnScreen(rootScreen);
        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");

        float[] bl = mapFinalCoordinate(backdropX, backdropY, configRotation, matrixSnapshot);
        float[] br = mapFinalCoordinate(
                backdropX + backdropW, backdropY, configRotation, matrixSnapshot);
        float[] tl = mapFinalCoordinate(
                backdropX, backdropY + backdropH, configRotation, matrixSnapshot);
        float[] tr = mapFinalCoordinate(
                backdropX + backdropW, backdropY + backdropH, configRotation, matrixSnapshot);

        MainHook.log(TAG + " stage-B mapping rootScreen=["
                + rootScreen[0] + "," + rootScreen[1] + "]"
                + " viewScreen=[" + viewScreen[0] + "," + viewScreen[1] + "]"
                + " hostScreen=[" + hostScreen[0] + "," + hostScreen[1] + "]"
                + " hostSize=" + materialHost.getWidth() + "x" + materialHost.getHeight()
                + " winFrame=" + formatRect(winFrame)
                + " rootSurface=" + boundSurfaceWidth + "x" + boundSurfaceHeight
                + " producerBuffer=" + boundBufferWidth + "x" + boundBufferHeight
                + " coverage=" + producerCoverage
                + " backdropRect=[" + backdropX + "," + backdropY + ","
                + backdropW + "," + backdropH + "]"
                + " validDockRect=[" + validDockLeft + "," + validDockBottom + ","
                + validDockRight + "," + validDockTop + "]"
                + " configRot=" + configRotation
                + " texture matrix=" + formatTextureMatrix(matrixSnapshot)
                + " mapped corners bl=[" + bl[0] + "," + bl[1] + "]"
                + " br=[" + br[0] + "," + br[1] + "]"
                + " tl=[" + tl[0] + "," + tl[1] + "]"
                + " tr=[" + tr[0] + "," + tr[1] + "]");
    }

    private static float[] mapFinalCoordinate(
            float rootX, float rootY, int rotation, float[] matrix) {
        float orientedX = rootX;
        float orientedY = rootY;
        if (rotation == 1) {
            orientedX = rootY;
            orientedY = 1f - rootX;
        } else if (rotation == 2) {
            orientedX = 1f - rootX;
            orientedY = 1f - rootY;
        } else if (rotation == 3) {
            orientedX = 1f - rootY;
            orientedY = rootX;
        }
        float[] input = compensateSurfaceTextureCropPreservingOrientation(
                orientedX, orientedY, matrix);
        return mapTextureCoordinate(matrix, input[0], input[1]);
    }

    private static float[] compensateSurfaceTextureCropPreservingOrientation(
            float x, float y, float[] matrix) {
        if (matrix == null || matrix.length < 16) return new float[]{x, y};
        float a00 = matrix[0];
        float a01 = matrix[4];
        float a10 = matrix[1];
        float a11 = matrix[5];
        float scale0 = (float) Math.hypot(a00, a10);
        float scale1 = (float) Math.hypot(a01, a11);
        float determinant = a00 * a11 - a01 * a10;
        if (scale0 <= 0.000001f || scale1 <= 0.000001f
                || Math.abs(determinant) <= 0.000001f) {
            return new float[]{x, y};
        }

        float o00 = a00 / scale0;
        float o10 = a10 / scale0;
        float o01 = a01 / scale1;
        float o11 = a11 / scale1;
        if (Math.abs(o00 * o01 + o10 * o11) > 0.001f) return new float[]{x, y};

        float biasX = -Math.min(0f, o00) - Math.min(0f, o01);
        float biasY = -Math.min(0f, o10) - Math.min(0f, o11);
        float desiredX = o00 * x + o01 * y + biasX;
        float desiredY = o10 * x + o11 * y + biasY;
        float rhsX = desiredX - matrix[12];
        float rhsY = desiredY - matrix[13];
        return new float[]{
                (a11 * rhsX - a01 * rhsY) / determinant,
                (-a10 * rhsX + a00 * rhsY) / determinant
        };
    }

    private static float[] mapTextureCoordinate(float[] matrix, float x, float y) {
        if (matrix == null || matrix.length < 16) return new float[]{x, y};
        float mappedX = matrix[0] * x + matrix[4] * y + matrix[12];
        float mappedY = matrix[1] * x + matrix[5] * y + matrix[13];
        float mappedW = matrix[3] * x + matrix[7] * y + matrix[15];
        if (Math.abs(mappedW) > 0.000001f) {
            mappedX /= mappedW;
            mappedY /= mappedW;
        }
        return new float[]{mappedX, mappedY};
    }

    private static int createTexture2D(int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texture = textures[0];
        if (texture == 0) throw new IllegalStateException("2D texture=0");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int[] framebuffers = new int[1];
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        int framebuffer = framebuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer incomplete=0x" + Integer.toHexString(status));
        }
        return framebuffer;
    }

    private void releaseFbos() {
        int[] framebuffers = new int[]{rawFramebuffer, blurFramebufferH, blurFramebufferV};
        for (int framebuffer : framebuffers) {
            if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{framebuffer}, 0);
        }
        int[] textures = new int[]{rawTexture, blurTextureH, blurTextureV};
        for (int texture : textures) {
            if (texture != 0) GLES20.glDeleteTextures(1, new int[]{texture}, 0);
        }
        rawFramebuffer = 0;
        blurFramebufferH = 0;
        blurFramebufferV = 0;
        rawTexture = 0;
        blurTextureH = 0;
        blurTextureV = 0;
        fboWidth = 0;
        fboHeight = 0;
        blurWidth = 0;
        blurHeight = 0;
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
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
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

        try { releaseFbos(); } catch (Throwable ignored) {}
        if (oesTexture != 0) {
            try { GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0); } catch (Throwable ignored) {}
            oesTexture = 0;
        }
        int[] programs = new int[]{normalizeProgram, blurProgram, materialProgram};
        for (int program : programs) {
            if (program != 0) {
                try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) {}
            }
        }
        normalizeProgram = 0;
        blurProgram = 0;
        materialProgram = 0;

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
        boundBufferWidth = 0;
        boundBufferHeight = 0;
        boundConfigRotation = -1;
        backdropX = 0f;
        backdropY = 0f;
        backdropW = 1f;
        backdropH = 1f;
        validSampleLeft = 0f;
        validSampleBottom = 0f;
        validSampleRight = 1f;
        validSampleTop = 1f;
        validDockLeft = 0f;
        validDockBottom = 0f;
        validDockRight = 1f;
        validDockTop = 1f;
        dockUvLeft = 0f;
        dockUvBottom = 0f;
        dockUvWidth = 1f;
        dockUvHeight = 1f;
        producerCoverage = Miuix307BackdropMapping.Coverage.FULL;
    }

    private void fail(String stage, Throwable error) {
        activationExhausted = true;
        gpuBackdropActive = false;
        MainHook.log(TAG + " PassBlur TextureView " + stage + " failed: " + error);
        requestTerminalShutdown();
    }

    private void requestTerminalShutdown() {
        if (shuttingDown) return;
        if (android.os.Looper.myLooper() == getContext().getMainLooper()) {
            shutdown();
        } else {
            mainHandler.post(this::shutdown);
        }
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

    private static Rect readViewRootRectField(View view, String fieldName) {
        if (view == null) return null;
        try {
            Object viewRoot = getViewRootImpl(view);
            if (viewRoot == null) return null;
            Field field = findField(viewRoot.getClass(), fieldName);
            field.setAccessible(true);
            Object value = field.get(viewRoot);
            return value instanceof Rect ? new Rect((Rect) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
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

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing uniform " + name);
        return location;
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

    private static String formatRect(Rect rect) {
        if (rect == null) return "unavailable";
        return "[" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + "]";
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