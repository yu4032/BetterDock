package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression for 10x6 pages whose visual grid exceeds MIUI's stock drag geometry. */
public class HomeGridDragRangeTest {

    @Test
    public void liveCellPitchMapsTouchIntoAllTenPortraitRows() throws Exception {
        Class<?> policy = loadPolicy();
        Method pointToCell = policy.getDeclaredMethod("pointToCell",
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class);
        pointToCell.setAccessible(true);

        // 6x10 portrait geometry: the tenth row starts at top + 9 * (cell + gap).
        int[] rowTen = (int[]) pointToCell.invoke(null,
                45, 20 + 9 * (80 + 10) + 40,
                10, 20, 70, 80, 10, 10, 6, 10);
        assertArrayEquals(new int[]{0, 9}, rowTen);

        // A point in row eight must not collapse back into the stock first-seven-row domain.
        int[] rowEight = (int[]) pointToCell.invoke(null,
                45, 20 + 7 * (80 + 10) + 40,
                10, 20, 70, 80, 10, 10, 6, 10);
        assertArrayEquals(new int[]{0, 7}, rowEight);
    }

    @Test
    public void liveCellPitchConvertsLateCellsBackToTheirRealPixelOrigin() throws Exception {
        Class<?> policy = loadPolicy();
        Method cellToPoint = policy.getDeclaredMethod("cellToPoint",
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        cellToPoint.setAccessible(true);

        int[] point = (int[]) cellToPoint.invoke(null,
                2, 9, 10, 20, 70, 80, 10, 10);
        assertArrayEquals(new int[]{10 + 2 * 80, 20 + 9 * 90}, point);
    }

    @Test
    public void fourWideWidgetCanReachAlignedLateRowsWithoutLeavingTheGrid() throws Exception {
        Class<?> policy = loadPolicy();
        Method legal = policy.getDeclaredMethod("isSwapPlacementLegal",
                int.class, int.class, int.class, int.class, int.class, int.class);
        legal.setAccessible(true);

        // Portrait 6x10: stock MIUI only admits y=0/2 for spanX=4. 10x6 must extend this
        // to every aligned location that actually fits, including the final two rows.
        assertTrue((Boolean) legal.invoke(null, 0, 8, 4, 2, 6, 10));
        assertTrue((Boolean) legal.invoke(null, 2, 8, 4, 2, 6, 10));
        // Landscape 10x6 must likewise allow the far aligned block.
        assertTrue((Boolean) legal.invoke(null, 6, 4, 4, 2, 10, 6));

        assertFalse((Boolean) legal.invoke(null, 3, 8, 2, 2, 6, 10));
        assertFalse((Boolean) legal.invoke(null, 2, 9, 4, 2, 6, 10));
        assertFalse((Boolean) legal.invoke(null, 4, 8, 4, 2, 6, 10));
    }

    @Test
    public void productionUsesPerCellLayoutDragGeometryInsteadOfGlobalCellSize() throws Exception {
        Path hookPath = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/HomeGridDragGeometryHook.java");
        assertTrue("10x6 drag geometry needs a dedicated narrow owner", Files.exists(hookPath));
        String source = Files.readString(hookPath, StandardCharsets.UTF_8);

        assertTrue(source.contains("ThreadLocal"));
        assertTrue(source.contains("findDropTargetPosition"));
        assertTrue(source.contains("translateTouchY"));
        assertTrue("translateTouchY also reads DeviceConfig.getCellCountY; the live profile must own it",
                source.contains("\"getCellCountY\""));
        assertTrue(source.contains("LayoutDropRuleForSwapPlaces"));
        assertTrue(source.contains("cellToPoint"));
        assertTrue(source.contains("LayoutDropRuleSqueezePlaces"));
        assertTrue(source.contains("pointToCell"));
        assertTrue(source.contains("isLegalXY"));
        assertFalse(source.contains("setCellSize"));
        assertFalse(source.contains("setCountY"));
    }

    @Test
    public void moduleInstallsDragGeometryAfterProfileCounts() throws Exception {
        String module = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/ModuleMain.java"),
                StandardCharsets.UTF_8);
        int profile = module.indexOf("HomeGridProfileOverlayHook.install(classLoader);");
        int drag = module.indexOf("HomeGridDragGeometryHook.install(classLoader);");
        assertTrue(profile >= 0);
        assertTrue(drag > profile);
    }

    private static Class<?> loadPolicy() throws Exception {
        try {
            return Class.forName("com.hellovoid.liquiddock.HomeGridDragGeometryPolicy");
        } catch (ClassNotFoundException error) {
            fail("HomeGridDragGeometryPolicy must own pure drag-coordinate math");
            throw error;
        }
    }
}
