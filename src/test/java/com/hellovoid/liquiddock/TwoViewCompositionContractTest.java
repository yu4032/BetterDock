package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class TwoViewCompositionContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", relative));
    }

    @Test public void overlayViewIsRemovedAndHostOwnsSharpLayer() throws Exception {
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockStrokeOverlayView.java")));
        String host = source("DockLiquidGlassHostView.java");
        assertTrue(host.contains("private static final String HIGHLIGHT_SHADER"));
        assertTrue(host.contains("DockStrokeRenderer.configure(this"));
        assertFalse(host.contains("DockStrokeOverlayView"));
    }

    @Test public void hostHasExactlyOneGlassChildContract() throws Exception {
        String host = source("DockLiquidGlassHostView.java");
        assertTrue(host.contains("void setLayers(DockLiquidGlassView glass)"));
        assertTrue(host.contains("addView(glass"));
        assertEquals(1, count(host, "addView("));

        String hook = source("MainHook.java");
        assertFalse(hook.contains("new DockStrokeOverlayView"));
        assertTrue(hook.contains("host.setLayers(glass)"));
    }

    @Test public void hostClipPathIsGeometryCached() throws Exception {
        String host = source("DockLiquidGlassHostView.java");
        assertTrue(host.contains("private boolean shapeDirty = true"));
        assertTrue(host.contains("ensureClipPath()"));
        int dispatch = host.indexOf("dispatchDraw(Canvas canvas)");
        int ensure = host.indexOf("ensureClipPath()", dispatch);
        int build = host.indexOf("DockShapePath.build", dispatch);
        assertTrue(dispatch >= 0 && ensure > dispatch);
        assertTrue("dispatchDraw must not rebuild shape directly", build < 0 || build < dispatch);
    }

    @Test public void strokeGeometryIsCachedUntilBoundsOrStyleChanges() throws Exception {
        String renderer = source("DockStrokeRenderer.java");
        assertTrue(renderer.contains("private boolean geometryDirty = true"));
        assertTrue(renderer.contains("ensureGeometry("));
        assertTrue(renderer.contains("geometryDirty = true"));
    }

    private static int count(String s, String needle) {
        int n = 0, at = 0;
        while ((at = s.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }
}
