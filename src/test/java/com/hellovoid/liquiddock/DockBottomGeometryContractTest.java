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

public class DockBottomGeometryContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    @Test
    public void bottomOffsetPreservesStockReserveButMovesHotSeatsLayoutBounds() throws Exception {
        String owner = read("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue(owner.contains("installStockMarginFence"));
        assertTrue(owner.contains("DockBottomGeometryPolicy.stockMargin"));
        assertTrue("bottom offset must move HotSeats bounds after vendor layout",
                owner.contains("offsetTopAndBottom"));
        assertFalse("bottom offset must not rewrite parent layout params / reserve",
                owner.contains("setLayoutParams"));
    }

    @Test
    public void visibleHotSeatsLayoutOwnsBottomOffsetWithoutTranslationPropertyConflict() throws Exception {
        Path path = Paths.get("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("installVisualLayoutOwner"));
        assertTrue("owner must stay scoped to concrete HotSeats instances",
                source.contains("OnLayoutChangeListener"));
        assertTrue(source.contains("addOnLayoutChangeListener"));
        assertTrue(source.contains("offsetTopAndBottom"));
        assertTrue(source.contains("isLaptopDockHierarchy"));
        assertFalse("HotSeats translationY is animated by MIUI and cannot own a persistent offset",
                source.contains("setTranslationY"));
    }

    @Test
    public void layoutDeltaAndStockMarginMathAreStable() throws Exception {
        try {
            Class<?> policy = Class.forName("com.hellovoid.liquiddock.DockBottomGeometryPolicy");
            Method delta = policy.getDeclaredMethod("layoutDeltaY", int.class);
            delta.setAccessible(true);
            assertEquals(-24, ((Integer) delta.invoke(null, 24)).intValue());
            assertEquals(2, ((Integer) delta.invoke(null, -2)).intValue());
            Method stock = policy.getDeclaredMethod("stockMargin", int.class, int.class);
            stock.setAccessible(true);
            assertEquals(18, ((Integer) stock.invoke(null, 40, 22)).intValue());
            assertEquals(0, ((Integer) stock.invoke(null, 12, 22)).intValue());
        } catch (ClassNotFoundException error) {
            fail("DockBottomGeometryPolicy must exist");
        }
    }

    @Test
    public void ordinaryDockContainerIsNotLaptopHierarchy() throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.DockBottomGeometryPolicy");
        Method method = policy.getDeclaredMethod("isLaptopHierarchyClassName", String.class);
        method.setAccessible(true);
        assertFalse((Boolean) method.invoke(null,
                "com.miui.home.launcher.dock.DockContainerView$DockDragLayer"));
        assertTrue((Boolean) method.invoke(null,
                "com.miui.home.launcher.laptop.dockbar.DockContainerView"));
    }

    @Test
    public void finalOwnerDoesNotTrustGlobalWorkstationState() throws Exception {
        String source = read("src/main/java/com/hellovoid/liquiddock/DockBottomGeometryHook.java");
        int finalOwner = source.indexOf("installVisualLayoutOwner");
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
