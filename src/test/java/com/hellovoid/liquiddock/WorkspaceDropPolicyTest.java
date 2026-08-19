package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Rotation-safe placement contract for widgets handled by MIUI's native SPECIAL_WIDGET path. */
public class WorkspaceDropPolicyTest {

    @Test
    public void tenBySixFourByTwoUsesOnlyNativeSpecialWidgetAnchors() {
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 0, 0, 4, 2));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 0, 2, 4, 2));

        assertFalse(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 2, 0, 4, 2));
        assertFalse(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 4, 0, 4, 2));
        assertFalse(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 6, 4, 4, 2));
        assertFalse(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 0, 4, 4, 2));
        assertFalse(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 2, 8, 4, 2));
    }

    @Test
    public void eightByFourKeepsExistingFourByTwoPlacementBehavior() {
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_8X4, 4, 2, 4, 2));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_8X4, 0, 6, 4, 2));
    }

    @Test
    public void nonFourByTwoItemsAreNotRestrictedByThisPolicy() {
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 9, 5, 1, 1));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 6, 4, 2, 2));
        assertTrue(WorkspaceDropPolicy.isPlacementAllowed(
                HomeGridProfile.GRID_10X6, 2, 6, 2, 4));
    }
}
