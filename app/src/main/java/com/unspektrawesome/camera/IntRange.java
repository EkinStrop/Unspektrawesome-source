package com.unspektrawesome.camera;

import java.util.Objects;

public final class IntRange {
    public final int lower;
    public final int upper;

    public IntRange(int lower, int upper) {
        if (lower > upper) throw new IllegalArgumentException("lower exceeds upper");
        this.lower = lower;
        this.upper = upper;
    }

    public boolean contains(int value) { return value >= lower && value <= upper; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IntRange)) return false;
        IntRange range = (IntRange) other;
        return lower == range.lower && upper == range.upper;
    }

    @Override
    public int hashCode() { return Objects.hash(lower, upper); }

    @Override
    public String toString() { return "[" + lower + ", " + upper + "]"; }
}
