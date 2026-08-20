from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
VIEW = ROOT / "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"
TEST = ROOT / "src/test/java/com/hellovoid/liquiddock/PrismalModuleBoundaryContractTest.java"


def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"{label}: expected one match, found {n}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, replacement, label):
    out, n = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f"{label}: expected one regex match, found {n}")
    return out


text = VIEW.read_text(encoding="utf-8")

sampling_block = '''    private static final class SamplingInsets {
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
'''

snapshot_block = sampling_block + '''
    /** One immutable UI-thread mapping generation consumed atomically by the GL thread. */
    private static final class BackdropSnapshot {
        final int visibleWidth;
        final int visibleHeight;
        final int sampleWidth;
        final int sampleHeight;
        final int configRotation;
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final PrismalParams prismalParams;
        final float backdropX;
        final float backdropY;
        final float backdropW;
        final float backdropH;
        final float validSampleLeft;
        final float validSampleBottom;
        final float validSampleRight;
        final float validSampleTop;
        final float validDockLeft;
        final float validDockBottom;
        final float validDockRight;
        final float validDockTop;
        final float dockUvLeft;
        final float dockUvBottom;
        final float dockUvWidth;
        final float dockUvHeight;
        final Miuix307BackdropMapping.Coverage coverage;

        BackdropSnapshot(
                int visibleWidth, int visibleHeight,
                int sampleWidth, int sampleHeight,
                int configRotation,
                int surfaceWidth, int surfaceHeight,
                int bufferWidth, int bufferHeight,
                PrismalParams prismalParams,
                float backdropX, float backdropY, float backdropW, float backdropH,
                float validSampleLeft, float validSampleBottom,
                float validSampleRight, float validSampleTop,
                float validDockLeft, float validDockBottom,
                float validDockRight, float validDockTop,
                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                Miuix307BackdropMapping.Coverage coverage) {
            this.visibleWidth = visibleWidth;
            this.visibleHeight = visibleHeight;
            this.sampleWidth = sampleWidth;
            this.sampleHeight = sampleHeight;
            this.configRotation = configRotation;
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.prismalParams = prismalParams;
            this.backdropX = backdropX;
            this.backdropY = backdropY;
            this.backdropW = backdropW;
            this.backdropH = backdropH;
            this.validSampleLeft = validSampleLeft;
            this.validSampleBottom = validSampleBottom;
            this.validSampleRight = validSampleRight;
            this.validSampleTop = validSampleTop;
            this.validDockLeft = validDockLeft;
            this.validDockBottom = validDockBottom;
            this.validDockRight = validDockRight;
            this.validDockTop = validDockTop;
            this.dockUvLeft = dockUvLeft;
            this.dockUvBottom = dockUvBottom;
            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.coverage = coverage;
        }
    }
'''
text = replace_once(text, sampling_block, snapshot_block, "insert BackdropSnapshot")

text = replace_once(
    text,
    "    private volatile PrismalParams portablePrismalParams;\n",
    "    private volatile PrismalParams portablePrismalParams;\n"
    "    private volatile BackdropSnapshot backdropSnapshot;\n",
    "add snapshot field",
)

old_resolve = '''    private SamplingInsets resolveSamplingInsets(int width, int height) {
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
new_resolve = '''    private SamplingInsets resolveSamplingInsets(int width, int height) {
        return resolveSamplingInsets(width, height, portablePrismalParams);
    }

    private SamplingInsets resolveSamplingInsets(
            int width, int height, PrismalParams prismalParams) {
        if (prismalParams == null) return new SamplingInsets(0, 0, 0, 0);
        int opticalX = PrismalSampling.requiredGuardPx(
                prismalParams, width, height, true);
        int opticalY = PrismalSampling.requiredGuardPx(
                prismalParams, width, height, false);

        int left = Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX);
        int right = Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX);
        int top = Math.max(Math.max(0, topOverscanPx), opticalY);
        int bottom = Math.max(Math.max(0, bottomOverscanPx), opticalY);

        int[] horizontal = fitInsetPairToTextureLimit(width, left, right, maxTextureSize);
        int[] vertical = fitInsetPairToTextureLimit(height, top, bottom, maxTextureSize);
        return new SamplingInsets(horizontal[0], horizontal[1], vertical[0], vertical[1]);
    }
