package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import org.junit.Test;

public class HomeOwnershipPolicyTest {
    @Test public void homeVisibleWithoutNonHomeFullscreenIsHome() {
        HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(true, 10, -1, false);
        assertEquals(HomeOwnershipPolicy.Baseline.HOME, result.baseline);
        assertFalse(result.retryRecommended);
    }

    @Test public void hiddenHomeIsAppWhenHomeTaskIsKnown() {
        HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(false, 10, -1, false);
        assertEquals(HomeOwnershipPolicy.Baseline.APP, result.baseline);
        assertFalse(result.retryRecommended);
    }

    @Test public void initialVisibleHomeConflictFailsClosedAndRequestsOneConfirmation() {
        HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(true, 10, 20, false);
        assertEquals(HomeOwnershipPolicy.Baseline.UNKNOWN, result.baseline);
        assertTrue(result.retryRecommended);
    }

    @Test public void confirmedVisibleHomeConflictMeansApp() {
        HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(true, 10, 20, true);
        assertEquals(HomeOwnershipPolicy.Baseline.APP, result.baseline);
        assertFalse(result.retryRecommended);
    }

    @Test public void missingHomeTaskAlwaysFailsClosed() {
        assertEquals(HomeOwnershipPolicy.Baseline.UNKNOWN,
                HomeOwnershipPolicy.classify(true, -1, 20, false).baseline);
        assertEquals(HomeOwnershipPolicy.Baseline.UNKNOWN,
                HomeOwnershipPolicy.classify(false, -1, -1, false).baseline);
    }

    @Test public void homeTaskAsTopFullscreenStillMeansHomeWhenVisible() {
        HomeOwnershipPolicy.Result result = HomeOwnershipPolicy.classify(true, 10, 10, false);
        assertEquals(HomeOwnershipPolicy.Baseline.HOME, result.baseline);
        assertFalse(result.retryRecommended);
    }
}
