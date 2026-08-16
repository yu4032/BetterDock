from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match for {old!r}, got {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# The preceding demo patch step has materialized the already device-validated
# configuration/UI/MainHook edits in this checkout. Promote those edits to stored source.
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    "Miuix307DemoPipeline.install(classLoader, config)",
    "Miuix307MaterialPipeline.install(classLoader, config)",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    "MiuiX 307 demo active; legacy liquid capture bypassed",
    "MiuiX 307 material active; legacy liquid capture bypassed",
)
replace_once(
    "src/main/java/com/hellovoid/liquiddock/MainHook.java",
    "MiuiX 307 demo unavailable; falling back to legacy pipeline",
    "MiuiX 307 material unavailable; falling back to legacy pipeline",
)

old_pipeline = Path("src/main/java/com/hellovoid/liquiddock/Miuix307DemoPipeline.java")
new_pipeline = Path("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java")
if not old_pipeline.exists() or new_pipeline.exists():
    raise SystemExit("unexpected MiuiX 307 pipeline file state")
pipeline_text = old_pipeline.read_text(encoding="utf-8")
pipeline_text = pipeline_text.replace("Miuix307DemoPipeline", "Miuix307MaterialPipeline")
pipeline_text = pipeline_text.replace("Opt-in demo adapter", "Opt-in material adapter")
pipeline_text = pipeline_text.replace("MiuiX 307 demo", "MiuiX 307 material")
new_pipeline.write_text(pipeline_text, encoding="utf-8")
old_pipeline.unlink()

highlight = Path("src/main/java/com/hellovoid/liquiddock/Miuix307HighlightView.java")
highlight_text = highlight.read_text(encoding="utf-8").replace(
    "demo pipeline", "material pipeline")
highlight.write_text(highlight_text, encoding="utf-8")

old_test = Path("src/test/java/com/hellovoid/liquiddock/Miuix307DemoPipelineContractTest.java")
new_test = Path("src/test/java/com/hellovoid/liquiddock/Miuix307MaterialPipelineContractTest.java")
if not old_test.exists() or new_test.exists():
    raise SystemExit("unexpected MiuiX 307 contract file state")
test_text = old_test.read_text(encoding="utf-8")
test_text = test_text.replace("Miuix307DemoPipelineContractTest", "Miuix307MaterialPipelineContractTest")
test_text = test_text.replace("Miuix307DemoPipeline.install", "Miuix307MaterialPipeline.install")
test_text = test_text.replace("Miuix307DemoPipeline.java", "Miuix307MaterialPipeline.java")
test_text = test_text.replace("material demo pipeline", "material pipeline")
test_text = test_text.replace("demoUsesNativeMiuixBlurWithoutCapturePipeline", "materialPipelineUsesNativeMiuixBlurWithoutCapturePipeline")
test_text = test_text.replace("demo pipeline source must exist", "material pipeline source must exist")
new_test.write_text(test_text, encoding="utf-8")
old_test.unlink()

workflow = Path(".github/workflows/api101-build.yml")
workflow_text = workflow.read_text(encoding="utf-8")
begin = "      # BEGIN MIUIX307_FORMALIZE\n"
end = "      # END MIUIX307_FORMALIZE\n"
start = workflow_text.find(begin)
finish = workflow_text.find(end)
if start < 0 or finish < 0 or finish < start:
    raise SystemExit("formalization workflow marker missing")
finish += len(end)
workflow_text = workflow_text[:start] + workflow_text[finish:]
workflow_text = workflow_text.replace("\npermissions:\n  contents: write\n", "\n")
workflow.write_text(workflow_text, encoding="utf-8")

Path(".github/patch_miuix307_demo.py").unlink()
Path(__file__).unlink()
print("MiuiX 307 demo promoted to source-native material pipeline")
