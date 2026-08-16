package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class SystemUiTaskStateProviderContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void sharedProviderOwnsBrokerRegistrationAndRoutesCapabilities() throws Exception {
        String provider = source("SystemUiTaskStateProvider.java");
        assertTrue(provider.contains("FreeformLeashBrokerClient"));
        assertTrue(provider.contains("SystemUiFreeformLeashProvider.handles"));
        assertTrue(provider.contains("SystemUiHomeOwnershipSource.handles"));
        assertTrue(provider.contains("setSystemUiProvider"));
    }

    @Test public void freeformProviderNoLongerOwnsBrokerOrProviderBinder() throws Exception {
        String freeform = source("SystemUiFreeformLeashProvider.java");
        assertFalse(freeform.contains("volatile FreeformLeashBrokerClient brokerClient"));
        assertFalse(freeform.contains("private static final Binder PROVIDER_BINDER"));
        assertTrue(freeform.contains("SystemUiTaskStateProvider.attachContext"));
    }

    @Test public void executorSourceOnlyObservesExistingShellOrganizer() throws Exception {
        String source = source("SystemUiTaskExecutorSource.java");
        assertTrue(source.contains("com.android.wm.shell.ShellTaskOrganizer"));
        assertTrue(source.contains("getExecutor"));
        assertFalse(source.contains("new ShellTaskOrganizer"));
        assertFalse(source.contains("SparseArray"));
    }
}
