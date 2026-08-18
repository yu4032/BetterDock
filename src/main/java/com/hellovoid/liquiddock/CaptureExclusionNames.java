package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.LinkedHashSet;

/** Builds one deterministic, duplicate-free mode-1 SurfaceFlinger exclusion list. */
final class CaptureExclusionNames {
    private static final String[] MIUIX_307_SYSTEM_UI_LAYERS = {
            "NavigationBar", "StatusBar", "GestureStub", "DockAssistantView"
    };

    /** Closing-task auxiliaries that do not carry the application package prefix. */
    private static final String[] HOME_CLOSE_AUX_LAYERS = {
            "PreColorStarting", "Splash Screen ", "Miui Caption of Task="
    };

    /**
     * Package-name prefix of the APP that has committed to Launcher 4.50 performAppToHome().
     * The pull/drag phase remains fully live. This becomes non-null only at the vendor HOME
     * commit boundary, before CLOSE_TO_HOME starts drawing, and is cleared on HOME finish,
     * abort, Overview, generation reset, or a new Launcher-to-APP transition.
     */
    private static volatile String transitionAppLayerPrefix;

    private CaptureExclusionNames() {}

    static void setTransitionAppLayerPrefix(String packagePrefix) {
        if (packagePrefix == null || packagePrefix.isEmpty()
                || "com.miui.home".equals(packagePrefix)) {
            transitionAppLayerPrefix = null;
        } else {
            transitionAppLayerPrefix = packagePrefix;
        }
    }

    static void clearTransitionAppLayerPrefix() {
        transitionAppLayerPrefix = null;
    }

    static String transitionAppLayerPrefixForTests() {
        return transitionAppLayerPrefix;
    }

    static String[] merge(String dockLayer, String dragLayer,
                          Collection<String> freeformLayers) {
        // HOME closing-task exclusion is a transition authority, not a material-pipeline feature.
        // Force the 307 name-exclusion path while the early vendor HOME filter is armed.
        boolean homeCloseActive = transitionAppLayerPrefix != null;
        return mergeInternal(dockLayer, dragLayer, freeformLayers,
                Miuix307MaterialPipeline.isInstalled() || homeCloseActive);
    }

    /**
     * HyperOS 3.0.307+ pass-window blur samples only the compositor backdrop below the Dock.
     * Our mode-1 screen capture instead sees later SystemUI layers unless they are explicitly
     * excluded. Generic prefixes match concrete SF names such as NavigationBar0 and
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
            String closingPackage = transitionAppLayerPrefix;
            if (closingPackage != null) {
                add(names, closingPackage);
                for (String name : HOME_CLOSE_AUX_LAYERS) add(names, name);
            }
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
