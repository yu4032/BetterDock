package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small SurfaceFlinger debug-layer adapter shared by app and freeform capture policy. */
final class SurfaceLayerNameResolver {
    private static final int MAX_DIAGNOSTIC_CANDIDATES = 96;

    static final class DiagnosticLayer {
        final String name;
        final Integer ownerUid;
        final boolean targetUidMatch;
        final boolean keywordMatch;
        final boolean suspiciousSystemLayer;
        final String extra;

        DiagnosticLayer(String name, Integer ownerUid, boolean targetUidMatch,
                        boolean keywordMatch, boolean suspiciousSystemLayer, String extra) {
            this.name = name;
            this.ownerUid = ownerUid;
            this.targetUidMatch = targetUidMatch;
            this.keywordMatch = keywordMatch;
            this.suspiciousSystemLayer = suspiciousSystemLayer;
            this.extra = extra;
        }
    }

    static final class DiagnosticSnapshot {
        final boolean serviceAvailable;
        final boolean composerAvailable;
        final boolean methodAvailable;
        final boolean invocationSucceeded;
        final int totalLayerCount;
        final String layerClassName;
        final boolean ownerUidAccessorAvailable;
        final boolean nameAccessorAvailable;
        final int ownerUidReadableCount;
        final int nameReadableCount;
        final String layerMetadataError;
        final List<DiagnosticLayer> candidates;
        final boolean candidatesTruncated;
        final String failureStage;
        final String error;

        DiagnosticSnapshot(boolean serviceAvailable, boolean composerAvailable,
                           boolean methodAvailable, boolean invocationSucceeded,
                           int totalLayerCount, String layerClassName,
                           boolean ownerUidAccessorAvailable, boolean nameAccessorAvailable,
                           int ownerUidReadableCount, int nameReadableCount,
                           String layerMetadataError, List<DiagnosticLayer> candidates,
                           boolean candidatesTruncated, String failureStage, String error) {
            this.serviceAvailable = serviceAvailable;
            this.composerAvailable = composerAvailable;
            this.methodAvailable = methodAvailable;
            this.invocationSucceeded = invocationSucceeded;
            this.totalLayerCount = totalLayerCount;
            this.layerClassName = layerClassName;
            this.ownerUidAccessorAvailable = ownerUidAccessorAvailable;
            this.nameAccessorAvailable = nameAccessorAvailable;
            this.ownerUidReadableCount = ownerUidReadableCount;
            this.nameReadableCount = nameReadableCount;
            this.layerMetadataError = layerMetadataError;
            this.candidates = candidates;
            this.candidatesTruncated = candidatesTruncated;
            this.failureStage = failureStage;
            this.error = error;
        }
    }

    String resolveTopmostByOwnerUid(int ownerUid) throws Exception {
        String best = null;
        for (Object layer : queryLayers()) {
            Integer uid = ownerUid(layer);
            if (uid == null || uid != ownerUid) continue;
            String name = layerName(layer);
            if (name != null && !name.isEmpty()) best = name;
        }
        return best;
    }

