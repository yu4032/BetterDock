from pathlib import Path

VIEW = Path("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java")
SHADER = Path("src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java")


def replace_once(path: Path, old: str, new: str) -> None:
    source = path.read_text()
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"expected one anchor in {path}, found {count}: {old[:100]!r}")
    path.write_text(source.replace(old, new, 1))


replace_once(
    VIEW,
    '    private static final float BLUR_FBO_SCALE = 0.5f;\n',
    '    private static final float BLUR_FBO_SCALE = 0.5f;\n'
    '    // Keep real scene pixels around the visible Dock so refraction can see approaching content\n'
    '    // before it crosses the material edge. Output remains clipped to the Dock itself.\n'
    '    private static final float EDGE_OVERSCAN_DP = 32f;\n',
)

replace_once(
    VIEW,
    '''    // Unclamped host-to-producer mapping plus the valid Dock-local sub-rectangle.
    private volatile float backdropX;
    private volatile float backdropY;
    private volatile float backdropW = 1f;
    private volatile float backdropH = 1f;
    private volatile float validDockLeft;
    private volatile float validDockBottom;
    private volatile float validDockRight = 1f;
    private volatile float validDockTop = 1f;
    private volatile Miuix307BackdropMapping.Coverage producerCoverage =
            Miuix307BackdropMapping.Coverage.FULL;
''',
    '''    // Stage A samples a real overscan ring around the visible Dock. The sample-valid
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
''',
)

replace_once(
    VIEW,
    '''    private void ensureFboSize(int width, int height) {
        int nextWidth = Math.max(1, width);
        int nextHeight = Math.max(1, height);
        int nextBlurWidth = Math.max(1, Math.round(nextWidth * BLUR_FBO_SCALE));
''',
    '''    private void ensureFboSize(int width, int height) {
        int overscanPx = edgeOverscanPx();
        int nextWidth = Math.max(1, width + overscanPx * 2);
        int nextHeight = Math.max(1, height + overscanPx * 2);
        int nextBlurWidth = Math.max(1, Math.round(nextWidth * BLUR_FBO_SCALE));
''',
)

replace_once(
    VIEW,
    '''        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uValidDockRect"),
                validDockLeft, validDockBottom, validDockRight, validDockTop);
''',
    '''        GLES20.glUniform4f(
                requireUniform(normalizeProgram, "uValidDockRect"),
                validSampleLeft, validSampleBottom, validSampleRight, validSampleTop);
''',
)

replace_once(
    VIEW,
    '''        Miuix307PrismalMaterial.applyUniforms(
                materialProgram, opticalParams, cornerRadiusPx, outputWidth, outputHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
''',
    '''        Miuix307PrismalMaterial.applyUniforms(
                materialProgram, opticalParams, cornerRadiusPx, outputWidth, outputHeight);
        int uDockUvRect = requireUniform(materialProgram, "u_dockUvRect");
        GLES20.glUniform4f(
                uDockUvRect, dockUvLeft, dockUvBottom, dockUvWidth, dockUvHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
''',
)

