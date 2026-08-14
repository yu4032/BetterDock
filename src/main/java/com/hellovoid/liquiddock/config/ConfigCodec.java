package com.hellovoid.liquiddock.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure transformations between preference writes and the historical JSON value contract. */
public final class ConfigCodec {
    private ConfigCodec() {}

    public static LinkedHashMap<String, Object> exportValues(Map<String, ?> preferences) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (key.exportMode() == ConfigKey.ExportMode.NEVER) continue;
            if (key.exportMode() == ConfigKey.ExportMode.IF_PRESENT
                    && !preferences.containsKey(key.name())) continue;
            out.put(key.name(), exportValue(key, preferences));
        }
        return out;
    }

    public static LinkedHashMap<String, Object> importValues(Map<String, ?> jsonValues) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        importMandatoryGridMigrationFlags(jsonValues, out);
        for (ConfigKey<?> key : ConfigSchema.all()) {
            if (!isDirectlyImportable(key) || !jsonValues.containsKey(key.name())) continue;
            importValue(key, jsonValues.get(key.name()), out);
        }
        importLegacyHorizontalMargins(jsonValues, out);
        importPreAxisLegacyMargins(jsonValues, out);
        importLegacyWorkstationAllAppsOffsets(jsonValues, out);
        return out;
    }

    private static Object exportValue(ConfigKey<?> key, Map<String, ?> preferences) {
        if (key == ConfigSchema.Dock.DIMENSIONS_DP || key == ConfigSchema.Glass.DIMENSIONS_DP) {
            return Boolean.TRUE;
        }
        if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
            Object tenths = preferences.get(key.name() + "_tenths");
            if (preferences.containsKey(key.name() + "_tenths") && tenths instanceof Number) {
                return ((Number) tenths).intValue() / 10.0d;
            }
        }
        Object value = preferences.get(key.name());
        return value == null ? key.exportDefault() : value;
    }

    private static boolean isDirectlyImportable(ConfigKey<?> key) {
        return key.exportMode() != ConfigKey.ExportMode.NEVER;
    }

    private static void importMandatoryGridMigrationFlags(Map<String, ?> jsonValues,
                                                            Map<String, Object> out) {
        out.put(ConfigSchema.Grid.MARGINS_DP.name(), booleanValue(
                jsonValues.get(ConfigSchema.Grid.MARGINS_DP.name())));
        out.put(ConfigSchema.Grid.MARGINS_OFFSET.name(), booleanValue(
                jsonValues.get(ConfigSchema.Grid.MARGINS_OFFSET.name())));
    }

    private static void importValue(ConfigKey<?> key, Object value, Map<String, Object> out) {
        if (key.storageMode() == ConfigKey.StorageMode.DP_TENTHS) {
            importDp(key.name(), value, out);
        } else if (key.type() == ConfigKey.Type.BOOLEAN) {
            out.put(key.name(), booleanValue(value));
        } else if (key.type() == ConfigKey.Type.INT && value instanceof Number) {
            out.put(key.name(), clamp(((Number) value).intValue(), key.minInt(), key.maxInt()));
        } else if (key.type() == ConfigKey.Type.STRING && value != null) {
            out.put(key.name(), String.valueOf(value));
        }
    }

    private static void importLegacyHorizontalMargins(Map<String, ?> jsonValues,
                                                       Map<String, Object> out) {
        copyHorizontalMargin(jsonValues, out, ConfigSchema.Grid.LEGACY_LANDSCAPE_HORIZONTAL_MARGIN,
                ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT, ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT);
        copyHorizontalMargin(jsonValues, out, ConfigSchema.Grid.LEGACY_PORTRAIT_HORIZONTAL_MARGIN,
                ConfigSchema.Grid.PORTRAIT_MARGIN_LEFT, ConfigSchema.Grid.PORTRAIT_MARGIN_RIGHT);
    }

    private static void copyHorizontalMargin(Map<String, ?> jsonValues, Map<String, Object> out,
                                             ConfigKey<Integer> legacy, ConfigKey<Integer> left,
                                             ConfigKey<Integer> right) {
        Object value = jsonValues.get(legacy.name());
        if (jsonValues.containsKey(legacy.name()) && value instanceof Number) {
            int margin = ((Number) value).intValue();
            out.put(left.name(), margin);
            out.put(right.name(), margin);
        }
    }

    private static void importPreAxisLegacyMargins(Map<String, ?> jsonValues,
                                                    Map<String, Object> out) {
        if (jsonValues.containsKey(ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT.name())
                || !jsonValues.containsKey(ConfigSchema.Grid.LEGACY_MARGIN_LEFT.name())) return;

        int left = clamp(legacyMargin(jsonValues, ConfigSchema.Grid.LEGACY_MARGIN_LEFT), 0, 400);
        int right = clamp(legacyMargin(jsonValues, ConfigSchema.Grid.LEGACY_MARGIN_RIGHT), 0, 400);
        int top = clamp(legacyMargin(jsonValues, ConfigSchema.Grid.LEGACY_MARGIN_TOP), 0, 400);
        int bottom = clamp(legacyMargin(jsonValues, ConfigSchema.Grid.LEGACY_MARGIN_BOTTOM), 0, 400);
        out.put(ConfigSchema.Grid.LANDSCAPE_MARGIN_LEFT.name(), left);
        out.put(ConfigSchema.Grid.LANDSCAPE_MARGIN_RIGHT.name(), right);
        out.put(ConfigSchema.Grid.LANDSCAPE_MARGIN_TOP.name(), top);
        out.put(ConfigSchema.Grid.LANDSCAPE_MARGIN_BOTTOM.name(), bottom);
        out.put(ConfigSchema.Grid.PORTRAIT_MARGIN_LEFT.name(), top);
        out.put(ConfigSchema.Grid.PORTRAIT_MARGIN_RIGHT.name(), bottom);
        out.put(ConfigSchema.Grid.PORTRAIT_MARGIN_TOP.name(), right);
        out.put(ConfigSchema.Grid.PORTRAIT_MARGIN_BOTTOM.name(), left);
    }

    private static int legacyMargin(Map<String, ?> jsonValues, ConfigKey<Integer> key) {
        Object value = jsonValues.get(key.name());
        return value instanceof Number ? ((Number) value).intValue() : key.uiDefault();
    }

    private static void importLegacyWorkstationAllAppsOffsets(Map<String, ?> jsonValues,
                                                               Map<String, Object> out) {
        importLegacyDp(jsonValues, out, ConfigSchema.Workstation.LEGACY_ALL_APPS_HORIZONTAL_OFFSET);
        importLegacyDp(jsonValues, out, ConfigSchema.Workstation.LEGACY_ALL_APPS_VERTICAL_OFFSET);
    }

    private static void importLegacyDp(Map<String, ?> jsonValues, Map<String, Object> out,
                                       ConfigKey<Integer> key) {
        if (jsonValues.containsKey(key.name())) importDp(key.name(), jsonValues.get(key.name()), out);
    }

    private static void importDp(String key, Object value, Map<String, Object> out) {
        if (!(value instanceof Number)) return;
        double dp = ((Number) value).doubleValue();
        out.put(key, (int) Math.round(dp));
        out.put(key + "_tenths", (int) Math.round(dp * 10.0d));
    }

    private static int clamp(int value, Integer min, Integer max) {
        if (min != null && value < min) return min;
        if (max != null && value > max) return max;
        return value;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(String.valueOf(value));
    }
}
