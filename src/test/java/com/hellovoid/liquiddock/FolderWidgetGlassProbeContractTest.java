package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contract for the read-only large-folder/widget host probe. */
public class FolderWidgetGlassProbeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path PROBE = MAIN.resolve("FolderWidgetGlassProbe.java");

    private static String probe() throws Exception {
        assertTrue("probe production source must exist", Files.exists(PROBE));
        return Files.readString(PROBE);
    }

    @Test public void probeCoversLargeFoldersAndBothWidgetHostFamilies() throws Exception {
        String source = probe();
        assertTrue(source.contains("FolderIcon4x4_16"));
        assertTrue(source.contains("FolderIcon3x3_9"));
        assertTrue(source.contains("FolderIcon2x2_4"));
        assertTrue(source.contains("FolderIcon2x2_9"));
        assertTrue(source.contains("LauncherAppWidgetHostView"));
        assertTrue(source.contains("com.miui.home.launcher.maml.MaMlHostView"));
    }

    @Test public void probeReportsLifecycleGeometryParentAndRadius() throws Exception {
        String source = probe();
        assertTrue(source.contains("addOnAttachStateChangeListener"));
        assertTrue(source.contains("onViewAttachedToWindow"));
        assertTrue(source.contains("onViewDetachedFromWindow"));
        assertTrue(source.contains("getLocationOnScreen"));
        assertTrue(source.contains("computeRoundedCornerRadius"));
        assertTrue(source.contains("parent="));
        assertTrue(source.contains("screenRect="));
        assertTrue(source.contains("radius="));
    }

    @Test public void probeNeverMutatesVisualState() throws Exception {
        String source = probe();
        assertFalse(source.contains("setBackground("));
        assertFalse(source.contains("setAlpha("));
        assertFalse(source.contains("setVisibility("));
        assertFalse(source.contains("addView("));
        assertFalse(source.contains("removeView("));
        assertFalse(source.contains("SetPassBlurSurface"));
    }

    @Test public void mainInstallsProbeAfterMasterSwitchGate() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        assertTrue(main.contains("FolderWidgetGlassProbe.install(classLoader)"));
    }
}
