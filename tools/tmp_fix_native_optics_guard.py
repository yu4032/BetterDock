from pathlib import Path

p = Path('tools/tmp_fix_307_native_optics.py')
s = p.read_text()
old = '''s = once(s,\n''' + "'''" + '''        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\\n        host.reloadOpticsOnly(config.dock, config.glass);\\n''' + "'''" + ''',\n''' + "'''" + '''        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);\\n        host.reloadOpticsPreservingGeometry(config.glass);\\n''' + "'''" + ''',\n'install host native geometry')'''
new = '''old_install_geometry = ''' + "'''" + '''        host.setGeometry(glassRadius, config.dock.squircle, config.dock.squircleCp);\\n        host.reloadOpticsOnly(config.dock, config.glass);\\n''' + "'''" + '''\nnew_install_geometry = ''' + "'''" + '''        host.setGeometry(nativeRadius, false, SQUIRCLE_CP);\\n        host.reloadOpticsPreservingGeometry(config.glass);\\n''' + "'''" + '''\nif s.count(old_install_geometry) != 2:\n    raise SystemExit(f'install host native geometry: expected 2, found {s.count(old_install_geometry)}')\ns = s.replace(old_install_geometry, new_install_geometry, 1)'''
if old not in s:
    raise SystemExit('target guard block not found')
p.write_text(s.replace(old, new, 1))
print('relaxed duplicate install/sync geometry guard')
