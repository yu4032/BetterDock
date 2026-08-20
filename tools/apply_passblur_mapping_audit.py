from pathlib import Path
import re

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


def regex_once(path: Path, pattern: str, replacement: str) -> None:
    text = path.read_text()
    out, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{path}: regex expected one match, got {count}: {pattern[:120]!r}")
    path.write_text(out)


# ---------------------------------------------------------------------------
# Stage-B SurfaceTexture transform: remove crop/scale on both axes while
# preserving the matrix's orientation (flip or quarter turn).
# ---------------------------------------------------------------------------
shader = ROOT / "Miuix307PassBlurShaders.java"
replace_once(
    shader,
    '''            vec2 mirrorDockUv(vec2 uv) {\n                return vec2(\n                        mirrorIntoValidRange(uv.x, uValidDockRect.x, uValidDockRect.z),\n                        mirrorIntoValidRange(uv.y, uValidDockRect.y, uValidDockRect.w));\n            }\n\n            void main() {''',
    '''            vec2 mirrorDockUv(vec2 uv) {\n                return vec2(\n                        mirrorIntoValidRange(uv.x, uValidDockRect.x, uValidDockRect.z),\n                        mirrorIntoValidRange(uv.y, uValidDockRect.y, uValidDockRect.w));\n            }\n\n            vec2 compensateSurfaceTextureCropPreservingOrientation(vec2 orientedUv) {\n                // SurfaceTexture commonly supplies a signed-permutation orientation matrix with\n                // independent crop scales and translation. Normalize both columns to recover only\n                // orientation, then solve the original affine transform backwards so its crop is\n                // neutralized without cancelling flips or quarter turns.\n                vec2 column0 = vec2(uTexMatrix[0][0], uTexMatrix[0][1]);\n                vec2 column1 = vec2(uTexMatrix[1][0], uTexMatrix[1][1]);\n                float scale0 = length(column0);\n                float scale1 = length(column1);\n                float a00 = uTexMatrix[0][0];\n                float a01 = uTexMatrix[1][0];\n                float a10 = uTexMatrix[0][1];\n                float a11 = uTexMatrix[1][1];\n                float determinant = a00 * a11 - a01 * a10;\n                if (scale0 <= 0.000001 || scale1 <= 0.000001 || abs(determinant) <= 0.000001) {\n                    return orientedUv;\n                }\n\n                vec2 orientation0 = column0 / scale0;\n                vec2 orientation1 = column1 / scale1;\n                // A crop matrix should keep its two texture axes orthogonal. If a future producer\n                // supplies a real shear, preserve the framework transform instead of guessing.\n                if (abs(dot(orientation0, orientation1)) > 0.001) return orientedUv;\n\n                vec2 orientationBias = vec2(\n                        -min(0.0, orientation0.x) - min(0.0, orientation1.x),\n                        -min(0.0, orientation0.y) - min(0.0, orientation1.y));\n                vec2 desired = orientation0 * orientedUv.x\n                        + orientation1 * orientedUv.y + orientationBias;\n                vec2 translation = vec2(uTexMatrix[3][0], uTexMatrix[3][1]);\n                vec2 rhs = desired - translation;\n                return vec2(\n                        (a11 * rhs.x - a01 * rhs.y) / determinant,\n                        (-a10 * rhs.x + a00 * rhs.y) / determinant);\n            }\n\n            void main() {''')
replace_once(
    shader,
    '''                // HyperOS' SurfaceTexture matrix contains an extra horizontal crop. Neutralize\n                // that crop before applying the matrix so root-space calibration stays stable.\n                vec2 textureInputUv = orientedUv;\n                float textureScaleX = uTexMatrix[0][0];\n                float textureOffsetX = uTexMatrix[3][0];\n                if (abs(textureScaleX) > 0.000001) {\n                    textureInputUv.x = (orientedUv.x - textureOffsetX) / textureScaleX;\n                }\n\n                vec4 transformed = uTexMatrix * vec4(textureInputUv, 0.0, 1.0);''',
    '''                vec2 textureInputUv =\n                        compensateSurfaceTextureCropPreservingOrientation(orientedUv);\n                vec4 transformed = uTexMatrix * vec4(textureInputUv, 0.0, 1.0);''')


