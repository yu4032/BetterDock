package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic whole-layout planner used only when an orientation has no complete saved layout.
 * All work happens against temporary occupancy. A result is published only after every item fits.
 */
final class HomeGridPlacementPlanner {
    private static final double EPSILON = 1.0e-12;

    private HomeGridPlacementPlanner() {}

    static PlanResult plan(HomeGridProfile profile,
                           HomeGridOrientation targetOrientation,
                           Collection<HomeGridItemPosition> sourcePositions,
                           HomeGridLayoutSnapshot remembered) {
        if (profile == null || targetOrientation == null || sourcePositions == null) {
            return PlanResult.failure();
        }

        boolean targetPortrait = targetOrientation == HomeGridOrientation.PORTRAIT;
        int targetColumns = profile.columns(targetPortrait);
        int targetRows = profile.rows(targetPortrait);
        int sourceColumns = profile.columns(!targetPortrait);
        int sourceRows = profile.rows(!targetPortrait);

        LinkedHashMap<Long, HomeGridItemPosition> sourceById = new LinkedHashMap<>();
        for (HomeGridItemPosition source : sourcePositions) {
            if (source == null || !source.fitsWithin(sourceColumns, sourceRows)
                    || sourceById.put(source.itemId(), source) != null) {
                return PlanResult.failure();
            }
        }

        Map<Long, boolean[][]> occupiedByScreen = new HashMap<>();
        LinkedHashMap<Long, HomeGridItemPosition> planned = new LinkedHashMap<>();
        List<HomeGridItemPosition> remaining = new ArrayList<>();

        boolean rememberedMatchesScope = remembered != null
                && remembered.profile() == profile
                && remembered.orientation() == targetOrientation;

        // Exact compatible remembered placements are immutable anchors. They win over all
        // automatic placement rules and are reserved before new/unremembered items are planned.
        for (HomeGridItemPosition source : sourceById.values()) {
            HomeGridItemPosition saved = rememberedMatchesScope
                    ? remembered.get(source.itemId()) : null;
            if (isCompatibleRemembered(source, saved, targetColumns, targetRows)
                    && canPlace(occupiedByScreen, saved, targetColumns, targetRows)) {
                reserve(occupiedByScreen, saved, targetColumns, targetRows);
                planned.put(saved.itemId(), saved);
            } else {
                remaining.add(source);
            }
        }

        remaining.sort(Comparator
                .comparingInt(HomeGridPlacementPlanner::placementPriority)
                .thenComparingLong(HomeGridItemPosition::itemId));

        for (HomeGridItemPosition source : remaining) {
            HomeGridItemPosition placed = findNearestPlacement(
                    source, sourceColumns, sourceRows,
                    targetColumns, targetRows, occupiedByScreen);
            if (placed == null) return PlanResult.failure();
            reserve(occupiedByScreen, placed, targetColumns, targetRows);
            planned.put(placed.itemId(), placed);
        }

        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                profile, targetOrientation, planned.values());
        return snapshot == null ? PlanResult.failure() : PlanResult.success(snapshot);
    }

    private static boolean isCompatibleRemembered(HomeGridItemPosition source,
                                                   HomeGridItemPosition saved,
                                                   int targetColumns, int targetRows) {
        return saved != null
                && saved.screenId() == source.screenId()
                && saved.spanX() == source.spanX()
                && saved.spanY() == source.spanY()
                && saved.fitsWithin(targetColumns, targetRows);
    }

    private static int placementPriority(HomeGridItemPosition item) {
        long area = (long) item.spanX() * item.spanY();
        if (area >= 4L) return 0;
        if (area > 1L) return 1;
        return 2;
    }

    private static HomeGridItemPosition findNearestPlacement(
            HomeGridItemPosition source,
            int sourceColumns, int sourceRows,
            int targetColumns, int targetRows,
            Map<Long, boolean[][]> occupiedByScreen) {
        if (source.spanX() <= 0 || source.spanY() <= 0
                || source.spanX() > targetColumns || source.spanY() > targetRows) {
            return null;
        }

        double sourceCenterX = (source.cellX() + source.spanX() / 2.0) / sourceColumns;
        double sourceCenterY = (source.cellY() + source.spanY() / 2.0) / sourceRows;
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestX = -1;
        int bestY = -1;

        // Row-major enumeration is the deterministic tie break: only a strictly better distance
        // may replace the first equally-good candidate.
        for (int y = 0; y <= targetRows - source.spanY(); y++) {
            for (int x = 0; x <= targetColumns - source.spanX(); x++) {
                HomeGridItemPosition candidate = new HomeGridItemPosition(
                        source.itemId(), source.screenId(),
                        x, y, source.spanX(), source.spanY());
                if (!canPlace(occupiedByScreen, candidate, targetColumns, targetRows)) continue;

                double targetCenterX = (x + source.spanX() / 2.0) / targetColumns;
                double targetCenterY = (y + source.spanY() / 2.0) / targetRows;
                double dx = targetCenterX - sourceCenterX;
                double dy = targetCenterY - sourceCenterY;
                double distance = dx * dx + dy * dy;
                if (distance + EPSILON < bestDistance) {
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        return bestX < 0 ? null : new HomeGridItemPosition(
                source.itemId(), source.screenId(),
                bestX, bestY, source.spanX(), source.spanY());
    }

    private static boolean canPlace(Map<Long, boolean[][]> occupiedByScreen,
                                    HomeGridItemPosition item,
                                    int columns, int rows) {
        if (!item.fitsWithin(columns, rows)) return false;
        boolean[][] occupied = occupiedByScreen.get(item.screenId());
        if (occupied == null) return true;
        for (int y = item.cellY(); y < item.cellY() + item.spanY(); y++) {
            for (int x = item.cellX(); x < item.cellX() + item.spanX(); x++) {
                if (occupied[y][x]) return false;
            }
        }
        return true;
    }

    private static void reserve(Map<Long, boolean[][]> occupiedByScreen,
                                HomeGridItemPosition item,
                                int columns, int rows) {
        boolean[][] occupied = occupiedByScreen.computeIfAbsent(
                item.screenId(), ignored -> new boolean[rows][columns]);
        for (int y = item.cellY(); y < item.cellY() + item.spanY(); y++) {
            for (int x = item.cellX(); x < item.cellX() + item.spanX(); x++) {
                occupied[y][x] = true;
            }
        }
    }

    static final class PlanResult {
        private final HomeGridLayoutSnapshot snapshot;

        private PlanResult(HomeGridLayoutSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        static PlanResult success(HomeGridLayoutSnapshot snapshot) {
            return new PlanResult(snapshot);
        }

        static PlanResult failure() {
            return new PlanResult(null);
        }

        boolean success() {
            return snapshot != null;
        }

        HomeGridLayoutSnapshot snapshot() {
            return snapshot;
        }
    }
}
