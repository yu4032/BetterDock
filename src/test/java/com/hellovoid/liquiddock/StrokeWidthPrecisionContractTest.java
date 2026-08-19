package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression contract for thin Dock strokes on high-density displays. */
public class StrokeWidthPrecisionContractTest {
    @Test
    public void allDockStrokeWidthControlsAllowSubDpValues() throws Exception {
        assertEquals(Integer.valueOf(0), ConfigSchema.Dock.SQUIRCLE_STROKE_WIDTH.minInt());
        assertEquals(Integer.valueOf(0), ConfigSchema.Dock.FILL_DIFF_STROKE_WIDTH.minInt());
        assertEquals(Integer.valueOf(0), ConfigSchema.Dock.STANDARD_STROKE_WIDTH.minInt());

        // DP_TENTHS already stores decimal dp losslessly. Keep the UI on 0.1dp steps so values
        // such as 0.5dp are selectable instead of forcing the old 1dp lower bound.
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(compose.contains("${spec.key}_tenths"));
        assertTrue(compose.contains("(nextValue * 10f).roundToInt() / 10f"));
        assertTrue(compose.contains("(spec.max - spec.min) * 10 - 1"));
    }
}
