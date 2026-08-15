package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.LinkedHashSet;

/** Builds one deterministic, duplicate-free mode-1 SurfaceFlinger exclusion list. */
final class CaptureExclusionNames {
    private CaptureExclusionNames() {}

    static String[] merge(String dockLayer, String dragLayer,
                          Collection<String> freeformLayers) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        add(names, dockLayer);
        add(names, dragLayer);
        if (freeformLayers != null) {
            for (String name : freeformLayers) add(names, name);
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    private static void add(LinkedHashSet<String> names, String value) {
        if (value != null && !value.isEmpty()) names.add(value);
    }
}
