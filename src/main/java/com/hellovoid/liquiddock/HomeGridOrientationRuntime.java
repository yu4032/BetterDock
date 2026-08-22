package com.hellovoid.liquiddock;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Pure policy layer for capturing, restoring and preflighting per-orientation layouts. */
final class HomeGridOrientationRuntime {
    private final HomeGridProfile profile;
    private final HomeGridOrientationMemory memory;

    HomeGridOrientationRuntime(HomeGridProfile profile, HomeGridOrientationMemory memory) {
        if (profile == null) throw new IllegalArgumentException("profile == null");
        if (memory == null) throw new IllegalArgumentException("memory == null");
        this.profile = profile;
        this.memory = memory;
    }

    HomeGridLayoutSnapshot captureCurrent(HomeGridOrientation orientation,
                                          Collection<HomeGridItemPosition> positions) {
        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                profile, orientation, positions);
        if (snapshot != null) memory.save(snapshot);
        return snapshot;
    }

    HomeGridLayoutSnapshot rememberedTarget(HomeGridOrientation targetOrientation,
                                            Collection<HomeGridItemPosition> currentItems) {
        if (targetOrientation == null || currentItems == null) return null;
        HomeGridLayoutSnapshot remembered = memory.load(profile, targetOrientation);
        if (remembered == null || remembered.size() != currentItems.size()) return null;

        Set<Long> seen = new HashSet<>();
        for (HomeGridItemPosition item : currentItems) {
            if (item == null || !seen.add(item.itemId())
                    || remembered.get(item.itemId()) == null) {
                return null;
            }
        }
        return remembered;
    }

    HomeGridLayoutSnapshot preflightOther(HomeGridOrientation currentOrientation,
                                          Collection<HomeGridItemPosition> currentPositions) {
        if (currentOrientation == null || currentPositions == null) return null;
        HomeGridLayoutSnapshot current = captureCurrent(currentOrientation, currentPositions);
        if (current == null) return null;

        HomeGridOrientation other = currentOrientation.other();
        HomeGridLayoutSnapshot remembered = memory.load(profile, other);
        HomeGridPlacementPlanner.PlanResult result = HomeGridPlacementPlanner.plan(
                profile, other, current.positions(), remembered);
        if (!result.success()) {
            memory.invalidate(profile, other);
            return null;
        }
        memory.save(result.snapshot());
        return result.snapshot();
    }
}
