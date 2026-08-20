from pathlib import Path

P = Path("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java")
s = P.read_text()


def once(old: str, new: str):
    global s
    if s.count(old) != 1:
        raise SystemExit(f"expected exactly one match, got {s.count(old)}: {old[:100]!r}")
    s = s.replace(old, new, 1)


def between(start: str, end: str, replacement: str):
    global s
    a = s.find(start)
    if a < 0:
        raise SystemExit(f"missing start: {start}")
    b = s.find(end, a)
    if b < 0:
        raise SystemExit(f"missing end: {end}")
    s = s[:a] + replacement + s[b:]


once(
    "import android.view.ViewTreeObserver;\n\nimport java.lang.ref.WeakReference;",
    "import android.view.ViewTreeObserver;\n\n"
    "import com.hellovoid.prismal.PrismalGeometry;\n"
    "import com.hellovoid.prismal.PrismalParams;\n"
    "import com.hellovoid.prismal.PrismalRenderer;\n"
    "import com.hellovoid.prismal.PrismalSampling;\n\n"
    "import java.lang.ref.WeakReference;"
)

once(
    "    private volatile Miuix307PrismalMaterial.Params opticalParams;\n",
    "    private volatile Miuix307PrismalMaterial.Params opticalParams;\n"
    "    private volatile PrismalParams portablePrismalParams;\n"
)

once(
    "    private int normalizeProgram;\n"
    "    private int blurProgram;\n"
    "    private int materialProgram;\n"
    "    private int oesTexture;",
    "    private int normalizeProgram;\n"
    "    private int compositeProgram;\n"
    "    private PrismalRenderer prismalRenderer;\n"
    "    private int oesTexture;"
)

once(
    "    private int rawTexture;\n"
    "    private int rawFramebuffer;\n"
    "    private int blurTextureH;\n"
    "    private int blurFramebufferH;\n"
    "    private int blurTextureV;\n"
    "    private int blurFramebufferV;\n"
    "    private int fboWidth;\n"
    "    private int fboHeight;\n"
    "    private int blurWidth;\n"
    "    private int blurHeight;",
    "    private int rawTexture;\n"
    "    private int rawFramebuffer;\n"
    "    private int fboWidth;\n"
    "    private int fboHeight;"
)

once(
    "    private boolean stageBDiagnosticsLogged;\n",
    "    private boolean stageBDiagnosticsLogged;\n"
    "    private boolean prismalMappingLogged;\n"
)

once(
    "        opticalParams = Miuix307PrismalMaterial.defaults(\n"
    "                context.getResources().getDisplayMetrics().density);\n",
    "        opticalParams = Miuix307PrismalMaterial.defaults(\n"
    "                context.getResources().getDisplayMetrics().density);\n"
    "        portablePrismalParams = Miuix307PrismalAdapter.toPortable(opticalParams);\n"
)

once(
    "        opticalParams = Miuix307PrismalMaterial.fromConfig(\n"
    "                glassConfig, getResources().getDisplayMetrics().density);\n",
    "        opticalParams = Miuix307PrismalMaterial.fromConfig(\n"
    "                glassConfig, getResources().getDisplayMetrics().density);\n"
    "        portablePrismalParams = Miuix307PrismalAdapter.toPortable(opticalParams);\n"
)

# Reset the one-shot domain diagnostic whenever producer or mapping geometry changes.
s = s.replace(
    "        stageBDiagnosticsLogged = false;\n",
    "        stageBDiagnosticsLogged = false;\n        prismalMappingLogged = false;\n"
)

between(
    "    private void ensureGlResources() {",
    "    private void createInputProducer() {",
    '''    private void ensureGlResources() {
        if (normalizeProgram != 0 && compositeProgram != 0 && prismalRenderer != null
                && oesTexture != 0 && inputSurfaceTexture != null && inputProducerSurface != null) {
            return;
        }

        normalizeProgram = createProgram(
                Miuix307PassBlurShaders.QUAD_VERTEX,
                Miuix307PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        compositeProgram = createProgram(
                Miuix307PassBlurShaders.QUAD_VERTEX,
                Miuix307PrismalCompositeShaders.FRAGMENT);
        if (normalizeProgram == 0 || compositeProgram == 0) {
            throw new IllegalStateException("Prismal adapter program creation failed");
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer(getContext());

        createInputProducer();
        post(() -> bindProducerWhenReady(0));
    }

'''
)

