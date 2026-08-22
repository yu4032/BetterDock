package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete validated placement set for one grid profile and orientation. */
final class HomeGridLayoutSnapshot {
    private final HomeGridProfile profile;
    private final HomeGridOrientation orientation;
    private final Map<Long, HomeGridItemPosition> positions;

    private HomeGridLayoutSnapshot(HomeGridProfile profile,
                                   HomeGridOrientation orientation,
                                   Map<Long, HomeGridItemPosition> positions) {
        this.profile = profile;
        this.orientation = orientation;
        this.positions = Collections.unmodifiableMap(positions);
    }

    static HomeGridLayoutSnapshot create(HomeGridProfile profile,
                                         HomeGridOrientation orientation,
                                         Collection<HomeGridItemPosition> positions) {
        if (profile == null || orientation == null || positions == null) return null;

        int columns = profile.columns(orientation == HomeGridOrientation.PORTRAIT);
        int rows = profile.rows(orientation == HomeGridOrientation.PORTRAIT);
        LinkedHashMap<Long, HomeGridItemPosition> accepted = new LinkedHashMap<>();

        for (HomeGridItemPosition candidate : positions) {
            if (candidate == null || !candidate.fitsWithin(columns, rows)
                    || accepted.containsKey(candidate.itemId())) {
                return null;
            }
            for (HomeGridItemPosition existing : accepted.values()) {
                if (candidate.overlaps(existing)) return null;
            }
            accepted.put(candidate.itemId(), candidate);
        }

        return new HomeGridLayoutSnapshot(profile, orientation, accepted);
    }

    HomeGridProfile profile() { return profile; }
    HomeGridOrientation orientation() { return orientation; }
    int size() { return positions.size(); }
    HomeGridItemPosition get(long itemId) { return positions.get(itemId); }
}