'''
text = replace_once(text, old_resolve, new_resolve, "snapshot-aware sampling")

# Make the FBO allocation accept the already-computed sample dimensions from a snapshot.
text = regex_once(
    text,
    r'''    private void ensureFboSize\(int width, int height\) \{.*?\n    \}\n\n    private void drawLatestFrame''',
    '''    private void ensureFboSize(int width, int height) {
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("FBO allocation before GL_MAX_TEXTURE_SIZE query");
        }
        if (width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalStateException("visible material exceeds GL_MAX_TEXTURE_SIZE "
                    + width + "x" + height + " max=" + maxTextureSize);
        }
        SamplingInsets insets = resolveSamplingInsets(width, height);
        ensureFboSizeExact(
                Math.max(1, width + insets.left + insets.right),
                Math.max(1, height + insets.top + insets.bottom));
    }

    private void ensureFboSizeExact(int nextWidth, int nextHeight) {
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("FBO allocation before GL_MAX_TEXTURE_SIZE query");
        }
        if (nextWidth <= 0 || nextHeight <= 0
                || nextWidth > maxTextureSize || nextHeight > maxTextureSize) {
            throw new IllegalStateException("sample FBO exceeds GL_MAX_TEXTURE_SIZE "
                    + nextWidth + "x" + nextHeight + " max=" + maxTextureSize);
        }
        if (rawFramebuffer != 0 && fboWidth == nextWidth && fboHeight == nextHeight) return;

        releaseFbos();
        rawTexture = createTexture2D(nextWidth, nextHeight);
        rawFramebuffer = createFramebuffer(rawTexture);
        fboWidth = nextWidth;
        fboHeight = nextHeight;
    }

    private void drawLatestFrame''',
    "split exact FBO allocator",
)

text = regex_once(
    text,
    r'''    private void drawLatestFrame\(boolean fromFrameCallback\) \{.*?\n    \}\n\n    private void renderNormalizationPass\(\)''',
    '''    private void drawLatestFrame(boolean fromFrameCallback) {
        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE
                || normalizeProgram == 0 || compositeProgram == 0 || prismalRenderer == null
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
            }
            if (!hasConsumedFrame) return;

            BackdropSnapshot mapping = backdropSnapshot;
            if (mapping == null
                    || mapping.visibleWidth != outputWidth
                    || mapping.visibleHeight != outputHeight
                    || mapping.configRotation != boundConfigRotation) {
                return;
            }
            if (!firstFrameLogged) {
                firstFrameLogged = true;
                MainHook.log(TAG + " first OES frame configRot=" + mapping.configRotation);
            }
            if (!firstMatrixLogged) {
                firstMatrixLogged = true;
                MainHook.log(TAG + " texture matrix=" + formatTextureMatrix(textureMatrix)
                        + " stage=normalize-only configRot=" + mapping.configRotation);
            }

            ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight);
            renderNormalizationPass(mapping);
            PrismalGeometry prismalGeometry = createPrismalGeometry(mapping);
            int prismalTexture = prismalRenderer.render(
                    rawTexture, prismalGeometry, mapping.prismalParams);
            renderCompositePass(prismalTexture, mapping);

            int glError = GLES20.glGetError();
            if (glError != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("GLES error=0x" + Integer.toHexString(glError));
            }
            // UI geometry may advance while this GL frame is being prepared. Never publish a
            // frame assembled from an obsolete generation; updateBackdropMapping() will queue the
            // matching generation when a consumed producer frame is available.
            if (backdropSnapshot != mapping
                    || mapping.visibleWidth != outputWidth
                    || mapping.visibleHeight != outputHeight
                    || mapping.configRotation != boundConfigRotation) {
                return;
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
                        + " material=prismal-module-official"
                        + " blur=official-two-pass-0.5x"
                        + " coverage=" + mapping.coverage
                        + " backdropRect=[" + mapping.backdropX + "," + mapping.backdropY + ","
                        + mapping.backdropW + "," + mapping.backdropH + "]"
                        + " validDockRect=[" + mapping.validDockLeft + "," + mapping.validDockBottom + ","
                        + mapping.validDockRight + "," + mapping.validDockTop + "]"
                        + " output=" + mapping.visibleWidth + "x" + mapping.visibleHeight
                        + " producerSurface=" + mapping.surfaceWidth + "x" + mapping.surfaceHeight
                        + " producerBuffer=" + mapping.bufferWidth + "x" + mapping.bufferHeight
                        + " configRot=" + mapping.configRotation
                        + " frameCallback=" + fromFrameCallback);
            }
            if (gpuBackdropActive && !stageBDiagnosticsLogged) {
                stageBDiagnosticsLogged = true;
                float[] matrixSnapshot = textureMatrix.clone();
                post(() -> logStageBDiagnostics(matrixSnapshot, mapping));
            }
            if (gpuBackdropActive && !prismalMappingLogged) {
                prismalMappingLogged = true;
                logPrismalMapping(prismalGeometry, mapping);
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private void renderNormalizationPass(BackdropSnapshot mapping)''',
    "atomic draw snapshot",
)

# renderNormalizationPass body: swap all mutable mapping reads for the captured generation.
text = replace_once(text,
    '''        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uBackdropRect"),
                backdropX, backdropY, backdropW, backdropH);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uConfigRot"), configRotation);
        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uValidDockRect"),
                validSampleLeft, validSampleBottom, validSampleRight, validSampleTop);
