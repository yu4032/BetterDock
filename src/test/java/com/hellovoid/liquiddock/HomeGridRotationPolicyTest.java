package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Pure policy for MIUI's transposed rotation matrices and 4x2 reserved blocks. */
public class HomeGridRotationPolicyTest {

    @Test
    public void targetCountsSelectTheTransposedSourceOrientation() {
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 10));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(10, 6));
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(4, 8));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(8, 4));
    }

    @Test
    public void ordinaryMappingIsIdentityWhenNoSpecialWidgetsAreReserved() {
        for (int source = 0; source < 15; source++) {
            assertEquals(source, HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                    10, 6, false, false, source));
            assertEquals(source, HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                    6, 10, false, false, source));
        }
    }

    @Test
    public void firstSpecialWidgetPreservesFreeBlockBijection() {
        assertFreeBlockBijection(10, 6, true, false);
        assertFreeBlockBijection(6, 10, true, false);
    }

    @Test
    public void secondSpecialWidgetPreservesFreeBlockBijection() {
        assertFreeBlockBijection(10, 6, false, true);
        assertFreeBlockBijection(6, 10, false, true);
    }

    @Test
    public void twoSpecialWidgetsPreserveFreeBlockBijection() {
        assertFreeBlockBijection(10, 6, true, true);
        assertFreeBlockBijection(6, 10, true, true);
    }

    @Test
    public void invalidDimensionsOrIndicesFailClosedToOriginalIndex() {
        assertEquals(-1, HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                10, 6, false, false, -1));
        assertEquals(15, HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                10, 6, false, false, 15));
        assertEquals(3, HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                9, 6, false, false, 3));
    }

    private static void assertFreeBlockBijection(int targetColumns, int targetRows,
                                                 boolean firstSpecial, boolean secondSpecial) {
        int sourceBlockColumns = targetRows / 2;
        int targetBlockColumns = targetColumns / 2;
        int total = (targetColumns / 2) * (targetRows / 2);
        Set<Integer> sourceReserved = reserved(total, sourceBlockColumns, firstSpecial, secondSpecial);
        Set<Integer> targetReserved = reserved(total, targetBlockColumns, firstSpecial, secondSpecial);
        Set<Integer> mapped = new HashSet<>();

        for (int source = 0; source < total; source++) {
            if (sourceReserved.contains(source)) continue;
            int target = HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                    targetColumns, targetRows, firstSpecial, secondSpecial, source);
            assertTrue(target >= 0 && target < total);
            assertFalse(targetReserved.contains(target));
            assertTrue("duplicate destination block " + target, mapped.add(target));
        }
        assertEquals(total - targetReserved.size(), mapped.size());
    }

    private static Set<Integer> reserved(int total, int secondRowStart,
                                         boolean firstSpecial, boolean secondSpecial) {
        Set<Integer> result = new HashSet<>();
        if (firstSpecial) {
            reservePair(result, total, 0);
        }
        if (secondSpecial) {
            reservePair(result, total, secondRowStart);
        }
        return result;
    }

    private static void reservePair(Set<Integer> set, int total, int start) {
        if (start >= 0 && start < total) set.add(start);
        if (start + 1 >= 0 && start + 1 < total) set.add(start + 1);
    }
}
