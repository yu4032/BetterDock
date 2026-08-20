package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contract for the low-risk large-folder BackgroundBlurDrawable prototype. */
public class LargeFolderNativeBlurPrototypeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path PROTOTYPE = MAIN.resolve("LargeFolderNativeBlurPrototype.java");

    private static String prototype() throws Exception {
        assertTrue("prototype production source must exist", Files.exists(PROTOTYPE));
        return Files.readString(PROTOTYPE);
    }

    @Test public void prototypeTargetsCurrentPadLargeFolderVariants() throws Exception {
        String source = prototype();
        assertTrue(source.contains("FolderIcon4x4_16"));
        assertTrue(source.contains("FolderIcon3x3_9"));
        assertTrue(source.contains("FolderIcon2x2_4"));
        assertTrue(source.contains("FolderIcon2x2_9"));
        assertTrue(source.contains("onFinishInflate"));
        assertTrue(source.contains("mIconImageView"));
    }

    @Test public void prototypeUsesViewRootBackgroundBlurWithoutAnotherPassBlurProducer() throws Exception {
        String source = prototype();
        assertTrue(source.contains("createBackgroundBlurDrawable"));
        assertTrue(source.contains("setBlurRadius"));
        assertTrue(source.contains("setCornerRadius"));
        assertTrue(source.contains("addView(blurLayer, 0"));
        assertFalse(source.contains("SetPassBlurSurface"));
        assertFalse(source.contains("Miuix307PassBlurTextureView"));
    }

    @Test public void prototypeKeepsStockFolderPlateAsFailureAndLifecycleFallback() throws Exception {
        String source = prototype();
        assertTrue(source.contains("stockImage"));
        assertTrue(source.contains("showEditPanel"));
        assertTrue(source.contains("openFolder"));
        assertTrue(source.contains("closeFolder"));
        assertTrue(source.contains("setPrototypeVisible"));
        assertTrue(source.contains("restoreStock"));
    }

    @Test public void mainEnablesPrototypeOnlyInsideLiquidGlassPath() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        int glassGate = main.indexOf("if (config.glass.enabled)");
        int install = main.indexOf("LargeFolderNativeBlurPrototype.install(classLoader)");
        assertTrue("prototype must be installed", install >= 0);
        assertTrue("prototype must be gated by liquid glass", glassGate >= 0 && install > glassGate);
    }
}