replace_once(
    VIEW,
    '''    private void updateBackdropMapping() {
        if (shuttingDown) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null || !materialHost.isAttachedToWindow()) return;
        int hostWidth = materialHost.getWidth();
        int hostHeight = materialHost.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0) return;

        Rect winFrame = readViewRootRectField(materialHost, "mWinFrameInScreen");
        if (winFrame == null || winFrame.width() <= 0 || winFrame.height() <= 0) return;
        int[] hostScreen = new int[2];
        materialHost.getLocationOnScreen(hostScreen);

        Miuix307BackdropMapping.Result next = Miuix307BackdropMapping.compute(
                hostScreen[0], hostScreen[1], hostWidth, hostHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());
        boolean unchanged = Float.compare(backdropX, next.backdropX) == 0
                && Float.compare(backdropY, next.backdropY) == 0
                && Float.compare(backdropW, next.backdropW) == 0
                && Float.compare(backdropH, next.backdropH) == 0
                && Float.compare(validDockLeft, next.validLeft) == 0
                && Float.compare(validDockBottom, next.validBottom) == 0
                && Float.compare(validDockRight, next.validRight) == 0
                && Float.compare(validDockTop, next.validTop) == 0
                && producerCoverage == next.coverage;
        if (unchanged) return;

        backdropX = next.backdropX;
        backdropY = next.backdropY;
        backdropW = next.backdropW;
        backdropH = next.backdropH;
        validDockLeft = next.validLeft;
        validDockBottom = next.validBottom;
        validDockRight = next.validRight;
        validDockTop = next.validTop;
        producerCoverage = next.coverage;
        stageBDiagnosticsLogged = false;
        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));
    }
''',
    '''    private int edgeOverscanPx() {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));
    }

    private void updateBackdropMapping() {
        if (shuttingDown) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null || !materialHost.isAttachedToWindow()) return;
        int hostWidth = materialHost.getWidth();
        int hostHeight = materialHost.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0) return;

        Rect winFrame = readViewRootRectField(materialHost, "mWinFrameInScreen");
        if (winFrame == null || winFrame.width() <= 0 || winFrame.height() <= 0) return;
        int[] hostScreen = new int[2];
        materialHost.getLocationOnScreen(hostScreen);

        int overscanPx = edgeOverscanPx();
        int sampleWidth = hostWidth + overscanPx * 2;
        int sampleHeight = hostHeight + overscanPx * 2;
        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(
                hostScreen[0] - overscanPx, hostScreen[1] - overscanPx,
                sampleWidth, sampleHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());
        Miuix307BackdropMapping.Result dock = Miuix307BackdropMapping.compute(
                hostScreen[0], hostScreen[1], hostWidth, hostHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());

        float nextDockUvLeft = overscanPx / (float) sampleWidth;
        float nextDockUvBottom = overscanPx / (float) sampleHeight;
        float nextDockUvWidth = hostWidth / (float) sampleWidth;
        float nextDockUvHeight = hostHeight / (float) sampleHeight;

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
''',
)

replace_once(
    VIEW,
    '''        validDockLeft = 0f;
        validDockBottom = 0f;
        validDockRight = 1f;
        validDockTop = 1f;
        producerCoverage = Miuix307BackdropMapping.Coverage.FULL;
''',
    '''        validSampleLeft = 0f;
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
''',
)

replace_once(
    SHADER,
    '''            uniform vec2  u_backdropSampleScale;
            uniform float u_parallaxScale;
''',
    '''            uniform vec2  u_backdropSampleScale;
            // Visible Dock rectangle inside the larger zero-copy overscan texture.
            uniform vec4  u_dockUvRect;
            uniform float u_parallaxScale;
''',
)

replace_once(
    SHADER,
    '''            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {
                float press = clamp(u_pressProgress, 0.0, 1.0);
                float pinch = mix(1.0, max(u_backdropPinch, 0.01), press * pinchMix);
                vec2 s = max(u_backdropSampleScale, vec2(0.01)) / vec2(pinch);
                vec2 scaled = (screenUv - 0.5) / s + 0.5;
                return clamp(scaled + offset, vec2(0.0), vec2(1.0));
            }
''',
    '''            vec2 mapDockUvToBackdrop(vec2 dockUv) {
                return clamp(
                    u_dockUvRect.xy + dockUv * u_dockUvRect.zw,
                    vec2(0.0),
                    vec2(1.0)
                );
            }

            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {
                float press = clamp(u_pressProgress, 0.0, 1.0);
                float pinch = mix(1.0, max(u_backdropPinch, 0.01), press * pinchMix);
                vec2 s = max(u_backdropSampleScale, vec2(0.01)) / vec2(pinch);
                vec2 scaled = (screenUv - 0.5) / s + 0.5;
                vec2 dockUv = scaled + offset;
                return mapDockUvToBackdrop(dockUv);
            }
''',
)

replace_once(
    SHADER,
    '''                vec2 reflUv = clamp(
                    v_screenTexCoord + baseOffset
                        + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm,
                    vec2(0.0),
                    vec2(1.0)
                );
''',
    '''                vec2 reflDockUv = v_screenTexCoord + baseOffset
                    + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm;
                vec2 reflUv = mapDockUvToBackdrop(reflDockUv);
''',
)
