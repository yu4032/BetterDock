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
)
glsl, generated = replace_both(glsl, generated, old, new, 'compact radial center fade')

old = '    float edgeDist = -distMask;\n'
new = old + (
    '    // On large glass, suppress only the deep face plateau that can reveal the rounded-box\n'
    '    // height field as a bright rectangle. Edge Fresnel/rim lighting remains untouched.\n'
    '    float deepCenterFade = mix(1.0, 1.0 - 0.72 * smoothstep(minDim * 0.28, minDim * 0.72, edgeDist), largeGlass);\n'
)
glsl, generated = replace_both(glsl, generated, old, new, 'large center fade')

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
