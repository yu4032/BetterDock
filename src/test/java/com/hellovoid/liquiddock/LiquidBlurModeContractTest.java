package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigCodec;
import com.hellovoid.liquiddock.config.ConfigSchema;
import com.hellovoid.liquiddock.config.PresetManager;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class LiquidBlurModeContractTest {
    @Test
    public void absentPreferenceExportsShaderAsCompatibilityDefault() {
        Map<String, Object> exported = ConfigCodec.exportValues(new HashMap<>());

        assertEquals("shader", exported.get("liquid_blur_mode"));
        assertEquals("shader", ConfigSchema.Glass.BLUR_MODE.uiDefault());
    }

    @Test
    public void advancedMaterialModeRoundTripsThroughJsonCodec() {
        Map<String, Object> json = new HashMap<>();
        json.put("liquid_blur_mode", "advanced_material");

        Map<String, Object> imported = ConfigCodec.importValues(json);
        assertEquals("advanced_material", imported.get("liquid_blur_mode"));

        Map<String, Object> exported = ConfigCodec.exportValues(imported);
        assertEquals("advanced_material", exported.get("liquid_blur_mode"));
    }

    @Test
    public void defaultPresetKeepsExistingShaderBehavior() {
        assertEquals("shader", PresetManager.defaultValues().get("liquid_blur_mode"));
    }

    @Test
    public void persistedModeParsingFailsClosedToShader() {
        assertEquals(LiquidBlurMode.SHADER, LiquidBlurMode.fromPersisted(null));
        assertEquals(LiquidBlurMode.SHADER, LiquidBlurMode.fromPersisted("unknown"));
        assertEquals(LiquidBlurMode.SHADER, LiquidBlurMode.fromPersisted("shader"));
        assertEquals(LiquidBlurMode.ADVANCED_MATERIAL,
                LiquidBlurMode.fromPersisted("advanced_material"));
        assertEquals("advanced_material", LiquidBlurMode.ADVANCED_MATERIAL.persistedValue());
    }
}
