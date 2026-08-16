package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small SurfaceFlinger debug-layer adapter shared by app and freeform capture policy. */
final class SurfaceLayerNameResolver {
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
}
