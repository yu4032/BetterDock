package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import org.junit.Test;

public class HomeOwnershipShadowPolicyTest {
    @Test public void matchingBaselinesAgreeExactly() {
        assertTrue(HomeOwnershipShadowPolicy.matches(true, true));
        assertTrue(HomeOwnershipShadowPolicy.matches(false, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(true, false));
        assertFalse(HomeOwnershipShadowPolicy.matches(false, true));
    }

    @Test public void specialScenesAreNotMigrationEvidence() {
        assertTrue(HomeOwnershipShadowPolicy.baselineEligible(false, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(true, false, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, true, false));
        assertFalse(HomeOwnershipShadowPolicy.baselineEligible(false, false, true));
    }

    @Test public void recheckClassifiesConvergence() {
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.TRANSIENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, true));
        assertEquals(HomeOwnershipShadowPolicy.RecheckResult.PERSISTENT_MISMATCH,
                HomeOwnershipShadowPolicy.recheckResult(true, false));
    }

    @Test public void externalFullscreenTaskWinsOverStillVisibleHome() {
        assertEquals(HomeOwnershipShadowPolicy.SystemUiBaseline.APP,
                HomeOwnershipShadowPolicy.systemUiBaseline(true, 1, 1494));
    }

    @Test public void homeWithoutExternalFullscreenTaskIsHome() {
        assertEquals(HomeOwnershipShadowPolicy.SystemUiBaseline.HOME,
                HomeOwnershipShadowPolicy.systemUiBaseline(true, 1, -1));
        assertEquals(HomeOwnershipShadowPolicy.SystemUiBaseline.HOME,
                HomeOwnershipShadowPolicy.systemUiBaseline(true, 1, 1));
    }

    @Test public void externalFullscreenTaskIsAppEvenAfterHomeBecomesInvisible() {
        assertEquals(HomeOwnershipShadowPolicy.SystemUiBaseline.APP,
                HomeOwnershipShadowPolicy.systemUiBaseline(false, 1, 1494));
    }

    @Test public void missingHomeAndFullscreenEvidenceIsUnknown() {
        assertEquals(HomeOwnershipShadowPolicy.SystemUiBaseline.UNKNOWN,
                HomeOwnershipShadowPolicy.systemUiBaseline(false, 1, -1));
    }

    @Test public void combinedBaselineMatchesLauncherOnlyWhenKnown() {
        assertTrue(HomeOwnershipShadowPolicy.matchesLauncher(
                true, HomeOwnershipShadowPolicy.SystemUiBaseline.HOME));
        assertTrue(HomeOwnershipShadowPolicy.matchesLauncher(
                false, HomeOwnershipShadowPolicy.SystemUiBaseline.APP));
        assertFalse(HomeOwnershipShadowPolicy.matchesLauncher(
                true, HomeOwnershipShadowPolicy.SystemUiBaseline.APP));
        assertFalse(HomeOwnershipShadowPolicy.matchesLauncher(
                false, HomeOwnershipShadowPolicy.SystemUiBaseline.HOME));
        assertFalse(HomeOwnershipShadowPolicy.matchesLauncher(
                true, HomeOwnershipShadowPolicy.SystemUiBaseline.UNKNOWN));
    }
}
