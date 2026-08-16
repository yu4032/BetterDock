package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot, read-only device diagnostic for the freeform capture fallback path.
 *
 * This class must never participate in capture policy. It snapshots the same runtime
 * environment that production code is using, prints one grouped report, and returns.
 */
final class FreeformCaptureDiagnostic {
    private static final String TAG = "LiquidDockDiag";
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);

    private FreeformCaptureDiagnostic() {}

    static final class CaptureFacts {
        final boolean workstationMode;
        final boolean fullscreenCapture;
        final boolean useFullscreen;
        final CaptureScene scene;
        final CaptureSourcePolicy.Source requestedSource;
        final boolean launcherLifecycleKnown;
        final boolean launcherResumed;
        final boolean windowVisible;
        final boolean windowFocused;
        final boolean systemUiPanelExpanded;
        final int displayId;
        final String dockWindowLayerName;
        final String dragLayerName;
        final boolean dockSurfaceValid;

        CaptureFacts(boolean workstationMode, boolean fullscreenCapture, boolean useFullscreen,
                     CaptureScene scene, CaptureSourcePolicy.Source requestedSource,
                     boolean launcherLifecycleKnown, boolean launcherResumed,
                     boolean windowVisible, boolean windowFocused,
                     boolean systemUiPanelExpanded, int displayId,
                     String dockWindowLayerName, String dragLayerName,
                     boolean dockSurfaceValid) {
            this.workstationMode = workstationMode;
            this.fullscreenCapture = fullscreenCapture;
            this.useFullscreen = useFullscreen;
            this.scene = scene;
            this.requestedSource = requestedSource;
            this.launcherLifecycleKnown = launcherLifecycleKnown;
            this.launcherResumed = launcherResumed;
            this.windowVisible = windowVisible;
            this.windowFocused = windowFocused;
            this.systemUiPanelExpanded = systemUiPanelExpanded;
            this.displayId = displayId;
            this.dockWindowLayerName = dockWindowLayerName;
            this.dragLayerName = dragLayerName;
            this.dockSurfaceValid = dockSurfaceValid;
        }
    }

    static void runOnce(Context context,
                        FreeformLayerResolver taskResolver,
                        SurfaceLayerNameResolver surfaceResolver,
                        CaptureFacts facts,
                        boolean productionExclusionsEvaluated,
                        boolean productionFreeformActive,
                        String[] productionFreeformLayerNames,
                        String[] mergedExclusionNames,
                        boolean productionExclusionSafe) {
        if (taskResolver == null || surfaceResolver == null || facts == null) return;

        FreeformLayerResolver.DiagnosticSnapshot taskSnapshot;
        try {
            taskSnapshot = taskResolver.snapshotForDiagnostics();
        } catch (Throwable error) {
            // The production resolver may already have positively identified a freeform task.
            // In that case this diagnostic failure itself is important evidence and should
            // still consume the one-shot report.
            if (!productionFreeformActive) return;
            taskSnapshot = new FreeformLayerResolver.DiagnosticSnapshot(
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(),
                    false,
                    diagnosticError(error));
        }

        if (!productionFreeformActive && !taskSnapshot.hasVisibleFreeformTask()) return;
        if (!ATTEMPTED.compareAndSet(false, true)) return;

        String id = Long.toHexString(SystemClock.elapsedRealtimeNanos());
        Log.i(TAG, "BEGIN id=" + id);
        try {
            String processPackage = context != null ? context.getPackageName() : "-";
            Log.i(TAG, "CAPTURE id=" + id
                    + " process=" + processPackage
                    + " workstation=" + facts.workstationMode
                    + " fullscreenConfig=" + facts.fullscreenCapture
                    + " useFullscreen=" + facts.useFullscreen
                    + " scene=" + facts.scene
                    + " requestedSource=" + facts.requestedSource
                    + " lifecycleKnown=" + facts.launcherLifecycleKnown
                    + " launcherResumed=" + facts.launcherResumed
                    + " windowVisible=" + facts.windowVisible
                    + " windowFocused=" + facts.windowFocused
                    + " systemUiPanelExpanded=" + facts.systemUiPanelExpanded
                    + " displayId=" + facts.displayId
                    + " dockSurfaceValid=" + facts.dockSurfaceValid
                    + " dockLayer=" + quoted(facts.dockWindowLayerName)
                    + " dragLayer=" + quoted(facts.dragLayerName));

            Log.i(TAG, "TASKS id=" + id
                    + " count=" + taskSnapshot.tasks.size()
                    + " visibleFreeformDetected=" + taskSnapshot.visibleFreeformDetected
                    + " ownerUids=" + taskSnapshot.freeformOwnerUids
                    + " keywords=" + taskSnapshot.freeformKeywords
                    + " error=" + quoted(taskSnapshot.error));
            for (int i = 0; i < taskSnapshot.tasks.size(); i++) {
                FreeformLayerResolver.DiagnosticTask task = taskSnapshot.tasks.get(i);
                Log.i(TAG, "TASK id=" + id
                        + " index=" + i
                        + " taskId=" + task.taskId
                        + " displayId=" + task.displayId
                        + " mode=" + task.windowingMode
                        + " visible=" + task.visible
                        + " freeform=" + task.visibleFreeform
                        + " pkg=" + quoted(task.packageName)
                        + " uid=" + task.packageUid
                        + " top=" + quoted(task.topActivity)
                        + " base=" + quoted(task.baseActivity)
                        + " bounds=" + quoted(task.bounds)
                        + " error=" + quoted(task.error));
            }

            SurfaceLayerNameResolver.DiagnosticSnapshot sfSnapshot =
                    surfaceResolver.snapshotForDiagnostics(
                            taskSnapshot.freeformOwnerUids, taskSnapshot.freeformKeywords);
            int targetUidCandidateCount = 0;
            for (SurfaceLayerNameResolver.DiagnosticLayer layer : sfSnapshot.candidates) {
                if (layer.targetUidMatch) targetUidCandidateCount++;
            }

            Log.i(TAG, "SF id=" + id
                    + " service=" + sfSnapshot.serviceAvailable
                    + " composer=" + sfSnapshot.composerAvailable
                    + " method=" + sfSnapshot.methodAvailable
                    + " invoked=" + sfSnapshot.invocationSucceeded
                    + " totalLayers=" + sfSnapshot.totalLayerCount
                    + " candidates=" + sfSnapshot.candidates.size()
                    + " targetUidCandidates=" + targetUidCandidateCount
                    + " truncated=" + sfSnapshot.candidatesTruncated
                    + " failureStage=" + quoted(sfSnapshot.failureStage)
                    + " error=" + quoted(sfSnapshot.error));
            for (int i = 0; i < sfSnapshot.candidates.size(); i++) {
                SurfaceLayerNameResolver.DiagnosticLayer layer = sfSnapshot.candidates.get(i);
                Log.i(TAG, "LAYER id=" + id
                        + " index=" + i
                        + " uid=" + layer.ownerUid
                        + " targetUid=" + layer.targetUidMatch
                        + " keyword=" + layer.keywordMatch
                        + " suspicious=" + layer.suspiciousSystemLayer
                        + " name=" + quoted(layer.name)
                        + " extra=" + quoted(layer.extra));
            }

            String[] freeformLayers = productionFreeformLayerNames != null
                    ? productionFreeformLayerNames : new String[0];
            Log.i(TAG, "RESOLVER id=" + id
                    + " productionEvaluated=" + productionExclusionsEvaluated
                    + " freeformActive=" + productionFreeformActive
                    + " resolvedFreeformLayers=" + Arrays.toString(freeformLayers)
                    + " mergedExclusions=" + Arrays.toString(mergedExclusionNames)
                    + " safe=" + productionExclusionSafe);

            boolean wouldSafetyFallback = productionExclusionsEvaluated
                    && facts.requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                    && !productionExclusionSafe;
            String classification = classify(
                    taskSnapshot,
                    sfSnapshot,
                    targetUidCandidateCount,
                    productionExclusionsEvaluated,
                    freeformLayers,
                    productionExclusionSafe,
                    facts.requestedSource);
            Log.i(TAG, "DECISION id=" + id
                    + " requestedSource=" + facts.requestedSource
                    + " wouldSafetyFallback=" + wouldSafetyFallback
                    + " classification=" + classification);
        } catch (Throwable error) {
            Log.w(TAG, "ERROR id=" + id + " error=" + diagnosticError(error));
        } finally {
            Log.i(TAG, "END id=" + id);
        }
    }

    private static String classify(
            FreeformLayerResolver.DiagnosticSnapshot tasks,
            SurfaceLayerNameResolver.DiagnosticSnapshot sf,
            int targetUidCandidateCount,
            boolean productionExclusionsEvaluated,
            String[] productionFreeformLayerNames,
            boolean productionExclusionSafe,
            CaptureSourcePolicy.Source requestedSource) {
        if (tasks.error != null && !tasks.hasVisibleFreeformTask()) {
            return "TASK_DETECTION_FAILED";
        }
        if (!sf.invocationSucceeded) {
            return "SURFACEFLINGER_API_FAILED";
        }
        if (productionExclusionsEvaluated
                && productionFreeformLayerNames != null
                && productionFreeformLayerNames.length > 0) {
            if (requestedSource == CaptureSourcePolicy.Source.FULL_DISPLAY
                    && productionExclusionSafe) {
                return "POST_RESOLUTION_CAPTURE_PATH";
            }
            return "LAYER_RESOLUTION_SUCCEEDED";
        }
        if (targetUidCandidateCount == 0) {
            return "UID_MATCH_FAILED";
        }
        return "UNKNOWN";
    }

    private static String quoted(String value) {
        if (value == null) return "-";
        return '"' + value.replace('\n', ' ').replace('\r', ' ') + '"';
    }

    private static String diagnosticError(Throwable error) {
        Throwable cause = error;
        if (error instanceof java.lang.reflect.InvocationTargetException
                && ((java.lang.reflect.InvocationTargetException) error).getTargetException() != null) {
            cause = ((java.lang.reflect.InvocationTargetException) error).getTargetException();
        }
        return cause.getClass().getName() + ":" + String.valueOf(cause.getMessage());
    }
}
