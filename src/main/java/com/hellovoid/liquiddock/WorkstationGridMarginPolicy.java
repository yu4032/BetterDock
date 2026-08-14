package com.hellovoid.liquiddock;

/** Pure workstation All Apps absolute spacing policy. */
final class WorkstationGridMarginPolicy {
    private WorkstationGridMarginPolicy() {}

    /**
     * Returns {left, right, top, bottom} as absolute symmetric edge spacing.
     * The native All Apps grid is intentionally ignored because its stock margins are
     * asymmetric; adding deltas to them can never make one numeric setting truly centered.
     * The base arguments remain only to preserve the existing call/test signature.
     */
    static int[] apply(int baseLeft, int baseRight, int baseTop, int baseBottom,
                       int horizontalSpacing, int verticalSpacing) {
        int horizontal = Math.max(0, horizontalSpacing);
        int vertical = Math.max(0, verticalSpacing);
        return new int[]{horizontal, horizontal, vertical, vertical};
    }
}
