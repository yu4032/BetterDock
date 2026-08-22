package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HomeGridOrientationRuntimeTest {

    @Test
    public void captureCurrentPersistsOnlyCompleteValidLayout() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);

        HomeGridLayoutSnapshot valid = runtime.captureCurrent(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(1, 0, 0, 0, 4, 2),
                        pos(2, 0, 7, 3, 1, 1)));
        assertNotNull(valid);
        assertNotNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.LANDSCAPE));

        HomeGridLayoutSnapshot invalid = runtime.captureCurrent(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 0, 0, 0, 4, 2),
                        pos(2, 0, 0, 1, 1, 1)));
        assertNull(invalid);
        assertNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.PORTRAIT));
    }

    @Test
    public void rememberedTargetRequiresExactStableItemIdentitySet() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 0, 0, 4, 4, 2),
                pos(2, 0, 3, 7, 1, 1)));

        assertNotNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 0, 0, 0, 4, 2),
                        pos(2, 0, 7, 3, 1, 1))));

        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(
                        pos(1, 0, 0, 0, 4, 2),
                        pos(3, 0, 7, 3, 1, 1))));
    }

    @Test
    public void rememberedTargetRejectsStaleSpanAfterResize() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 0, 0, 4, 2, 2)));

        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1, 0, 1, 1, 4, 2))));
    }

    @Test
    public void rememberedTargetRejectsCrossScreenPlacement() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 4, 0, 4, 1, 1)));

        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1, 5, 2, 1, 1, 1))));
    }

    @Test
    public void rememberedTargetRejectsOffMacroblockTwoByTwo() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 0, 1, 3, 2, 2)));

        assertNull(runtime.rememberedTarget(
                HomeGridOrientation.PORTRAIT,
                Arrays.asList(pos(1, 0, 2, 0, 2, 2))));
    }

    @Test
    public void preflightKeepsCurrentLayoutAndRegeneratesOtherOrientation() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 5, 0, 5, 4, 2)));

        HomeGridLayoutSnapshot other = runtime.preflightOther(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(
                        pos(1, 5, 2, 1, 4, 2),
                        pos(2, 5, 7, 3, 1, 1)));

        HomeGridLayoutSnapshot current = memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.LANDSCAPE);
        assertNotNull(current);
        assertEquals(2, current.size());
        assertNotNull(other);
        assertEquals(0, other.get(1).cellX());
        assertEquals(5, other.get(1).cellY());
        assertNotNull(other.get(2));
    }

    @Test
    public void impossibleOtherOrientationInvalidatesOnlyOtherMemory() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridOrientationRuntime runtime = new HomeGridOrientationRuntime(
                HomeGridProfile.GRID_8X4, memory);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(99, 0, 0, 0, 1, 1)));

        HomeGridLayoutSnapshot other = runtime.preflightOther(
                HomeGridOrientation.LANDSCAPE,
                Arrays.asList(pos(1, 0, 0, 0, 5, 1)));

        assertNull(other);
        assertNotNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.LANDSCAPE));
        assertNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.PORTRAIT));
    }

    private static HomeGridLayoutSnapshot snapshot(HomeGridProfile profile,
                                                   HomeGridOrientation orientation,
                                                   HomeGridItemPosition... positions) {
        HomeGridLayoutSnapshot snapshot = HomeGridLayoutSnapshot.create(
                profile, orientation, Arrays.asList(positions));
        if (snapshot == null) throw new AssertionError("invalid test fixture");
        return snapshot;
    }

    private static HomeGridItemPosition pos(long id, long screenId,
                                            int x, int y, int spanX, int spanY) {
        return new HomeGridItemPosition(id, screenId, x, y, spanX, spanY);
    }

    private static final class MapStore implements HomeGridOrientationMemoryStore {
        private final Map<String, String> values = new HashMap<>();
        @Override public String read(String key) { return values.get(key); }
        @Override public void write(String key, String value) { values.put(key, value); }
        @Override public void remove(String key) { values.remove(key); }
    }
}
