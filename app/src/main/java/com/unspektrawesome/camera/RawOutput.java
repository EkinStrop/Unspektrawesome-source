package com.unspektrawesome.camera;

import java.util.Objects;

public final class RawOutput {
    public final RawFormat format;
    public final Size2d size;
    public final long minimumFrameDurationNs;
    public final long stallDurationNs;
    public final boolean maximumResolutionMode;

    public RawOutput(RawFormat format, Size2d size, long minimumFrameDurationNs,
                     long stallDurationNs, boolean maximumResolutionMode) {
        this.format = Objects.requireNonNull(format);
        this.size = Objects.requireNonNull(size);
        this.minimumFrameDurationNs = Math.max(0, minimumFrameDurationNs);
        this.stallDurationNs = Math.max(0, stallDurationNs);
        this.maximumResolutionMode = maximumResolutionMode;
    }

    public double maximumFps() {
        return minimumFrameDurationNs == 0 ? Double.POSITIVE_INFINITY
                : 1_000_000_000.0 / minimumFrameDurationNs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RawOutput)) return false;
        RawOutput output = (RawOutput) other;
        return minimumFrameDurationNs == output.minimumFrameDurationNs
                && stallDurationNs == output.stallDurationNs
                && maximumResolutionMode == output.maximumResolutionMode
                && format == output.format && size.equals(output.size);
    }

    @Override
    public int hashCode() {
        return Objects.hash(format, size, minimumFrameDurationNs, stallDurationNs,
                maximumResolutionMode);
    }
}
