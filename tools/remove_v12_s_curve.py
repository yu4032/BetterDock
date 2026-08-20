from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def path(rel):
    return ROOT / rel


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def rewrite(rel, transform):
    p = path(rel)
    old = p.read_text(encoding="utf-8")
    new = transform(old)
    if new == old:
        raise SystemExit(f"{rel}: no change")
    p.write_text(new, encoding="utf-8")
    print("updated", rel)


# UI: remove explanatory copy and the slider itself.
def ui_transform(s):
    s = replace_once(
        s,
        '    "liquid_legacy_s_curve" -> "旧版整片 SDF 折射几何：0=关闭，100=复现 v1.2.0，200=双倍"\n',
        '',
        'UI legacy S-curve summary',
    )
    s = replace_once(
        s,
        '    IntSpec(ConfigSchema.Glass.LEGACY_S_CURVE, "v1.2 S形折射", "%"),\n',
        '',
        'UI legacy S-curve slider',
    )
    return s

rewrite('src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt', ui_transform)


# Config schema: remove the key entirely so old JSON imports no longer map it into current prefs.
def schema_transform(s):
    s = replace_once(
        s,
        '        public static final ConfigKey<Integer> LEGACY_S_CURVE = integer(\n'
        '                "liquid_legacy_s_curve", 0, 0, 0, 0, 200, ConfigKey.ExportMode.ALWAYS);\n',
        '',
        'ConfigSchema legacy S-curve key',
    )
    s = replace_once(
        s,
        '        Glass.LEGACY_S_CURVE, Glass.CAPTURE_SCALE, Glass.DYNAMIC_APP_CAPTURE, Glass.FULLSCREEN_CAPTURE,\n',
        '                Glass.CAPTURE_SCALE, Glass.DYNAMIC_APP_CAPTURE, Glass.FULLSCREEN_CAPTURE,\n',
        'ConfigSchema key registry legacy S-curve',
    )
    return s

rewrite('src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java', schema_transform)


# Runtime typed config: remove the retired field and read.
def config_transform(s):
    s = replace_once(
        s,
        '        lensRefraction, depthEffect, legacySCurveStrength, highlightWidth, brightness,\n',
        '        lensRefraction, depthEffect, highlightWidth, brightness,\n',
        'LiquidDockConfig legacy field',
    )
    s = replace_once(
        s,
        '    legacySCurveStrength = c.i(ConfigSchema.Glass.LEGACY_S_CURVE.name(),\n'
        '            ConfigSchema.Glass.LEGACY_S_CURVE.runtimeFallback()) / 100f;\n',
        '',
        'LiquidDockConfig legacy read',
    )
    return s

rewrite('src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java', config_transform)


# Preset/default state must no longer recreate the retired preference.
def preset_transform(s):
    return replace_once(
        s,
        '        values.put("liquid_legacy_s_curve", 0);\n',
        '',
        'PresetManager legacy default',
    )

rewrite('src/main/java/com/hellovoid/liquiddock/config/PresetManager.java', preset_transform)


# Existing installs may still contain the old key; purge it before glass-generation detection.
def migration_transform(s):
    s = replace_once(
        s,
        '    public static void migrate(Context context, SharedPreferences preferences) {\n'
        '        resetUnsupportedGlassConfigGeneration(preferences);\n',
        '    public static void migrate(Context context, SharedPreferences preferences) {\n'
        '        removeRetiredGlassPreferences(preferences);\n'
        '        resetUnsupportedGlassConfigGeneration(preferences);\n',
        'ConfigMigration call ordering',
    )
    marker = '    /**\n     * Glass/Prismal values from development builds used several incompatible unit systems and\n'
    method = (
        '    private static void removeRetiredGlassPreferences(SharedPreferences sp) {\n'
        '        if (!sp.contains("liquid_legacy_s_curve")) return;\n'
        '        SharedPreferences.Editor e = sp.edit();\n'
        '        e.remove("liquid_legacy_s_curve");\n'
        '        e.commit();\n'
        '    }\n\n'
    )
    s = replace_once(s, marker, method + marker, 'ConfigMigration retired preference purge')
    return s

rewrite('src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java', migration_transform)


