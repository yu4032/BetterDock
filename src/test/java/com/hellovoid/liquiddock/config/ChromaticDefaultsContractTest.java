package com.hellovoid.liquiddock.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChromaticDefaultsContractTest {
    @Test
    public void currentChromaticDefaultMatchesPrismalWhileHistoricalExportStaysTwo() {
        assertEquals(Integer.valueOf(26), ConfigSchema.Glass.CHROMATIC.uiDefault());
        assertEquals(Integer.valueOf(26), ConfigSchema.Glass.CHROMATIC.runtimeFallback());
        assertEquals(Integer.valueOf(2), ConfigSchema.Glass.CHROMATIC.exportDefault());
        assertEquals(Integer.valueOf(26), PresetManager.defaultValues().get("liquid_chromatic"));
    }
}
