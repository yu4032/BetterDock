package com.hellovoid.liquiddock;

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
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalInteractionState;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import com.hellovoid.prismal.PrismalSampling;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One zero-copy PassBlur/OES/Prismal renderer for all glass nodes in one Launcher ViewRoot.
 * Sink views own only output surfaces; producer, EGL context and backdrop textures live here.
 */
final class LauncherGlassSession {
    private static final String TAG = "[DC][LauncherGlass]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
    private static final AtomicInteger NEXT_SESSION_ID = new AtomicInteger(1);
    private static final AtomicInteger NEXT_NODE_ID = new AtomicInteger(1);
    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class ProducerGeometry {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int configRotation;
        final SurfaceControl rootSurface;

        ProducerGeometry(int surfaceWidth, int surfaceHeight, int bufferWidth, int bufferHeight,
                         int configRotation, SurfaceControl rootSurface) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.configRotation = configRotation;
            this.rootSurface = rootSurface;
        }
    }

    private static final class NodeState {
        final int id = NEXT_NODE_ID.getAndIncrement();
        final WeakReference<LauncherGlassSinkView> sinkRef;
        volatile LauncherGlassGeometry.Snapshot geometry;
        volatile PrismalInteractionState interaction = PrismalInteractionState.IDLE;

        NodeState(LauncherGlassSinkView sink) {
            sinkRef = new WeakReference<>(sink);
        }
    }

    private static final class OutputState {
        final Surface surface;
        EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
        int width;
        int height;

        OutputState(Surface surface, int width, int height) {
            this.surface = surface;
            this.width = width;
            this.height = height;
        }
    }

    private final int sessionId = NEXT_SESSION_ID.getAndIncrement();
    private final WeakReference<View> rootRef;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final Handler mainHandler;
    private final FloatBuffer quadBuffer;
    private final LauncherGlassFramePolicy framePolicy = new LauncherGlassFramePolicy();
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final float[] textureMatrix = new float[16];
    private final Map<LauncherGlassSinkView, NodeState> nodes =
            Collections.synchronizedMap(new WeakHashMap<>());
    // EGL surfaces are created/destroyed only on renderHandler.
    private final Map<LauncherGlassSinkView, OutputState> outputs = new WeakHashMap<>();

    private volatile boolean shuttingDown;
    private volatile PrismalParams prismalParams;
    private volatile PrismalHighlightProfile launcherHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile int rootWidth;
    private volatile int rootHeight;
    private volatile int configRotation;
    private volatile int boundBufferWidth;
    private volatile int boundBufferHeight;
    private volatile Miuix307PassBlurBridge.Binding binding;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile LauncherGlassGpuAtlas.Layout atlasLayout;
    private volatile boolean hasConsumedFrame;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglPbufferSurface = EGL14.EGL_NO_SURFACE;
    private int normalizeProgram;
    private int compositeProgram;
    private int oesTexture;
    private int rawTexture;
    private int rawFramebuffer;
    private int rawWidth;
    private int rawHeight;
    private volatile int maxTextureSize;
    private PrismalRenderer prismalRenderer;
    private ViewTreeObserver rootObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private final View.OnAttachStateChangeListener rootAttachListener =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { installRootObserver(); }
                @Override public void onViewDetachedFromWindow(View v) {
                    // Only the stable Launcher root owns the session. A folder/material reparent
                    // never reaches this listener. Defer one main-loop turn so transient root
                    // reattachment cannot kill a session that is already coming back.
                    mainHandler.post(() -> {
                        View root = rootRef.get();
                        if (root == v && !v.isAttachedToWindow()) shutdown();
                    });
                }
            };

    LauncherGlassSession(View root, LiquidDockConfig.Glass glassConfig) {
        rootRef = new WeakReference<>(root);
        rootWidth = Math.max(0, root.getWidth());
        rootHeight = Math.max(0, root.getHeight());
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadBuffer.put(QUAD).position(0);
        renderThread = new HandlerThread("LiquidDock-LauncherGlass-EGL");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        mainHandler = new Handler(root.getContext().getMainLooper());
        setGlassConfig(glassConfig);
        root.addOnAttachStateChangeListener(rootAttachListener);
        installRootObserver();
        MainHook.log(TAG + " " + debugLabel() + " created source=PassBlur-wallpaper-only");
    }

    boolean isShutdown() { return shuttingDown; }

    boolean ownsRoot(View root) {
        return root != null && rootRef.get() == root;
    }

    String debugLabel() {
        View root = rootRef.get();
        String name = root != null ? root.getClass().getSimpleName() : "released-root";
        int width = root != null ? root.getWidth() : rootWidth;
        int height = root != null ? root.getHeight() : rootHeight;
        return "session#" + sessionId + " root=" + name + "@"
                + Integer.toHexString(root != null ? System.identityHashCode(root) : 0)
                + " size=" + width + "x" + height;
    }

    void setGlassConfig(LiquidDockConfig.Glass glassConfig) {
        View root = rootRef.get();
        float density = root != null ? root.getResources().getDisplayMetrics().density : 1f;
        Miuix307PrismalMaterial.Params optical = glassConfig != null
                ? Miuix307PrismalMaterial.fromConfig(glassConfig, density)
                : Miuix307PrismalMaterial.defaults(density);
        prismalParams = Miuix307PrismalAdapter.toPortable(optical);
        launcherHighlightProfile = glassConfig != null
                ? glassConfig.launcherHighlightProfile
                : PrismalHighlightProfile.ALL_ENABLED;
        mainHandler.post(this::syncSceneOnUiThread);
        requestFrame(false);
    }

    void registerSink(LauncherGlassSinkView sink) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            if (!nodes.containsKey(sink)) nodes.put(sink, new NodeState(sink));
        }
        syncSceneOnUiThread();
        requestLifecycleRefresh();
    }

    void unregisterSink(LauncherGlassSinkView sink) {
        if (sink == null) return;
        synchronized (nodes) { nodes.remove(sink); }
        requestLifecycleRefresh();
    }

    void updateInteraction(LauncherGlassSinkView sink, PrismalInteractionState interaction) {
        if (sink == null || shuttingDown) return;
        synchronized (nodes) {
            NodeState node = nodes.get(sink);
            if (node == null) return;
            node.interaction = interaction != null ? interaction : PrismalInteractionState.IDLE;
        }
        // Interaction redraws reuse the last consumed wallpaper texture. Never request producer refresh.
        requestFrame(false);
    }

    void requestLifecycleRefresh() {
        if (shuttingDown) return;
        requestFrame(false);
    }

    void attachOutput(LauncherGlassSinkView sink, Surface surface, int width, int height) {
        if (sink == null || surface == null) return;
        if (shuttingDown || !renderThread.isAlive()) {
            surface.release();
            return;
        }
        postRender(() -> {
            if (shuttingDown) {
                surface.release();
                return;
            }
            try {
                ensureEglAndGl();
                OutputState previous = outputs.remove(sink);
                releaseOutput(previous);
                OutputState next = new OutputState(surface, width, height);
                next.eglSurface = EGL14.eglCreateWindowSurface(
                        eglDisplay, eglConfig, surface, new int[]{EGL14.EGL_NONE}, 0);
                checkEgl("eglCreateWindowSurface", next.eglSurface != EGL14.EGL_NO_SURFACE);
                outputs.put(sink, next);
                requestFrame(false);
            } catch (Throwable error) {
                MainHook.log(TAG + " attach output failed " + debugLabel() + ": " + error);
                surface.release();
            }
        }, surface::release);
    }

    void resizeOutput(LauncherGlassSinkView sink, int width, int height) {
        if (sink == null || shuttingDown || !renderThread.isAlive()) return;
        postRender(() -> {
            OutputState output = outputs.get(sink);
            if (output != null) {
                output.width = Math.max(1, width);
                output.height = Math.max(1, height);
                requestFrame(false);
            }
        }, null);
    }

    void detachOutput(LauncherGlassSinkView sink, Surface surface) {
        if (sink == null || shuttingDown || !renderThread.isAlive()) {
            if (surface != null) surface.release();
            return;
        }
        postRender(() -> {
            OutputState output = outputs.remove(sink);
            if (output != null) releaseOutput(output);
            else if (surface != null) surface.release();
        }, () -> { if (surface != null) surface.release(); });
    }

    private void installRootObserver() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        removeRootObserver();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            syncSceneOnUiThread();
            return true;
        };
        observer.addOnPreDrawListener(listener);
        rootObserver = observer;
        preDrawListener = listener;
    }

    private void removeRootObserver() {
        ViewTreeObserver observer = rootObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        rootObserver = null;
        preDrawListener = null;
        if (observer != null && listener != null) {
            try { if (observer.isAlive()) observer.removeOnPreDrawListener(listener); }
            catch (Throwable ignored) {}
        }
    }

    private void syncSceneOnUiThread() {
        if (shuttingDown) return;
        View root = rootRef.get();
        if (root == null) return;
        int nextWidth = root.getWidth();
        int nextHeight = root.getHeight();
        boolean changed = nextWidth > 0 && nextHeight > 0
                && (nextWidth != rootWidth || nextHeight != rootHeight);
        if (nextWidth > 0) rootWidth = nextWidth;
        if (nextHeight > 0) rootHeight = nextHeight;

        List<NodeState> snapshot;
        synchronized (nodes) { snapshot = new ArrayList<>(nodes.values()); }
        for (NodeState node : snapshot) {
            LauncherGlassSinkView sink = node.sinkRef.get();
            if (sink == null) continue;
            changed |= sink.syncFromMaterial();
            LauncherGlassGeometry.Snapshot next = sink.captureGeometry(root);
            LauncherGlassGeometry.Snapshot old = node.geometry;
            if ((old == null) != (next == null) || (old != null && !old.sameAs(next))) {
                node.geometry = next;
                changed = true;
            }
        }
        changed |= refreshProducerGeometryOnUi(root);
        changed |= rebuildAtlasLayout(root);
        if (changed) {
            requestFrame(false);
        }
    }

    private boolean rebuildAtlasLayout(View root) {
        PrismalParams params = prismalParams;
        if (root == null || params == null || rootWidth <= 0 || rootHeight <= 0) return false;
        List<NodeState> snapshot;
        synchronized (nodes) { snapshot = new ArrayList<>(nodes.values()); }
        ArrayList<LauncherGlassGpuAtlas.Request> requests = new ArrayList<>();
        for (NodeState node : snapshot) {
            LauncherGlassGeometry.Snapshot geometry = node.geometry;
            if (geometry == null) continue;
            int guardX = PrismalSampling.requiredGuardPx(
                    params, geometry.width, geometry.height, true);
            int guardY = PrismalSampling.requiredGuardPx(
                    params, geometry.width, geometry.height, false);
            int left = Math.max(0, (int) Math.floor(geometry.left - guardX));
            int top = Math.max(0, (int) Math.floor(geometry.top - guardY));
            int right = Math.min(rootWidth,
                    (int) Math.ceil(geometry.left + geometry.width + guardX));
            int bottom = Math.min(rootHeight,
                    (int) Math.ceil(geometry.top + geometry.height + guardY));
            if (right <= left || bottom <= top) continue;
            requests.add(new LauncherGlassGpuAtlas.Request(
                    node.id, left, top, right - left, bottom - top,
                    geometry.left, geometry.top, geometry.width, geometry.height,
                    geometry.cornerRadius));
        }
        int limit = maxTextureSize > 0 ? maxTextureSize
                : Math.max(4096, Math.max(rootWidth, rootHeight));
        LauncherGlassGpuAtlas.Layout next = LauncherGlassGpuAtlas.pack(requests, limit);
        LauncherGlassGpuAtlas.Layout old = atlasLayout;
        atlasLayout = next;
        return old == null ? next != null : !old.sameAs(next);
    }

    private boolean refreshProducerGeometryOnUi(View root) {
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null) return false;
        int nextRotation = geometry.configRotation;
        boolean changed = nextRotation != configRotation
                || geometry.bufferWidth != boundBufferWidth
                || geometry.bufferHeight != boundBufferHeight;
        configRotation = nextRotation;
        if (changed) {
            boundBufferWidth = geometry.bufferWidth;
            boundBufferHeight = geometry.bufferHeight;
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
                postRender(() -> {
                    if (!shuttingDown && input == inputSurfaceTexture) {
                        input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                    }
                }, null);
            }
        }
        Miuix307PassBlurBridge.Binding current = binding;
        if (current != null && (!current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface))) {
            rebindProducer();
            return true;
        }
        return changed;
    }

    private boolean postRender(Runnable action, Runnable rejected) {
        if (action == null || shuttingDown || !renderThread.isAlive()) {
            if (rejected != null) rejected.run();
            return false;
        }
        try {
            boolean accepted = renderHandler.post(action);
            if (!accepted && rejected != null) rejected.run();
            return accepted;
        } catch (Throwable error) {
            MainHook.log(TAG + " render queue rejected " + debugLabel() + ": " + error);
            if (rejected != null) rejected.run();
            return false;
        }
    }

    private void requestFrame(boolean refreshProducer) {
        if (shuttingDown) return;
        if (framePolicy.request(refreshProducer)) postRender(this::drainFrameWork, null);
    }

    private void drainFrameWork() {
        if (shuttingDown) return;
        LauncherGlassFramePolicy.Work work = framePolicy.consume();
        if (!work.render) return;
        try {
            ensureEglAndGl();
            if (work.refreshProducer) refreshProducer();
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && frameAvailable.getAndSet(false)) {
                makePbufferCurrent();
                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                hasConsumedFrame = true;
            }
            if (!hasConsumedFrame) return;
            renderScene();
        } catch (Throwable error) {
            MainHook.log(TAG + " render failed: " + error);
        }
    }

    private void refreshProducer() {
        Miuix307PassBlurBridge.Binding current = binding;
        if (current == null || !current.bound || !current.rootSurface.isValid()) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            current.setUpdateTextureFlag.invoke(
                    transaction, current.rootSurface, Boolean.TRUE, Float.valueOf(current.scale));
            transaction.apply();
        } catch (Throwable error) {
            MainHook.log(TAG + " PassBlur refresh failed: " + error);
        }
    }

    private void ensureEglAndGl() {
        ensureEgl();
        makePbufferCurrent();
        if (normalizeProgram == 0) {
            normalizeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        }
        if (compositeProgram == 0) {
            compositeProgram = createProgram(
                    Miuix307PassBlurShaders.QUAD_VERTEX,
                    Miuix307PrismalCompositeShaders.FRAGMENT);
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();
        if (oesTexture == 0 || inputSurfaceTexture == null || inputProducerSurface == null) {
            createInputProducer();
        }
        if (maxTextureSize <= 0) {
            int[] size = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, size, 0);
            maxTextureSize = Math.max(1, size[0]);
            mainHandler.post(this::syncSceneOnUiThread);
        }
    }

    private void ensureEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null && eglPbufferSurface != EGL14.EGL_NO_SURFACE) return;
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEgl("eglGetDisplay", display != EGL14.EGL_NO_DISPLAY);
        int[] version = new int[2];
        checkEgl("eglInitialize", EGL14.eglInitialize(display, version, 0, version, 1));
        int[] attrs = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        checkEgl("eglChooseConfig", EGL14.eglChooseConfig(
                display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0);
        EGLContext context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreateContext", context != EGL14.EGL_NO_CONTEXT);
        EGLSurface pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0], new int[]{
                EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        checkEgl("eglCreatePbufferSurface", pbuffer != EGL14.EGL_NO_SURFACE);
        eglDisplay = display;
        eglConfig = configs[0];
        eglContext = context;
        eglPbufferSurface = pbuffer;
    }

    private void createInputProducer() {
        makePbufferCurrent();
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        oesTexture = textures[0];
        if (oesTexture == 0) throw new IllegalStateException("OES texture=0");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        SurfaceTexture input = new SurfaceTexture(oesTexture);
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture) return;
            frameAvailable.set(true);
            requestFrame(false);
        }, renderHandler);
        mainHandler.post(() -> bindProducerWhenReady(0));
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null) return;
        View root = rootRef.get();
        Surface producer = inputProducerSurface;
        SurfaceTexture input = inputSurfaceTexture;
        if (root == null || !root.isAttachedToWindow() || producer == null || input == null) {
            retryBind(attempt);
            return;
        }
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryBind(attempt);
            return;
        }
        postRender(() -> {
            if (shuttingDown || input != inputSurfaceTexture) return;
            input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
            mainHandler.post(() -> finishBind(root, producer, geometry, attempt));
        }, null);
    }

    private void finishBind(View root, Surface producer, ProducerGeometry geometry, int attempt) {
        if (shuttingDown || binding != null || producer != inputProducerSurface) return;
        Miuix307PassBlurBridge.Binding next = Miuix307PassBlurBridge.bind(root, producer, 1f);
        if (next == null) {
            retryBind(attempt);
            return;
        }
        binding = next;
        configRotation = geometry.configRotation;
        boundBufferWidth = geometry.bufferWidth;
        boundBufferHeight = geometry.bufferHeight;
        MainHook.log(TAG + " shared PassBlur producer bound " + debugLabel()
                + " surface=" + next.rootName + " buffer="
                + geometry.bufferWidth + "x" + geometry.bufferHeight);
        requestFrame(true);
    }

    private void retryBind(int attempt) {
        if (shuttingDown || binding != null || attempt >= MAX_BIND_RETRY_FRAMES) return;
        View root = rootRef.get();
        if (root != null) root.postOnAnimation(() -> bindProducerWhenReady(attempt + 1));
    }

    private void rebindProducer() {
        if (shuttingDown) return;
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        mainHandler.post(() -> bindProducerWhenReady(0));
    }

    private void renderScene() {
        PrismalParams params = prismalParams;
        LauncherGlassGpuAtlas.Layout layout = atlasLayout;
        if (layout == null || layout.width <= 0 || layout.height <= 0
                || params == null || outputs.isEmpty()) return;
        makePbufferCurrent();
        ensureRawTarget(layout.width, layout.height);
        renderNormalization(layout);
        prismalRenderer.prepareBackdrop(rawTexture, layout.width, layout.height, params);
        prismalRenderer.beginGlassFrame();

        List<NodeState> snapshot;
        synchronized (nodes) { snapshot = new ArrayList<>(nodes.values()); }
        java.util.HashMap<Integer, NodeState> byId = new java.util.HashMap<>();
        for (NodeState node : snapshot) byId.put(node.id, node);

        boolean drew = false;
        java.util.HashMap<Integer, LauncherGlassGeometry.Snapshot> atlasGeometries =
                new java.util.HashMap<>();
        for (LauncherGlassGpuAtlas.Tile tile : layout.tiles) {
            NodeState node = byId.get(tile.request.id);
            if (node == null || node.sinkRef.get() == null) continue;
            float left = tile.glassAtlasLeft();
            float top = tile.glassAtlasTop();
            LauncherGlassGeometry.Snapshot geometry = LauncherGlassGeometry.resolve(
                    layout.width, layout.height, left, top,
                    left + tile.request.glassWidth, top + tile.request.glassHeight,
                    tile.request.cornerRadius);
            if (geometry == null) continue;
            atlasGeometries.put(tile.request.id, geometry);
            PrismalGeometry prismalGeometry = new PrismalGeometry(
                    layout.width, layout.height, geometry.centerX, geometry.centerY,
                    geometry.width, geometry.height, geometry.cornerRadius);
            prismalRenderer.drawGlass(
                    prismalGeometry, params, launcherHighlightProfile, node.interaction);
            drew = true;
        }
        if (!drew) return;

        int sceneTexture = prismalRenderer.outputTexture();
        for (Map.Entry<LauncherGlassSinkView, OutputState> entry
                : new ArrayList<>(outputs.entrySet())) {
            NodeState node = nodes.get(entry.getKey());
            if (node == null) continue;
            LauncherGlassGeometry.Snapshot geometry = atlasGeometries.get(node.id);
            if (geometry == null) continue;
            present(sceneTexture, geometry, entry.getValue());
        }
    }

    private void renderNormalization(LauncherGlassGpuAtlas.Layout layout) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rawFramebuffer);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glViewport(0, 0, layout.width, layout.height);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(requireUniform(normalizeProgram, "uTexMatrix"),
                1, false, textureMatrix, 0);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), configRotation);
        GLES20.glUniform4f(requireUniform(normalizeProgram, "uValidDockRect"), 0f, 0f, 1f, 1f);
        for (LauncherGlassGpuAtlas.Tile tile : layout.tiles) {
            LauncherGlassGpuAtlas.Request request = tile.request;
            int viewportY = layout.height - tile.atlasBottom();
            GLES20.glViewport(tile.atlasLeft, viewportY,
                    request.sourceWidth, request.sourceHeight);
            float left = request.sourceLeft / (float) Math.max(1, rootWidth);
            float bottom = (rootHeight - (request.sourceTop + request.sourceHeight))
                    / (float) Math.max(1, rootHeight);
            float width = request.sourceWidth / (float) Math.max(1, rootWidth);
            float height = request.sourceHeight / (float) Math.max(1, rootHeight);
            GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"),
                    left, bottom, width, height);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }
        unbindQuad(normalizeProgram);
    }

    private void present(int sceneTexture, LauncherGlassGeometry.Snapshot geometry,
                         OutputState output) {
        if (output == null || output.eglSurface == EGL14.EGL_NO_SURFACE
                || output.width <= 0 || output.height <= 0) return;
        makeCurrent(output.eglSurface);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, output.width, output.height);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                geometry.cropLeft, geometry.cropBottom, geometry.cropWidth, geometry.cropHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        if (!EGL14.eglSwapBuffers(eglDisplay, output.eglSurface)) {
            throw new IllegalStateException("eglSwapBuffers error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void ensureRawTarget(int width, int height) {
        if (width <= 0 || height <= 0 || (maxTextureSize > 0
                && (width > maxTextureSize || height > maxTextureSize))) {
            throw new IllegalStateException("launcher glass FBO size invalid " + width + "x" + height);
        }
        if (rawFramebuffer != 0 && rawWidth == width && rawHeight == height) return;
        releaseRawTarget();
        rawTexture = createTexture2D(width, height);
        rawFramebuffer = createFramebuffer(rawTexture);
        rawWidth = width;
        rawHeight = height;
    }

    private void makePbufferCurrent() { makeCurrent(eglPbufferSurface); }

    private void makeCurrent(EGLSurface surface) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglContext == EGL14.EGL_NO_CONTEXT
                || surface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL surface unavailable");
        }
        checkEgl("eglMakeCurrent", EGL14.eglMakeCurrent(
                eglDisplay, surface, surface, eglContext));
    }

    private void releaseOutput(OutputState output) {
        if (output == null) return;
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && output.eglSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, output.eglSurface); } catch (Throwable ignored) {}
            output.eglSurface = EGL14.EGL_NO_SURFACE;
        }
        try { output.surface.release(); } catch (Throwable ignored) {}
    }

    private void releaseRawTarget() {
        if (rawFramebuffer != 0) GLES20.glDeleteFramebuffers(1, new int[]{rawFramebuffer}, 0);
        if (rawTexture != 0) GLES20.glDeleteTextures(1, new int[]{rawTexture}, 0);
        rawFramebuffer = 0;
        rawTexture = 0;
        rawWidth = 0;
        rawHeight = 0;
    }

    void shutdown() {
        if (shuttingDown) return;
        MainHook.log(TAG + " shutdown " + debugLabel());
        shuttingDown = true;
        View root = rootRef.get();
        removeRootObserver();
        if (root != null) {
            try { root.removeOnAttachStateChangeListener(rootAttachListener); } catch (Throwable ignored) {}
            LauncherGlassSessionRegistry.forget(root, this);
        }
        Miuix307PassBlurBridge.Binding old = binding;
        binding = null;
        Miuix307PassBlurBridge.unbind(old);
        if (renderThread.isAlive()) {
            try {
                if (!renderHandler.post(this::releaseGl)) {
                    MainHook.log(TAG + " release queue rejected " + debugLabel());
                }
            } catch (Throwable error) {
                MainHook.log(TAG + " release queue unavailable " + debugLabel() + ": " + error);
            }
            renderThread.quitSafely();
        }
    }

    private void releaseGl() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglPbufferSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) makePbufferCurrent();
        } catch (Throwable ignored) {}
        for (OutputState output : new ArrayList<>(outputs.values())) releaseOutput(output);
        outputs.clear();
        try { releaseRawTarget(); } catch (Throwable ignored) {}
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (normalizeProgram != 0) GLES20.glDeleteProgram(normalizeProgram);
        if (compositeProgram != 0) GLES20.glDeleteProgram(compositeProgram);
        normalizeProgram = compositeProgram = 0;
        if (oesTexture != 0) GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
        oesTexture = 0;
        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        if (producer != null) producer.release();
        SurfaceTexture input = inputSurfaceTexture;
        inputSurfaceTexture = null;
        if (input != null) {
            try { input.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}
            input.release();
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglPbufferSurface != EGL14.EGL_NO_SURFACE) {
            try { EGL14.eglDestroySurface(eglDisplay, eglPbufferSurface); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try { EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        }
        eglPbufferSurface = EGL14.EGL_NO_SURFACE;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    private ProducerGeometry readSurfaceGeometry(View root) {
        try {
            Object viewRoot = getViewRootImpl(root);
            if (viewRoot == null) return null;
            Field sizeField = findField(viewRoot.getClass(), "mSurfaceSize");
            sizeField.setAccessible(true);
            Object sizeValue = sizeField.get(viewRoot);
            if (!(sizeValue instanceof Point)) return null;
            Point surfaceSize = (Point) sizeValue;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;
            int rotation = readConfigRotation(root);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (rotation == 1 || rotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }
            Method method = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            method.setAccessible(true);
            Object value = method.invoke(viewRoot);
            SurfaceControl surfaceControl = value instanceof SurfaceControl
                    ? (SurfaceControl) value : null;
            return new ProducerGeometry(surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight, rotation, surfaceControl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int readConfigRotation(View view) {
        Display display = view != null ? view.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        try {
            Method method = Display.class.getMethod("getInstallOrientation");
            Object value = method.invoke(display);
            if (value instanceof Number) installOrientation = ((Number) value).intValue();
        } catch (Throwable ignored) {}
        int result = (installOrientation + display.getRotation()) % 4;
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
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
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

    private void bindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) throw new IllegalStateException("quad attribute unavailable");
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false,
                4 * Float.BYTES, quadBuffer);
    }

    private void unbindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private static int createTexture2D(int width, int height) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int texture = ids[0];
        if (texture == 0) throw new IllegalStateException("texture=0");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer incomplete=0x" + Integer.toHexString(status));
        }
        return ids[0];
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("program link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("shader compile failed: " + log);
        }
        return shader;
    }

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing uniform " + name);
        return location;
    }

    private static void checkEgl(String stage, boolean ok) {
        if (!ok) throw new IllegalStateException(stage + " error=0x"
                + Integer.toHexString(EGL14.eglGetError()));
    }
}
