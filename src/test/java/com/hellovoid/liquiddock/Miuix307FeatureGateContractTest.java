package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Miuix307FeatureGateContractTest {
    private static String javaSource(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock", relative));
    }

    private static String schemaSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
    }

    @Test public void compatibilityFlagIsPersistedAndDefaultOff() throws Exception {
        String schema = schemaSource();
        assertTrue(schema.contains("MIUIX_307_PIPELINE = bool("));
        assertTrue(schema.contains("\"liquid_miuix_307_pipeline\", false, false, false"));

        String config = javaSource("LiquidDockConfig.java");
        assertTrue(config.contains("miuix307Pipeline"));
        assertTrue(config.contains("ConfigSchema.Glass.MIUIX_307_PIPELINE"));
    }

    @Test public void mainHookUsesOneExplicit307FeatureGate() throws Exception {
        String hook = javaSource("MainHook.java");
        assertTrue(hook.contains("boolean liquidGlass = config.glass.enabled"));
        assertTrue(hook.contains("liquidGlass && config.glass.miuix307Pipeline"));
        assertTrue(hook.contains("Miuix307Compatibility.install("));
        assertEquals(1, count(hook, "Miuix307Compatibility.install("));
    }

    @Test public void compatibilityFacadeExistsAndOwns307Installation() throws Exception {
        Path facade = Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307Compatibility.java");
        assertTrue(Files.exists(facade));
        String source = Files.readString(facade);
        assertTrue(source.contains("final class Miuix307Compatibility"));
        assertTrue(source.contains("static boolean install("));
        assertTrue(source.contains("Miuix307MaterialPipeline.install("));
    }

    private static int count(String s, String needle) {
        int n = 0, at = 0;
        while ((at = s.indexOf(needle, at)) >= 0) {
            n++;
            at += needle.length();
        }
        return n;
    }
}