between(
    "    private void ensureFboSize(int width, int height) {",
    "    private void drawLatestFrame(boolean fromFrameCallback) {",
    '''    private void ensureFboSize(int width, int height) {
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
        if (rawFramebuffer != 0 && fboWidth == nextWidth && fboHeight == nextHeight) return;

        releaseFbos();
        rawTexture = createTexture2D(nextWidth, nextHeight);
        rawFramebuffer = createFramebuffer(rawTexture);
        fboWidth = nextWidth;
        fboHeight = nextHeight;
    }

'''
)

once(
    "        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE\n"
    "                || normalizeProgram == 0 || blurProgram == 0 || materialProgram == 0\n"
    "                || oesTexture == 0) {",
    "        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE\n"
    "                || normalizeProgram == 0 || compositeProgram == 0 || prismalRenderer == null\n"
    "                || oesTexture == 0) {"
)

once(
    "            renderNormalizationPass();\n"
    "            renderBlurPasses();\n"
    "            renderMaterialPass();",
    "            renderNormalizationPass();\n"
    "            PrismalGeometry prismalGeometry = createPrismalGeometry();\n"
    "            int prismalTexture = prismalRenderer.render(\n"
    "                    rawTexture, prismalGeometry, portablePrismalParams);\n"
    "            renderCompositePass(prismalTexture);"
)

once(
    "                        + \" material=prismal-upstream\"\n"
    "                        + \" blur=two-pass-0.5x\"",
    "                        + \" material=prismal-module-official\"\n"
    "                        + \" blur=official-two-pass-0.5x\""
)

once(
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\n"
    "                stageBDiagnosticsLogged = true;\n"
    "        prismalMappingLogged = false;\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\n"
    "            }",
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\n"
    "                stageBDiagnosticsLogged = true;\n"
    "                prismalMappingLogged = false;\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\n"
    "            }\n"
    "            if (gpuBackdropActive && !prismalMappingLogged) {\n"
    "                prismalMappingLogged = true;\n"
    "                logPrismalMapping(prismalGeometry);\n"
    "            }"
)

between(
    "    private void renderBlurPasses() {",
    "    private void bindQuad(int program) {",
    '''    private PrismalGeometry createPrismalGeometry() {
        float glassWidth = Math.max(1f, dockUvWidth * fboWidth);
        float glassHeight = Math.max(1f, dockUvHeight * fboHeight);
        float centerX = (dockUvLeft + dockUvWidth * 0.5f) * fboWidth;
        float centerGlY = (dockUvBottom + dockUvHeight * 0.5f) * fboHeight;
        float centerYTop = fboHeight - centerGlY;

        float cornerRadiusPx = Math.max(1f, glassHeight * 0.44f);
        View materialHost = materialHostRef.get();
        if (materialHost != null) {
            float nativeRadius = MiuixGlassHook.readNativeOpticsRadius(materialHost);
            if (!Float.isNaN(nativeRadius) && !Float.isInfinite(nativeRadius) && nativeRadius > 0f) {
                cornerRadiusPx = nativeRadius;
            }
        }
        return new PrismalGeometry(
                fboWidth, fboHeight,
                centerX, centerYTop,
                glassWidth, glassHeight,
                cornerRadiusPx);
    }

    private void renderCompositePass(int prismalTexture) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, Math.max(1, outputWidth), Math.max(1, outputHeight));
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (producerCoverage == Miuix307BackdropMapping.Coverage.OUTSIDE
                || validDockRight <= validDockLeft || validDockTop <= validDockBottom) {
            return;
        }
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
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prismalTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                dockUvLeft, dockUvBottom, dockUvWidth, dockUvHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void logPrismalMapping(PrismalGeometry g) {
        float left = dockUvLeft;
        float bottom = dockUvBottom;
        float right = dockUvLeft + dockUvWidth;
        float top = dockUvBottom + dockUvHeight;
        float officialTop = 1f - top;
        float officialBottom = 1f - bottom;
        MainHook.log("[DC][PRISMAL-MAP] producer surface="
                + boundSurfaceWidth + "x" + boundSurfaceHeight
                + " buffer=" + boundBufferWidth + "x" + boundBufferHeight
                + " configRot=" + configRotation
                + " textureMatrix=" + formatTextureMatrix(textureMatrix));
        MainHook.log("[DC][PRISMAL-MAP] normalized output="
                + outputWidth + "x" + outputHeight
                + " rawFbo=" + fboWidth + "x" + fboHeight
                + " dockUvRect=[" + left + "," + bottom + "," + dockUvWidth + "," + dockUvHeight + "]"
                + " validDockRect=[" + validDockLeft + "," + validDockBottom + ","
                + validDockRight + "," + validDockTop + "] coverage=" + producerCoverage);
        MainHook.log("[DC][PRISMAL-MAP] prismal resolution="
                + g.framebufferWidth + "x" + g.framebufferHeight
                + " glassSize=" + g.glassWidth + "x" + g.glassHeight
                + " centerTopLeft=[" + g.centerX + "," + g.centerY + "]"
                + " u_mousePos=[" + g.centerX + "," + (g.framebufferHeight - g.centerY) + "]"
                + " input=standard-fbo-bottom-left adapter=official-bitmap-orientation");
        MainHook.log("[DC][PRISMAL-MAP] basis dock+X -> raw(+U) -> official(+U); "
                + "dock+Y(down) -> raw(-V) -> official(+V)");
        MainHook.log("[DC][PRISMAL-MAP] anchors "
                + "TL raw=[" + left + "," + top + "] official=[" + left + "," + officialTop + "] "
                + "TR raw=[" + right + "," + top + "] official=[" + right + "," + officialTop + "] "
                + "BL raw=[" + left + "," + bottom + "] official=[" + left + "," + officialBottom + "] "
                + "BR raw=[" + right + "," + bottom + "] official=[" + right + "," + officialBottom + "]");
    }

'''
)

