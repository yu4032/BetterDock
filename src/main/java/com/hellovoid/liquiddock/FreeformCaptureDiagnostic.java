package com.hellovoid.liquiddock;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot, read-only device diagnostic for the freeform exclusion resolver.
 *
 * This class must never participate in capture policy. It snapshots the same task and
 * SurfaceFlinger environment production resolution sees, prints one grouped report, and returns.
 */
final class FreeformCaptureDiagnostic {
    private static final String TAG = "LiquidDockDiag";
    private static final AtomicBoolean ATTEMPTED = new AtomicBoolean(false);

    private FreeformCaptureDiagnostic() {}

    static boolean hasAttempted() {
        return ATTEMPTED.get();
    }

    static void runOnce(Context context,
                        FreeformLayerResolver.DiagnosticSnapshot taskSnapshot,
                        SurfaceLayerNameResolver surfaceResolver,
                        Collection<Integer> productionOwnerUids,
                        Collection<String> productionResolvedLayers,
                        Throwable productionResolutionError) {
        if (taskSnapshot == null || surfaceResolver == null) return;

        boolean productionFreeformDetected = productionOwnerUids != null
                && !productionOwnerUids.isEmpty();
        if (!productionFreeformDetected && !taskSnapshot.hasVisibleFreeformTask()) return;
        if (!ATTEMPTED.compareAndSet(false, true)) return;

        String id = Long.toHexString(SystemClock.elapsedRealtimeNanos());
        Log.i(TAG, "BEGIN id=" + id);
        try {
            Log.i(TAG, "PROCESS id=" + id
                    + " package=" + quoted(context != null ? context.getPackageName() : null));

            Log.i(TAG, "TASKS id=" + id
                    + " count=" + taskSnapshot.tasks.size()
                    + " visibleFreeformDetected=" + taskSnapshot.visibleFreeformDetected
                    + " diagnosticOwnerUids=" + taskSnapshot.freeformOwnerUids
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

            LinkedHashSet<Integer> targetUids = new LinkedHashSet<>();
            if (productionOwnerUids != null) targetUids.addAll(productionOwnerUids);
            targetUids.addAll(taskSnapshot.freeformOwnerUids);

            SurfaceLayerNameResolver.DiagnosticSnapshot sfSnapshot =
                    surfaceResolver.snapshotForDiagnostics(
                            targetUids, taskSnapshot.freeformKeywords);
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
                    + " layerClass=" + quoted(sfSnapshot.layerClassName)
                    + " ownerUidAccessor=" + sfSnapshot.ownerUidAccessorAvailable
                    + " nameAccessor=" + sfSnapshot.nameAccessorAvailable
                    + " ownerUidReadable=" + sfSnapshot.ownerUidReadableCount
                    + " nameReadable=" + sfSnapshot.nameReadableCount
                    + " metadataError=" + quoted(sfSnapshot.layerMetadataError)
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

            ArrayList<Integer> ownerUids = productionOwnerUids == null
                    ? new ArrayList<>() : new ArrayList<>(productionOwnerUids);
            ArrayList<String> resolvedLayers = productionResolvedLayers == null
                    ? new ArrayList<>() : new ArrayList<>(productionResolvedLayers);
            boolean productionSafe = ownerUids.isEmpty() || !resolvedLayers.isEmpty();
            boolean wouldSafetyFallback = !ownerUids.isEmpty() && resolvedLayers.isEmpty();

            Log.i(TAG, "RESOLVER id=" + id
                    + " productionOwnerUids=" + ownerUids
                    + " resolvedLayers=" + resolvedLayers
                    + " resolutionError=" + quoted(diagnosticErrorOrNull(productionResolutionError))
                    + " productionSafe=" + productionSafe);

            String classification = classify(
                    taskSnapshot,
                    sfSnapshot,
                    targetUidCandidateCount,
                    resolvedLayers,
                    productionResolutionError);
            Log.i(TAG, "DECISION id=" + id
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
            Collection<String> productionResolvedLayers,
            Throwable productionResolutionError) {
        if (tasks.error != null && !tasks.hasVisibleFreeformTask()) {
            return "TASK_DETECTION_FAILED";
        }
        if (!sf.invocationSucceeded) {
            return "SURFACEFLINGER_API_FAILED";
        }
        if (sf.totalLayerCount == 0) {
            return "SURFACEFLINGER_EMPTY_RESULT";
        }
        if (sf.ownerUidReadableCount == 0 || sf.nameReadableCount == 0) {
            return "LAYER_METADATA_API_FAILED";
        }
        if (productionResolutionError != null) {
            return "PRODUCTION_RESOLVER_FAILED";
        }
        if (productionResolvedLayers != null && !productionResolvedLayers.isEmpty()) {
            return "POST_RESOLUTION_CAPTURE_PATH";
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

    private static String diagnosticErrorOrNull(Throwable error) {
        return error == null ? null : diagnosticError(error);
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
