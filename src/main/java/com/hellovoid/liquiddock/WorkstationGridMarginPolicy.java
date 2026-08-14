package com.hellovoid.liquiddock;

/** Pure workstation All Apps margin adjustment policy. */
final class WorkstationGridMarginPolicy {
    private WorkstationGridMarginPolicy() {}

    /** Returns {left, right, top, bottom}. Positive values grow both opposing margins. */
    static int[] apply(int baseLeft, int baseRight, int baseTop, int baseBottom,
                       int horizontalDelta, int verticalDelta) {
        return new int[]{
                Math.max(0, baseLeft + horizontalDelta),
                Math.max(0, baseRight + horizontalDelta),
                Math.max(0, baseTop + verticalDelta),
                Math.max(0, baseBottom + verticalDelta)
        };
    }
}
