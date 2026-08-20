from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/Miuix307PrismalShader.java")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected one match, found {count}: {old!r}")
    text = text.replace(old, new, 1)


replace_once(
    """            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {\n""",
    """            // Upstream Prismal computes optical displacement in a texture basis whose Y\n            // axis is flipped in the vertex shader. Our normalized zero-copy FBO keeps local\n            // bottom-origin UV, so convert displacement vectors here without flipping the image.\n            vec2 upstreamOffsetToLocalTextureUv(vec2 offset) {\n                return vec2(offset.x, -offset.y);\n            }\n\n            vec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {\n""",
)
replace_once(
    """                vec2 dockUv = scaled + offset;\n""",
    """                vec2 dockUv = scaled + upstreamOffsetToLocalTextureUv(offset);\n""",
)
replace_once(
    """                vec2 reflDockUv = v_screenTexCoord + baseOffset\n                    + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm;\n                vec2 reflUv = mapDockUvToBackdrop(reflDockUv);\n""",
    """                vec2 reflOffset = baseOffset\n                    + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm;\n                vec2 reflDockUv = v_screenTexCoord\n                    + upstreamOffsetToLocalTextureUv(reflOffset);\n                vec2 reflUv = mapDockUvToBackdrop(reflDockUv);\n""",
)

# Guard the intended coordinate contract: one basis conversion, no whole-image flip, no overscan sign flip.
required = [
    "vec2 upstreamOffsetToLocalTextureUv(vec2 offset)",
    "return vec2(offset.x, -offset.y);",
    "vec2 dockUv = scaled + upstreamOffsetToLocalTextureUv(offset);",
    "upstreamOffsetToLocalTextureUv(reflOffset)",
    "u_dockUvRect.xy + dockUv * u_dockUvRect.zw",
]
for token in required:
    if token not in text:
        raise SystemExit(f"missing required coordinate contract: {token}")
for forbidden in (
    "v_screenTexCoord = vec2(aUv.x, 1.0 - aUv.y);",
    "u_dockUvRect.xy + vec2(dockUv.x, -dockUv.y) * u_dockUvRect.zw",
):
    if forbidden in text:
        raise SystemExit(f"forbidden broad coordinate flip: {forbidden}")

path.write_text(text)
