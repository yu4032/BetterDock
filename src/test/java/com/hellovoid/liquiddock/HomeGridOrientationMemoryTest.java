package com.hellovoid.liquiddock;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class HomeGridOrientationMemoryTest {

    @Test
    public void snapshotRoundTripsWithoutChangingUserAuthoredCoordinates() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridLayoutSnapshot original = snapshot(
                HomeGridProfile.GRID_10X6,
                HomeGridOrientation.PORTRAIT,
                pos(1, 11, 0, 6, 4, 2),
                pos(2, 11, 5, 9, 1, 1));

        memory.save(original);
        HomeGridLayoutSnapshot loaded = memory.load(
                HomeGridProfile.GRID_10X6, HomeGridOrientation.PORTRAIT);

        assertNotNull(loaded);
        assertEquals(2, loaded.size());
        assertEquals(0, loaded.get(1).cellX());
        assertEquals(6, loaded.get(1).cellY());
        assertEquals(5, loaded.get(2).cellX());
        assertEquals(9, loaded.get(2).cellY());
    }

    @Test
    public void profileAndOrientationAreIndependentNamespaces() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                pos(1, 0, 7, 3, 1, 1)));

        assertNotNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.LANDSCAPE));
        assertNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.PORTRAIT));
        assertNull(memory.load(
                HomeGridProfile.GRID_10X6, HomeGridOrientation.LANDSCAPE));
    }

    @Test
    public void corruptPayloadNeverProducesPartialSnapshot() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        HomeGridLayoutSnapshot original = snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 0, 0, 0, 1, 1));
        memory.save(original);

        store.values.put(store.lastWrittenKey,
                "v1|8x4|PORTRAIT\n1,0,0,0,1,1\nBROKEN-LINE");

        assertNull(memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.PORTRAIT));
    }

    @Test
    public void savingAgainAtomicallyReplacesWholeOrientationSnapshot() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                pos(1, 2, 0, 0, 1, 1),
                pos(2, 2, 1, 0, 1, 1)));
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                pos(1, 2, 6, 3, 1, 1)));

        HomeGridLayoutSnapshot loaded = memory.load(
                HomeGridProfile.GRID_8X4, HomeGridOrientation.LANDSCAPE);
        assertNotNull(loaded);
        assertEquals(1, loaded.size());
        assertEquals(6, loaded.get(1).cellX());
        assertNull(loaded.get(2));
    }

    @Test
    public void invalidateRemovesOnlyRequestedOrientation() {
        MapStore store = new MapStore();
        HomeGridOrientationMemory memory = new HomeGridOrientationMemory(store);
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.LANDSCAPE,
                pos(1, 0, 0, 0, 1, 1)));
        memory.save(snapshot(
                HomeGridProfile.GRID_8X4,
                HomeGridOrientation.PORTRAIT,
                pos(1, 0, 0, 0, 1, 1)));

        memory.invalidate(HomeGridProfile.GRID_8X4, HomeGridOrientation.PORTRAIT);

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
        final Map<String, String> values = new HashMap<>();
        String lastWrittenKey;

        @Override public String read(String key) {
            return values.get(key);
        }

        @Override public void write(String key, String value) {
            lastWrittenKey = key;
            values.put(key, value);
        }

        @Override public void remove(String key) {
            values.remove(key);
        }
    }
}
