from pathlib import Path

p = Path("tools/apply_prismal_module_refactor.py")
s = p.read_text()
marker = 'once(\n    "            if (gpuBackdropActive && !stageBDiagnosticsLogged)'
start = s.find(marker)
if start < 0:
    raise SystemExit("diagnostic replacement block not found")
end_marker = '\n\nbetween(\n    "    private void renderBlurPasses() {"'
end = s.find(end_marker, start)
if end < 0:
    raise SystemExit("diagnostic replacement block end not found")
new = r'''once(
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\n"
    "                stageBDiagnosticsLogged = true;\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\n"
    "            }",
    "            if (gpuBackdropActive && !stageBDiagnosticsLogged) {\n"
    "                stageBDiagnosticsLogged = true;\n"
    "                float[] matrixSnapshot = textureMatrix.clone();\n"
    "                post(() -> logStageBDiagnostics(matrixSnapshot));\n"
    "            }\n"
    "            if (gpuBackdropActive && !prismalMappingLogged) {\n"
    "                prismalMappingLogged = true;\n"
    "                logPrismalMapping(prismalGeometry);\n"
    "            }"
)'''
s = s[:start] + new + s[end:]
p.write_text(s)
print("fixed staged Prismal patch")
