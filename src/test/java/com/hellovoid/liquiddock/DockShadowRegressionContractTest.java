package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/** Regression contracts for the independent Dock shadow. */
public class DockShadowRegressionContractTest {
    private static String mainHook() throws IOException {
        return Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/MainHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void customDockShadowUsesSoftwareLayerForPaintShadowLayer() throws IOException {
        String source = mainHook();
        assertTrue("Paint.setShadowLayer requires the custom shadow View to stay software-rendered",
                source.contains("view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);"));
    }

    @Test
    public void dockAnimationDoesNotResyncIndependentShadowGeometry() throws IOException {
        String source = mainHook();
        assertTrue("glass/stroke may track animation, but the independent shadow must remain stable",
                source.contains("boolean anim = animating(bg);"));
        assertTrue("shadow geometry sync must be gated until the Dock animation has settled",
                source.contains("if (!anim && bgW != lastShadowW)"));
        assertTrue("shadow corner geometry must not follow transient radius animation frames",
                source.contains("if (!animating(v)) strokeR = Math.max(0f, systemRadius + co);"));
    }

    @Test
    public void liquidGlassHostTracksDockTranslationDuringStartupAnimation() throws IOException {
        String source = mainHook();
        assertTrue("glass host must inherit Dock translationX on transient startup frames",
                source.contains("liquidGlassHostView.setTranslationX(bg.getTranslationX());"));
        assertTrue("glass host must inherit Dock translationY on transient startup frames",
                source.contains("liquidGlassHostView.setTranslationY(bg.getTranslationY());"));
    }
}
