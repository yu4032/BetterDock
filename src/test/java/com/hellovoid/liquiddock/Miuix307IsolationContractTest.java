package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307IsolationContractTest {
    private static String src(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", name));
    }

    @Test public void ordinaryDragApiKeepsLegacySemantics() throws Exception {
        String glass = src("DockLiquidGlassView.java");
        String ordinarySignature = "void setDockDragging(boolean dragging, String dragSurfaceLayerName)";
        int start = glass.indexOf(ordinarySignature);
        assertTrue(start >= 0);
        int overload = glass.indexOf("void setDockDragging(boolean dragging, String dragSurfaceLayerName,", start);
        assertTrue(overload > start);
        String ordinary = glass.substring(start, overload);
        assertFalse("ordinary path must not enter 307 Surface-aware overload",
                ordinary.contains("setDockDragging(dragging, dragSurfaceLayerName, null)"));
        assertTrue(ordinary.contains("dockDragging = dragging"));
        assertTrue(ordinary.contains("dragLayerName = dragging ? dragSurfaceLayerName : null"));
        assertTrue(ordinary.contains("requestStateCapture"));
        assertTrue(ordinary.contains("drag-start"));
    }

    @Test public void ordinaryContentBlurCleanupDoesNotClear307PassBlur() throws Exception {
        String bridge = src("MiBlurBridge.java");
        int start = bridge.indexOf("static void clearContentBlur(View view)");
        assertTrue(start >= 0);
        String method = bridge.substring(start);
        assertFalse("legacy cleanup must not mutate vendor pass-window blur",
                method.contains("clearPassWindowBlur(view)"));
        assertTrue(method.contains("if (!LEGACY_AVAILABLE || view == null) return"));
    }
}
