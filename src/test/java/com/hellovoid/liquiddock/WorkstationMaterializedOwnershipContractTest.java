package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Contracts for the final materialized 307 runtime. Raw feature sources may not yet contain the
 * shared-glass shadow path, so the ownership assertions become active once that path is present.
 */
public class WorkstationMaterializedOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test
    public void zeroCopyDockStateRejectsLaptopOverlayMaterial() throws Exception {
        String main = read("MainHook.java");
        String pipeline = read("Miuix307MaterialPipeline.java");
        if (!main.contains("static void syncDockShadow")) return;

        int sync = main.indexOf("static void syncDockShadow");
        int next = main.indexOf("private static void ensureShadowBelowBackground", sync);
        String body = main.substring(sync, next);
        int guard = body.indexOf("Miuix307MaterialPipeline.isOrdinaryHotSeatsBackground(dockBg)");
        int owner = body.indexOf("setOldBg(dockBg);");

        assertTrue("Laptop overlay must be rejected before normal-Dock ownership changes",
                guard >= 0 && owner > guard);
        assertTrue("307 pipeline must identify the ordinary Launcher HotSeats material",
                pipeline.contains("static boolean isOrdinaryHotSeatsBackground(View candidate)"));

        int setup = pipeline.indexOf("\"com.miui.home.launcher.Launcher\", \"setupViews\"");
        int setupEnd = pipeline.indexOf("if (backgroundClass != null)", setup);
        String setupBody = pipeline.substring(setup, setupEnd);
        assertTrue("Launcher.mHotSeats identity must be retained before workstation early return",
                setupBody.indexOf("hotSeatsRef = new WeakReference<>(hotSeats);")
                        < setupBody.indexOf("if (MainHook.isWorkstationMode()) return result;"));
    }

    @Test
    public void laptopOverlayMaterialKeepsItsNativeBackdrop() throws Exception {
        String pipeline = read("Miuix307MaterialPipeline.java");
        String glass = read("MiuixGlassHook.java");
        if (!pipeline.contains("static boolean isOrdinaryHotSeatsBackground(View candidate)")) return;

        int ensureStart = pipeline.indexOf("private static boolean ensureGlassBound(");
        int ensureEnd = pipeline.indexOf("/** Observe both pieces", ensureStart);
        String ensure = pipeline.substring(ensureStart, ensureEnd);
        int ensureOwnerGate = ensure.indexOf("if (!isOrdinaryHotSeatsBackground(background)) return false;");
        int ensureBoundCheck = ensure.indexOf("if (MiuixGlassHook.isBoundTo(background))");
        assertTrue("Laptop overlay material must be rejected before Prismal binding is considered",
                ensureOwnerGate >= 0 && ensureOwnerGate < ensureBoundCheck);

        int blurStart = glass.indexOf("static int suppressCompatBackgroundBlurRadius(");
        int blurEnd = glass.indexOf("static boolean install(", blurStart);
        String blur = glass.substring(blurStart, blurEnd);
        int blurOwnerGate = blur.indexOf(
                "if (!Miuix307MaterialPipeline.isOrdinaryHotSeatsBackground(dockBg))");
        int suppressToZero = blur.indexOf("return 0;");
        assertTrue("Laptop overlay must retain its vendor BlurBackground2 backdrop",
                blurOwnerGate >= 0 && blurOwnerGate < suppressToZero);
        assertTrue("Non-ordinary material must preserve the vendor-requested blur radius",
                blur.substring(blurOwnerGate, suppressToZero).contains("return requestedRadius;"));
    }

    @Test
    public void normalLayoutBackupAndRestoreAreWorkspaceScoped() throws Exception {
        String main = read("MainHook.java");
        String home = read("HomeGridHook.java");
        if (!main.contains("static void syncDockShadow")) return;

        int backup = main.indexOf("private static void backupNormalHomeLayout()");
        int collect = main.indexOf("private static void collectHomeItemPositions", backup);
        String transition = main.substring(backup, collect);

        assertTrue("HomeGridHook must expose the current ordinary Workspace owner",
                home.contains("static android.view.View currentWorkspace()"));
        assertTrue("layout backup/restore must start from Workspace",
                transition.contains("HomeGridHook.currentWorkspace()"));
        assertFalse("Dock background root must never be used as the layout restore root",
                transition.contains("dockBg.getRootView()"));
        assertFalse("oldBg must not anchor desktop item backup/restore",
                transition.contains("View dockBg = oldBg();"));
    }
}
