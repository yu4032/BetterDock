package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source-level contract for promoting the device-validated 307 demo into a real backend. */
public class Miuix307FormalPipelineContractTest {
    private static String read(String path) throws IOException {
        Path source = Paths.get(path);
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    @Test
    public void workflowBuildsStoredSourceWithoutDemoPatcher() throws IOException {
        String workflow = read(".github/workflows/api101-build.yml");
        assertFalse("formal pipeline must not mutate source in CI",
                workflow.contains("patch_miuix307_demo.py"));
    }

    @Test
    public void mainHookDelegatesToFormalMaterialPipeline() throws IOException {
        String main = read("src/main/java/com/hellovoid/liquiddock/MainHook.java");
        assertTrue(main.contains("config.glass.miuix307Pipeline"));
        assertTrue(main.contains("Miuix307MaterialPipeline.install(classLoader, config)"));
        assertFalse(main.contains("Miuix307DemoPipeline.install(classLoader, config)"));
    }

    @Test
    public void persistedSwitchLivesInStoredSchemaAndTypedConfig() throws IOException {
        String schema = read("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String config = read("src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java");
        assertTrue(schema.contains("MIUIX_307_PIPELINE"));
        assertTrue(schema.contains("\"liquid_miuix_307_pipeline\", false, false, false"));
        assertTrue(config.contains("miuix307Pipeline"));
    }

    @Test
    public void formalPipelineHasNoCaptureStateDependencies() throws IOException {
        String pipeline = read("src/main/java/com/hellovoid/liquiddock/Miuix307MaterialPipeline.java");
        assertTrue(pipeline.contains("HotSeatsListContentMiuiXBlurBackground"));
        assertTrue(pipeline.contains("refreshBlur"));
        assertFalse(pipeline.contains("LiveScreenCapture"));
        assertFalse(pipeline.contains("CaptureSceneState"));
        assertFalse(pipeline.contains("CaptureSource"));
    }
}
