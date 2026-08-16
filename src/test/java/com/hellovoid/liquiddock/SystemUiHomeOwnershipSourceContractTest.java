package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class SystemUiHomeOwnershipSourceContractTest {
    @Test public void sourceReadsExistingRepositoryOnSharedShellExecutor() throws Exception {
        String source = Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/SystemUiHomeOwnershipSource.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("MultiTaskingTaskRepository"));
        assertTrue(source.contains("isHomeVisible"));
        assertTrue(source.contains("getHomeTask"));
        assertTrue(source.contains("getTopFullscreenTaskInfo"));
        assertTrue(source.contains("WeakReference"));
        assertTrue(source.contains("SystemUiTaskExecutorSource.executor"));
        assertTrue(source.contains("HomeOwnershipPolicy.classify"));
        assertTrue(source.contains("SystemUiTaskStateProvider.attachContext"));
        assertFalse(source.contains("FreeformBridgePolicy.CircuitBreaker"));
        assertFalse(source.contains("HomeOwnershipShadowProtocol"));
        assertFalse(source.contains("SparseArray"));
        assertFalse(source.contains("writeInt(homeTaskId"));
        assertFalse(source.contains("writeInt(topFullscreenTaskId"));
    }
}
