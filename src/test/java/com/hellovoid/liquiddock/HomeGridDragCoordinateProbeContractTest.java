package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HomeGridDragCoordinateProbeContractTest {
    @Test public void tenBySixProbeObservesNearestVacantAreaBoundaryInputs() throws Exception {
        String entry = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"));
        String probe = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridDragCoordinateProbe.java"));

        assertTrue(entry.contains("HomeGridDragCoordinateProbe.install(classLoader"));
        assertTrue(probe.contains("findNearestVacantArea"));
        assertTrue(probe.contains("mTotalCells"));
        assertTrue(probe.contains("mLayoutDropRule"));
        assertTrue(probe.contains("isCellOccupied"));
        assertTrue(probe.contains("getRotation()"));
        assertTrue(probe.contains("rows789="));
    }
}
