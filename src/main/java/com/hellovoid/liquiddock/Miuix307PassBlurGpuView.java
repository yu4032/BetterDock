package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GPU-only consumer for the HyperOS PassBlur output.
 *
 * The center of the Dock is an exact passthrough of the live compositor texture. A smooth rounded
 * edge lens displaces only the inner edge band so spatial refraction can be judged without the
 * artificial horizontal grating used by the first proof-of-path demo.
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
            + "uniform vec2 uViewSize;\n"
            + "uniform float uGlassRadius;\n"
            + "uniform int uConfigRot;\n"
            + "varying vec2 vUv;\n"
            + "float sdRoundRect(vec2 p, vec2 h, float r) {\n"
            + "  vec2 q = abs(p) - (h - vec2(r));\n"
            + "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n"
            + "}\n"
            + "void main() {\n"
            + "  vec2 halfSize = max(uViewSize * 0.5 - vec2(0.5), vec2(1.0));\n"
            + "  float radius = clamp(uGlassRadius, 1.0, min(halfSize.x, halfSize.y));\n"
            + "  vec2 p = vUv * uViewSize - uViewSize * 0.5;\n"
            + "  float sd = sdRoundRect(p, halfSize, radius);\n"
            + "  float insidePx = max(-sd, 0.0);\n"
            + "  float bandPx = clamp(uViewSize.y * 0.30, 18.0, 58.0);\n"
            + "  float edgeWeight = (1.0 - smoothstep(0.0, bandPx, insidePx))\n"
            + "                   * smoothstep(0.0, 2.0, insidePx);\n"
            + "  edgeWeight *= edgeWeight;\n"
            + "  float stepPx = 1.0;\n"
            + "  vec2 grad = vec2(\n"
            + "      sdRoundRect(p + vec2(stepPx, 0.0), halfSize, radius)\n"
            + "        - sdRoundRect(p - vec2(stepPx, 0.0), halfSize, radius),\n"
            + "      sdRoundRect(p + vec2(0.0, stepPx), halfSize, radius)\n"
            + "        - sdRoundRect(p - vec2(0.0, stepPx), halfSize, radius));\n"
            + "  vec2 normal = length(grad) > 0.001 ? normalize(grad) : vec2(0.0);\n"
            + "  float displacementPx = clamp(uViewSize.y * 0.055, 4.0, 12.0);\n"
            + "  vec2 refractedUv = clamp(vUv - normal * displacementPx / uViewSize, 0.0, 1.0);\n"
            + "  vec2 lensUv = mix(vUv, refractedUv, edgeWeight);\n"
            + "  vec2 rootUv = vec2(uCrop.x + lensUv.x * uCrop.z,\n"
            + "                     uCrop.y + lensUv.y * uCrop.w);\n"
            + "  vec2 sampleUv = rootUv;\n"
            + "  if (uConfigRot == 1) {\n"
            + "    sampleUv = vec2(rootUv.y, 1.0 - rootUv.x);\n"
            + "  } else if (uConfigRot == 2) {\n"
            + "    sampleUv = vec2(1.0 - rootUv.x, 1.0 - rootUv.y);\n"
            + "  } else if (uConfigRot == 3) {\n"
            + "    sampleUv = vec2(1.0 - rootUv.y, rootUv.x);\n"
            + "  }\n"
            + "  vec4 transformed = uTexMatrix * vec4(sampleUv, 0.0, 1.0);\n"
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
    private volatile int configRotation;
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile SurfaceTexture surfaceTexture;
    private volatile Surface producerSurface;

    private int program;
    private int oesTexture;
    private int boundSurfaceWidth;
    private int boundSurfaceHeight;
    private int boundConfigRotation = -1;
    private SurfaceControl boundOutputSurface;
    private boolean rebindPosted;
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
        resetBoundGeometry();

        Surface currentProducer = producerSurface;
        producerSurface = null;
        if (currentProducer != null) currentProducer.release();
        SurfaceTexture currentTexture = surfaceTexture;
        surfaceTexture = null;
        if (currentTexture != null) currentTexture.release();

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
            resetInputForNewGlContext();
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

            SurfaceTexture nextTexture = new SurfaceTexture(oesTexture);
            Surface nextProducer = new Surface(nextTexture);
            surfaceTexture = nextTexture;
            producerSurface = nextProducer;
            nextTexture.setOnFrameAvailableListener(textureSource -> {
                if (shuttingDown || textureSource != surfaceTexture) return;
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
        post(this::rebindProducerForGeometryChange);
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
                    MainHook.log(TAG + " first OES frame configRot=" + configRotation);
                }
            }
            if (!hasConsumedFrame) return;

            GLES20.glUseProgram(program);
            int position = GLES20.glGetAttribLocation(program, "aPosition");
            int uv = GLES20.glGetAttribLocation(program, "aUv");
            int texture = GLES20.glGetUniformLocation(program, "uTexture");
            int matrix = GLES20.glGetUniformLocation(program, "uTexMatrix");
            int crop = GLES20.glGetUniformLocation(program, "uCrop");
            int viewSize = GLES20.glGetUniformLocation(program, "uViewSize");
            int glassRadius = GLES20.glGetUniformLocation(program, "uGlassRadius");
            int rotation = GLES20.glGetUniformLocation(program, "uConfigRot");
            if (position < 0 || uv < 0 || texture < 0 || matrix < 0 || crop < 0
                    || viewSize < 0 || glassRadius < 0 || rotation < 0) {
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
            GLES20.glUniform4f(crop, cropX, cropY, cropW, cropH);
            GLES20.glUniform2f(viewSize, Math.max(1f, getWidth()), Math.max(1f, getHeight()));
            GLES20.glUniform1f(glassRadius, Math.max(1f, glassRadiusPx));
            GLES20.glUniform1i(rotation, configRotation);
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
                        + cropX + "," + cropY + "," + cropW + "," + cropH + "]"
                        + " configRot=" + configRotation
                        + " producerSurface=" + boundSurfaceWidth + "x" + boundSurfaceHeight
                        + " lens=edge-only passthrough-center");
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null) return;
        View materialHost = materialHostRef.get();
        SurfaceTexture textureSource = surfaceTexture;
        Surface targetSurface = producerSurface;
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || !isAttachedToWindow() || textureSource == null || targetSurface == null) {
            retryBind(attempt, "views/input not ready");
            return;
        }

        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        SurfaceControl outputSurface = getSurfaceControl();
        if (geometry == null || geometry.bufferWidth <= 0 || geometry.bufferHeight <= 0
                || geometry.rootSurface == null || !geometry.rootSurface.isValid()
                || outputSurface == null || !outputSurface.isValid()) {
            retryBind(attempt, "geometry/output surface not ready");
            return;
        }

        try {
            int bufferWidth = geometry.bufferWidth;
            int bufferHeight = geometry.bufferHeight;
            textureSource.setDefaultBufferSize(bufferWidth, bufferHeight);
            configRotation = geometry.configRotation;
            updateCrop();
            Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(
                    materialHost, this, targetSurface, 1.0f);
            if (next == null) {
                retryBind(attempt, "framework bind returned null");
                return;
            }
            binding = next;
            boundSurfaceWidth = geometry.surfaceWidth;
            boundSurfaceHeight = geometry.surfaceHeight;
            boundConfigRotation = geometry.configRotation;
            boundOutputSurface = outputSurface;
            activationExhausted = false;
            applyOutputCornerRadius();
            View root = materialHost.getRootView();
            MainHook.log(TAG + " producer geometry surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                    + " buffer=" + bufferWidth + "x" + bufferHeight
                    + " configRot=" + geometry.configRotation
                    + " rootView=" + (root != null ? root.getWidth() + "x" + root.getHeight() : "null"));
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
            if (geometryChangedSinceBind()) postGeometryRebind();
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
        cropY = clamp01(1f - (top + height));
        cropH = Math.max(0.0001f, Math.min(1f - cropY, height));
    }

    private boolean geometryChangedSinceBind() {
        Miuix307PassBlurBridge.Binding currentBinding = binding;
        if (shuttingDown || currentBinding == null) return false;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return false;
        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        SurfaceControl outputSurface = getSurfaceControl();
        if (geometry == null || outputSurface == null || !outputSurface.isValid()) return false;
        if (geometry.surfaceWidth != boundSurfaceWidth
                || geometry.surfaceHeight != boundSurfaceHeight
                || geometry.configRotation != boundConfigRotation) {
            return true;
        }
        if (!isSameSurface(currentBinding.rootSurface, geometry.rootSurface)) return true;
        return boundOutputSurface == null || !isSameSurface(boundOutputSurface, outputSurface);
    }

    private void postGeometryRebind() {
        if (rebindPosted || shuttingDown) return;
        rebindPosted = true;
        post(() -> {
            rebindPosted = false;
            rebindProducerForGeometryChange();
        });
    }

    private void rebindProducerForGeometryChange() {
        if (shuttingDown) return;
        if (binding == null) {
            bindProducerWhenReady(0);
            return;
        }
        if (!geometryChangedSinceBind()) {
            updateCrop();
            return;
        }

        Miuix307PassBlurBridge.Binding stale = binding;
        binding = null;
        gpuBackdropActive = false;
        activationExhausted = false;
        hasConsumedFrame = false;
        frameAvailable.set(false);
        resetBoundGeometry();
        Miuix307PassBlurBridge.unbind(stale);
        firstFrameLogged = false;
        firstDrawLogged = false;
        MainHook.log(TAG + " producer geometry changed; rebinding PassBlur");
        bindProducerWhenReady(0);
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
            if (configRotation == 1 || configRotation == 3) {
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
                    configRotation, rootSurface);
        } catch (Throwable error) {
            MainHook.log(TAG + " producer geometry unavailable: " + error);
            return null;
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

    private void resetInputForNewGlContext() {
        Miuix307PassBlurBridge.Binding stale = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(stale);
        resetBoundGeometry();
        gpuBackdropActive = false;
        activationExhausted = false;
        hasConsumedFrame = false;
        frameAvailable.set(false);
        firstFrameLogged = false;
        firstDrawLogged = false;

        Surface currentProducer = producerSurface;
        producerSurface = null;
        if (currentProducer != null) currentProducer.release();
        SurfaceTexture currentTexture = surfaceTexture;
        surfaceTexture = null;
        if (currentTexture != null) currentTexture.release();
        program = 0;
        oesTexture = 0;
    }

    private void resetBoundGeometry() {
        boundSurfaceWidth = 0;
        boundSurfaceHeight = 0;
        boundConfigRotation = -1;
        boundOutputSurface = null;
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
