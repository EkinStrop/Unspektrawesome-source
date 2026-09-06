package com.unspektrawesome.camera;

import java.util.Objects;

/** Integer dimensions used by the camera domain without depending on Android value types. */
public final class Size2d {
    public final int width;
    public final int height;

    public Size2d(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public long area() {
        return (long) width * height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Size2d)) return false;
        Size2d size = (Size2d) other;
        return width == size.width && height == size.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