# ---------------------------------------------------------------------------
# Prismal optical reach: one authoritative pixel-space bound shared by the
# zero-copy overscan allocator and the actual uniform upload.
# ---------------------------------------------------------------------------
material = ROOT / "Miuix307PrismalMaterial.java"
replace_once(
    material,
    '''    static float blurSigma(Params p) {\n        Params value = p != null ? p : defaults(1f);\n        return Math.max(value.blurRadiusPx * 0.5f, 0.5f);\n    }\n\n    static void applyUniforms(''',
    '''    static float blurSigma(Params p) {\n        Params value = p != null ? p : defaults(1f);\n        return Math.max(value.blurRadiusPx * 0.5f, 0.5f);\n    }\n\n    private static float lensRefractionPx(Params p, int widthPx, int heightPx) {\n        float width = Math.max(1, widthPx);\n        float height = Math.max(1, heightPx);\n        float minGlassDim = Math.min(width, height);\n        float refractionHeight = Math.max(\n                p.heightTransitionWidthPx * (1f + 0.55f * clamp(p.liquidDome, 0f, 2f)), 1f);\n        float lensPx = refractionHeight * 2f\n                * Math.abs(p.displacementScale) * Math.abs(p.lensRefractionScale);\n        return clamp(lensPx, 4f, Math.max(4f, minGlassDim * 0.85f));\n    }\n\n    private static float smoothstep(float edge0, float edge1, float x) {\n        if (edge0 == edge1) return x < edge0 ? 0f : 1f;\n        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);\n        return t * t * (3f - 2f * t);\n    }\n\n    private static float prismalPxNorm(int widthPx, int heightPx) {\n        float halfMin = Math.min(Math.max(1, widthPx), Math.max(1, heightPx)) * 0.5f;\n        return clamp(halfMin / 108f, 0.36f, 1f)\n                + smoothstep(88f, 220f, halfMin) * 0.45f;\n    }\n\n    /**\n     * Conservative full-resolution pixel reach of every Prismal backdrop sample. This mirrors\n     * the shader's lens, Snell, bulge, chromatic and reflection terms so the zero-copy texture\n     * owns enough real scene pixels before any final texture-edge clamp is reached.\n     */\n    static int requiredSampleGuardPx(\n            Params p0, int widthPx, int heightPx, boolean horizontal) {\n        Params p = p0 != null ? p0 : defaults(1f);\n        float width = Math.max(1, widthPx);\n        float height = Math.max(1, heightPx);\n        float axis = horizontal ? width : height;\n        float pxNorm = prismalPxNorm(widthPx, heightPx);\n\n        float sampleScale = Math.max(0.01f,\n                horizontal ? Math.abs(p.backdropScaleX) : Math.abs(p.backdropScaleY));\n        float scaleExpansion = Math.max(0f, 1f / sampleScale - 1f) * axis * 0.5f;\n\n        float lens = lensRefractionPx(p, widthPx, heightPx) * 1.45f * 1.12f;\n        float parallax = 29f * 0.052f * Math.abs(p.displacementScale)\n                * Math.abs(p.parallaxScale) * 1.12f;\n        float snell = Math.abs(p.thicknessPx) * 0.85f * Math.abs(p.displacementScale)\n                * 1.18f * pxNorm;\n        float modernBulge = axis * (0.014f + 0.01f * clamp(p.liquidDome, 0f, 2f)) * pxNorm;\n        float modernBase = lens + parallax + snell + modernBulge;\n\n        float legacyLens = Math.abs(p.legacyLensRefractionPx) * 1.12f;\n        float legacyParallax = 29f * 0.052f * 1.15f * 1.12f;\n        float legacySnell = Math.abs(p.legacyThicknessPx) * 0.85f * 1.15f * 1.18f * pxNorm;\n        float legacyBulge = axis * 0.012f;\n        float legacyBase = legacyLens + legacyParallax + legacySnell + legacyBulge;\n        float legacyStrength = clamp(p.legacySCurveStrength, 0f, 2f);\n        float baseReach = modernBase;\n        if (legacyStrength > 0f && legacyStrength <= 1f) {\n            baseReach = Math.max(modernBase, legacyBase);\n        } else if (legacyStrength > 1f) {\n            baseReach = legacyBase * legacyStrength;\n        }\n\n        float dispersion = Math.max(Math.abs(p.dispersionR), Math.abs(p.dispersionB));\n        float chromatic = Math.abs(p.chromaticAberration) * 0.0018f\n                * dispersion * pxNorm * axis;\n        float reflection = 56f * pxNorm;\n        return Math.max(0, (int) Math.ceil(\n                scaleExpansion + baseReach + chromatic + reflection + 2f));\n    }\n\n    static void applyUniforms(''')
