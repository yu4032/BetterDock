package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts screen-space wallpaper alignment and the shared folder-radius override. */
public class LauncherGlassAlignmentAndRadiusTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void screenSpaceMappingUsesStableRootScreenOrigin() {
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                24, 48, 124, 148, 324, 348);
        assertEquals(100f, bounds.left, 0.001f);
        assertEquals(100f, bounds.top, 0.001f);
        assertEquals(300f, bounds.right, 0.001f);
        assertEquals(300f, bounds.bottom, 0.001f);
    }

    @Test
    public void folderRadiusZeroPreservesAutoAndPositiveValueOverridesInDp() {
        assertEquals(22f,
                LauncherGlassCornerRadiusPolicy.resolve(0f, 2f, 22f, 18f), 0.001f);
        assertEquals(18f,
                LauncherGlassCornerRadiusPolicy.resolve(0f, 2f, Float.NaN, 18f), 0.001f);
        assertEquals(48f,
                LauncherGlassCornerRadiusPolicy.resolve(24f, 2f, 22f, 18f), 0.001f);
    }

    @Test
    public void sinkWiresGlobalMaterialRectToRootLocationNotVisibleCrop() throws Exception {
        String sink = read("src/main/java/com/hellovoid/liquiddock/LauncherGlassSinkView.java");
        assertTrue(sink.contains("root.getLocationOnScreen(rootLocation)"));
        assertTrue(sink.contains("LauncherGlassScreenSpace.relativeToRoot("));
        assertFalse(sink.contains("root.getGlobalVisibleRect(rootRect)"));
    }

    @Test
    public void folderRadiusIsPersistedAndSharedByStaticAndDragGlass() throws Exception {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String runtime = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        String folder = read("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java");
        String drag = read("src/main/java/com/hellovoid/liquiddock/LauncherGlassDragOverlay.java");

        assertTrue(schema.contains("FOLDER_CORNER_RADIUS = integer("));
        assertTrue(schema.contains("\"liquid_folder_corner_radius\", 0, 0, 0, 0, 96"));
        assertTrue(schema.contains("Glass.FOLDER_GLASS, Glass.FOLDER_CORNER_RADIUS"));
        assertTrue(runtime.contains("final float folderCornerRadiusDp;"));
        assertTrue(runtime.contains("folderCornerRadiusDp = c.f(ConfigSchema.Glass.FOLDER_CORNER_RADIUS.name()"));
        assertTrue(folder.contains("LauncherGlassCornerRadiusPolicy.resolve("));
        assertTrue(drag.contains("LauncherGlassCornerRadiusPolicy.resolve("));
        assertTrue(drag.contains("kind == LauncherGlassDragState.Kind.FOLDER"));
    }

    @Test
    public void composeAndLegacyGuiExposeFolderRadiusWithFolderDependency() throws Exception {
        String compose = read("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
        String legacy = read("src/main/res/xml/preferences.xml");

        assertTrue(compose.contains("ConfigSchema.Glass.FOLDER_CORNER_RADIUS"));
        assertTrue(compose.contains("masterEnabled && liquidGlass && folderGlass"));
        assertTrue(compose.contains("\"文件夹圆角\""));
        assertTrue(legacy.contains("android:key=\"liquid_folder_corner_radius\""));
        assertTrue(legacy.contains("app:max=\"96\""));
        assertTrue(legacy.contains("android:dependency=\"liquid_folder_glass\""));
    }
}
