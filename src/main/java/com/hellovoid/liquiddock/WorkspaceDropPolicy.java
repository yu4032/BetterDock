package com.hellovoid.liquiddock;

/**
 * Pure placement policy for constraints that must remain valid across Workspace rotation.
 *
 * <p>MIUI's native grid transform treats 4x2 widgets as two fixed SPECIAL_WIDGET slots before
 * moving ordinary 2x2 blocks. The 10x6 profile must therefore not admit arbitrary 4x2 origins
 * that the native transform cannot represent safely after transposing to 6x10.</p>
 */
final class WorkspaceDropPolicy {
    private static final int SPECIAL_WIDGET_SPAN_X = 4;
    private static final int SPECIAL_WIDGET_SPAN_Y = 2;

    private WorkspaceDropPolicy() {}

    static boolean isPlacementAllowed(HomeGridProfile profile,
                                      int cellX, int cellY, int spanX, int spanY) {
        if (profile != HomeGridProfile.GRID_10X6
                || spanX != SPECIAL_WIDGET_SPAN_X
                || spanY != SPECIAL_WIDGET_SPAN_Y) {
            return true;
        }

        // These are the two native SPECIAL_WIDGET reservations used by the rotation transform.
        // The anchors are valid in both 10x6 and its 6x10 transpose.
        return cellX == 0 && (cellY == 0 || cellY == 2);
    }
}
