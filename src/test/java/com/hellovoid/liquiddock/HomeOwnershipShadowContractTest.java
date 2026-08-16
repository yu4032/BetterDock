package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class HomeOwnershipShadowContractTest {
    private static final Path ROOT = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String source(String file) throws Exception {
        Path path = ROOT.resolve(file);
        assertTrue("missing production source: " + file, Files.exists(path));
        return Files.readString(path);
    }

    @Test public void protocolUsesDedicatedCodesAndBoundedTiming() {
        assertNotEquals(FreeformLeashProtocol.TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT,
                HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW);
        assertEquals(160L, HomeOwnershipShadowProtocol.RECHECK_DELAY_MS);
        assertEquals(1500L, HomeOwnershipShadowProtocol.PENDING_TTL_MS);
        assertEquals(16, HomeOwnershipShadowProtocol.MAX_PENDING);
    }

    @Test public void systemUiShadowIsReadOnlyAndBreakerIndependent() throws Exception {
        String source = source("SystemUiHomeOwnershipShadow.java");
        assertTrue(source.contains("WeakReference<Object>"));
        assertTrue(source.contains("MultiTaskingTaskRepository"));
        assertTrue(source.contains("mBgExecutor"));
        assertTrue(source.contains("isHomeVisible"));
        assertTrue(source.contains("getHomeTask"));
        assertTrue(source.contains("getTopFullscreenTaskInfo"));
        assertTrue(source.contains("execute"));
        assertFalse(source.contains("SurfaceControl"));
        assertFalse(source.contains("registerTaskOrganizer"));
        assertFalse(source.contains("onTaskAppeared"));
        assertFalse(source.contains("onTaskVanished"));
        assertFalse(source.contains("FreeformBridgePolicy.CircuitBreaker"));
    }

    @Test public void diagnosticsReuseTheAlreadyDiscoveredProviderBinder() throws Exception {
        String resolver = source("FreeformTaskLeashResolver.java");
        String runtime = source("FreeformLeashRuntime.java");
        assertTrue(resolver.contains("providerBinderForDiagnostics()"));
        assertTrue(runtime.contains("providerBinderForDiagnostics()"));
        assertFalse(runtime.contains("new FreeformLeashBrokerClient"));
    }

    @Test public void launcherProbeIsAsyncAndCannotWriteProductionSceneState() throws Exception {
        String source = source("HomeOwnershipShadowProbe.java");
        assertTrue(source.contains("IBinder.FLAG_ONEWAY"));
        assertTrue(source.contains("Handler(Looper.getMainLooper())"));
        assertTrue(source.contains("RECHECK_DELAY_MS"));
        assertTrue(source.contains("PENDING_TTL_MS"));
        assertTrue(source.contains("MAX_PENDING"));
        assertTrue(source.contains("[DC-SHADOW]"));
        assertTrue(source.contains("FreeformLeashRuntime.providerBinderForDiagnostics()"));
        assertFalse(source.contains("CountDownLatch"));
        assertFalse(source.contains("Future.get"));
        assertFalse(source.contains("Thread.sleep"));
        assertFalse(source.contains("new FreeformLeashBrokerClient"));
        assertFalse(source.contains("launcherResumed ="));
        assertFalse(source.contains("setLauncherState("));
        assertFalse(source.contains("CaptureSceneState"));
        assertFalse(source.contains("FreeformBridgePolicy.CircuitBreaker"));
    }
}
