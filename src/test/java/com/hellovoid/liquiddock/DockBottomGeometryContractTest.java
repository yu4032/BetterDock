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
    public void bottomOffsetNoLongerHooksDeviceConfigMarginGetter() throws Exception {
        String mainHook = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
        String material = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java"),
                StandardCharsets.UTF_8);

        assertFalse(mainHook.contains("getHotSeatsMarginBottom"));
        assertFalse(material.contains("getHotSeatsMarginBottom"));
    }

    @Test
    public void finalVisibleHotSeatsOwnsBottomOffset() throws Exception {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue("DockBottomGeometryHook must be the final visible-Y owner", Files.exists(path));
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("offsetTopAndBottom"));
        assertTrue(source.contains("HotSeats"));
        assertFalse(source.contains("getMingouLaptopDockBottomOffsetPx"));
        assertFalse(source.contains("isMingouLaptopPcModeEnabled"));
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
