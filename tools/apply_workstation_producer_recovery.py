from pathlib import Path

root = Path("src/main/java/com/hellovoid/liquiddock")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


# Restore the two device-validated lifecycle recovery boundaries without restoring screen capture.
pipeline = root / "Miuix307MaterialPipeline.java"
replace_once(
    pipeline,
    """            installCompatBackgroundBlurSuppression(classLoader);\n            installDockCustomizationCompatibility(classLoader, config);\n\n            HookUtil.hookMethod(classLoader,""",
    """            installCompatBackgroundBlurSuppression(classLoader);\n            installDockCustomizationCompatibility(classLoader, config);\n            installHotSeatsAttachRecovery(classLoader, config);\n            installWorkstationResumeProducerRecovery(classLoader);\n\n            HookUtil.hookMethod(classLoader,""",
)

marker = """    private static Class<?> loadOptionalClass(ClassLoader classLoader, String name) {\n"""
methods = """    /**
     * HotSeats is the stable lifecycle owner across both default MiuiX and themed material
     * implementations. Recover the active material at the concrete HotSeats attach boundary.
     */
    private static void installHotSeatsAttachRecovery(
            ClassLoader classLoader, LiquidDockConfig config) {
        try {
            Class<?> hotSeatsClass = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, classLoader);
            Method attach = hotSeatsClass.getDeclaredMethod("onAttachedToWindow");
            HookUtil.hook(attach, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) return result;
                Object hotSeats = chain.getThisObject();
                hotSeatsRef = new WeakReference<>(hotSeats);
                View background = resolveBackground(hotSeats);
                if (background == null) {
                    MainHook.log("[DC] MiuiX 307 HotSeats attach recovery: background not ready");
                    return result;
                }
                if (!ensureGlassBound(background, config, classLoader)) {
                    MainHook.log("[DC] MiuiX 307 HotSeats attach recovery deferred");
                    return result;
                }
                MiuixGlassHook.syncSize(background);
                MiuixGlassHook.syncGeometry(background, config);
                MainHook.log("[DC] MiuiX 307 HotSeats attach recovery complete class="
                        + background.getClass().getSimpleName());
                return result;
            });
            MainHook.log("[DC] MiuiX 307 HotSeats attach recovery installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 HotSeats attach recovery unavailable: " + error);
        }
    }

    /**
     * A fullscreen workstation app can disconnect SurfaceFlinger's PassBlur producer while the
     * Java TextureView hierarchy remains attached. Launcher.onResume is the device-verified
     * recovery boundary: replace only the producer, preserving the current glass hierarchy.
     */
    private static void installWorkstationResumeProducerRecovery(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = Class.forName(
                    "com.miui.home.launcher.Launcher", false, classLoader);
            Method resume = launcherClass.getDeclaredMethod("onResume");
            HookUtil.hook(resume, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.isWorkstationMode()) {
                    Miuix307ZeroCopyRenderer.rebindProducer("workstation-launcher-resume");
                }
                return result;
            });
            MainHook.log("[DC] MiuiX 307 workstation resume producer recovery installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 workstation resume producer recovery unavailable: "
                    + error);
        }
    }

"""
replace_once(pipeline, marker, methods + marker)

# Add a producer-only recovery entry point to the zero-copy owner.
renderer = root / "Miuix307ZeroCopyRenderer.java"
replace_once(
    renderer,
    """    static void clear() {\n""",
    """    static void rebindProducer(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.rebindProducer(reason);
    }

    static void clear() {
""",
)

# Replace the stale SurfaceFlinger BufferQueue producer, not merely the Java Binding object.
view = root / "Miuix307PassBlurTextureView.java"
replace_once(
    view,
    """    private volatile boolean hasConsumedFrame;\n    private volatile int configRotation;\n""",
    """    private volatile boolean hasConsumedFrame;
    private volatile boolean producerRebindPending;
    private volatile int configRotation;
""",
)

replace_once(
    view,
    """    void shutdown() {\n        if (shuttingDown) return;\n        shuttingDown = true;\n        gpuBackdropActive = false;\n""",
    """    /**
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
""",
)

old_input = """        int[] textures = new int[1];
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
        post(() -> bindProducerWhenReady(0));
    }
"""
new_input = """        createInputProducer();
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
"""
replace_once(view, old_input, new_input)

replace_once(
    view,
    """        binding = next;\n        configRotation = current.configRotation;\n""",
    """        binding = next;
        producerRebindPending = false;
        configRotation = current.configRotation;
""",
)
replace_once(
    view,
    """        if (attempt >= MAX_BIND_RETRY_FRAMES) {\n            activationExhausted = true;\n            MainHook.log(TAG + " PassBlur TextureView activation exhausted reason=" + reason);\n""",
    """        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            activationExhausted = true;
            producerRebindPending = false;
            MainHook.log(TAG + " PassBlur TextureView activation exhausted reason=" + reason);
""",
)
replace_once(
    view,
    """        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) return;\n        if (!isSameSurface(binding.rootSurface, geometry.rootSurface)) return;\n""",
    """        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) return;
        if (!binding.rootSurface.isValid()
                || !isSameSurface(binding.rootSurface, geometry.rootSurface)) {
            rebindProducer("producer-root-changed");
            return;
        }
""",
)

# Guard the zero-copy-only architecture: no legacy screen-capture implementation may return.
all_java = "\n".join(p.read_text(errors="ignore") for p in root.glob("*.java"))
for token in ("captureScreenAsync(", "class DockLiquidGlassView", "class LiveScreenCapture"):
    if token in all_java:
        raise SystemExit(f"retired capture backend token restored: {token}")
