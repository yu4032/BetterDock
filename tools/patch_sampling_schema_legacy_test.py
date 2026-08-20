from pathlib import Path

path = Path("src/test/java/com/hellovoid/liquiddock/config/ConfigSchemaTest.java")
text = path.read_text()
old = '''        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.runtimeFallback());
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.uiDefault());
        assertEquals(Integer.valueOf(48), ConfigSchema.Glass.CAPTURE_BLEED_TOP.exportDefault());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.runtimeFallback());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.uiDefault());
        assertEquals(Integer.valueOf(16), ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.exportDefault());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_TOP.storageMode());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.CAPTURE_BLEED_BOTTOM.storageMode());
'''
new = '''        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_TOP.runtimeFallback());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_TOP.uiDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_TOP.exportDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.runtimeFallback());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.uiDefault());
        assertEquals(Integer.valueOf(0), ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.exportDefault());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.SAMPLING_EXTRA_TOP.storageMode());
        assertEquals(ConfigKey.StorageMode.DIRECT, ConfigSchema.Glass.SAMPLING_EXTRA_BOTTOM.storageMode());
'''
if old not in text:
    raise SystemExit("legacy sampling schema assertion block missing")
path.write_text(text.replace(old, new, 1))
print("migrated historical top/bottom sampling schema assertions")
