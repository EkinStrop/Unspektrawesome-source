package com.unspektrawesome.camera;

import java.util.Objects;

public final class Rect2d {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public Rect2d(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            throw new IllegalArgumentException("Rectangle must have positive dimensions");
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rect2d)) return false;
        Rect2d rect = (Rect2d) other;
        return left == rect.left && top == rect.top && right == rect.right && bottom == rect.bottom;
    }

    @Override
    public int hashCode() { return Objects.hash(left, top, right, bottom); }
}
