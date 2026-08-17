package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307CaptureCompatibilityContractTest {
    private static String src(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
    }

    @Test public void homePrearmUsesNative307Boundaries() throws Exception {
        String pipeline = src("Miuix307MaterialPipeline.java");
        assertTrue(pipeline.contains("com.miui.home.launcher.dock.v3.GestureToHome"));
        assertTrue(pipeline.contains("com.miui.home.recents.util.StateNotifyUtils"));
        assertTrue(pipeline.contains("MiuixGlassHook.onHomeTransitionStart()"));
        assertFalse(pipeline.contains("hasWindowFocus()"));
    }

    @Test public void dragAdapterUsesSurfaceWhenAvailableAndFreezesOtherwise() throws Exception {
        String drag = src("Miuix307DragCaptureHook.java");
        String glass = src("DockLiquidGlassView.java");
        assertTrue(drag.contains("startDragInDockForSystem"));
        assertTrue(drag.contains("setSystemDockDragActive(true)"));
        assertTrue(drag.contains("mDragViews"));
        assertTrue(drag.contains("views.getClass().isArray()"));
        assertTrue(drag.contains("SurfaceControl"));
        assertTrue(glass.contains("setSystemDockDragActive"));
        assertTrue(glass.contains("setDockDragging"));
    }
}
