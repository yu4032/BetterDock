package com.hellovoid.liquiddock;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Contracts that keep workstation All Apps capture-equivalent to HOME. */
public class WorkstationAllAppsHomeBackdropContractTest {
    private static String setAllAppsActiveMethod() throws IOException {
        String source = Files.readString(
                Paths.get("src/main/java/com/hellovoid/liquiddock/DockLiquidGlassView.java"),
                StandardCharsets.UTF_8);
        int start = source.indexOf("void setAllAppsActive(boolean active, View captureRoot)");
        int end = source.indexOf("/** Exact Overview lifecycle", start);
        assertTrue("setAllAppsActive method must remain present", start >= 0 && end > start);
        return source.substring(start, end);
    }

    @Test
    public void workstationAllAppsUpdatesUiStateThenReturnsBeforeCaptureScheduling()
            throws IOException {
        String method = setAllAppsActiveMethod();
        int stateUpdate = method.indexOf(
                "sceneState.setAllAppsActive(active, workstationMode);");
        int workstationBranch = method.indexOf("if (workstationMode)", stateUpdate);
        int workstationReturn = method.indexOf("return;", workstationBranch);
        int observationReset = method.indexOf("observationValid = false;");

        assertTrue("scene state must receive the workstation HOME-backdrop policy",
                stateUpdate >= 0);
        assertTrue("workstation All Apps must return before capture state is dirtied",
                workstationBranch > stateUpdate
                        && workstationReturn > workstationBranch
                        && observationReset > workstationReturn);
        assertFalse("workstation All Apps must not start its own capture burst",
                method.substring(workstationBranch, observationReset)
                        .contains("startWorkstationCaptureBurst"));
    }

    @Test
    public void normalAllAppsKeepsExistingCaptureRefreshPath() throws IOException {
        String method = setAllAppsActiveMethod();
        int workstationBranch = method.indexOf("if (workstationMode)");
        int workstationReturn = method.indexOf("return;", workstationBranch);
        int request = method.indexOf(
                "requestStateCapture(active ? \"all-apps-enter\" : \"all-apps-exit\");");

        assertTrue("normal All Apps must retain its existing capture refresh",
                workstationReturn >= 0 && request > workstationReturn);
    }
}
