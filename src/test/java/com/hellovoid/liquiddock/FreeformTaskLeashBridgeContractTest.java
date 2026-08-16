package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FreeformTaskLeashBridgeContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test public void scopeManifestAndEntryPointCoverOnlyApprovedHosts() throws Exception {
        String scope = source("src/main/resources/META-INF/xposed/scope.list");
        assertTrue(scope.contains("com.miui.home"));
        assertTrue(scope.contains("com.android.systemui"));

        String manifest = source("src/main/AndroidManifest.xml");
        assertTrue(manifest.contains("<package android:name=\"com.miui.home\""));
        assertTrue(manifest.contains("<package android:name=\"com.android.systemui\""));
        assertTrue(manifest.contains(".FreeformLeashBrokerService"));
        assertTrue(manifest.contains("android:exported=\"true\""));

        String main = source("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        assertTrue(main.contains("SystemUiFreeformLeashProvider.install"));
        assertTrue(main.contains("FreeformCaptureLeashHook.install"));
        assertTrue(main.contains("catch (Throwable error)"));
        assertTrue("SystemUI branch must return before Launcher hooks",
                main.indexOf("SystemUiFreeformLeashProvider.install") < main.indexOf("return;"));
    }

    @Test public void systemUiProviderOwnsDisplayScopedSnapshotEnumeration() throws Exception {
        String provider = source(
                "src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java");
        assertTrue(provider.contains("WeakReference<Object>"));
        assertTrue(provider.contains("ListenerState"));
        assertTrue(provider.contains("mShellTaskOrganizer"));
        assertTrue(provider.contains("mTasks"));
        assertTrue(provider.contains("mTaskInfo"));
        assertTrue(provider.contains("mLeash"));
        assertTrue(provider.contains("executor.execute"));
        assertTrue(provider.contains("tasks.size()"));
        assertTrue(provider.contains("tasks.valueAt("));
        assertTrue(provider.contains("shouldIncludeFreeformCandidate"));
        assertTrue(provider.contains("writeTypedObject(surfaces[i], 0)"));
        assertTrue(provider.contains("TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT"));
        assertTrue(provider.contains("TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT"));
        assertTrue(provider.contains("included.size() > FreeformLeashProtocol.MAX_TASKS"));
        assertFalse(provider.contains("readTaskIds("));
        assertFalse(provider.contains("taskIds"));
        assertFalse(provider.contains("createIntArray()"));
        assertFalse(provider.contains("new SurfaceControl.Transaction"));
        assertFalse(provider.contains("registerTaskOrganizer"));
        assertFalse(provider.contains("onTaskAppeared"));
        assertFalse(provider.contains("SystemUIApplication"));
        assertFalse(provider.contains("PARCELABLE_WRITE_RETURN_VALUE"));
        assertFalse("hidden RunningTaskInfo.displayId must not be a compile-time dependency",
                provider.contains("taskInfo.displayId"));
    }

    @Test public void brokerNeverCarriesSurfaceControls() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformLeashBrokerService.java");
        assertTrue(source.contains("SYSTEM_UI_PACKAGE"));
        assertTrue(source.contains("LAUNCHER_PACKAGE"));
        assertTrue(source.contains("linkToDeath"));
        assertFalse(source.contains("SurfaceControl"));
    }

    @Test public void resolverUsesBoundedAsyncRequestAndOwnedWrapperCleanup() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java");
        assertTrue(source.contains("REQUEST_TIMEOUT_MS"));
        assertTrue(source.contains("IBinder.FLAG_ONEWAY"));
        assertTrue(source.contains("CountDownLatch"));
        assertTrue(source.contains("SurfaceControl.CREATOR"));
        assertTrue(source.contains("surface.release()"));
        assertTrue(source.contains("received.size() != requestedTaskIds.length"));
        assertTrue(source.contains("releaseAll(surfaces)"));
        assertFalse(source.contains("writeIntArray(taskIds)"));
    }

    @Test public void runningTaskDisplayIdUsesReflectionInsteadOfHiddenSdkField() throws Exception {
        String layerResolver = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        String leashResolver = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformTaskLeashResolver.java");
        for (String resolver : new String[]{layerResolver, leashResolver}) {
            assertFalse("hidden RunningTaskInfo.displayId must not be a compile-time dependency",
                    resolver.contains("task.displayId"));
            assertTrue("display filtering must go through the reflective helper",
                    resolver.contains("displayId(task)"));
            assertTrue("reflective helper must look up the hidden displayId member by name",
                    resolver.contains("\"displayId\""));
        }
    }

    @Test public void captureGateFailsClosedAndMergesSurfaceControls() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java");
        assertTrue(source.contains("LiveScreenCapture.class.getDeclaredMethod"));
        assertTrue(source.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue(source.contains("allValid(remote)"));
        assertTrue(source.contains("args[3] = merge"));
        assertTrue(source.contains("args[5] = 2"));
        assertTrue(source.contains("resolution.close()"));
        assertTrue(source.contains("setCaptureGateInstalled(true)"));
        assertTrue(source.contains("setCaptureGateInstalled(false)"));
    }

    @Test public void productionFreeformAndLegacyResolverDoNotUseRetiredDebugApi()
            throws Exception {
        String freeform = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        String legacy = source(
                "src/main/java/com/hellovoid/liquiddock/SurfaceLayerNameResolver.java");
        assertTrue(freeform.contains("FreeformLeashRuntime"));
        assertFalse(freeform.contains("resolveAllByOwnerUids"));
        assertFalse(freeform.contains("getLayerDebugInfo"));
        assertFalse(freeform.contains("getPackageUid"));
        assertFalse(legacy.contains("ISurfaceComposer"));
        assertFalse(legacy.contains("getLayerDebugInfo"));
    }

    @Test public void protocolDeadlineAndSnapshotVersionAreExplicit() throws Exception {
        String protocol = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformLeashProtocol.java");
        assertTrue(protocol.contains("TRANSACTION_REQUEST_VISIBLE_LEASH_SNAPSHOT"));
        assertTrue(protocol.contains("TRANSACTION_VISIBLE_LEASH_SNAPSHOT_RESULT"));
        assertEquals(25L, FreeformLeashProtocol.REQUEST_TIMEOUT_MS);
        assertEquals(32, FreeformLeashProtocol.MAX_TASKS);
    }
}
