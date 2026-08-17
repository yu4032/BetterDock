package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Regression for Dock bottom offset changing Workspace reserved geometry. */
public class DockBottomGeometryContractTest {

    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void bottomOffsetNeverMutatesVendorMarginOrLayoutBounds() throws Exception {
        String owner = read("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        String main = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        String miuix = read("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java");

        // getHotSeatsMarginBottom participates in both HotSeats bottomMargin and
        // DeviceConfig.getDockWindowHeight(), so touching it changes Workspace/indicator reserve.
        assertFalse(owner.contains("getHotSeatsMarginBottom"));
        assertFalse(main.contains("getHotSeatsMarginBottom"));
        assertFalse(miuix.contains("getHotSeatsMarginBottom"));

        // offsetTopAndBottom mutates actual layout bounds. The feature must be visual-only.
        assertFalse(owner.contains("offsetTopAndBottom"));
    }

    @Test
    public void visibleHotSeatsTranslationOwnsBottomOffset() throws Exception {
        Path path = Paths.get(
                "src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue("DockBottomGeometryHook must remain the visual-Y owner", Files.exists(path));
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("setTranslationY"));
        assertTrue(source.contains("HotSeats"));
        assertTrue(source.contains("isLaptopDockHierarchy"));
        assertTrue(source.contains("chain.getArg(0)"));
        assertTrue(source.contains("chain.proceed(args)"));
    }

    @Test
    public void positiveBottomOffsetMovesOrdinaryDockUpWithoutChangingVendorAnimationDelta()
            throws Exception {
        try {
            Class<?> policy = Class.forName("com.hellovoid.liquiddock.DockBottomGeometryPolicy");
            Method method = policy.getDeclaredMethod("visualTranslationY", float.class, int.class);
            method.setAccessible(true);
            assertEquals(-24f, (Float) method.invoke(null, 0f, 24), 0.001f);
            assertEquals(76f, (Float) method.invoke(null, 100f, 24), 0.001f);
            assertEquals(112f, (Float) method.invoke(null, 100f, -12), 0.001f);
        } catch (ClassNotFoundException error) {
            fail("DockBottomGeometryPolicy must own pure visual translation math");
        }
    }

    @Test
    public void finalOwnerDoesNotTrustGlobalWorkstationState() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        int finalOwner = source.indexOf("installVisualTranslationOwner");
        int hierarchy = source.indexOf("isLaptopDockHierarchy", finalOwner);
        assertTrue(finalOwner >= 0);
        assertTrue(hierarchy > finalOwner);
        assertFalse(source.substring(finalOwner, hierarchy)
                .contains("if (MainHook.isWorkstationMode()) return result"));
    }

    @Test
    public void moduleInstallsBottomOwnerAfterMainHook() throws Exception {
        String module = read("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        int main = module.indexOf("new MainHook().install(classLoader);");
        int bottom = module.indexOf("DockBottomGeometryHook.install(classLoader);");
        assertTrue(main >= 0);
        assertTrue(bottom > main);
    }
}
