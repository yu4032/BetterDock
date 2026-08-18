package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class WorkspaceDropRuleHookContractTest {
    private static String read(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/hellovoid/liquiddock/" + name));
    }

    @Test public void customEightByFourGridRemovesOnlyVendorSwapPlacementRule() throws Exception {
        String entry = read("ModuleMain.java");
        String hook = read("WorkspaceDropRuleHook.java");

        assertTrue(entry.contains("WorkspaceDropRuleHook.install(classLoader"));
        assertTrue(hook.contains("LayoutDropRuleForSwapPlaces"));
        assertTrue(hook.contains("\"isLegalXY\""));
        assertTrue(hook.contains("int.class, int.class, int.class, int.class"));
        assertTrue(hook.contains("return true"));
    }
}
