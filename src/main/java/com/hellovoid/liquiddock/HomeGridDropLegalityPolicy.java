package com.hellovoid.liquiddock;

/** Pure drop legality policy for custom home-grid placement. */
final class HomeGridDropLegalityPolicy {
    private HomeGridDropLegalityPolicy() {}

    static boolean isLegal(HomeGridProfile profile,
                           int columns, int rows,
                           int cellX, int cellY,
                           int spanX, int spanY) {
        if (profile == null || !profile.matchesCounts(columns, rows)) return false;
        if (columns <= 0 || rows <= 0 || spanX <= 0 || spanY <= 0
                || cellX < 0 || cellY < 0) {
            return false;
        }
        long right = (long) cellX + spanX;
        long bottom = (long) cellY + spanY;
        if (right > columns || bottom > rows) return false;

        if (spanX != 2 || spanY != 2) return true;

        boolean portrait = rows > columns;
        for (int[] origin : profile.blockOrigins(portrait)) {
            if (origin != null && origin.length >= 2
                    && origin[0] == cellX && origin[1] == cellY) {
                return true;
            }
        }
        return false;
    }
}
