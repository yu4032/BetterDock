package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression for the captured length=6,index=6 rotation crash. */
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
    public void productionOverlayRelatchesBeforeNativeBlockAndIconTransform() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("installRotationDirectionFix"));
        assertTrue(source.contains("get4x2WidgetCase"));
        assertTrue(source.contains("mIsVerticalCellCount"));
        assertTrue(source.contains("HomeGridRotationPolicy.sourceUsesHorizontalCoordinates"));
        assertFalse(source.contains("HomeGridTransformEngine.transform"));
    }

    private static void assertBlocksFit(int[][] blocks, int sourceColumns, int sourceRows) {
        for (int[] origin : blocks) {
            assertTrue(origin[0] >= 0 && origin[0] + 1 < sourceColumns);
            assertTrue(origin[1] >= 0 && origin[1] + 1 < sourceRows);
        }
    }
}
