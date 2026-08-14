package com.hellovoid.liquiddock.config;

/** Immutable metadata for one persisted LiquidDock configuration setting. */
public final class ConfigKey<T> {
    public enum Type { BOOLEAN, INT, STRING }
    public enum StorageMode { DIRECT, DP_TENTHS }
    public enum ExportMode { ALWAYS, IF_PRESENT, NEVER }

    private final String name;
    private final Type type;
    private final T uiDefault;
    private final T runtimeFallback;
    private final T exportDefault;
    private final Integer minInt;
    private final Integer maxInt;
    private final StorageMode storageMode;
    private final ExportMode exportMode;

    public ConfigKey(String name, Type type, T uiDefault, T runtimeFallback,
                     T exportDefault, Integer minInt, Integer maxInt,
                     StorageMode storageMode, ExportMode exportMode) {
        this.name = name;
        this.type = type;
        this.uiDefault = uiDefault;
        this.runtimeFallback = runtimeFallback;
        this.exportDefault = exportDefault;
        this.minInt = minInt;
        this.maxInt = maxInt;
        this.storageMode = storageMode;
        this.exportMode = exportMode;
    }

    public String name() { return name; }
    public Type type() { return type; }
    public T uiDefault() { return uiDefault; }
    public T runtimeFallback() { return runtimeFallback; }
    public T exportDefault() { return exportDefault; }
    public Integer minInt() { return minInt; }
    public Integer maxInt() { return maxInt; }
    public StorageMode storageMode() { return storageMode; }
    public ExportMode exportMode() { return exportMode; }
}
