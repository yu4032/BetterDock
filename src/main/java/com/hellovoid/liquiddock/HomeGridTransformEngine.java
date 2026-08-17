package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic transpose transform used only by the 10x6 profile.
 *
 * HyperOS' LayoutTransformRuleGridChanged is internally specialized for the stock
 * 6x4/4x6 pair. Extending only its block metadata leaves widgetCaseInBlock() with
 * stock-sized assumptions and can index a length-6 axis at index 6. This engine
 * operates directly on LayoutTransformRule's already-created source/destination
 * occupancy matrices and never enters those private stock block routines.
 */
final class HomeGridTransformEngine {
    private HomeGridTransformEngine() {}

    static boolean transform(Object rule) {
        Object sourceValue = HookUtil.invoke(rule, "getMSrcOccupied");
        Object destinationValue = HookUtil.invoke(rule, "getMDstOccupied");
        if (!(sourceValue instanceof Object[][]) || !(destinationValue instanceof Object[][])) {
            MainHook.log("[DC] 10x6 transform skipped: occupancy matrix unavailable");
            return false;
        }

        Object[][] source = (Object[][]) sourceValue;
        Object[][] destination = (Object[][]) destinationValue;
        int srcCols = source.length;
        int srcRows = rectangularRows(source);
        int dstCols = destination.length;
        int dstRows = rectangularRows(destination);
        if (srcCols <= 0 || srcRows <= 0 || dstCols <= 0 || dstRows <= 0
                || srcCols * srcRows != dstCols * dstRows) {
            MainHook.log("[DC] 10x6 transform skipped: invalid occupancy shape src="
                    + srcCols + "x" + srcRows + " dst=" + dstCols + "x" + dstRows);
            return false;
        }

        Object space = destination[0][0];
        if (space == null) {
            MainHook.log("[DC] 10x6 transform skipped: SPACE_INFO unavailable");
            return false;
        }

        Map<Object, Item> byData = new LinkedHashMap<>();
        for (int x = 0; x < srcCols; x++) {
            for (int y = 0; y < srcRows; y++) {
                Object info = source[x][y];
                if (info == null || info == space) continue;
                Object data = HookUtil.invoke(info, "getMData");
                // SPACE_INFO and MARK_INFO carry no real launcher item payload.
                if (data == null) continue;
                Item item = byData.get(data);
                if (item == null) {
                    item = new Item(info, data, x, y);
                    byData.put(data, item);
                } else {
                    item.include(x, y);
                }
            }
        }

        for (int x = 0; x < dstCols; x++) {
            for (int y = 0; y < dstRows; y++) destination[x][y] = space;
        }
        if (byData.isEmpty()) {
            MainHook.log("[DC] 10x6 transform complete empty src="
                    + srcCols + "x" + srcRows + " dst=" + dstCols + "x" + dstRows);
            return true;
        }

        List<Item> items = new ArrayList<>(byData.values());
        items.sort(Comparator
                .comparingInt(Item::area).reversed()
                .thenComparingInt(item -> item.minY)
                .thenComparingInt(item -> item.minX));

        boolean[][] used = new boolean[dstCols][dstRows];
        int moved = 0;
        for (Item item : items) {
            int spanX = item.spanX();
            int spanY = item.spanY();
            if (spanX > dstCols || spanY > dstRows) {
                MainHook.log("[DC] 10x6 transform item too large span="
                        + spanX + "x" + spanY + " dst=" + dstCols + "x" + dstRows);
                continue;
            }

            int[] preferred;
            if (spanX == 1 && spanY == 1) {
                preferred = HomeGridRotationPolicy.mapIconCell(
                        item.minX, item.minY, srcCols, srcRows, dstCols, dstRows);
            } else {
                preferred = HomeGridRotationPolicy.mapWidgetAnchor(
                        item.minX, item.minY, spanX, spanY,
                        srcCols, srcRows, dstCols, dstRows);
            }
            int[] position = findNearestFree(
                    used, preferred[0], preferred[1], spanX, spanY, dstCols, dstRows);
            if (position == null) {
                MainHook.log("[DC] 10x6 transform no free area span="
                        + spanX + "x" + spanY + " preferred="
                        + preferred[0] + "," + preferred[1]);
                continue;
            }

            fill(destination, used, item.info,
                    position[0], position[1], spanX, spanY);
            if (position[0] != item.minX || position[1] != item.minY) moved++;
        }

        MainHook.log("[DC] 10x6 transform complete src=" + srcCols + "x" + srcRows
                + " dst=" + dstCols + "x" + dstRows
                + " items=" + items.size() + " moved=" + moved);
        return true;
    }

    private static int rectangularRows(Object[][] matrix) {
        if (matrix.length == 0 || matrix[0] == null) return 0;
        int rows = matrix[0].length;
        for (Object[] column : matrix) {
            if (column == null || column.length != rows) return -1;
        }
        return rows;
    }

    private static int[] findNearestFree(boolean[][] used,
                                         int preferredX, int preferredY,
                                         int spanX, int spanY,
                                         int cols, int rows) {
        int bestX = -1;
        int bestY = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = 0; y <= rows - spanY; y++) {
            for (int x = 0; x <= cols - spanX; x++) {
                if (!areaFree(used, x, y, spanX, spanY)) continue;
                int distance = Math.abs(x - preferredX) + Math.abs(y - preferredY);
                if (distance < bestDistance
                        || (distance == bestDistance
                        && (bestY < 0 || y < bestY || (y == bestY && x < bestX)))) {
                    bestDistance = distance;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return bestX < 0 ? null : new int[]{bestX, bestY};
    }

    private static boolean areaFree(boolean[][] used,
                                    int x, int y, int spanX, int spanY) {
        for (int xx = x; xx < x + spanX; xx++) {
            for (int yy = y; yy < y + spanY; yy++) {
                if (used[xx][yy]) return false;
            }
        }
        return true;
    }

    private static void fill(Object[][] destination, boolean[][] used, Object info,
                             int x, int y, int spanX, int spanY) {
        for (int xx = x; xx < x + spanX; xx++) {
            for (int yy = y; yy < y + spanY; yy++) {
                destination[xx][yy] = info;
                used[xx][yy] = true;
            }
        }
    }

    private static final class Item {
        final Object info;
        final Object data;
        int minX;
        int minY;
        int maxX;
        int maxY;

        Item(Object info, Object data, int x, int y) {
            this.info = info;
            this.data = data;
            minX = maxX = x;
            minY = maxY = y;
        }

        void include(int x, int y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        int spanX() {
            return maxX - minX + 1;
        }

        int spanY() {
            return maxY - minY + 1;
        }

        int area() {
            return spanX() * spanY();
        }
    }
}
