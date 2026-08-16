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
}
