from pathlib import Path

p = Path('src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java')
s = p.read_text()

old = '        if (existing != null && existing.getParent() == parent) return existing;\n'
new = (
    '        if (existing != null && existing.getParent() == parent) {\n'
    '            // MIUI can repopulate mIconImageView after our sink already exists. The material\n'
    '            // is above the sink in child order, so a restored drawable would cover Prismal.\n'
    '            clearVendorBlur(material);\n'
    '            makeMaterialTransparent(material);\n'
    '            return existing;\n'
    '        }\n'
)

if old not in s:
    raise SystemExit('existing-sink fast path not found')

p.write_text(s.replace(old, new, 1))
