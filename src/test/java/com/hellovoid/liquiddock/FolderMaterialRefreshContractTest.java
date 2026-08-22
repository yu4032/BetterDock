package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FolderMaterialRefreshContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void existingSinkReassertsTransparentMaterialBeforeReturn() throws Exception {
        String source = source();
        int branch = source.indexOf("if (existing != null && existing.getParent() == parent)");
        assertTrue("existing-sink fast path missing", branch >= 0);
        int returned = source.indexOf("return existing;", branch);
        assertTrue("existing-sink return missing", returned > branch);
        int clear = source.indexOf("clearVendorBlur(material);", branch);
        int transparent = source.indexOf("makeMaterialTransparent(material);", branch);
        assertTrue("vendor blur must be cleared before existing sink returns",
                clear > branch && clear < returned);
        assertTrue("material must be forced transparent before existing sink returns",
                transparent > branch && transparent < returned);
    }
}
