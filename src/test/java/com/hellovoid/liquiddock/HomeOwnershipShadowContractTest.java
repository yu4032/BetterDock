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

    @Test public void systemUiShadowReadsOnTheShellTaskOrganizerExecutorOnly() throws Exception {
        String source = source("SystemUiHomeOwnershipShadow.java");
        String provider = source("SystemUiFreeformLeashProvider.java");
        assertTrue(source.contains("WeakReference<Object>"));
        assertTrue(source.contains("MultiTaskingTaskRepository"));
        assertTrue(source.contains("SystemUiFreeformLeashProvider.taskStateExecutorForDiagnostics()"));
        assertTrue(source.contains("final Executor executor"));
        assertTrue(source.contains("executor.execute("));
        assertTrue(source.contains("isHomeVisible"));
        assertTrue(source.contains("getHomeTask"));
        assertTrue(source.contains("getTopFullscreenTaskInfo"));
        assertTrue(provider.contains("taskStateExecutorForDiagnostics()"));
        assertFalse(source.contains("mBgExecutor"));
        assertFalse(source.contains("executeMethod"));
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

    @Test public void launcherAdapterOnlyReadsExistingProductionDecision() throws Exception {
        String adapter = source("HomeOwnershipShadowLauncherHook.java");
        String mainHook = source("MainHook.java");
        assertTrue(adapter.contains("launcherResumed"));
        assertTrue(adapter.contains("foregroundTaskWindowingMode"));
        assertTrue(adapter.contains("MAIN.post"));
        assertTrue(adapter.contains("HomeOwnershipShadowProbe.sample"));
        assertTrue(adapter.contains("HomeOwnershipShadowProbe.setOverviewActive"));
        assertTrue(adapter.contains("HomeOwnershipShadowProbe.setAllAppsActive"));
        assertFalse(adapter.contains("launcherResumed ="));
        assertFalse(adapter.contains("Field.set("));
        assertFalse(adapter.contains("setBoolean("));
        assertFalse(adapter.contains("setLauncherState("));
        assertFalse(mainHook.contains("HomeOwnershipShadowProbe"));
        assertTrue(mainHook.contains("foregroundTaskWindowingMode"));
        assertTrue(mainHook.contains("LauncherSceneOwnershipPolicy.launcherOwnsScene"));
    }

    @Test public void r8KeepsOnlyMembersReflectedByTheDiagnosticAdapter() throws Exception {
        String keep = Files.readString(Path.of("src/main/keepRules/liquiddock.keep"));
        assertTrue(keep.contains("boolean launcherResumed;"));
        assertTrue(keep.contains("boolean launcherLifecycleKnown;"));
        assertTrue(keep.contains("DockLiquidGlassView liquidGlassView;"));
        assertTrue(keep.contains("int foregroundTaskWindowingMode(java.lang.Object);"));
        assertTrue(keep.contains("boolean overviewActive;"));
    }
}
