package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression for the captured length=6,index=6 rotation crash and post-rotation drift. */
public class HomeGridRotationDirectionTest {

    @Test
    public void ruleTargetCountsSelectTheTransposedSourceOrientation() {
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 10));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(10, 6));
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(4, 6));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 4));
    }

    @Test
    public void selectedBlockCoordinatesFitTheActualTransposedSourceMatrix() {
        assertBlocksFit(HomeGridProfile.GRID_10X6.blockOrigins(false), 10, 6);
        assertBlocksFit(HomeGridProfile.GRID_10X6.blockOrigins(true), 6, 10);
    }

    @Test
    public void secondSpecialWidgetRemapIsBijectiveAcrossRemainingFifteenBlocks() {
        assertFreeBlockBijection(6, 10);  // 10x6 source -> 6x10 target
        assertFreeBlockBijection(10, 6);  // 6x10 source -> 10x6 target
    }

    @Test
    public void productionOverlayRelatchesBeforeNativeBlockAndIconTransform() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("installRotationDirectionFix"));
        assertTrue(source.contains("get4x2WidgetCase"));
        assertTrue(source.contains("mIsVerticalCellCount"));
        assertTrue(source.contains("HomeGridRotationPolicy.sourceUsesHorizontalCoordinates"));
        assertTrue(source.contains("installOtherWidgetBlockRemap"));
        assertTrue(source.contains("getDstBlockXY"));
        assertTrue(source.contains("HomeGridRotationPolicy.mapOtherWidgetBlockIndex"));
        assertFalse(source.contains("HomeGridTransformEngine.transform"));
    }

    private static void assertBlocksFit(int[][] blocks, int sourceColumns, int sourceRows) {
        for (int[] origin : blocks) {
            assertTrue(origin[0] >= 0 && origin[0] + 1 < sourceColumns);
            assertTrue(origin[1] >= 0 && origin[1] + 1 < sourceRows);
        }
    }

    private static void assertFreeBlockBijection(int targetColumns, int targetRows) {
        int sourceBlockColumns = targetRows / 2;
        int targetBlockColumns = targetColumns / 2;
        int total = (targetColumns / 2) * (targetRows / 2);
        Set<Integer> mapped = new HashSet<>();
        for (int source = 0; source < total; source++) {
            if (source == sourceBlockColumns || source == sourceBlockColumns + 1) continue;
            int target = HomeGridRotationPolicy.mapOtherWidgetBlockIndex(
                    targetColumns, targetRows, false, true, source);
            assertTrue(target >= 0 && target < total);
            assertTrue(target != targetBlockColumns && target != targetBlockColumns + 1);
            assertTrue("duplicate destination block " + target, mapped.add(target));
        }
        assertTrue(mapped.size() == total - 2);
    }
}
