package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GridSpacingSemanticsContractTest {

    @Test
    public void composeGridControlsExposeEdgeOffsetAndMarginInsteadOfMixedDistances() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(compose.contains("LANDSCAPE_HORIZONTAL_DISTANCE, \"横屏 Edge Offset\""));
        assertTrue(compose.contains("PORTRAIT_HORIZONTAL_DISTANCE, \"竖屏 Edge Offset\""));
        assertTrue(compose.contains("LANDSCAPE_ROW_GAP, \"横屏 Margin\""));
        assertTrue(compose.contains("PORTRAIT_ROW_GAP, \"竖屏 Margin\""));

        assertFalse(compose.contains("\"横屏顶部距离偏移\""));
        assertFalse(compose.contains("\"横屏底部距离偏移\""));
        assertFalse(compose.contains("\"竖屏顶部距离偏移\""));
        assertFalse(compose.contains("\"竖屏底部距离偏移\""));
    }

    @Test
    public void summariesStateThatEdgeOffsetIsHorizontalOnlyAndZeroMarginIsAutomatic() throws Exception {
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));

        assertTrue(compose.contains("Edge Offset 只调整左右边缘，不改变纵向位置"));
        assertTrue(compose.contains("Margin 是图标单元实际间距；0 表示自动使用屏幕宽度的 0.9%"));
    }

    @Test
    public void absentLegacyUnitFlagsUseCurrentDpAndOffsetSemantics() {
        assertTrue(ConfigSchema.Grid.MARGINS_DP.runtimeFallback());
        assertTrue(ConfigSchema.Grid.MARGINS_OFFSET.runtimeFallback());
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.LANDSCAPE_ROW_GAP.runtimeFallback());
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.PORTRAIT_ROW_GAP.runtimeFallback());
    }

    @Test
    public void edgeOffsetAndMarginNoLongerExposeNoOpNegativeRanges() {
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.LANDSCAPE_HORIZONTAL_DISTANCE.minInt());
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.PORTRAIT_HORIZONTAL_DISTANCE.minInt());
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.LANDSCAPE_ROW_GAP.minInt());
        assertEquals(Integer.valueOf(0), ConfigSchema.Grid.PORTRAIT_ROW_GAP.minInt());
    }

    @Test
    public void rowGapRuntimeFallbackNoLongerDependsOnLegacyOffsetFlags() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));

        assertTrue(config.contains(
                "ConfigSchema.Grid.LANDSCAPE_ROW_GAP.runtimeFallback()"));
        assertTrue(config.contains(
                "ConfigSchema.Grid.PORTRAIT_ROW_GAP.runtimeFallback()"));
        assertFalse(config.contains("offsets ? 0 : (dp ? 1 : 3)"));
    }

    @Test
    public void normalWorkspaceGeometryUsesLauncherVerticalReserves() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"));

        assertTrue(hook.contains("getTop"));
        assertTrue(hook.contains("getIndicatorBarHeight"));
        assertTrue(hook.contains("getBottom"));
        assertTrue(hook.contains("getDockBarHeight"));
        assertTrue(hook.contains("HomeGridGeometryPolicy.compute"));
    }

    @Test
    public void workspaceRefreshWaitsForMeasuredBounds() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/HomeGridHook.java"));

        assertTrue(hook.contains(
                "workspace.post(() -> refreshWorkspaceGridIfReady(workspace))"));
        assertTrue(hook.contains(
                "workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 180L)"));
        assertTrue(hook.contains(
                "workspace.postDelayed(() -> refreshWorkspaceGridIfReady(workspace), 500L)"));
    }

    @Test
    public void zeroCopyRendererChecksThemeHostSupportRatherThanExactBlurClass() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307ZeroCopyRenderer.java"));

        assertTrue(renderer.contains("supportsZeroCopyBackdrop(materialHost)"));
        assertFalse(renderer.contains("!Miuix307CompositorOpticsBridge.usesExactBackgroundBlur(materialHost)"));
    }
}
