package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

/** Stable traversal-order-independent fingerprint of all persisted home-grid placement fields. */
final class HomeGridLayoutFingerprint {
    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private HomeGridLayoutFingerprint() {}

    static long of(Collection<HomeGridItemPosition> positions) {
        if (positions == null) return 0L;
        ArrayList<HomeGridItemPosition> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.comparingLong(HomeGridItemPosition::itemId));

        long hash = mix(OFFSET_BASIS, ordered.size());
        for (HomeGridItemPosition item : ordered) {
            if (item == null) {
                hash = mix(hash, Long.MIN_VALUE);
                continue;
            }
            hash = mix(hash, item.itemId());
            hash = mix(hash, item.screenId());
            hash = mix(hash, item.cellX());
            hash = mix(hash, item.cellY());
            hash = mix(hash, item.spanX());
            hash = mix(hash, item.spanY());
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        long result = hash;
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            result ^= (value >>> shift) & 0xffL;
            result *= PRIME;
        }
        return result;
    }
}