between(
    "    private int blurSamplingGuardPx() {",
    "    private static int[] fitInsetPairToTextureLimit(",
    '''    private SamplingInsets resolveSamplingInsets(int width, int height) {
        int opticalX = PrismalSampling.requiredGuardPx(
                portablePrismalParams, width, height, true);
        int opticalY = PrismalSampling.requiredGuardPx(
                portablePrismalParams, width, height, false);

        int left = Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX);
        int right = Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX);
        int top = Math.max(Math.max(0, topOverscanPx), opticalY);
        int bottom = Math.max(Math.max(0, bottomOverscanPx), opticalY);

        int[] horizontal = fitInsetPairToTextureLimit(width, left, right, maxTextureSize);
        int[] vertical = fitInsetPairToTextureLimit(height, top, bottom, maxTextureSize);
        return new SamplingInsets(horizontal[0], horizontal[1], vertical[0], vertical[1]);
    }

'''
)

between(
    "    private void releaseFbos() {",
    "    private void makeCurrent() {",
    '''    private void releaseFbos() {
        if (rawFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{rawFramebuffer}, 0);
            rawFramebuffer = 0;
        }
        if (rawTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{rawTexture}, 0);
            rawTexture = 0;
        }
        fboWidth = 0;
        fboHeight = 0;
    }

'''
)

between(
    "    private void releaseRenderResources() {",
    "    private void resetBoundGeometry() {",
    '''    private void releaseRenderResources() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY
                    && eglWindowSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) {
                makeCurrent();
            }
        } catch (Throwable ignored) {}

        try { releaseFbos(); } catch (Throwable ignored) {}
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (oesTexture != 0) {
            try { GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0); } catch (Throwable ignored) {}
            oesTexture = 0;
        }
        int[] programs = new int[]{normalizeProgram, compositeProgram};
        for (int program : programs) {
            if (program != 0) {
                try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) {}
            }
        }
        normalizeProgram = 0;
        compositeProgram = 0;

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

'''
)

# Remove now-owned-by-module local blur constants from the Dock adapter.
once("    private static final float BLUR_FBO_SCALE = 0.5f;\n", "")
once("    private static final int BLUR_KERNEL_RADIUS_TEXELS = 15;\n", "")

P.write_text(s)
print("patched", P)
