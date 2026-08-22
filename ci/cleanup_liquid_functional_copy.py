from pathlib import Path

PATH = Path("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt")
text = PATH.read_text()

replacements = {
    '"liquid_thickness" -> "影响 Snell 折射路径的虚拟玻璃厚度"':
        '"liquid_thickness" -> "控制虚拟玻璃厚度对折射效果的影响"',
    '"liquid_normal_strength" -> "表面高度场对法线的影响"':
        '"liquid_normal_strength" -> "控制表面起伏对折射与光照的影响"',
    '"liquid_dome" -> "控制玻璃穹顶/meniscus 的凸起程度"':
        '"liquid_dome" -> "控制玻璃表面的凸起程度"',
    '"liquid_lens_refraction" -> "边缘透镜位移倍率；迁移后的常用值约 1.3×，过高会快速达到短边位移上限"':
        '"liquid_lens_refraction" -> "控制边缘折射位移倍率"',
    '"liquid_highlight_width" -> "同时调整 Fresnel 轮廓与边缘高光带宽"':
        '"liquid_highlight_width" -> "控制边缘反射与高光带宽度"',
    '"liquid_edge_band" -> "兼容调整 Prismal rim band 宽度"':
        '"liquid_edge_band" -> "控制边缘高光带宽度"',
    '"liquid_prismal_height_transition_width" -> "高度场从边缘过渡到中心的宽度"':
        '"liquid_prismal_height_transition_width" -> "控制玻璃表面从边缘到中心的高度过渡范围"',
    '"liquid_prismal_smin_smoothing" -> "圆角 SDF 的多项式平滑尺度"':
        '"liquid_prismal_smin_smoothing" -> "控制圆角边界的平滑程度"',
    '"liquid_prismal_fresnel_reflect" -> "Fresnel 背景反射与 sky haze 强度"':
        '"liquid_prismal_fresnel_reflect" -> "控制随观察角度增强的边缘反射强度"',
    '"liquid_prismal_shadow_softness" -> "内阴影柔和度；界面值按 ×100 存储，1000 对应 shader 10.0"':
        '"liquid_prismal_shadow_softness" -> "控制内阴影边缘的柔和程度"',
    '"liquid_prismal_transmittance" -> "玻璃最终透射/Alpha"':
        '"liquid_prismal_transmittance" -> "控制玻璃的透射与透明程度"',
}

for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one copy anchor, got {count}: {old}")
    text = text.replace(old, new, 1)

start = text.index("private fun optionSummary")
end = text.index("private val gridSpecs", start)
summaries = text[start:end]
if "Prismal" in summaries:
    raise RuntimeError("Prismal branding remains in setting summaries")

for forbidden in (
    "PassBlur → OES → Prismal zero-copy",
    "Launcher Prismal",
    "Prismal ·",
    "zero-copy 后端",
    "双通道 FBO",
    "v1.0.6 Quick Start",
):
    if forbidden in text:
        raise RuntimeError(f"forbidden Liquid UI implementation phrase remains: {forbidden}")

PATH.write_text(text)
print("Liquid settings copy is functional-only")
