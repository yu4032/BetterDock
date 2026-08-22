package com.hellovoid.liquiddock;

/** Minimal key/value persistence boundary for orientation layout snapshots. */
interface HomeGridOrientationMemoryStore {
    String read(String key);
    void write(String key, String value);
    void remove(String key);
}
