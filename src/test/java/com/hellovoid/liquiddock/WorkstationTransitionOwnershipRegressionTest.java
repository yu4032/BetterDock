package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for keeping normal HotSeats ownership separate from the Laptop overlay. */
public class WorkstationTransitionOwnershipRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void zeroCopyShadowStateAcceptsOnlyOrdinaryHotSeatsBackground() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        int sync = main.indexOf("static void syncDockShadow");
        int setOwner = main.indexOf("setOldBg(dockBg);", sync);
        int nextMethod = main.indexOf("private static void ensureShadowBelowBackground", sync);
        String body = main.substring(sync, nextMethod);

        assertTrue("307 shadow/alpha state must reject Laptop-overlay backgrounds before oldBg changes",
                body.contains("Miuix307MaterialPipeline.isOrdinaryHotSeatsBackground(dockBg)"));
        assertTrue("ownership gate must run before the normal background reference is replaced",
                body.indexOf("isOrdinaryHotSeatsBackground(dockBg)") < setOwner - sync);
        assertTrue("pipeline must expose an identity check against the launcher's ordinary HotSeats background",
                pipeline.contains("static boolean isOrdinaryHotSeatsBackground(View candidate)"));
        assertTrue("ordinary HotSeats identity must be retained even when setupViews runs in workstation mode",
                pipeline.indexOf("launcherRef = new WeakReference<>(launcher);")
                        < pipeline.indexOf("if (MainHook.isWorkstationMode()) return result;"));
    }

    @Test
    public void workstationBackgroundCannotBecomeNormalLayoutRestoreAnchor() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        int sync = main.indexOf("static void syncDockShadow");
        int owner = main.indexOf("setOldBg(dockBg);", sync);
        int guard = main.indexOf("isOrdinaryHotSeatsBackground(dockBg)", sync);
        assertTrue("normal-layout backup/restore depends on oldBg, so owner filtering must precede assignment",
                guard >= 0 && owner > guard);
        assertTrue("normal layout restore must still anchor from the retained ordinary Dock background",
                main.contains("View dockBg = oldBg();"));
    }
}
