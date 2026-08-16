package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

public class FreeformBridgePolicyTest {
    @Test public void packageMembershipToleratesSharedUidLists() {
        assertTrue(FreeformBridgePolicy.packageListContains(
                new String[]{"android", "com.android.systemui"}, "com.android.systemui"));
        assertFalse(FreeformBridgePolicy.packageListContains(null, "com.android.systemui"));
        assertFalse(FreeformBridgePolicy.packageListContains(
                new String[]{"com.example"}, "com.android.systemui"));
    }

    @Test public void taskIdsAreDeduplicatedInStableOrder() {
        assertArrayEquals(new int[]{8, 3, 9},
                FreeformBridgePolicy.deduplicateTaskIds(new int[]{8, 3, 8, 9, 3}));
    }

    @Test public void breakerTripsOnlyAtThirdInfrastructureFailure() {
        FreeformBridgePolicy.CircuitBreaker breaker = new FreeformBridgePolicy.CircuitBreaker();
        assertFalse(breaker.isDisabled());
        assertFalse(breaker.recordInfrastructureFailure());
        assertFalse(breaker.recordInfrastructureFailure());
        assertTrue(breaker.recordInfrastructureFailure());
        assertTrue(breaker.isDisabled());
        assertEquals(3, breaker.failureCount());
    }
}