''',
    '''        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uBackdropRect"),
                mapping.backdropX, mapping.backdropY, mapping.backdropW, mapping.backdropH);
        GLES20.glUniform1i(
                requireUniform(normalizeProgram, "uConfigRot"), mapping.configRotation);
        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uValidDockRect"),
                mapping.validSampleLeft, mapping.validSampleBottom,
                mapping.validSampleRight, mapping.validSampleTop);
''',
    "normalization snapshot uniforms")

text = regex_once(
    text,
    r'''    private PrismalGeometry createPrismalGeometry\(\) \{.*?\n    \}\n\n    private void renderCompositePass\(int prismalTexture\)''',
    '''    private PrismalGeometry createPrismalGeometry(BackdropSnapshot mapping) {
        float glassWidth = Math.max(1f, mapping.dockUvWidth * mapping.sampleWidth);
        float glassHeight = Math.max(1f, mapping.dockUvHeight * mapping.sampleHeight);
        float centerX = (mapping.dockUvLeft + mapping.dockUvWidth * 0.5f)
                * mapping.sampleWidth;
        float centerGlY = (mapping.dockUvBottom + mapping.dockUvHeight * 0.5f)
                * mapping.sampleHeight;
        float centerYTop = mapping.sampleHeight - centerGlY;

        float cornerRadiusPx = Math.max(1f, glassHeight * 0.44f);
        View materialHost = materialHostRef.get();
        if (materialHost != null) {
            float nativeRadius = MiuixGlassHook.readNativeOpticsRadius(materialHost);
            if (!Float.isNaN(nativeRadius) && !Float.isInfinite(nativeRadius) && nativeRadius > 0f) {
                cornerRadiusPx = nativeRadius;
            }
        }
        return new PrismalGeometry(
                mapping.sampleWidth, mapping.sampleHeight,
                centerX, centerYTop,
                glassWidth, glassHeight,
                cornerRadiusPx);
    }

    private void renderCompositePass(int prismalTexture, BackdropSnapshot mapping)''',
    "geometry from snapshot",
)

# Composite viewport/crop/scissor all belong to the same generation.
for old, new, label in [
    ("GLES20.glViewport(0, 0, Math.max(1, outputWidth), Math.max(1, outputHeight));",
     "GLES20.glViewport(0, 0, mapping.visibleWidth, mapping.visibleHeight);",
     "composite viewport"),
    ("if (producerCoverage == Miuix307BackdropMapping.Coverage.OUTSIDE\n                || validDockRight <= validDockLeft || validDockTop <= validDockBottom) {",
     "if (mapping.coverage == Miuix307BackdropMapping.Coverage.OUTSIDE\n                || mapping.validDockRight <= mapping.validDockLeft\n                || mapping.validDockTop <= mapping.validDockBottom) {",
     "composite outside"),
    ("if (producerCoverage == Miuix307BackdropMapping.Coverage.PARTIAL) {\n            int left = Math.max(0, Math.round(validDockLeft * outputWidth));\n            int bottom = Math.max(0, Math.round(validDockBottom * outputHeight));\n            int right = Math.min(outputWidth, Math.round(validDockRight * outputWidth));\n            int top = Math.min(outputHeight, Math.round(validDockTop * outputHeight));",
     "if (mapping.coverage == Miuix307BackdropMapping.Coverage.PARTIAL) {\n            int left = Math.max(0, Math.round(mapping.validDockLeft * mapping.visibleWidth));\n            int bottom = Math.max(0, Math.round(mapping.validDockBottom * mapping.visibleHeight));\n            int right = Math.min(mapping.visibleWidth,\n                    Math.round(mapping.validDockRight * mapping.visibleWidth));\n            int top = Math.min(mapping.visibleHeight,\n                    Math.round(mapping.validDockTop * mapping.visibleHeight));",
     "composite partial"),
    ("dockUvLeft, dockUvBottom, dockUvWidth, dockUvHeight);",
     "mapping.dockUvLeft, mapping.dockUvBottom, mapping.dockUvWidth, mapping.dockUvHeight);",
     "composite crop"),
]:
    text = replace_once(text, old, new, label)

# Publish the same Prismal params used to compute sampling guard and all geometry fields atomically.
text = replace_once(
    text,
    '''        SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight);
        int sampleWidth = visibleWidth + insets.left + insets.right;
