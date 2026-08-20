package com.hellovoid.liquiddock;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure target-orientation placement planner inspired by ColorOS' alternate-orientation cache.
 * It owns no Launcher persistence and has no Android dependencies.
 */
final class HomeGridRotationPlanner {
    private HomeGridRotationPlanner() {}

    static final class Item {
        final long id;
        final long screenId;
        final int x;
        final int y;
        final int spanX;
        final int spanY;
        final int targetSpanX;
        final int targetSpanY;

        Item(long id, long screenId, int x, int y, int spanX, int spanY,
             int targetSpanX, int targetSpanY) {
            this.id = id;
            this.screenId = screenId;
            this.x = x;
            this.y = y;
            this.spanX = spanX;
            this.spanY = spanY;
            this.targetSpanX = targetSpanX;
            this.targetSpanY = targetSpanY;
        }
    }

    static final class Position {
        final long screenId;
        final int x;
        final int y;
        final int spanX;
        final int spanY;

        Position(long screenId, int x, int y, int spanX, int spanY) {
            this.screenId = screenId;
            this.x = x;
            this.y = y;
            this.spanX = spanX;
            this.spanY = spanY;
        }

        boolean overlaps(Position other) {
            if (other == null || screenId != other.screenId) return false;
            return x < other.x + other.spanX && x + spanX > other.x
                    && y < other.y + other.spanY && y + spanY > other.y;
        }

        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Position)) return false;
            Position other = (Position) value;
            return screenId == other.screenId && x == other.x && y == other.y
                    && spanX == other.spanX && spanY == other.spanY;
        }

        @Override public int hashCode() {
            return Objects.hash(screenId, x, y, spanX, spanY);
        }
    }

    static final class Plan {
        private final Map<Long, Position> positions;
        private final Set<Long> unresolved;

        Plan(Map<Long, Position> positions, Set<Long> unresolved) {
            this.positions = Collections.unmodifiableMap(new HashMap<>(positions));
            this.unresolved = Collections.unmodifiableSet(new HashSet<>(unresolved));
        }

        Position position(long id) { return positions.get(id); }
        boolean isUnresolved(long id) { return unresolved.contains(id); }
        Map<Long, Position> positions() { return positions; }
    }

    static Plan plan(int sourceColumns, int sourceRows,
                     int targetColumns, int targetRows,
                     List<Item> items,
                     Map<Long, Position> rememberedTarget) {
        Map<Long, Position> result = new HashMap<>();
        Set<Long> unresolved = new HashSet<>();
        Map<Long, boolean[][]> occupiedByScreen = new HashMap<>();
        if (items == null) return new Plan(result, unresolved);

        Map<Long, Position> remembered = rememberedTarget == null
                ? Collections.emptyMap() : rememberedTarget;
        for (Item item : items) {
            if (item == null || item.targetSpanX <= 0 || item.targetSpanY <= 0) continue;
            boolean[][] occupied = occupiedByScreen.computeIfAbsent(
                    item.screenId, ignored -> new boolean[Math.max(0, targetColumns)][Math.max(0, targetRows)]);

            Position chosen = null;
            Position cached = remembered.get(item.id);
            if (cached != null
                    && cached.spanX == item.targetSpanX
                    && cached.spanY == item.targetSpanY
                    && cached.screenId == item.screenId
                    && canPlace(cached, targetColumns, targetRows, occupied)) {
                chosen = cached;
            }

            Position derived = deriveCandidate(item, sourceColumns, sourceRows,
                    targetColumns, targetRows);
            if (chosen == null && canPlace(derived, targetColumns, targetRows, occupied)) {
                chosen = derived;
            }
            if (chosen == null) {
                chosen = findNearestVacancy(derived, targetColumns, targetRows, occupied);
            }

            if (chosen == null) {
                unresolved.add(item.id);
                continue;
            }
            mark(chosen, occupied);
            result.put(item.id, chosen);
        }
        return new Plan(result, unresolved);
    }

    private static Position deriveCandidate(Item item,
                                            int sourceColumns, int sourceRows,
                                            int targetColumns, int targetRows) {
        int targetX = mapAnchor(item.y, sourceRows, item.spanY,
                targetColumns, item.targetSpanX);
        int targetY = mapAnchor(item.x, sourceColumns, item.spanX,
                targetRows, item.targetSpanY);
        return new Position(item.screenId, targetX, targetY,
                item.targetSpanX, item.targetSpanY);
    }

    private static int mapAnchor(int sourceAnchor, int sourceExtent, int sourceSpan,
                                 int targetExtent, int targetSpan) {
        int sourceRange = Math.max(0, sourceExtent - Math.max(1, sourceSpan));
        int targetRange = Math.max(0, targetExtent - Math.max(1, targetSpan));
        if (sourceRange == 0 || targetRange == 0) return 0;
        int clamped = Math.max(0, Math.min(sourceRange, sourceAnchor));
        return Math.max(0, Math.min(targetRange,
                Math.round(clamped * (targetRange / (float) sourceRange))));
    }

    private static Position findNearestVacancy(Position origin,
                                                int columns, int rows,
                                                boolean[][] occupied) {
        if (origin == null) return null;
        int maxX = columns - origin.spanX;
        int maxY = rows - origin.spanY;
        if (maxX < 0 || maxY < 0) return null;
        int startX = Math.max(0, Math.min(maxX, origin.x));
        int startY = Math.max(0, Math.min(maxY, origin.y));
        int maxDistance = Math.max(columns, rows) * 2;
        for (int distance = 0; distance <= maxDistance; distance++) {
            for (int y = 0; y <= maxY; y++) {
                for (int x = 0; x <= maxX; x++) {
                    if (Math.abs(x - startX) + Math.abs(y - startY) != distance) continue;
                    Position candidate = new Position(origin.screenId, x, y,
                            origin.spanX, origin.spanY);
                    if (canPlace(candidate, columns, rows, occupied)) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean canPlace(Position position, int columns, int rows,
                                    boolean[][] occupied) {
        if (position == null || position.x < 0 || position.y < 0
                || position.spanX <= 0 || position.spanY <= 0
                || position.x + position.spanX > columns
                || position.y + position.spanY > rows) {
            return false;
        }
        for (int x = position.x; x < position.x + position.spanX; x++) {
            for (int y = position.y; y < position.y + position.spanY; y++) {
                if (occupied[x][y]) return false;
            }
        }
        return true;
    }

    private static void mark(Position position, boolean[][] occupied) {
        for (int x = position.x; x < position.x + position.spanX; x++) {
            for (int y = position.y; y < position.y + position.spanY; y++) {
                occupied[x][y] = true;
            }
        }
    }
}
