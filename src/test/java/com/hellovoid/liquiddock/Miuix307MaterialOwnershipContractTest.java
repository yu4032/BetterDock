package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307MaterialOwnershipContractTest {
    private static String src(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
    }

    @Test public void nativeMaterialOwnsShellWhilePrismalOwnsBody() throws Exception {
        Path hookPath = Path.of("src/main/java/com/hellovoid/liquiddock/MiuixGlassHook.java");
        assertTrue(Files.exists(hookPath));
        String hook = Files.readString(hookPath);
        assertTrue(hook.contains("hasReadyNativeGeometry"));
        assertTrue(hook.contains("dockBg.isAttachedToWindow()"));
        assertTrue(hook.contains("dockBg.getWidth() <= 0 || dockBg.getHeight() <= 0"));
        assertTrue(hook.contains("radius > 0.5f"));
        assertTrue(hook.contains("if (!hasReadyNativeGeometry(dockBg)) return false"));
        assertTrue(hook.contains("materialHost.addView(host"));
        assertTrue(hook.contains("glass.setPreserveGeometrySourceVisuals(true)"));
        assertTrue(hook.contains("suppressVendorMaterialBody"));
        assertTrue(hook.contains("host.setGeometry(nativeRadius, false"));
        assertTrue(hook.contains("configureReplacingForeground"));
        assertFalse(hook.contains("dockBg.setAlpha(0f)"));
    }

    @Test public void nativeOpticsAndDecorativeStrokeAreSeparated() throws Exception {
        String hook = src("MiuixGlassHook.java");
        String host = src("DockLiquidGlassHostView.java");
        assertTrue(hook.contains("readNativeOpticsRadius"));
        assertTrue(hook.contains("mCornerRadius"));
        assertTrue(host.contains("reloadOpticsPreservingGeometry"));
        assertTrue(src("DockStrokeRenderer.java").contains("configureReplacingForeground"));
    }
}