# Old compatibility material: delete retired constructor state, guard budget, and uniform uploads.
def material_transform(s):
    for old, new, label in [
        ('final float lensDepthEffect;\nfinal float legacySCurveStrength;\nfinal float legacyLensRefractionPx;\nfinal float legacyThicknessPx;\nfinal float chromaticAberration;\n',
         'final float lensDepthEffect;\nfinal float chromaticAberration;\n', 'material fields'),
        ('        float lensDepthEffect,\n        float legacySCurveStrength,\n        float legacyLensRefractionPx,\n        float legacyThicknessPx,\n        float chromaticAberration,\n',
         '        float lensDepthEffect,\n        float chromaticAberration,\n', 'material constructor parameters'),
        ('    this.lensDepthEffect = lensDepthEffect;\n    this.legacySCurveStrength = legacySCurveStrength;\n    this.legacyLensRefractionPx = legacyLensRefractionPx;\n    this.legacyThicknessPx = legacyThicknessPx;\n    this.chromaticAberration = chromaticAberration;\n',
         '    this.lensDepthEffect = lensDepthEffect;\n    this.chromaticAberration = chromaticAberration;\n', 'material assignments'),
        ('                1f,\n                0f,\n                12f * d,\n                18f * d,\n                26f,\n',
         '                1f,\n                26f,\n', 'material defaults legacy arguments'),
        ('                resolveLensDepth(glass.normalStrength, glass.depthEffect),\n                Math.max(0f, Math.min(2f, glass.legacySCurveStrength)),\n        12f * d,\n        18f * d,\n        Math.max(0f, glass.chromatic),\n',
         '                resolveLensDepth(glass.normalStrength, glass.depthEffect),\n        Math.max(0f, glass.chromatic),\n', 'material fromConfig legacy arguments'),
        ('        uniform1f(program, "u_lensDepthEffect", p.lensDepthEffect);\n        uniform1f(program, "u_legacySCurveStrength", p.legacySCurveStrength);\n        uniform1f(program, "u_legacyLensRefractionPx", p.legacyLensRefractionPx);\n        uniform1f(program, "u_legacyThicknessPx", p.legacyThicknessPx);\n\n',
         '        uniform1f(program, "u_lensDepthEffect", p.lensDepthEffect);\n\n', 'material legacy uniforms'),
    ]:
        s = replace_once(s, old, new, label)

    legacy_guard = re.compile(
        r'\n        float legacyLens = Math\.abs\(p\.legacyLensRefractionPx\).*?'
        r'\n        float dispersion =',
        re.DOTALL,
    )
    replacement = '\n        float baseReach = modernBase;\n\n        float dispersion ='
    s, count = legacy_guard.subn(replacement, s, count=1)
    if count != 1:
        raise SystemExit(f'material legacy sample guard: expected one block, found {count}')
    return s

rewrite('src/main/java/com/hellovoid/liquiddock/Miuix307PrismalMaterial.java', material_transform)


# Old hybrid shader retained for compatibility/reference: delete its v1.2 optical fork completely.
def shader_transform(s):
    s = replace_once(
        s,
        '    uniform float u_lensDepthEffect;\n'
        '    uniform float u_legacySCurveStrength;\n'
        '    uniform float u_legacyLensRefractionPx;\n'
        '    uniform float u_legacyThicknessPx;\n\n'
        '    uniform float u_chromaticAberration;\n',
        '    uniform float u_lensDepthEffect;\n\n'
        '    uniform float u_chromaticAberration;\n',
        'hybrid shader legacy uniforms',
    )
    legacy_block = re.compile(
        r'                vec2 currentOffset = lensDeltaUv \+ snellOff \+ bulgeUv;\n'
        r'        float legacyStrength = clamp\(u_legacySCurveStrength, 0\.0, 2\.0\);\n'
        r'        vec2 baseOffset = currentOffset;\n'
        r'        if \(legacyStrength > 0\.001\) \{.*?\n'
        r'        \}\n'
        r'        float pinchMix =',
        re.DOTALL,
    )
    replacement = (
        '                vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;\n'
        '        float pinchMix ='
    )
    s, count = legacy_block.subn(replacement, s, count=1)
    if count != 1:
        raise SystemExit(f'hybrid shader legacy optical block: expected one block, found {count}')
    return s

rewrite('src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java', shader_transform)


# Final production-source audit. The old preference name is allowed only in the one-way purge.
forbidden = [
    'LEGACY_S_CURVE', 'legacySCurveStrength', 'legacyLensRefractionPx', 'legacyThicknessPx',
    'u_legacySCurveStrength', 'u_legacyLensRefractionPx', 'u_legacyThicknessPx', 'v1.2 S形折射',
]
for root in [path('src/main'), path('prismal/src/main')]:
    if not root.exists():
        continue
    for file in root.rglob('*'):
        if not file.is_file():
            continue
        text = file.read_text(encoding='utf-8', errors='ignore')
        for token in forbidden:
            if token in text:
                raise SystemExit(f'retired token {token!r} remains in {file.relative_to(ROOT)}')

for root in [path('src/main'), path('prismal/src/main')]:
    if not root.exists():
        continue
    for file in root.rglob('*'):
        if not file.is_file() or file == path('src/main/java/com/hellovoid/liquiddock/config/ConfigMigration.java'):
            continue
        text = file.read_text(encoding='utf-8', errors='ignore')
        if 'liquid_legacy_s_curve' in text:
            raise SystemExit(f'retired preference remains outside migration purge: {file.relative_to(ROOT)}')

print('v1.2 S-curve production path fully retired')