    Collection<String> resolveAllByOwnerUids(Collection<Integer> ownerUids) throws Exception {
        if (ownerUids == null || ownerUids.isEmpty()) return Collections.emptyList();
        Set<Integer> wanted = new java.util.HashSet<>(ownerUids);
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Object layer : queryLayers()) {
            Integer uid = ownerUid(layer);
            if (uid == null || !wanted.contains(uid)) continue;
            String name = layerName(layer);
            if (name != null && !name.isEmpty()) names.add(name);
        }
        return names;
    }

    /**
     * Independent SurfaceFlinger probe for the temporary one-shot diagnostic. Production
     * resolution above continues to use queryLayers() unchanged; this method records each
     * hidden-API stage explicitly so a device failure is no longer collapsed to an empty list.
     */
    DiagnosticSnapshot snapshotForDiagnostics(Collection<Integer> targetUids,
                                              Collection<String> keywords) {
        boolean serviceAvailable = false;
        boolean composerAvailable = false;
        boolean methodAvailable = false;
        boolean invocationSucceeded = false;
        int totalLayerCount = 0;
        String layerClassName = null;
        boolean ownerUidAccessorAvailable = false;
        boolean nameAccessorAvailable = false;
        int ownerUidReadableCount = 0;
        int nameReadableCount = 0;
        String layerMetadataError = null;
        ArrayList<DiagnosticLayer> candidates = new ArrayList<>();
        boolean truncated = false;
        String stage = "service_lookup";

        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            java.lang.reflect.Method getService =
                    serviceManager.getMethod("getService", String.class);
            Object binder = getService.invoke(null, "SurfaceFlinger");
            serviceAvailable = binder != null;
            if (binder == null) {
                return diagnosticSnapshot(serviceAvailable, composerAvailable, methodAvailable,
                        invocationSucceeded, totalLayerCount, layerClassName,
                        ownerUidAccessorAvailable, nameAccessorAvailable,
                        ownerUidReadableCount, nameReadableCount, layerMetadataError,
                        candidates, false, stage, "SurfaceFlinger service returned null");
            }

            stage = "as_interface";
            Class<?> stub = Class.forName("android.view.ISurfaceComposer$Stub");
            java.lang.reflect.Method asInterface = stub.getMethod(
                    "asInterface", android.os.IBinder.class);
            Object composer = asInterface.invoke(null, binder);
            composerAvailable = composer != null;
            if (composer == null) {
                return diagnosticSnapshot(serviceAvailable, composerAvailable, methodAvailable,
                        invocationSucceeded, totalLayerCount, layerClassName,
                        ownerUidAccessorAvailable, nameAccessorAvailable,
                        ownerUidReadableCount, nameReadableCount, layerMetadataError,
                        candidates, false, stage,
                        "ISurfaceComposer.Stub.asInterface returned null");
            }

            stage = "method_lookup";
            java.lang.reflect.Method getLayerDebugInfo =
                    composer.getClass().getMethod("getLayerDebugInfo");
            getLayerDebugInfo.setAccessible(true);
            methodAvailable = true;

            stage = "method_invoke";
            Object result = getLayerDebugInfo.invoke(composer);
            if (!(result instanceof List)) {
                return diagnosticSnapshot(serviceAvailable, composerAvailable, methodAvailable,
                        false, 0, layerClassName,
                        ownerUidAccessorAvailable, nameAccessorAvailable,
                        ownerUidReadableCount, nameReadableCount, layerMetadataError,
                        candidates, false, stage,
                        "getLayerDebugInfo returned "
                                + (result == null ? "null" : result.getClass().getName()));
            }
            invocationSucceeded = true;
            List<?> layers = (List<?>) result;
            totalLayerCount = layers.size();

            stage = "candidate_extract";
            Set<Integer> wanted = targetUids == null
                    ? Collections.emptySet() : new java.util.HashSet<>(targetUids);
            ArrayList<String> normalizedKeywords = new ArrayList<>();
            if (keywords != null) {
                for (String keyword : keywords) {
                    if (keyword == null || keyword.isEmpty()) continue;
                    normalizedKeywords.add(keyword.toLowerCase(Locale.ROOT));
                }
            }

            for (Object layer : layers) {
                if (layer == null) continue;
                if (layerClassName == null) layerClassName = layer.getClass().getName();

                Integer uid = null;
                String name = null;
                try {
                    java.lang.reflect.Method method = layer.getClass().getMethod("getOwnerUid");
                    ownerUidAccessorAvailable = true;
                    Object value = method.invoke(layer);
                    if (value instanceof Integer) {
                        uid = (Integer) value;
                        ownerUidReadableCount++;
                    } else if (layerMetadataError == null) {
                        layerMetadataError = "getOwnerUid returned "
                                + (value == null ? "null" : value.getClass().getName());
                    }
                } catch (Throwable error) {
                    if (layerMetadataError == null) {
                        layerMetadataError = "getOwnerUid=" + diagnosticError(error);
                    }
                }

                try {
                    java.lang.reflect.Method method = layer.getClass().getMethod("getName");
                    nameAccessorAvailable = true;
                    Object value = method.invoke(layer);
                    if (value instanceof String) {
                        name = (String) value;
                        nameReadableCount++;
                    } else if (layerMetadataError == null) {
                        layerMetadataError = "getName returned "
                                + (value == null ? "null" : value.getClass().getName());
                    }
                } catch (Throwable error) {
                    if (layerMetadataError == null) {
                        layerMetadataError = "getName=" + diagnosticError(error);
                    }
                }

                String lower = name != null ? name.toLowerCase(Locale.ROOT) : "";
                boolean targetUidMatch = uid != null && wanted.contains(uid);
                boolean keywordMatch = containsAny(lower, normalizedKeywords);
                boolean suspicious = lower.contains("freeform")
                        || lower.contains("miuifreeform")
                        || lower.contains("task")
                        || lower.contains("leash")
                        || lower.contains("window");
                if (!targetUidMatch && !keywordMatch && !suspicious) continue;
                if (candidates.size() >= MAX_DIAGNOSTIC_CANDIDATES) {
                    truncated = true;
                    continue;
                }
                candidates.add(new DiagnosticLayer(
                        name != null ? name : "-",
                        uid,
                        targetUidMatch,
                        keywordMatch,
                        suspicious,
                        diagnosticExtra(layer)));
            }

            return diagnosticSnapshot(serviceAvailable, composerAvailable, methodAvailable,
                    invocationSucceeded, totalLayerCount, layerClassName,
                    ownerUidAccessorAvailable, nameAccessorAvailable,
                    ownerUidReadableCount, nameReadableCount, layerMetadataError,
                    candidates, truncated, null, null);
        } catch (Throwable error) {
            return diagnosticSnapshot(serviceAvailable, composerAvailable, methodAvailable,
                    invocationSucceeded, totalLayerCount, layerClassName,
                    ownerUidAccessorAvailable, nameAccessorAvailable,
                    ownerUidReadableCount, nameReadableCount, layerMetadataError,
                    candidates, truncated, stage, diagnosticError(error));
        }
    }

    private List<?> queryLayers() throws Exception {
        Class<?> stub = Class.forName("android.view.ISurfaceComposer$Stub");
        java.lang.reflect.Method asInterface = stub.getMethod(
                "asInterface", android.os.IBinder.class);
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        java.lang.reflect.Method getService = serviceManager.getMethod("getService", String.class);
        Object binder = getService.invoke(null, "SurfaceFlinger");
        Object composer = asInterface.invoke(null, binder);
        if (composer == null) return Collections.emptyList();
        Object result = composer.getClass().getMethod("getLayerDebugInfo").invoke(composer);
        return result instanceof List ? (List<?>) result : Collections.emptyList();
    }

    private static Integer ownerUid(Object layer) {
        try {
            Object value = layer.getClass().getMethod("getOwnerUid").invoke(layer);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String layerName(Object layer) {
        try {
            Object value = layer.getClass().getMethod("getName").invoke(layer);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static DiagnosticSnapshot diagnosticSnapshot(
            boolean serviceAvailable, boolean composerAvailable, boolean methodAvailable,
            boolean invocationSucceeded, int totalLayerCount, String layerClassName,
            boolean ownerUidAccessorAvailable, boolean nameAccessorAvailable,
            int ownerUidReadableCount, int nameReadableCount, String layerMetadataError,
            List<DiagnosticLayer> candidates, boolean candidatesTruncated,
            String failureStage, String error) {
        return new DiagnosticSnapshot(
                serviceAvailable,
                composerAvailable,
                methodAvailable,
                invocationSucceeded,
                totalLayerCount,
                layerClassName,
                ownerUidAccessorAvailable,
                nameAccessorAvailable,
                ownerUidReadableCount,
                nameReadableCount,
                layerMetadataError,
                Collections.unmodifiableList(new ArrayList<>(candidates)),
                candidatesTruncated,
                failureStage,
                error);
    }

    private static boolean containsAny(String value, Collection<String> needles) {
        if (value == null || value.isEmpty() || needles == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && value.contains(needle)) return true;
        }
        return false;
    }

    private static String diagnosticExtra(Object layer) {
        return "id=" + optionalMethod(layer, "getId")
                + ",layerId=" + optionalMethod(layer, "getLayerId")
                + ",parentId=" + optionalMethod(layer, "getParentId")
                + ",z=" + optionalMethod(layer, "getZ");
    }

    private static String optionalMethod(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value != null ? String.valueOf(value) : "-";
        } catch (Throwable ignored) {
            return "-";
        }
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
