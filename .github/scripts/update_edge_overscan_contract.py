from pathlib import Path

path = Path("src/test/java/com/hellovoid/liquiddock/Miuix307EdgeOverscanContractTest.java")
text = path.read_text()
old = '''        assertTrue("normalization FBO must include an overscan ring around the visible Dock",\n                view.contains("EDGE_OVERSCAN_DP")\n                        && view.contains("overscanPx")\n                        && view.contains("uDockUvRect"));'''
new = '''        assertTrue("normalization FBO must keep the 32dp base and support asymmetric horizontal overscan",\n                view.contains("EDGE_OVERSCAN_DP")\n                        && view.contains("horizontalOverscanPx()")\n                        && view.contains("leftOverscanPx")\n                        && view.contains("rightOverscanPx")\n                        && view.contains("uDockUvRect"));'''
if text.count(old) != 1:
    raise SystemExit("edge overscan contract anchor mismatch")
path.write_text(text.replace(old, new, 1))
print("edge overscan contract updated")
