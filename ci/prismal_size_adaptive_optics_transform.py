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
    '    // Broad glass needs calmer face lighting; compact glass needs the opposite-side\n'
    '    // center plateau suppressed without weakening its Fresnel/rim silhouette.\n'
    '    // minDim is half the short side: large 60..180 => 120..360 px.\n'
    '    float largeGlass = smoothstep(60.0, 180.0, minDim);\n'
    '    float specSizeScale = mix(1.0, 0.50, largeGlass);\n'
    '    float causticSizeScale = mix(1.0, 0.30, largeGlass);\n'
    '    float highlightSizeScale = mix(1.0, 0.50, largeGlass);\n'
    '    // Compact attenuation is fully active through ~96 px short side and fades out\n'
    '    // by ~152 px, leaving medium glass at the original face-lighting strength.\n'
    '    float compactGlass = 1.0 - smoothstep(48.0, 76.0, minDim);\n'
    '    float compactSpecScale = mix(1.0, 0.30, compactGlass);\n'
    '    float compactCausticScale = mix(1.0, 0.15, compactGlass);\n'
    '    float compactHighlightScale = mix(1.0, 0.40, compactGlass);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'size response')

old = (
    '    vec2 pPx = v_shapeCoord * u_glassSize;\n'
    '    vec2 cKy = vec2(pPx.x, -pPx.y);\n'
)
new = old + (
    '    // A normalized radial fade cannot inherit the rounded-box plateau shape.\n'
    '    // It therefore removes the compact center square without touching edge optics.\n'
    '    vec2 compactNorm = pPx / max(halfSz, vec2(1.0));\n'
    '    float compactRadius = length(compactNorm);\n'
    '    float compactCenterFade = mix(1.0, 0.28 + 0.72 * smoothstep(0.18, 0.88, compactRadius), compactGlass);\n'
    '    // Only the deep compact core is radialized. The outer rounded-rect shell remains\n'
    '    // authoritative for clipping, opacity, Fresnel and rim geometry.\n'
    '    float compactCore = compactGlass * (1.0 - smoothstep(0.58, 0.98, compactRadius));\n'
    '    float compactRadialHeight = clamp(1.0 - dot(compactNorm, compactNorm), 0.0, 1.0);\n'
    '    vec2 compactRadialGrad = -2.0 * compactNorm / max(halfSz, vec2(1.0));\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'compact radial core')

old = '    float edgeDist = -distMask;\n'
new = old + (
    '    // On large glass, suppress only the deep face plateau that can reveal the rounded-box\n'
    '    // height field as a bright rectangle. Edge Fresnel/rim lighting remains untouched.\n'
    '    float deepCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(minDim * 0.28, minDim * 0.72, edgeDist), largeGlass);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'large center fade')

old = '    float tShell = 1.0 - tDeep;\n'
new = old + (
    '    // gradSdRoundedRectRealistic is intentionally exact at the shell, but in the deep\n'
    '    // interior its axis branch changes at |x|=|y| and can expose an X-shaped seam.\n'
    '    // Blend that edge direction into a continuous elliptical-radial optical field.\n'
    '    vec2 opticalRadialRaw = cKy / max(halfSz, vec2(1.0));\n'
    '    float opticalRadialLen = length(opticalRadialRaw);\n'
    '    vec2 opticalRadial = opticalRadialLen > 1e-5 ? opticalRadialRaw / opticalRadialLen : vec2(0.0);\n'
    '    vec2 opticalEdgeDir = normalize(gradLens + vec2(1e-5));\n'
    '    float opticalInteriorW = smoothstep(0.12, 0.52, tDeep);\n'
    '    vec2 opticalBlend = mix(opticalEdgeDir, opticalRadial, opticalInteriorW);\n'
    '    float opticalBlendLen = length(opticalBlend);\n'
    '    vec2 opticalDir = opticalBlendLen > 1e-5 ? opticalBlend / opticalBlendLen : vec2(0.0);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'continuous interior direction')

old = '    height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);\n'
new = old + (
    '    // Compact icons have too little room for the rounded-box depth plateau. Replace only\n'
    '    // the deep face with a smooth radial dome; the shell is still the exact box SDF.\n'
    '    height = mix(height, compactRadialHeight, compactCore);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'compact radial height')

old = '    vec2 gradH = mix(gradHSig, gCap, domeW);\n'
new = old + (
    '    gradH = mix(gradH, compactRadialGrad, compactCore);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'compact radial gradient')

old = (
    '    vec2 cenSafe = cKy + vec2(1e-4, 1e-4);\n'
    '    vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);\n'
)
new = (
    '    vec2 lensDir = opticalDir + u_lensDepthEffect * opticalRadial;\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'radial lens direction')

glsl, generated = replace_both(
    glsl,
    generated,
    '    vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;\n',
    '    vec2 parallax = (opticalDir * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;\n',
    'radial parallax direction',
)

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
