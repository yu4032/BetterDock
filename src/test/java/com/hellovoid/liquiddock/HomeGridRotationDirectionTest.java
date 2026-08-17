package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression for 10x6/6x10 rotation ownership. */
public class HomeGridRotationDirectionTest {

    @Test
    public void ruleFieldsDescribeDestinationWhileSourceIsTranspose() {
        // LayoutTransformRule.init() allocates source as [mVCells][mHCells]
        // and destination as [mHCells][mVCells]. Therefore a 6x10 target
        // is transforming a 10x6 source, not a 6x10 source.
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(6, 10));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(10, 6));

        // Same invariant for the device-proven 8x4 core.
        assertTrue(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(4, 8));
        assertFalse(HomeGridRotationPolicy.sourceUsesHorizontalCoordinates(8, 4));
    }

    @Test
    public void tenBySixIconMappingRoundTripsEveryCell() throws Exception {
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 10; x++) {
                int[] portrait = mapIcon(x, y, 10, 6, 6, 10);
                int[] landscape = mapIcon(
                        portrait[0], portrait[1], 6, 10, 10, 6);
                assertArrayEquals(new int[]{x, y}, landscape);
            }
        }
    }

    @Test
    public void productionOverlayBypassesStockSixCellWidgetTransformForTenBySix()
            throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("transformToDstLayout"));
        assertTrue(source.contains("HomeGridTransformEngine.transform"));
        assertFalse(source.contains("get4x2WidgetCase"));
        assertFalse(source.contains("mIsVerticalCellCount"));
    }

    private static int[] mapIcon(int x, int y,
                                 int srcCols, int srcRows,
                                 int dstCols, int dstRows) throws Exception {
        try {
            Method method = HomeGridRotationPolicy.class.getDeclaredMethod(
                    "mapIconCell", int.class, int.class,
                    int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            return (int[]) method.invoke(null, x, y, srcCols, srcRows, dstCols, dstRows);
        } catch (NoSuchMethodException error) {
            fail("HomeGridRotationPolicy.mapIconCell must exist");
            throw error;
        }
    }
}
