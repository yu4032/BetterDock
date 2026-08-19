package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Static ownership contract for the narrow 10x6 runtime overlay. */
public class HomeGridProfileOverlayContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/HomeGridProfileOverlayHook.java");

    @Test
    public void overlayOwnsOnlyProfileSizedCountsAndRotationMetadata() throws Exception {
        assertTrue("10x6 overlay production source must exist", Files.exists(SOURCE));
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("getCellCountXMin"));
        assertTrue(source.contains("getCellCountXDef"));
        assertTrue(source.contains("getCellCountYMin"));
        assertTrue(source.contains("getCellCountYDef"));
        assertTrue(source.contains("setCountX"));
        assertTrue(source.contains("setCountY"));
        assertTrue(source.contains("getCountX"));
        assertTrue(source.contains("getCountY"));
        assertTrue(source.contains("profileRewriteForGridName"));

        assertTrue(source.contains("LayoutTransformRuleGridChanged"));
        assertTrue(source.contains("checkCellCount"));
        assertTrue(source.contains("get4x2WidgetCase"));
        assertTrue(source.contains("getDstBlockXY"));
        assertTrue(source.contains("HomeGridRotationPolicy"));
        assertTrue(source.contains("totalBlocks"));
    }

    @Test
    public void overlayIsInjectedWithTypedConfigAndFailsClosedOutsideWorkspace() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("install(ClassLoader classLoader, boolean customGridEnabled,"));
        assertTrue(source.contains("HomeGridProfile selectedProfile"));
        assertTrue(source.contains("selectedProfile != HomeGridProfile.GRID_10X6"));
        assertTrue(source.contains("MainHook.isWorkstationMode()"));
        assertTrue(source.contains("isExcludedGridConfigCall()"));
        assertTrue(source.contains(".folder."));
        assertTrue(source.contains("allapps"));
        assertTrue(source.contains(".laptop."));
        assertTrue(source.contains("hotseats"));
        assertTrue(source.contains("dockbar"));

        assertFalse("overlay must not create a second runtime config reader",
                source.contains("ConfigReader.load()"));
        assertFalse(source.contains("DockLiquidGlass"));
        assertFalse(source.contains("Miuix307"));
        assertFalse(source.contains("SystemUI"));
        assertFalse(source.contains("captureScreen"));
        assertFalse(source.contains("PassBlur"));
        assertFalse(source.contains("Prismal"));
    }
}