regex_once(
    material,
    r'''        float width = Math\.max\(1, widthPx\);\n        float height = Math\.max\(1, heightPx\);\n        float minGlassDim = Math\.min\(width, height\);\n        float refractionHeight = Math\.max\(\n                p\.heightTransitionWidthPx \* \(1f \+ 0\.55f \* clamp\(p\.liquidDome, 0f, 2f\)\), 1f\);\n        float lensPx = refractionHeight \* 2f \* p\.displacementScale \* p\.lensRefractionScale;\n        lensPx = clamp\(lensPx, 4f, Math\.max\(4f, minGlassDim \* 0\.85f\)\);''',
    '''        float width = Math.max(1, widthPx);\n        float height = Math.max(1, heightPx);\n        float lensPx = lensRefractionPx(p, widthPx, heightPx);''')


# ---------------------------------------------------------------------------
# TextureView/FBO geometry: use one visible geometry source and one sampling
# inset resolver for both allocation and Dock->overscan UV mapping.
# ---------------------------------------------------------------------------
view = ROOT / "Miuix307PassBlurTextureView.java"
replace_once(
    view,
    '''    private static final float BLUR_FBO_SCALE = 0.5f;\n''',
    '''    private static final float BLUR_FBO_SCALE = 0.5f;\n    private static final int BLUR_KERNEL_RADIUS_TEXELS = 15;\n''')
replace_once(
    view,
    '''    private static final class ProducerGeometry {\n''',
    '''    private static final class SamplingInsets {\n        final int left;\n        final int right;\n        final int top;\n        final int bottom;\n\n        SamplingInsets(int left, int right, int top, int bottom) {\n            this.left = Math.max(0, left);\n            this.right = Math.max(0, right);\n            this.top = Math.max(0, top);\n            this.bottom = Math.max(0, bottom);\n        }\n    }\n\n    private static final class ProducerGeometry {\n''')
replace_once(
    view,
    '''        outputWidth = Math.max(1, width);\n        outputHeight = Math.max(1, height);\n        Surface window = new Surface(surface);''',
    '''        outputWidth = Math.max(1, width);\n        outputHeight = Math.max(1, height);\n        updateBackdropMapping();\n        Surface window = new Surface(surface);''')
regex_once(
    view,
    r'''    private void ensureFboSize\(int width, int height\) \{\n.*?\n    \}\n\n    private void drawLatestFrame''',
    '''    private void ensureFboSize(int width, int height) {\n        SamplingInsets insets = resolveSamplingInsets(width, height);\n        int nextWidth = Math.max(1, width + insets.left + insets.right);\n        int nextHeight = Math.max(1, height + insets.top + insets.bottom);\n        int nextBlurWidth = Math.max(1, Math.round(nextWidth * BLUR_FBO_SCALE));\n        int nextBlurHeight = Math.max(1, Math.round(nextHeight * BLUR_FBO_SCALE));\n        if (rawFramebuffer != 0 && blurFramebufferH != 0 && blurFramebufferV != 0\n                && fboWidth == nextWidth && fboHeight == nextHeight\n                && blurWidth == nextBlurWidth && blurHeight == nextBlurHeight) {\n            return;\n        }\n\n        releaseFbos();\n        rawTexture = createTexture2D(nextWidth, nextHeight);\n        rawFramebuffer = createFramebuffer(rawTexture);\n        blurTextureH = createTexture2D(nextBlurWidth, nextBlurHeight);\n        blurFramebufferH = createFramebuffer(blurTextureH);\n        blurTextureV = createTexture2D(nextBlurWidth, nextBlurHeight);\n        blurFramebufferV = createFramebuffer(blurTextureV);\n        fboWidth = nextWidth;\n        fboHeight = nextHeight;\n        blurWidth = nextBlurWidth;\n        blurHeight = nextBlurHeight;\n    }\n\n    private void drawLatestFrame''')
