from pathlib import Path

p = Path('src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java')
s = p.read_text()

replacements = [
    ('dockBg, workspace, config.glass, config.dock, false, SQUIRCLE_CP);',
     'dockBg, workspace, config.glass, config.dock, true, SQUIRCLE_CP);'),
    ('host.setGeometry(radius, false, SQUIRCLE_CP);',
     'host.setGeometry(radius, true, SQUIRCLE_CP);'),
]

old, new = replacements[0]
if s.count(old) != 1:
    raise SystemExit(f'factory squircle token count={s.count(old)}')
s = s.replace(old, new, 1)

old, new = replacements[1]
if s.count(old) != 2:
    raise SystemExit(f'host squircle token count={s.count(old)}')
s = s.replace(old, new)

p.write_text(s)

if 'config.dock, false, SQUIRCLE_CP' in s or 'host.setGeometry(radius, false, SQUIRCLE_CP)' in s:
    raise SystemExit('old 307 rounded-rect token remains')
print('enabled squircle in factory + initial host geometry + sync geometry')
