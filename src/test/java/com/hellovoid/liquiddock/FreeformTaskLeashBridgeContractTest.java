package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class FreeformTaskLeashBridgeContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test public void scopeAndEntryPointIsolateSystemUi() throws Exception {
        String scope = source("src/main/resources/META-INF/xposed/scope.list");
        assertTrue(scope.contains("com.miui.home"));
        assertTrue(scope.contains("com.android.systemui"));

        String main = source("src/main/java/com/hellovoid/liquiddock/ModuleMain.java");
        assertTrue(main.contains("SystemUiFreeformLeashProvider.install"));
        assertTrue(main.contains("FreeformCaptureLeashHook.install"));
        assertTrue(main.contains("catch (Throwable error)"));
        assertTrue("SystemUI branch must return before Launcher hooks",
                main.indexOf("SystemUiFreeformLeashProvider.install") < main.indexOf("return;"));
    }

    @Test public void systemUiProviderIsPassiveAndThreadConfined() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/SystemUiFreeformLeashProvider.java");
        assertTrue(source.contains("WeakReference<Object>"));
        assertTrue(source.contains("mShellTaskOrganizer"));
        assertTrue(source.contains("mTasks"));
        assertTrue(source.contains("mLeash"));
        assertTrue(source.contains("executor.execute"));
        assertTrue(source.contains("writeTypedObject(surfaces[i], 0)"));
        assertFalse(source.contains("new SurfaceControl.Transaction"));
        assertFalse(source.contains("registerTaskOrganizer"));
        assertFalse(source.contains("onTaskAppeared"));
        assertFalse(source.contains("SystemUIApplication"));
        assertFalse(source.contains("PARCELABLE_WRITE_RETURN_VALUE"));
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
    }

    @Test public void captureGateFailsClosedAndMergesSurfaceControls() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformCaptureLeashHook.java");
        assertTrue(source.contains("LiveScreenCapture.class.getDeclaredMethod"));
        assertTrue(source.contains("resolution.borrowedRemoteLeashes()"));
        assertTrue(source.contains("args[3] = merge"));
        assertTrue(source.contains("args[5] = 2"));
        assertTrue(source.contains("resolution.close()"));
        assertTrue(source.contains("setCaptureGateInstalled(true)"));
        assertTrue(source.contains("setCaptureGateInstalled(false)"));
    }

    @Test public void freeformPreflightNoLongerCallsSurfaceFlingerDebugResolver() throws Exception {
        String source = source(
                "src/main/java/com/hellovoid/liquiddock/FreeformLayerResolver.java");
        assertTrue(source.contains("FreeformLeashRuntime"));
        assertFalse(source.contains("resolveAllByOwnerUids"));
        assertFalse(source.contains("getLayerDebugInfo"));
        assertFalse(source.contains("getPackageUid"));
    }

    @Test public void protocolDeadlineIsTwentyFiveMilliseconds() throws Exception {
        assertEquals(25L, FreeformLeashProtocol.REQUEST_TIMEOUT_MS);
        assertEquals(32, FreeformLeashProtocol.MAX_TASKS);
    }
}
