package com.hellovoid.liquiddock;

/**
 * Single feature boundary for HyperOS 3.0.307+ compatibility.
 * MainHook decides whether the user-enabled feature is active; implementation details
 * stay inside the dedicated material/capture adapters.
 */
final class Miuix307Compatibility {
    private Miuix307Compatibility() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig config) {
        return Miuix307MaterialPipeline.install(classLoader, config);
    }
}
