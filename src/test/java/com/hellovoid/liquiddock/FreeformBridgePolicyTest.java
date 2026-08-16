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

    @Test public void visibleCurrentDisplayCandidateIsIncluded() {
        assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, true, 0));
    }

    @Test public void knownInvisibleCandidateIsSkipped() {
        assertFalse(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, false, 0));
    }

    @Test public void knownOtherDisplayCandidateIsSkipped() {
        assertFalse(FreeformBridgePolicy.shouldIncludeFreeformCandidate(2, true, 0));
    }

    @Test public void unknownMetadataFailsClosedByIncludingCandidate() {
        assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(null, true, 0));
        assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(0, null, 0));
        assertTrue(FreeformBridgePolicy.shouldIncludeFreeformCandidate(null, null, 0));
    }

    @Test public void breakerTripsOnlyAtThirdRuntimeInfrastructureFailure() {
        FreeformBridgePolicy.CircuitBreaker breaker = new FreeformBridgePolicy.CircuitBreaker();
        assertFalse(breaker.isDisabled());
        assertFalse(breaker.recordInfrastructureFailure());
        assertFalse(breaker.recordInfrastructureFailure());
        assertTrue(breaker.recordInfrastructureFailure());
        assertTrue(breaker.isDisabled());
        assertEquals(3, breaker.failureCount());
    }

    @Test public void structuralInstallFailureCanDisableImmediately() {
        FreeformBridgePolicy.CircuitBreaker breaker = new FreeformBridgePolicy.CircuitBreaker();
        breaker.disableForProcess();
        assertTrue(breaker.isDisabled());
        assertEquals(3, breaker.failureCount());
    }
}
