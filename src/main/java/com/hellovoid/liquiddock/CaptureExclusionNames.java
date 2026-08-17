package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.LinkedHashSet;

/** Builds one deterministic, duplicate-free mode-1 SurfaceFlinger exclusion list. */
final class CaptureExclusionNames {
    private static final String[] MIUIX_307_SYSTEM_UI_LAYERS = {
            "NavigationBar", "StatusBar", "GestureStub", "DockAssistantView"
    };

    private CaptureExclusionNames() {}

    static String[] merge(String dockLayer, String dragLayer,
                          Collection<String> freeformLayers) {
        return mergeInternal(dockLayer, dragLayer, freeformLayers,
                Miuix307MaterialPipeline.isInstalled());
    }

    /**
     * HyperOS 3.0.307+ pass-window blur samples only the compositor backdrop below the Dock.
     * Our mode-1 screen capture instead sees later SystemUI layers unless they are explicitly
     * excluded. Generic layer-name prefixes match concrete SF names such as NavigationBar0 and
     * GestureStubLeft/Right, just as "Floating Dock" matches "Floating Dock#...".
     */
    static String[] mergeMiuix307(String dockLayer, String dragLayer,
                                  Collection<String> freeformLayers) {
        return mergeInternal(dockLayer, dragLayer, freeformLayers, true);
    }

    private static String[] mergeInternal(String dockLayer, String dragLayer,
                                          Collection<String> freeformLayers,
                                          boolean miuix307) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        add(names, dockLayer);
        add(names, dragLayer);
        if (miuix307) {
            for (String name : MIUIX_307_SYSTEM_UI_LAYERS) add(names, name);
        }
        if (freeformLayers != null) {
            for (String name : freeformLayers) add(names, name);
        }
        return names.isEmpty() ? null : names.toArray(new String[0]);
    }

    private static void add(LinkedHashSet<String> names, String value) {
        if (value != null && !value.isEmpty()) names.add(value);
    }
}
