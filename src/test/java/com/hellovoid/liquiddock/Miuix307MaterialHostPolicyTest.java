package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Miuix307MaterialHostPolicyTest {
    private static final String BLUR2 =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final String MIUIX =
            "com.miui.home.launcher.hotseats.HotSeatsListContentMiuiXBlurBackground";

    @Test
    public void bothHyperOsDockMaterialClassesSupportZeroCopyBackdrop() {
        assertTrue(Miuix307MaterialHostPolicy.supportsZeroCopyBackdrop(BLUR2));
        assertTrue(Miuix307MaterialHostPolicy.supportsZeroCopyBackdrop(MIUIX));
    }

    @Test
    public void onlyBlurBackground2UsesTheExactFrameworkBlurCompatPath() {
        assertTrue(Miuix307MaterialHostPolicy.usesExactBackgroundBlur(BLUR2));
        assertFalse(Miuix307MaterialHostPolicy.usesExactBackgroundBlur(MIUIX));
    }

    @Test
    public void unknownThemeMaterialFailsClosed() {
        assertFalse(Miuix307MaterialHostPolicy.supportsZeroCopyBackdrop(
                "com.example.UnknownDockMaterial"));
    }
}
