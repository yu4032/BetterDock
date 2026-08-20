package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeGridGeometryPolicyTest {

    @Test
    public void automaticMarginIsNinePerMilleOfScreenWidth() {
        assertEquals(18, HomeGridGeometryPolicy.resolveMarginPx(2000, 0));
        assertEquals(23, HomeGridGeometryPolicy.resolveMarginPx(2560, 0));
        assertEquals(12, HomeGridGeometryPolicy.resolveMarginPx(1280, 12));
    }

    @Test
    public void edgeOffsetNeverMovesSquareGridOutsideHorizontalReserve() {
        HomeGridGeometryPolicy.Result base = HomeGridGeometryPolicy.compute(
                2400, 1600, 10, 6,
                0, 72, 0, 220,
                0, 18);
        HomeGridGeometryPolicy.Result inset = HomeGridGeometryPolicy.compute(
                2400, 1600, 10, 6,
                0, 72, 0, 220,
                80, 18);

        // Edge Offset is a symmetric minimum horizontal reserve. If the square grid
        // already has more natural free space than requested, the content does not
        // need to shrink just to manufacture a visible offset.
        assertTrue(inset.left >= base.left);
        assertTrue(inset.right >= base.right);
        assertEquals(inset.cellWidth, inset.cellHeight);
        assertEquals(base.top, inset.top);
        assertEquals(base.bottom, inset.bottom);
        assertEquals(base.cellHeight, inset.cellHeight);
        assertEquals(base.heightGap, inset.heightGap);
    }

    @Test
    public void marginIsTheActualCellGapOnBothAxes() {
        HomeGridGeometryPolicy.Result result = HomeGridGeometryPolicy.compute(
                2400, 1600, 10, 6,
                0, 72, 0, 220,
                40, 19);

        assertEquals(19, result.widthGap);
        assertEquals(19, result.heightGap);
    }

    @Test
    public void statusBarAndDockBoundTheAutomaticVerticalArea() {
        HomeGridGeometryPolicy.Result result = HomeGridGeometryPolicy.compute(
                2400, 1600, 10, 6,
                0, 96, 0, 260,
                0, 18);

        assertTrue(result.top >= 96);
        assertTrue(result.bottom >= 260);
        int occupiedBottom = result.top
                + result.cellHeight * 6
                + result.heightGap * 5;
        assertTrue(occupiedBottom <= 1600 - 260);
    }

    @Test
    public void tenBySixRemainsHorizontallyCenteredWithoutRightDrift() {
        HomeGridGeometryPolicy.Result result = HomeGridGeometryPolicy.compute(
                2560, 1600, 10, 6,
                0, 80, 0, 240,
                36, 23);

        assertTrue(Math.abs(result.left - result.right) <= 1);
    }

    @Test
    public void eightByFourUsesTheSameProfileNeutralGeometry() {
        HomeGridGeometryPolicy.Result result = HomeGridGeometryPolicy.compute(
                2560, 1600, 8, 4,
                0, 80, 0, 240,
                36, 23);

        assertEquals(23, result.widthGap);
        assertEquals(23, result.heightGap);
        assertTrue(result.cellWidth > 0);
        assertTrue(result.cellHeight > 0);
        assertEquals(result.cellWidth, result.cellHeight);
    }

    @Test
    public void launcherGridCellsRemainSquareWhenAxisBudgetsDiffer() {
        // HyperOS 3 Pad device evidence: 3008x1880 landscape GridConfig reserves
        // top=92, indicatorBarHeight=81, bottom=45 and dockBarHeight=230.
        // The lower reserved band is therefore 356px. Edge Offset was 152px and
        // automatic Margin resolves to 27px on this screen.
        HomeGridGeometryPolicy.Result result = HomeGridGeometryPolicy.compute(
                3008, 1880, 8, 4,
                0, 92, 0, 81 + 45 + 230,
                152, 27);

        assertEquals(result.cellWidth, result.cellHeight);
        assertEquals(27, result.widthGap);
        assertEquals(27, result.heightGap);
        assertTrue(result.left >= 152);
        assertTrue(result.right >= 152);
        assertTrue(result.top >= 92);
        int occupiedBottom = result.top
                + result.cellHeight * 4
                + result.heightGap * 3;
        assertTrue(occupiedBottom <= 1880 - (81 + 45 + 230));
    }
}
