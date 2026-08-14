package com.hellovoid.liquiddock.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigStorageCompatibilityTest {
    @Test
    public void historicalStorageModesRemainCompatible() {
        assertEquals(ConfigKey.StorageMode.DIRECT,
                ConfigSchema.Divider.WIDTH_DP.storageMode());
        assertEquals(ConfigKey.StorageMode.DIRECT,
                ConfigSchema.Divider.Y_OFFSET_DP.storageMode());
        assertEquals(ConfigKey.StorageMode.DP_TENTHS,
                ConfigSchema.Glass.HOME_SETTLE_DELAY_MS.storageMode());
    }
}
