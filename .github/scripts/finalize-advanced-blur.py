from pathlib import Path


def rep(path, old, new):
    p = Path(path)
    s = p.read_text()
    n = s.count(old)
    if n != 1:
        raise SystemExit(f"{path}: expected one match, got {n}: {old[:100]!r}")
    p.write_text(s.replace(old, new, 1))


# A Floating Dock window rebuild may detach and re-attach the same glass View.
rep(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    '''    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        captureGeneration++;''',
    '''    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL
                && !advancedMaterialUnavailableForProcess
                && !advancedMaterialActive) {
            updateBlurBackend();
        }
        captureGeneration++;''',
)
rep(
    "src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java",
    '''    @Override protected void onDetachedFromWindow() {
        attached = false;
        MiBlurBridge.clearContentBlur(this);
        cancelPendingCaptureWork();''',
    '''    @Override protected void onDetachedFromWindow() {
        attached = false;
        MiBlurBridge.clearContentBlur(this);
        advancedMaterialActive = false;
        activeBlurBackend = LiquidBlurMode.SHADER;
        cancelPendingCaptureWork();''',
)

rep(
    "CHANGELOG.md",
    "- 两条 `Launcher.setupViews()` 路径统一使用同一 Liquid Glass layer assembly；workstation 仍保持未完成适配状态",
    """- 两条 `Launcher.setupViews()` 路径统一使用同一 Liquid Glass layer assembly；workstation 仍保持未完成适配状态
- Floating Dock View detach 时清理 MIUI self-blur 状态；同一 View 重新 attach 后按保存的 advanced 请求自动重施""",
)

# Fail if the intended contract did not land exactly.
glass = Path("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java").read_text()
for needle in (
    "advancedMaterialActive = false;\n        activeBlurBackend = LiquidBlurMode.SHADER;",
    "requestedBlurMode == LiquidBlurMode.ADVANCED_MATERIAL\n                && !advancedMaterialUnavailableForProcess\n                && !advancedMaterialActive",
):
    if needle not in glass:
        raise SystemExit("missing lifecycle contract: " + needle)
