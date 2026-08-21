package com.hellovoid.liquiddock.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GridProfileConfigTest {
    @Test public void normalizesOnlySupportedProfiles() {
        assertEquals("10x6", GridProfileConfig.normalizeProfile("10x6"));
        assertEquals("10x6", GridProfileConfig.normalizeProfile("10X6"));
        assertEquals("8x4", GridProfileConfig.normalizeProfile("8x4"));
        assertEquals("8x4", GridProfileConfig.normalizeProfile("bad"));
        assertEquals("8x4", GridProfileConfig.normalizeProfile(null));
    }
}
