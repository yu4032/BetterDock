from pathlib import Path
p = Path("tools/apply_prismal_module_refactor.py")
s = p.read_text()
old = '''once(
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\\n"
    "                stageBDiagnosticsLogged = true;\\n"
    "        prismalMappingLogged = false;\\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\\n"
    "            }",
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\\n"
    "                stageBDiagnosticsLogged = true;\\nn"
    "                prismalMappingLogged = false;\\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\\n"
    "            }\\n"
    "            if (gpuBackdropActive && !prismalMappingLogged) {\\n"
    "                prismalMappingLogged = true;\\n"
    "                logPrismalMapping(prismalGeometry);\\n"
    "            }"
)
'''
# Match the actual staged block directly; keep this script intentionally tiny and disposable.
start = s.index('once(\n    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\\\\n"')
end = s.index('\n\nbetween(\n    "    private void renderBlurPasses() {"', start)
new = '''once(
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\\n"
    "                stageBDiagnosticsLogged = true;\\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\\n"
    "            }",
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\\n"
    "                stageBDiagnosticsLogged = true;\\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\\n"
    "            }\\n"
    "            if (gpuBackdropActive && !prismalMappingLogged) {\\n"
    "                prismalMappingLogged = true;\\n"
    "                logPrismalMapping(prismalGeometry);\\n"
    "            }"
)
'''
s = s[:start] + new + s[end:]
p.write_text(s)
print("fixed staged patch script")
