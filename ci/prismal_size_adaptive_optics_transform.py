from pathlib import Path

GLSL = Path('prismal/src/main/res/raw/prismal_fragment.glsl')
GENERATED = Path('prismal/src/main/java/com/hellovoid/prismal/PrismalShaderSources.java')


def java_string_body(text: str) -> str:
    return text.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n')


def replace_both(glsl: str, generated: str, old: str, new: str, label: str):
    if old not in glsl:
        raise SystemExit(f'{label}: GLSL anchor not found')
    escaped_old = java_string_body(old)
    escaped_new = java_string_body(new)
    if escaped_old not in generated:
        raise SystemExit(f'{label}: generated shader anchor not found')
    return glsl.replace(old, new, 1), generated.replace(escaped_old, escaped_new, 1)


glsl = GLSL.read_text()
generated = GENERATED.read_text()

old = (
    '    float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);\n'
    '    edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);\n'
)
new = old + (
    '    // Size changes face-lighting energy only. Optical geometry remains rounded-rect.\n'
    '    // minDim is half the short side: large 60..180 => 120..360 px.\n'
    '    float largeGlass = smoothstep(60.0, 180.0, minDim);\n'
    '    float specSizeScale = mix(1.0, 0.50, largeGlass);\n'
    '    float causticSizeScale = mix(1.0, 0.30, largeGlass);\n'
    '    float highlightSizeScale = mix(1.0, 0.50, largeGlass);\n'
    '    float compactGlass = 1.0 - smoothstep(48.0, 76.0, minDim);\n'
    '    float compactSpecScale = mix(1.0, 0.30, compactGlass);\n'
    '    float compactCausticScale = mix(1.0, 0.15, compactGlass);\n'
    '    float compactHighlightScale = mix(1.0, 0.40, compactGlass);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'size response')

old = (
    '    float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);\n'
    '    float edgeDist = -distMask;\n'
)
new = old + (
    '    // Face optics use the same rounded-rect family as the silhouette, but with enough\n'
    '    // polynomial smoothing that the nearest-horizontal/vertical direction rotates\n'
    '    // continuously across the diagonals instead of forming a visible X seam.\n'
    '    float faceSmoothK = max(u_sminSmoothing, minDim * 0.12);\n'
    '    float faceSd = sdRoundBox(pPx, halfSz, crMask, faceSmoothK);\n'
    '    float faceCenterDepth = max(-sdRoundBox(vec2(0.0), halfSz, crMask, faceSmoothK), 1.0);\n'
    '    float faceDepthT = clamp(max(0.0, -faceSd) / faceCenterDepth, 0.0, 1.0);\n'
    '    float smoothFaceHeight = faceDepthT * faceDepthT * (3.0 - 2.0 * faceDepthT);\n'
    '    float faceDx = 0.5 * (sdRoundBox(pPx + vec2(1.0, 0.0), halfSz, crMask, faceSmoothK) - sdRoundBox(pPx - vec2(1.0, 0.0), halfSz, crMask, faceSmoothK));\n'
    '    float faceDy = 0.5 * (sdRoundBox(pPx + vec2(0.0, 1.0), halfSz, crMask, faceSmoothK) - sdRoundBox(pPx - vec2(0.0, 1.0), halfSz, crMask, faceSmoothK));\n'
    '    float smoothFaceSlope = 6.0 * faceDepthT * (1.0 - faceDepthT) / faceCenterDepth;\n'
    '    vec2 smoothFaceGrad = -vec2(faceDx, faceDy) * smoothFaceSlope;\n'
    '    float compactCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(0.18, 0.88, faceDepthT), compactGlass);\n'
    '    // Large surfaces keep their rim energy while broad face lighting is reduced.\n'
    '    float deepCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(minDim * 0.28, minDim * 0.72, edgeDist), largeGlass);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'smooth rounded-rect face surface')

# Reuse the continuous face field as the slab input and avoid the old four-sample
# height gradient, which used a shallower saturated transition and could expose a core.
glsl, generated = replace_both(
    glsl,
    generated,
    '    float hSig = getHeightFromDist(distMask, tw);\n',
    '    float hSig = smoothFaceHeight;\n',
    'smooth face height input',
)
glsl, generated = replace_both(
    glsl,
    generated,
    '    vec2 gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);\n',
    '    vec2 gradHSig = smoothFaceGrad;\n',
    'smooth face gradient input',
)

old = '    height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);\n'
new = old + (
    '    // The broad face remains one smooth rounded-rect dome. Edge meniscus is applied\n'
    '    // separately through N_meniscus below, so no circular or square core is introduced.\n'
    '    height = smoothFaceHeight;\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'smooth rounded-rect final height')

old = '    vec2 gradH = mix(gradHSig, gCap, domeW);\n'
new = old + (
    '    // Keep specular/caustic face normals on the continuous rounded-rect gradient.\n'
    '    gradH = smoothFaceGrad;\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'smooth rounded-rect final gradient')

glsl, generated = replace_both(
    glsl,
    generated,
    '    float sp = u_specular * 1.05;\n',
    '    float sp = u_specular * 1.05 * specSizeScale * compactSpecScale;\n',
    'specular scale',
)

glsl, generated = replace_both(
    glsl,
    generated,
    '    color += (specP + specS) * vec3(0.99, 0.993, 1.0);\n',
    '    color += (specP + specS) * deepCenterFade * compactCenterFade * vec3(0.99, 0.993, 1.0);\n',
    'specular center fade',
)

glsl, generated = replace_both(
    glsl,
    generated,
    '    plusHL *= mix(0.42, 0.06, smallGlass);\n',
    '    plusHL *= mix(0.42, 0.06, smallGlass) * highlightSizeScale * compactHighlightScale * deepCenterFade * compactCenterFade;\n',
    'plain highlight scale',
)

glsl, generated = replace_both(
    glsl,
    generated,
    '        float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;\n',
    '        float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height * causticSizeScale * compactCausticScale * deepCenterFade * compactCenterFade;\n',
    'caustic scale',
)

GLSL.write_text(glsl)
GENERATED.write_text(generated)
