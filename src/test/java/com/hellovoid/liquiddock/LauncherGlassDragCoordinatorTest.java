package com.hellovoid.liquiddock;

import android.graphics.RectF;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LauncherGlassDragCoordinatorTest {
    @Test
    public void beginAndUpdateAreKindAgnosticAndOwnedByToken() {
        LauncherGlassDragCoordinator coordinator = new LauncherGlassDragCoordinator();
        Object token = new Object();
        RectF start = new RectF(10f, 20f, 210f, 220f);

        assertTrue(coordinator.begin(token, LauncherGlassDragState.Kind.FOLDER,
                start, 40f));
        LauncherGlassDragState first = coordinator.current();
        assertSame(token, first.token);
        assertEquals(LauncherGlassDragState.Kind.FOLDER, first.kind);
        assertEquals(start, first.rootBounds);
        assertEquals(40f, first.cornerRadiusPx, 0.001f);

        RectF moved = new RectF(310f, 420f, 510f, 620f);
        assertTrue(coordinator.update(token, moved, 1.08f, 3f, 0.92f));
        LauncherGlassDragState second = coordinator.current();
        assertEquals(moved, second.rootBounds);
        assertEquals(1.08f, second.scale, 0.001f);
        assertEquals(3f, second.rotation, 0.001f);
        assertEquals(0.92f, second.alpha, 0.001f);
    }

    @Test
    public void staleTokenCannotMoveOrEndActiveDrag() {
        LauncherGlassDragCoordinator coordinator = new LauncherGlassDragCoordinator();
        Object active = new Object();
        Object stale = new Object();
        coordinator.begin(active, LauncherGlassDragState.Kind.WIDGET,
                new RectF(0f, 0f, 400f, 200f), 28f);

        assertFalse(coordinator.update(stale, new RectF(50f, 50f, 450f, 250f),
                1f, 0f, 1f));
        assertFalse(coordinator.end(stale));
        assertSame(active, coordinator.current().token);

        assertTrue(coordinator.end(active));
        assertNull(coordinator.current());
    }

    @Test
    public void beginReplacesPriorOwnerAndCancelClearsOnlyMatchingOwner() {
        LauncherGlassDragCoordinator coordinator = new LauncherGlassDragCoordinator();
        Object icon = new Object();
        Object folder = new Object();

        assertTrue(coordinator.begin(icon, LauncherGlassDragState.Kind.ICON,
                new RectF(0f, 0f, 100f, 100f), 22f));
        assertTrue(coordinator.begin(folder, LauncherGlassDragState.Kind.FOLDER,
                new RectF(100f, 100f, 300f, 300f), 44f));
        assertSame(folder, coordinator.current().token);

        assertFalse(coordinator.cancel(icon));
        assertSame(folder, coordinator.current().token);
        assertTrue(coordinator.cancel(folder));
        assertNull(coordinator.current());
    }
}
