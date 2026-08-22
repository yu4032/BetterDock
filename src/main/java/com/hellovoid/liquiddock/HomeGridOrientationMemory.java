package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Versioned sidecar persistence for complete per-profile, per-orientation layouts. */
final class HomeGridOrientationMemory {
    private static final String KEY_PREFIX = "liquiddock_home_grid_memory_v1_";
    private static final String PAYLOAD_VERSION = "v1";

    private final HomeGridOrientationMemoryStore store;

    HomeGridOrientationMemory(HomeGridOrientationMemoryStore store) {
        if (store == null) throw new IllegalArgumentException("store == null");
        this.store = store;
    }

    void save(HomeGridLayoutSnapshot snapshot) {
        if (snapshot == null) return;
        store.write(key(snapshot.profile(), snapshot.orientation()), encode(snapshot));
    }

    HomeGridLayoutSnapshot load(HomeGridProfile profile, HomeGridOrientation orientation) {
        if (profile == null || orientation == null) return null;
        String payload = store.read(key(profile, orientation));
        return decode(profile, orientation, payload);
    }

    void invalidate(HomeGridProfile profile, HomeGridOrientation orientation) {
        if (profile == null || orientation == null) return;
        store.remove(key(profile, orientation));
    }

    private static String key(HomeGridProfile profile, HomeGridOrientation orientation) {
        return KEY_PREFIX + profile.persistedValue() + "_" + orientation.name().toLowerCase();
    }

    private static String encode(HomeGridLayoutSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        out.append(PAYLOAD_VERSION)
                .append('|').append(snapshot.profile().persistedValue())
                .append('|').append(snapshot.orientation().name());

        List<HomeGridItemPosition> positions = new ArrayList<>(snapshot.positions());
        positions.sort(Comparator.comparingLong(HomeGridItemPosition::itemId));
        for (HomeGridItemPosition position : positions) {
            out.append('\n')
                    .append(position.itemId()).append(',')
                    .append(position.screenId()).append(',')
                    .append(position.cellX()).append(',')
                    .append(position.cellY()).append(',')
                    .append(position.spanX()).append(',')
                    .append(position.spanY());
        }
        return out.toString();
    }

    private static HomeGridLayoutSnapshot decode(HomeGridProfile profile,
                                                  HomeGridOrientation orientation,
                                                  String payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            String[] lines = payload.split("\\n", -1);
            String expectedHeader = PAYLOAD_VERSION + "|" + profile.persistedValue()
                    + "|" + orientation.name();
            if (lines.length == 0 || !expectedHeader.equals(lines[0])) return null;

            List<HomeGridItemPosition> positions = new ArrayList<>();
            for (int index = 1; index < lines.length; index++) {
                if (lines[index].isEmpty()) return null;
                String[] fields = lines[index].split(",", -1);
                if (fields.length != 6) return null;
                positions.add(new HomeGridItemPosition(
                        Long.parseLong(fields[0]),
                        Long.parseLong(fields[1]),
                        Integer.parseInt(fields[2]),
                        Integer.parseInt(fields[3]),
                        Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5])));
            }
            return HomeGridLayoutSnapshot.create(profile, orientation, positions);
        } catch (RuntimeException error) {
            return null;
        }
    }
}
