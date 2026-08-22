package com.hellovoid.liquiddock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Packs only the root-space regions needed by active glass nodes into one GPU backdrop atlas. */
final class LauncherGlassGpuAtlas {
    private static final int TILE_GAP_PX = 2;

    private LauncherGlassGpuAtlas() {}

    static final class Request {
        final int id;
        final int sourceLeft;
        final int sourceTop;
        final int sourceWidth;
        final int sourceHeight;
        final float glassLeft;
        final float glassTop;
        final float glassWidth;
        final float glassHeight;
        final float cornerRadius;

        Request(int id, int sourceLeft, int sourceTop, int sourceWidth, int sourceHeight,
                float glassLeft, float glassTop, float glassWidth, float glassHeight,
                float cornerRadius) {
            this.id = id;
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.glassLeft = glassLeft;
            this.glassTop = glassTop;
            this.glassWidth = glassWidth;
            this.glassHeight = glassHeight;
            this.cornerRadius = cornerRadius;
        }
    }

    static final class Tile {
        final Request request;
        final int atlasLeft;
        final int atlasTop;

        Tile(Request request, int atlasLeft, int atlasTop) {
            this.request = request;
            this.atlasLeft = atlasLeft;
            this.atlasTop = atlasTop;
        }

        int atlasRight() { return atlasLeft + request.sourceWidth; }
        int atlasBottom() { return atlasTop + request.sourceHeight; }
        float glassAtlasLeft() { return atlasLeft + request.glassLeft - request.sourceLeft; }
        float glassAtlasTop() { return atlasTop + request.glassTop - request.sourceTop; }
    }

    static final class Layout {
        final int width;
        final int height;
        final List<Tile> tiles;

        Layout(int width, int height, List<Tile> tiles) {
            this.width = width;
            this.height = height;
            this.tiles = Collections.unmodifiableList(new ArrayList<>(tiles));
        }

        boolean sameAs(Layout other) {
            if (other == null || width != other.width || height != other.height
                    || tiles.size() != other.tiles.size()) return false;
            for (int i = 0; i < tiles.size(); i++) {
                Tile a = tiles.get(i);
                Tile b = other.tiles.get(i);
                Request ar = a.request;
                Request br = b.request;
                if (ar.id != br.id || a.atlasLeft != b.atlasLeft || a.atlasTop != b.atlasTop
                        || ar.sourceLeft != br.sourceLeft || ar.sourceTop != br.sourceTop
                        || ar.sourceWidth != br.sourceWidth || ar.sourceHeight != br.sourceHeight
                        || Math.abs(ar.glassLeft - br.glassLeft) >= 0.25f
                        || Math.abs(ar.glassTop - br.glassTop) >= 0.25f
                        || Math.abs(ar.glassWidth - br.glassWidth) >= 0.25f
                        || Math.abs(ar.glassHeight - br.glassHeight) >= 0.25f
                        || Math.abs(ar.cornerRadius - br.cornerRadius) >= 0.25f) return false;
            }
            return true;
        }
    }

    static Layout pack(List<Request> requests, int maxTextureSize) {
        if (requests == null || requests.isEmpty() || maxTextureSize <= 0) return null;
        int totalArea = 0;
        int maxTileWidth = 1;
        for (Request request : requests) {
            if (request == null || request.sourceWidth <= 0 || request.sourceHeight <= 0
                    || request.sourceWidth > maxTextureSize || request.sourceHeight > maxTextureSize) {
                return null;
            }
            totalArea = saturatedAdd(totalArea,
                    saturatedMultiply(request.sourceWidth + TILE_GAP_PX,
                            request.sourceHeight + TILE_GAP_PX));
            maxTileWidth = Math.max(maxTileWidth, request.sourceWidth);
        }
        int targetWidth = Math.max(maxTileWidth, (int) Math.ceil(Math.sqrt(totalArea)));
        targetWidth = Math.min(maxTextureSize, Math.max(1, targetWidth));
        Layout first = packAtWidth(requests, targetWidth, maxTextureSize);
        if (first != null) return first;
        if (targetWidth == maxTextureSize) return null;
        return packAtWidth(requests, maxTextureSize, maxTextureSize);
    }

    private static Layout packAtWidth(List<Request> requests, int rowLimit, int maxTextureSize) {
        ArrayList<Tile> tiles = new ArrayList<>(requests.size());
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int usedWidth = 0;
        for (Request request : requests) {
            if (x > 0 && x + request.sourceWidth > rowLimit) {
                y += rowHeight + TILE_GAP_PX;
                x = 0;
                rowHeight = 0;
            }
            if (y + request.sourceHeight > maxTextureSize) return null;
            tiles.add(new Tile(request, x, y));
            usedWidth = Math.max(usedWidth, x + request.sourceWidth);
            rowHeight = Math.max(rowHeight, request.sourceHeight);
            x += request.sourceWidth + TILE_GAP_PX;
        }
        int usedHeight = y + rowHeight;
        if (usedWidth <= 0 || usedHeight <= 0 || usedWidth > maxTextureSize
                || usedHeight > maxTextureSize) return null;
        return new Layout(usedWidth, usedHeight, tiles);
    }

    private static int saturatedMultiply(int a, int b) {
        long value = (long) a * (long) b;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int saturatedAdd(int a, int b) {
        long value = (long) a + (long) b;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
