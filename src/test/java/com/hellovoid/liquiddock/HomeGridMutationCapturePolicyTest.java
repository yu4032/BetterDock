package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeGridMutationCapturePolicyTest {

    @Test
    public void firstStableLayoutCapturesButDuplicateDoesNot() {
        HomeGridMutationCapturePolicy policy = new HomeGridMutationCapturePolicy();

        assertTrue(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 100L));
        assertFalse(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 100L));
        assertTrue(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 101L));
    }

    @Test
    public void transitionSuppressesIntermediateLayoutsAndSeedsFinalBaseline() {
        HomeGridMutationCapturePolicy policy = new HomeGridMutationCapturePolicy();
        assertTrue(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 10L));

        policy.beginTransition();
        assertFalse(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 20L));
        assertFalse(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 21L));

        policy.endTransition(HomeGridOrientation.PORTRAIT, 30L);
        assertFalse(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 30L));
        assertTrue(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 31L));
    }

    @Test
    public void orientationFingerprintsAreIndependent() {
        HomeGridMutationCapturePolicy policy = new HomeGridMutationCapturePolicy();

        assertTrue(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 42L));
        assertTrue(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 42L));
        assertFalse(policy.shouldCapture(HomeGridOrientation.LANDSCAPE, 42L));
        assertFalse(policy.shouldCapture(HomeGridOrientation.PORTRAIT, 42L));
    }

    @Test
    public void nullOrientationNeverCaptures() {
        HomeGridMutationCapturePolicy policy = new HomeGridMutationCapturePolicy();
        assertFalse(policy.shouldCapture(null, 1L));
    }
}
