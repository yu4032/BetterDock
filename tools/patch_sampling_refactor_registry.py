from pathlib import Path

path = Path(__file__).with_name("apply_sampling_safe_area_extra.py")
text = path.read_text()
needle = '''# Current preset: zero means pure automatic safe area.\n'''
insert = '''# ConfigSchema registry must move with the declarations.\nreplace(\n    "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java",\n    ''' + '"""' + '''                Glass.CAPTURE_BLEED_TOP, Glass.CAPTURE_BLEED_BOTTOM,\n                Glass.CAPTURE_BLEED_LEFT, Glass.CAPTURE_BLEED_RIGHT, Glass.THICKNESS,'''+ '"""' + ''',\n    ''' + '"""' + '''                Glass.SAMPLING_EXTRA_TOP, Glass.SAMPLING_EXTRA_BOTTOM,\n                Glass.SAMPLING_EXTRA_LEFT, Glass.SAMPLING_EXTRA_RIGHT, Glass.THICKNESS,'''+ '"""' + ''')\n\n'''
if insert in text:
    raise SystemExit("registry patch already present")
if needle not in text:
    raise SystemExit("insertion anchor missing")
path.write_text(text.replace(needle, insert + needle, 1))
print("patched ConfigSchema registry coverage into sampling refactor")
