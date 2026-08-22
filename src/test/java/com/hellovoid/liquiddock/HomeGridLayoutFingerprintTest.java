package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeGridLayoutFingerprintTest {

    @Test
    public void fingerprintIsIndependentOfTraversalOrder() {
        HomeGridItemPosition first = pos(9, 2, 3, 1, 4, 2);
        HomeGridItemPosition second = pos(3, 1, 0, 0, 1, 1);

        long a = HomeGridLayoutFingerprint.of(Arrays.asList(first, second));
        long b = HomeGridLayoutFingerprint.of(Arrays.asList(second, first));

        assertEquals(a, b);
    }

    @Test
    public void fingerprintChangesForEveryPersistedPlacementField() {
        HomeGridItemPosition base = pos(7, 4, 2, 1, 2, 2);
        long fingerprint = HomeGridLayoutFingerprint.of(Arrays.asList(base));

        assertDifferent(fingerprint, pos(7, 5, 2, 1, 2, 2));
        assertDifferent(fingerprint, pos(7, 4, 3, 1, 2, 2));
        assertDifferent(fingerprint, pos(7, 4, 2, 2, 2, 2));
        assertDifferent(fingerprint, pos(7, 4, 2, 1, 3, 2));
        assertDifferent(fingerprint, pos(7, 4, 2, 1, 2, 3));
    }

    private static void assertDifferent(long fingerprint, HomeGridItemPosition changed) {
        assertTrue(fingerprint != HomeGridLayoutFingerprint.of(Arrays.asList(changed)));
    }

    private static HomeGridItemPosition pos(long id, long screenId,
                                            int x, int y, int spanX, int spanY) {
        return new HomeGridItemPosition(id, screenId, x, y, spanX, spanY);
    }
}
