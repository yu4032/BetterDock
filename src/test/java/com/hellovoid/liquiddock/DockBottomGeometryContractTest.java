package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Regression for Dock bottom offset depending on DeviceConfig/Mingou geometry internals. */
public class DockBottomGeometryContractTest {

    @Test
    public void legacyMarginDeltaIsNeutralizedInsteadOfOwningVisibleGeometry() throws Exception {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("getHotSeatsMarginBottom"));
        assertTrue(source.contains("PRIORITY_HIGHEST"));
        assertTrue(source.contains("result - bottomOffsetPx"));
    }

    @Test
    public void finalVisibleHotSeatsOwnsBottomOffset() throws Exception {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue("DockBottomGeometryHook must be the final visible-Y owner", Files.exists(path));
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("offsetTopAndBottom"));
        assertTrue(source.contains("HotSeats"));
        assertTrue(source.contains("isLaptopDockHierarchy"));
        assertTrue(source.contains("dockcontainerview"));
        assertFalse(source.contains("getMingouLaptopDockBottomOffsetPx"));
        assertFalse(source.contains("isMingouLaptopPcModeEnabled"));
    }

    @Test
    public void finalOwnerDoesNotTrustGlobalWorkstationState() throws Exception {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java"),
                StandardCharsets.UTF_8);
        int finalOwner = source.indexOf("installFinalHotSeatsOffset");
        int hierarchy = source.indexOf("isLaptopDockHierarchy", finalOwner);
        assertTrue(finalOwner >= 0);
        assertTrue(hierarchy > finalOwner);
        assertFalse(source.substring(finalOwner, hierarchy)
                .contains("if (MainHook.isWorkstationMode()) return result"));
    }

    @Test
    public void moduleInstallsBottomOwnerAfterLegacyMainHook() throws Exception {
        String module = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/ModuleMain.java"),
                StandardCharsets.UTF_8);
        int main = module.indexOf("new MainHook().install(classLoader);");
        int bottom = module.indexOf("DockBottomGeometryHook.install(classLoader);");
        assertTrue(main >= 0);
        assertTrue(bottom > main);
    }
}
