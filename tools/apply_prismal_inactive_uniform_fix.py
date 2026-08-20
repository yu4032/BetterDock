from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RENDERER = ROOT / "prismal/src/main/java/com/hellovoid/prismal/PrismalRenderer.java"
TEST = ROOT / "src/test/java/com/hellovoid/liquiddock/PrismalModuleBoundaryContractTest.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)

renderer = RENDERER.read_text(encoding="utf-8")
renderer = replace_once(
    renderer,
    '        uniform1f("u_edgeRefractionFalloff", p.edgeRefractionFalloff);\n',
    '        uniform1fIfActive("u_edgeRefractionFalloff", p.edgeRefractionFalloff);\n',
    "edgeRefractionFalloff optional upload",
)
renderer = replace_once(
    renderer,
    '        uniform1f("u_highlightWidth", p.highlightWidth);\n',
    '        uniform1fIfActive("u_highlightWidth", p.highlightWidth);\n',
    "highlightWidth optional upload",
)
needle = '''    private void uniform1f(String name, float value) {\n        GLES20.glUniform1f(requireUniform(glassProgram, name), value);\n    }\n'''
replacement = needle + '''    /**\n     * Upload a declared upstream uniform only when the linker kept it active. GLSL is allowed to\n     * optimize declarations that do not participate in the final program, in which case\n     * glGetUniformLocation() returns -1. Keep this helper scoped to known upstream-inactive\n     * declarations; all optical uniforms that affect output still use requireUniform().\n     */\n    private void uniform1fIfActive(String name, float value) {\n        int location = GLES20.glGetUniformLocation(glassProgram, name);\n        if (location >= 0) GLES20.glUniform1f(location, value);\n    }\n'''
renderer = replace_once(renderer, needle, replacement, "add inactive-uniform helper")
RENDERER.write_text(renderer, encoding="utf-8")

test = TEST.read_text(encoding="utf-8")
needle = '        assertTrue(renderer.contains("PrismalShaderSources.FRAGMENT"));\n'
replacement = needle + (
    '        assertTrue(renderer.contains("uniform1fIfActive(\\\"u_edgeRefractionFalloff\\\""));\n'
    '        assertTrue(renderer.contains("uniform1fIfActive(\\\"u_highlightWidth\\\""));\n'
    '        assertTrue(renderer.contains("if (location >= 0) GLES20.glUniform1f(location, value)"));\n'
)
test = replace_once(test, needle, replacement, "inactive-uniform contract")
TEST.write_text(test, encoding="utf-8")

print("patched", RENDERER.relative_to(ROOT))
print("patched", TEST.relative_to(ROOT))
