package com.hellovoid.liquiddock;

/** Persisted Liquid Glass blur backend selection. */
enum LiquidBlurMode {
    SHADER("shader"),
    ADVANCED_MATERIAL("advanced_material");

    private final String persistedValue;

    LiquidBlurMode(String persistedValue) {
        this.persistedValue = persistedValue;
    }

    String persistedValue() {
        return persistedValue;
    }

    static LiquidBlurMode fromPersisted(String value) {
        if (ADVANCED_MATERIAL.persistedValue.equals(value)) {
            return ADVANCED_MATERIAL;
        }
        return SHADER;
    }
}
