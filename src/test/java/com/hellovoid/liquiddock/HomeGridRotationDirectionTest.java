package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression for MIUI's native 6x4/4x6 transform direction latch. The launcher hard-codes
 * mIsVerticalCellCount = (mHCells != 4), which accidentally still works for 8x4/4x8 but
 * classifies both 10x6 and 6x10 as the same direction.
 */
public class HomeGridRotationDirectionTest {

    @Test
    public void stockSixByFourDirectionRemainsCompatible() {
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 4));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(4, 6));
    }

    @Test
    public void eightByFourDirectionRemainsCompatible() {
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(8, 4));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(4, 8));
    }

    @Test
    public void tenBySixAndSixByTenHaveOppositeDirections() {
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(10, 6));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 10));
    }

    @Test
    public void productionOverlayRearmsNativeDirectionBeforeBlockTransform() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("installRotationDirectionFix"));
        assertTrue(source.contains("get4x2WidgetCase"));
        assertTrue(source.contains("HomeGridRotationPolicy.sourceUsesHorizontalCoordinates"));
        assertTrue(source.contains("mIsVerticalCellCount"));
    }
}