''',
    '''        PrismalParams frameParams = portablePrismalParams;
        if (frameParams == null) return;
        SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight, frameParams);
        int sampleWidth = visibleWidth + insets.left + insets.right;
''',
    "capture params in mapping",
)
text = replace_once(
    text,
    '''        boolean unchanged = Float.compare(backdropX, sample.backdropX) == 0
''',
    '''        BackdropSnapshot currentSnapshot = backdropSnapshot;
        boolean unchanged = currentSnapshot != null
                && currentSnapshot.visibleWidth == visibleWidth
                && currentSnapshot.visibleHeight == visibleHeight
                && currentSnapshot.sampleWidth == sampleWidth
                && currentSnapshot.sampleHeight == sampleHeight
                && currentSnapshot.configRotation == configRotation
                && currentSnapshot.surfaceWidth == boundSurfaceWidth
                && currentSnapshot.surfaceHeight == boundSurfaceHeight
                && currentSnapshot.bufferWidth == boundBufferWidth
                && currentSnapshot.bufferHeight == boundBufferHeight
                && currentSnapshot.prismalParams == frameParams
                && Float.compare(backdropX, sample.backdropX) == 0
''',
    "snapshot unchanged gate",
)
text = replace_once(
    text,
    '''        producerCoverage = dock.coverage;
        stageBDiagnosticsLogged = false;
''',
    '''        producerCoverage = dock.coverage;
        // Volatile publication is deliberately last: the GL thread never observes a mixture of
        // output size, overscan FBO, UV crop, rotation, or Prismal parameter generations.
        backdropSnapshot = new BackdropSnapshot(
                visibleWidth, visibleHeight,
                sampleWidth, sampleHeight,
                configRotation,
                boundSurfaceWidth, boundSurfaceHeight,
                boundBufferWidth, boundBufferHeight,
                frameParams,
                sample.backdropX, sample.backdropY, sample.backdropW, sample.backdropH,
                sample.validLeft, sample.validBottom, sample.validRight, sample.validTop,
                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                dock.coverage);
        stageBDiagnosticsLogged = false;
