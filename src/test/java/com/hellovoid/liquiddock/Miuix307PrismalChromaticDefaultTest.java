package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Miuix307PrismalChromaticDefaultTest {
    @Test
    public void prismalMaterialDefaultChromaticStrengthMatchesEffectiveQuickStart() {
        assertEquals(26f, Miuix307PrismalMaterial.defaults(1f).chromaticAberration, 0.0001f);
    }

    @Test
    public void emptyProfileAlsoResolvesToEffectiveQuickStartChromaticStrength() {
        LiquidDockConfig config = LiquidDockConfig.from(new ConfigReader(Collections.emptyMap()));
        assertEquals(26f,
                Miuix307PrismalMaterial.fromConfig(config.glass, 1f).chromaticAberration,
                0.0001f);
    }
}
