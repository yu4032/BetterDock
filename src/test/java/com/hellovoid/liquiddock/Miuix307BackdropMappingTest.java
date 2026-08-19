package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Miuix307BackdropMappingTest {
    private static final float EPS = 0.0001f;

    @Test
    public void fullyCoveredDockKeepsUnclampedBackdropAndFullValidity() {
        Miuix307BackdropMapping.Result r = Miuix307BackdropMapping.compute(
                786, 1619, 1435, 221,
                0, 1382, 3008, 498);

        assertEquals(0.2613032f, r.backdropX, EPS);
        assertEquals(0.0803213f, r.backdropY, EPS);
        assertEquals(0.4770612f, r.backdropW, EPS);
        assertEquals(0.4437751f, r.backdropH, EPS);
        assertEquals(0f, r.validLeft, EPS);
        assertEquals(0f, r.validBottom, EPS);
        assertEquals(1f, r.validRight, EPS);
        assertEquals(1f, r.validTop, EPS);
        assertEquals(Miuix307BackdropMapping.Coverage.FULL, r.coverage);
    }

    @Test
    public void bottomOverflowStaysNegativeButMarksOnlyAvailableRowsValid() {
        Miuix307BackdropMapping.Result r = Miuix307BackdropMapping.compute(
                786, 1663, 1435, 221,
                0, 1382, 3008, 498);

        assertEquals(-0.0080321f, r.backdropY, EPS);
        assertEquals(4f / 221f, r.validBottom, EPS);
        assertEquals(1f, r.validTop, EPS);
        assertEquals(Miuix307BackdropMapping.Coverage.PARTIAL, r.coverage);
    }

    @Test
    public void topOverflowMarksTopRowsUnavailableInDockLocalUv() {
        Miuix307BackdropMapping.Result r = Miuix307BackdropMapping.compute(
                786, 1360, 1435, 221,
                0, 1382, 3008, 498);

        assertEquals(0f, r.validBottom, EPS);
        assertEquals(1f - 22f / 221f, r.validTop, EPS);
        assertEquals(Miuix307BackdropMapping.Coverage.PARTIAL, r.coverage);
    }

    @Test
    public void fullyOutsideDockHasEmptyValidityInsteadOfEdgeClamp() {
        Miuix307BackdropMapping.Result r = Miuix307BackdropMapping.compute(
                786, 1910, 1435, 221,
                0, 1382, 3008, 498);

        assertEquals(Miuix307BackdropMapping.Coverage.OUTSIDE, r.coverage);
        assertEquals(0f, r.validLeft, EPS);
        assertEquals(0f, r.validBottom, EPS);
        assertEquals(0f, r.validRight, EPS);
        assertEquals(0f, r.validTop, EPS);
    }
}