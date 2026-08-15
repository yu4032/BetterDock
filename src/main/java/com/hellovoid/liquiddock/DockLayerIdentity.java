package com.hellovoid.liquiddock;

/** Pure layer-name identity helpers for Floating Dock SurfaceControl generations. */
final class DockLayerIdentity {
    private DockLayerIdentity() {}

    static long layerId(String layerName) {
        if (layerName == null) return -1L;
        int hash = layerName.lastIndexOf('#');
        if (hash < 0 || hash + 1 >= layerName.length()) return -1L;
        long value = 0L;
        boolean any = false;
        for (int i = hash + 1; i < layerName.length(); i++) {
            char c = layerName.charAt(i);
            if (c < '0' || c > '9') break;
            any = true;
            long next = value * 10L + (c - '0');
            if (next < value) return -1L;
            value = next;
        }
        return any ? value : -1L;
    }

    static boolean sameGeneration(String first, String second) {
        long firstId = layerId(first);
        long secondId = layerId(second);
        if (firstId >= 0L && secondId >= 0L) return firstId == secondId;
        return first != null && first.equals(second);
    }

    static boolean isNewerGeneration(String candidate, String current) {
        long candidateId = layerId(candidate);
        long currentId = layerId(current);
        if (candidateId >= 0L && currentId >= 0L) return candidateId > currentId;
        if (candidateId >= 0L && currentId < 0L) return true;
        return current == null && candidate != null;
    }
}
