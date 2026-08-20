from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "src/test/java/com/hellovoid/liquiddock/Miuix307TextureViewCoordinateParityTest.java"
text = PATH.read_text(encoding="utf-8")

pattern = re.compile(
    r'''        assertFalse\("producer allocation must never feed the window-frame mapping helper",.*?boundBufferHeight"\)\);''',
    re.S,
)
replacement = '''        assertTrue("sample mapping must be anchored to TextureView position plus resolved insets",
                region.contains("Miuix307BackdropMapping.Result sample = Miuix307BackdropMapping.compute(")
                        && region.contains("viewScreen[0] - insets.left")
                        && region.contains("viewScreen[1] - insets.top"));
        assertTrue("visible Dock mapping must be anchored to TextureView position",
                region.contains("Miuix307BackdropMapping.Result dock = Miuix307BackdropMapping.compute(")
                        && region.contains("viewScreen[0], viewScreen[1], visibleWidth, visibleHeight"));'''
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit(f"expected one generated allocation assertion block, found {count}")
PATH.write_text(text, encoding="utf-8")
print("fixed", PATH.relative_to(ROOT))
