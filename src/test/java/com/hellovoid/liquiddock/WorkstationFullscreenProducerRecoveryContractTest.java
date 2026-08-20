package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Regression contract for workstation Dock recovery after Launcher loses its PassBlur producer. */
public class WorkstationFullscreenProducerRecoveryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workstationLauncherResumeForcesProducerRebindWithoutRebuildingGlass() throws Exception {
        String pipeline = Files.readString(MAIN.resolve("Miuix307MaterialPipeline.java"));

        assertTrue(pipeline.contains("installWorkstationResumeProducerRecovery(classLoader);"));
        assertTrue(pipeline.contains("private static void installWorkstationResumeProducerRecovery("));
        assertTrue(pipeline.contains("launcherClass.getDeclaredMethod(\"onResume\")"));
        assertTrue(pipeline.contains("if (MainHook.isWorkstationMode())"));
        assertTrue(pipeline.contains(
                "Miuix307ZeroCopyRenderer.rebindProducer(\"workstation-launcher-resume\")"));

        assertFalse(pipeline.contains(
                "MiuixGlassHook.invalidateBinding(background); // workstation-launcher-resume"));
    }

    @Test public void producerRebindDropsStaleFrameworkBindingAndWaitsForANewOesFrame() throws Exception {
        String renderer = Files.readString(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(renderer.contains("static void rebindProducer(String reason)"));
        assertTrue(renderer.contains("gpuBackdrop.rebindProducer(reason);"));

        assertTrue(view.contains("void rebindProducer(String reason)"));
        assertTrue(view.contains("Miuix307PassBlurBridge.Binding stale = binding;"));
        assertTrue(view.contains("binding = null;"));
        assertTrue(view.contains("Miuix307PassBlurBridge.unbind(stale);"));
        assertTrue(view.contains("hasConsumedFrame = false;"));
        assertTrue(view.contains("frameAvailable.set(false);"));
        assertTrue(view.contains("activationExhausted = false;"));
        assertTrue(view.contains("bindProducerWhenReady(0)"));
    }

    @Test public void producerRebindNeverReusesAnAlreadyParceledBufferQueueProducer() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("private volatile boolean producerRebindPending;"));
        assertTrue(view.contains("if (producerRebindPending) return;"));
        assertTrue(view.contains("producerRebindPending = true;"));
        assertTrue(view.contains("renderHandler.post(() -> recreateInputProducer(reason));"));
        assertTrue(view.contains("private void recreateInputProducer(String reason)"));
        assertTrue(view.contains("Surface staleProducer = inputProducerSurface;"));
        assertTrue(view.contains("SurfaceTexture staleInput = inputSurfaceTexture;"));
        assertTrue(view.contains("staleProducer.release();"));
        assertTrue(view.contains("staleInput.release();"));
        assertTrue(view.contains("GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);"));
        assertTrue(view.contains("createInputProducer();"));
        assertTrue(view.contains("inputProducerSurface == staleProducer"));
        assertTrue(view.contains("inputSurfaceTexture == staleInput"));
        assertTrue(view.contains("PassBlur input producer was not replaced"));
        assertTrue(view.contains("post(() -> bindProducerWhenReady(0));"));
    }

    @Test public void rootSurfaceReplacementSelfHealsInsteadOfSilentlyKeepingTheOldBinding() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("!binding.rootSurface.isValid()"));
        assertTrue(view.contains("!isSameSurface(binding.rootSurface, geometry.rootSurface)"));
        assertTrue(view.contains("rebindProducer(\"producer-root-changed\")"));
    }
}