''',
    "publish immutable mapping",
)

text = regex_once(
    text,
    r'''    private void logStageBDiagnostics\(float\[\] matrixSnapshot\) \{.*?\n    \}\n\n    private static float\[\] mapFinalCoordinate''',
    '''    private void logStageBDiagnostics(
            float[] matrixSnapshot, BackdropSnapshot mapping) {
        if (shuttingDown || mapping == null) return;
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

        float[] bl = mapFinalCoordinate(
                mapping.backdropX, mapping.backdropY, mapping.configRotation, matrixSnapshot);
        float[] br = mapFinalCoordinate(
                mapping.backdropX + mapping.backdropW, mapping.backdropY,
                mapping.configRotation, matrixSnapshot);
        float[] tl = mapFinalCoordinate(
                mapping.backdropX, mapping.backdropY + mapping.backdropH,
                mapping.configRotation, matrixSnapshot);
        float[] tr = mapFinalCoordinate(
                mapping.backdropX + mapping.backdropW, mapping.backdropY + mapping.backdropH,
                mapping.configRotation, matrixSnapshot);

        MainHook.log(TAG + " stage-B mapping rootScreen=["
                + rootScreen[0] + "," + rootScreen[1] + "]"
                + " viewScreen=[" + viewScreen[0] + "," + viewScreen[1] + "]"
                + " hostScreen=[" + hostScreen[0] + "," + hostScreen[1] + "]"
                + " hostSize=" + materialHost.getWidth() + "x" + materialHost.getHeight()
                + " winFrame=" + formatRect(winFrame)
                + " rootSurface=" + mapping.surfaceWidth + "x" + mapping.surfaceHeight
                + " producerBuffer=" + mapping.bufferWidth + "x" + mapping.bufferHeight
                + " coverage=" + mapping.coverage
                + " backdropRect=[" + mapping.backdropX + "," + mapping.backdropY + ","
                + mapping.backdropW + "," + mapping.backdropH + "]"
                + " validDockRect=[" + mapping.validDockLeft + "," + mapping.validDockBottom + ","
                + mapping.validDockRight + "," + mapping.validDockTop + "]"
                + " configRot=" + mapping.configRotation
                + " texture matrix=" + formatTextureMatrix(matrixSnapshot)
                + " mapped corners bl=[" + bl[0] + "," + bl[1] + "]"
                + " br=[" + br[0] + "," + br[1] + "]"
                + " tl=[" + tl[0] + "," + tl[1] + "]"
                + " tr=[" + tr[0] + "," + tr[1] + "]");
    }

    private static float[] mapFinalCoordinate''',
    "stage-B diagnostics snapshot",
)

text = regex_once(
    text,
    r'''    private void logPrismalMapping\(PrismalGeometry g\) \{.*?\n    \}\n\n    private void bindQuad''',
    '''    private void logPrismalMapping(PrismalGeometry g, BackdropSnapshot mapping) {
        float left = mapping.dockUvLeft;
        float bottom = mapping.dockUvBottom;
        float right = mapping.dockUvLeft + mapping.dockUvWidth;
        float top = mapping.dockUvBottom + mapping.dockUvHeight;
        float officialTop = 1f - top;
        float officialBottom = 1f - bottom;
        MainHook.log("[DC][PRISMAL-MAP] producer surface="
                + mapping.surfaceWidth + "x" + mapping.surfaceHeight
                + " buffer=" + mapping.bufferWidth + "x" + mapping.bufferHeight
                + " configRot=" + mapping.configRotation
                + " textureMatrix=" + formatTextureMatrix(textureMatrix));
        MainHook.log("[DC][PRISMAL-MAP] normalized output="
                + mapping.visibleWidth + "x" + mapping.visibleHeight
                + " rawFbo=" + mapping.sampleWidth + "x" + mapping.sampleHeight
                + " dockUvRect=[" + left + "," + bottom + ","
                + mapping.dockUvWidth + "," + mapping.dockUvHeight + "]"
                + " validDockRect=[" + mapping.validDockLeft + "," + mapping.validDockBottom + ","
                + mapping.validDockRight + "," + mapping.validDockTop + "] coverage=" + mapping.coverage);
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

    private void bindQuad''',
    "Prismal mapping log snapshot",
)

VIEW.write_text(text, encoding="utf-8")

# Update architectural contract so it asserts the new atomic adapter boundary rather than old signatures.
test = TEST.read_text(encoding="utf-8")
test = replace_once(test,
    '        assertTrue(view.contains("createPrismalGeometry()"));\n',
    '        assertTrue(view.contains("createPrismalGeometry(mapping)"));\n'
    '        assertTrue(view.contains("private volatile BackdropSnapshot backdropSnapshot"));\n'
    '        assertTrue(view.contains("BackdropSnapshot mapping = backdropSnapshot"));\n'
    '        assertTrue(view.contains("ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight)"));\n'
    '        assertTrue(view.contains("renderNormalizationPass(mapping)"));\n',
    "update geometry contract")
test = replace_once(test,
    '        assertTrue(view.contains("renderCompositePass(prismalTexture)"));\n',
    '        assertTrue(view.contains("renderCompositePass(prismalTexture, mapping)"));\n'
    '        assertTrue(view.contains("if (backdropSnapshot != mapping"));\n',
    "update composite contract")
TEST.write_text(test, encoding="utf-8")

print("patched", VIEW.relative_to(ROOT))
print("patched", TEST.relative_to(ROOT))
