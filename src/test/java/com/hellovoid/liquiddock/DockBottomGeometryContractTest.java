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
    public void bottomOffsetNeverMutatesLayoutBoundsAndStockMarginIsFenced() throws Exception {
        String owner = read("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");

        // The compatibility fence may intercept the vendor getter, but it must reconstruct the
        // stock value independently of LiquidDock's bottomOffset. Actual feature ownership is Y
        // translation only; no measured/layout bounds may be moved.
        assertTrue(owner.contains("installStockMarginFence"));
        assertTrue(owner.contains("DockBottomGeometryPolicy.stockMargin"));
        assertFalse(owner.contains("offsetTopAndBottom"));
        assertFalse(owner.contains("setLayoutParams"));
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
            Method visual = policy.getDeclaredMethod("visualTranslationY", float.class, int.class);
            visual.setAccessible(true);
            assertEquals(-24f, (Float) visual.invoke(null, 0f, 24), 0.001f);
            assertEquals(76f, (Float) visual.invoke(null, 100f, 24), 0.001f);
            assertEquals(112f, (Float) visual.invoke(null, 100f, -12), 0.001f);

            Method stock = policy.getDeclaredMethod("stockMargin", int.class, int.class);
            stock.setAccessible(true);
            assertEquals(40, ((Integer) stock.invoke(null, 40, 0)).intValue());
            assertEquals(18, ((Integer) stock.invoke(null, 40, 22)).intValue());
            assertEquals(0, ((Integer) stock.invoke(null, 12, 22)).intValue());
        } catch (ClassNotFoundException error) {
            fail("DockBottomGeometryPolicy must own pure visual and stock-margin math");
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
