package com.hellovoid.liquiddock;

import java.util.Locale;

/** Pure matching policy for workstation All Apps layout signals across launcher variants. */
final class WorkstationLayoutClassifier {
    private WorkstationLayoutClassifier() {}

    static boolean matches(boolean exactMethodResult, String gridTypeText, String ancestryText) {
        if (exactMethodResult) return true;
        String grid = normalize(gridTypeText);
        if (grid.contains("all_apps") || grid.contains("allapps")) return true;
        String ancestry = normalize(ancestryText);
        return ancestry.contains("allapps") || ancestry.contains("all_apps");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
