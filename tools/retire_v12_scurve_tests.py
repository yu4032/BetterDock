from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def p(rel):
    return ROOT / rel


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def rewrite(rel, fn):
    file = p(rel)
    old = file.read_text(encoding='utf-8')
    new = fn(old)
    if new == old:
        raise SystemExit(f"{rel}: no change")
    file.write_text(new, encoding='utf-8')
    print('updated test', rel)


def glass_contract(s):
    pattern = re.compile(
        r'\n    @Test\n    public void legacy12SCurveIsExplicitOptInAndWiredEndToEnd\(\) throws Exception \{.*?\n    \}\n',
        re.DOTALL,
    )
    s, count = pattern.subn('\n', s, count=1)
    if count != 1:
        raise SystemExit(f'legacy S-curve opt-in test: expected one method, found {count}')
    s = replace_once(
        s,
        '                "u_heightTransitionWidth", "u_lensRefractionPx", "u_lensDepthEffect",\n'
        '                "u_legacySCurveStrength", "u_legacyLensRefractionPx", "u_legacyThicknessPx",\n'
        '                "u_chromaticAberration", "u_dispersionR", "u_dispersionB", "u_vibrancy",\n',
        '                "u_heightTransitionWidth", "u_lensRefractionPx", "u_lensDepthEffect",\n'
        '                "u_chromaticAberration", "u_dispersionR", "u_dispersionB", "u_vibrancy",\n',
        'visible optical uniform list',
    )
    s = replace_once(
        s,
        '                "glass.dome", "glass.lensRefraction", "glass.depthEffect",\n'
        '                "glass.legacySCurveStrength", "glass.chromatic", "glass.highlightWidth",\n',
        '                "glass.dome", "glass.lensRefraction", "glass.depthEffect",\n'
        '                "glass.chromatic", "glass.highlightWidth",\n',
        'visible optical config list',
    )
    return s

rewrite('src/test/java/com/hellovoid/liquiddock/Miuix307GlassCustomizationContractTest.java', glass_contract)


def mapping_test(s):
    return replace_once(
        s,
        '        assertTrue(source.contains("vec2 currentOffset = lensDeltaUv + snellOff + bulgeUv")\n'
        '                && source.contains("vec2 baseOffset = currentOffset"));\n',
        '        assertTrue(source.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv"));\n',
        'backdrop mapping active baseOffset contract',
    )

rewrite('src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewBackdropMappingTest.java', mapping_test)


def strong_test(s):
    return replace_once(
        s,
        '        assertTrue(source.contains("vec2 currentOffset = lensDeltaUv + snellOff + bulgeUv")\n'
        '                && source.contains("vec2 baseOffset = currentOffset"));\n',
        '        assertTrue(source.contains("vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv"));\n',
        'strong refraction active baseOffset contract',
    )

rewrite('src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewStrongRefractionTest.java', strong_test)


def codec_test(s):
    s = replace_once(s, '        assertEquals(129, exported.size());\n',
                     '        assertEquals(128, exported.size());\n',
                     'historical default export count')
    s = replace_once(s, '        assertEquals(0, exported.get("liquid_legacy_s_curve"));\n',
                     '        assertFalse(exported.containsKey("liquid_legacy_s_curve"));\n',
                     'retired key export assertion')
    return s

rewrite('src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java', codec_test)


def preset_test(s):
    return replace_once(s, '        expected.put("liquid_legacy_s_curve", 0);\n', '',
                        'retired preset key')

rewrite('src/test/java/com/hellovoid/liquiddock/config/ConfigPresetTest.java', preset_test)


def schema_test(s):
    return replace_once(
        s,
        '        assertComposeIntSpec(ConfigSchema.Glass.LEGACY_S_CURVE, 0, 0, 200);\n',
        '',
        'retired compose schema assertion',
    )

rewrite('src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java', schema_test)

print('legacy S-curve test contracts migrated')