replace_once(
    view,
    '''    private int horizontalOverscanPx() {\n        float density = getResources().getDisplayMetrics().density;\n        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));\n    }\n\n    private void updateBackdropMapping() {''',
    '''    private int horizontalOverscanPx() {\n        float density = getResources().getDisplayMetrics().density;\n        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));\n    }\n\n    private int blurSamplingGuardPx() {\n        return Math.max(0, (int) Math.ceil(\n                BLUR_KERNEL_RADIUS_TEXELS / Math.max(BLUR_FBO_SCALE, 0.0001f)));\n    }\n\n    private SamplingInsets resolveSamplingInsets(int width, int height) {\n        int opticalX = Miuix307PrismalMaterial.requiredSampleGuardPx(\n                opticalParams, width, height, true);\n        int opticalY = Miuix307PrismalMaterial.requiredSampleGuardPx(\n                opticalParams, width, height, false);\n        int blurGuard = blurSamplingGuardPx();\n        opticalX += blurGuard;\n        opticalY += blurGuard;\n\n        int left = Math.max(horizontalOverscanPx() + Math.max(0, leftExtraOverscanPx), opticalX);\n        int right = Math.max(horizontalOverscanPx() + Math.max(0, rightExtraOverscanPx), opticalX);\n        int top = Math.max(Math.max(0, topOverscanPx), opticalY);\n        int bottom = Math.max(Math.max(0, bottomOverscanPx), opticalY);\n        return new SamplingInsets(left, right, top, bottom);\n    }\n\n    private void updateBackdropMapping() {''')
regex_once(
    view,
    r'''    private void updateBackdropMapping\(\) \{\n.*?\n    \}\n\n    private ProducerGeometry readSurfaceGeometry''',
    '''    private void updateBackdropMapping() {\n        if (shuttingDown || !isAttachedToWindow()) return;\n        int visibleWidth = outputWidth > 0 ? outputWidth : getWidth();\n        int visibleHeight = outputHeight > 0 ? outputHeight : getHeight();\n        if (visibleWidth <= 0 || visibleHeight <= 0) return;\n\n        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");\n        if (winFrame == null || winFrame.width() <= 0 || winFrame.height() <= 0) return;\n        int[] viewScreen = new int[2];\n        getLocationOnScreen(viewScreen);\n\n        SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight);\n        int sampleWidth = visibleWidth + insets.left + insets.right;\n        int sampleHeight = visibleHeight + insets.top + insets.bottom;\n        Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(\n                viewScreen[0] - insets.left, viewScreen[1] - insets.top,\n                sampleWidth, sampleHeight,\n                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());\n        Miuix307BackdropMapping.Result dock = Miuix307BackdropMapping.compute(\n                viewScreen[0], viewScreen[1], visibleWidth, visibleHeight,\n                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());\n\n        float nextDockUvLeft = insets.left / (float) sampleWidth;\n        float nextDockUvBottom = insets.bottom / (float) sampleHeight;\n        float nextDockUvWidth = visibleWidth / (float) sampleWidth;\n        float nextDockUvHeight = visibleHeight / (float) sampleHeight;\n\n        boolean unchanged = Float.compare(backdropX, sample.backdropX) == 0\n                && Float.compare(backdropY, sample.backdropY) == 0\n                && Float.compare(backdropW, sample.backdropW) == 0\n                && Float.compare(backdropH, sample.backdropH) == 0\n                && Float.compare(validSampleLeft, sample.validLeft) == 0\n                && Float.compare(validSampleBottom, sample.validBottom) == 0\n                && Float.compare(validSampleRight, sample.validRight) == 0\n                && Float.compare(validSampleTop, sample.validTop) == 0\n                && Float.compare(validDockLeft, dock.validLeft) == 0\n                && Float.compare(validDockBottom, dock.validBottom) == 0\n                && Float.compare(validDockRight, dock.validRight) == 0\n                && Float.compare(validDockTop, dock.validTop) == 0\n                && Float.compare(dockUvLeft, nextDockUvLeft) == 0\n                && Float.compare(dockUvBottom, nextDockUvBottom) == 0\n                && Float.compare(dockUvWidth, nextDockUvWidth) == 0\n                && Float.compare(dockUvHeight, nextDockUvHeight) == 0\n                && producerCoverage == dock.coverage;\n        if (unchanged) return;\n\n        backdropX = sample.backdropX;\n        backdropY = sample.backdropY;\n        backdropW = sample.backdropW;\n        backdropH = sample.backdropH;\n        validSampleLeft = sample.validLeft;\n        validSampleBottom = sample.validBottom;\n        validSampleRight = sample.validRight;\n        validSampleTop = sample.validTop;\n        validDockLeft = dock.validLeft;\n        validDockBottom = dock.validBottom;\n        validDockRight = dock.validRight;\n        validDockTop = dock.validTop;\n        dockUvLeft = nextDockUvLeft;\n        dockUvBottom = nextDockUvBottom;\n        dockUvWidth = nextDockUvWidth;\n        dockUvHeight = nextDockUvHeight;\n        producerCoverage = dock.coverage;\n        stageBDiagnosticsLogged = false;\n        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));\n    }\n\n    private ProducerGeometry readSurfaceGeometry''')
