package com.hellovoid.prismal;

/**
 * Geometry consumed by the standalone Prismal renderer.
 *
 * Coordinates use Android/view convention: origin at framebuffer top-left, +X right, +Y down.
 * The renderer performs the one official Y conversion when uploading u_mousePos.
 */
public final class PrismalGeometry {
    public final int framebufferWidth;
    public final int framebufferHeight;
    public final float centerX;
    public final float centerY;
    public final float glassWidth;
    public final float glassHeight;
    public final float topLeftRadius;
    public final float topRightRadius;
    public final float bottomRightRadius;
    public final float bottomLeftRadius;

    public PrismalGeometry(
            int framebufferWidth,
            int framebufferHeight,
            float centerX,
            float centerY,
            float glassWidth,
            float glassHeight,
            float cornerRadius) {
        this(framebufferWidth, framebufferHeight, centerX, centerY, glassWidth, glassHeight,
                cornerRadius, cornerRadius, cornerRadius, cornerRadius);
    }

    public PrismalGeometry(
            int framebufferWidth,
            int framebufferHeight,
            float centerX,
            float centerY,
            float glassWidth,
            float glassHeight,
            float topLeftRadius,
            float topRightRadius,
            float bottomRightRadius,
            float bottomLeftRadius) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("framebuffer must be positive");
        }
        if (glassWidth <= 0f || glassHeight <= 0f) {
            throw new IllegalArgumentException("glass size must be positive");
        }
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.centerX = centerX;
        this.centerY = centerY;
        this.glassWidth = glassWidth;
        this.glassHeight = glassHeight;
        this.topLeftRadius = Math.max(0f, topLeftRadius);
        this.topRightRadius = Math.max(0f, topRightRadius);
        this.bottomRightRadius = Math.max(0f, bottomRightRadius);
        this.bottomLeftRadius = Math.max(0f, bottomLeftRadius);
    }

    public float left() { return centerX - glassWidth * 0.5f; }
    public float top() { return centerY - glassHeight * 0.5f; }
    public float right() { return centerX + glassWidth * 0.5f; }
    public float bottom() { return centerY + glassHeight * 0.5f; }
}
