package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.LinkedHashSet;

/** Builds one deterministic, duplicate-free mode-1 SurfaceFlinger exclusion list. */
final class CaptureExclusionNames {
    private static final String[] MIUIX_307_SYSTEM_UI_LAYERS = {
            "NavigationBar", "StatusBar", "GestureStub", "DockAssistantView"
    };

    /**
     * Package-name prefix of the APP layer currently closing into HOME.
     *
     * The 4.50 gesture pull itself remains live and fully captured. WMShell APP_TO_LAUNCHER starts
     * only after release, when MIUI reparents the closing task into the icon-flight transition.
     * During that final phase mode-1 should keep sampling Launcher/wallpaper below the Dock but
     * omit the shrinking APP surface so its icon-flight image cannot refract into the Dock glass.
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
            add(names, transitionAppLayerPrefix);
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