replace_once(
    view,
    '''        int[] hostScreen = new int[2];\n        int[] rootScreen = new int[2];\n        materialHost.getLocationOnScreen(hostScreen);\n        root.getLocationOnScreen(rootScreen);\n        Rect winFrame = readViewRootRectField(materialHost, "mWinFrameInScreen");''',
    '''        int[] viewScreen = new int[2];\n        int[] hostScreen = new int[2];\n        int[] rootScreen = new int[2];\n        getLocationOnScreen(viewScreen);\n        materialHost.getLocationOnScreen(hostScreen);\n        root.getLocationOnScreen(rootScreen);\n        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");''')
replace_once(
    view,
    '''        MainHook.log(TAG + " stage-B mapping rootScreen=["\n                + rootScreen[0] + "," + rootScreen[1] + "]"\n                + " hostScreen=[" + hostScreen[0] + "," + hostScreen[1] + "]"''',
    '''        MainHook.log(TAG + " stage-B mapping rootScreen=["\n                + rootScreen[0] + "," + rootScreen[1] + "]"\n                + " viewScreen=[" + viewScreen[0] + "," + viewScreen[1] + "]"\n                + " hostScreen=[" + hostScreen[0] + "," + hostScreen[1] + "]"''')
regex_once(
    view,
    r'''        float textureInputX = orientedX;\n        float textureScaleX = matrix != null && matrix\.length > 12 \? matrix\[0\] : 1f;\n        float textureOffsetX = matrix != null && matrix\.length > 12 \? matrix\[12\] : 0f;\n        if \(Math\.abs\(textureScaleX\) > 0\.000001f\) \{\n            textureInputX = \(orientedX - textureOffsetX\) / textureScaleX;\n        \}\n        return mapTextureCoordinate\(matrix, textureInputX, orientedY\);''',
    '''        float[] input = compensateSurfaceTextureCropPreservingOrientation(\n                orientedX, orientedY, matrix);\n        return mapTextureCoordinate(matrix, input[0], input[1]);''')
replace_once(
    view,
    '''    private static float[] mapTextureCoordinate(float[] matrix, float x, float y) {''',
    '''    private static float[] compensateSurfaceTextureCropPreservingOrientation(\n            float x, float y, float[] matrix) {\n        if (matrix == null || matrix.length < 16) return new float[]{x, y};\n        float a00 = matrix[0];\n        float a01 = matrix[4];\n        float a10 = matrix[1];\n        float a11 = matrix[5];\n        float scale0 = (float) Math.hypot(a00, a10);\n        float scale1 = (float) Math.hypot(a01, a11);\n        float determinant = a00 * a11 - a01 * a10;\n        if (scale0 <= 0.000001f || scale1 <= 0.000001f\n                || Math.abs(determinant) <= 0.000001f) {\n            return new float[]{x, y};\n        }\n\n        float o00 = a00 / scale0;\n        float o10 = a10 / scale0;\n        float o01 = a01 / scale1;\n        float o11 = a11 / scale1;\n        if (Math.abs(o00 * o01 + o10 * o11) > 0.001f) return new float[]{x, y};\n\n        float biasX = -Math.min(0f, o00) - Math.min(0f, o01);\n        float biasY = -Math.min(0f, o10) - Math.min(0f, o11);\n        float desiredX = o00 * x + o01 * y + biasX;\n        float desiredY = o10 * x + o11 * y + biasY;\n        float rhsX = desiredX - matrix[12];\n        float rhsY = desiredY - matrix[13];\n        return new float[]{\n                (a11 * rhsX - a01 * rhsY) / determinant,\n                (-a10 * rhsX + a00 * rhsY) / determinant\n        };\n    }\n\n    private static float[] mapTextureCoordinate(float[] matrix, float x, float y) {''')

# Safety: preserve the device-validated swapped-quarter-turn formulas exactly.
final_shader = shader.read_text()
for expected in (
        "return vec2(rootUv.y, 1.0 - rootUv.x);",
        "return vec2(1.0 - rootUv.x, 1.0 - rootUv.y);",
        "return vec2(1.0 - rootUv.y, rootUv.x);"):
    if expected not in final_shader:
        raise SystemExit(f"quarter-turn mapping changed unexpectedly: {expected}")

# The zero-copy-only architecture must stay intact.
all_java = "\n".join(p.read_text(errors="ignore") for p in ROOT.glob("*.java"))
for forbidden in ("captureScreenAsync(", "class DockLiquidGlassView", "class LiveScreenCapture"):
    if forbidden in all_java:
        raise SystemExit(f"retired capture backend token restored: {forbidden}")
