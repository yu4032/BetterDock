package com.hellovoid.liquiddock;

/** Pure workstation All Apps absolute spacing policy. */
final class WorkstationGridMarginPolicy {
    private WorkstationGridMarginPolicy() {}

    /** Returns {left, right, top, bottom}. Native asymmetric margins are intentionally ignored. */
    static int[] apply(int baseLeft, int baseRight, int baseTop, int baseBottom,
                       int horizontalSpacing, int topSpacing, int bottomSpacing) {
        int horizontal = Math.max(0, horizontalSpacing);
        int top = Math.max(0, topSpacing);
        int bottom = Math.max(0, bottomSpacing);
        return new int[]{horizontal, horizontal, top, bottom};
    }
}
