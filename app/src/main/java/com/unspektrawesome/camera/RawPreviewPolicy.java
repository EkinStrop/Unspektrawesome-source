package com.unspektrawesome.camera;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.EnumSet;

/** Selects a RAW acquisition stream and independent even-sized GPU processing dimensions. */
public final class RawPreviewPolicy {
    private static final double FPS_TOLERANCE = 0.25;

    public enum Quality {
        LOW(0.25f), MEDIUM(0.50f), HIGH(0.75f), FULL(1.0f);
        final float scale;
        Quality(float scale) { this.scale = scale; }
    }

    public static final class Plan {
        public final RawOutput acquisitionOutput;
        public final Size2d processingSize;
        public final int framesPerSecond;
        public final IntRange aeTargetRange;

        Plan(RawOutput acquisitionOutput, Size2d processingSize, int framesPerSecond,
             IntRange aeTargetRange) {
            this.acquisitionOutput = acquisitionOutput;
            this.processingSize = processingSize;
            this.framesPerSecond = framesPerSecond;
            this.aeTargetRange = aeTargetRange;
        }

        public boolean meetsRealtimeTarget() { return framesPerSecond >= 30; }
    }

    private RawPreviewPolicy() {}

    public static Plan select(List<RawOutput> outputs, List<IntRange> aeRanges,
                              Quality quality, int preferredFps) {
        return select(outputs, aeRanges, quality, preferredFps, EnumSet.allOf(RawFormat.class));
    }

    /** importableFormats should come from the active GPU external-memory capability query. */
    public static Plan select(List<RawOutput> outputs, List<IntRange> aeRanges,
                              Quality quality, int preferredFps,
                              Set<RawFormat> importableFormats) {
        Objects.requireNonNull(quality);
        Objects.requireNonNull(importableFormats);
        if (preferredFps <= 0) throw new IllegalArgumentException("Preferred FPS must be positive");
        List<RawOutput> regular = new ArrayList<>();
        for (RawOutput output : outputs) {
            if (!output.maximumResolutionMode && importableFormats.contains(output.format)) regular.add(output);
        }
        if (regular.isEmpty()) throw new IllegalArgumentException("No regular RAW preview output");

        RawOutput reference = regular.stream().max(Comparator.comparingLong(value -> value.size.area()))
                .orElseThrow(AssertionError::new);
        Size2d processing = scaledEven(reference.size, quality.scale);

        List<RawOutput> realtime = new ArrayList<>();
        for (RawOutput output : regular) {
            if (output.maximumFps() + FPS_TOLERANCE >= preferredFps) realtime.add(output);
        }
        List<RawOutput> candidates = realtime.isEmpty() ? regular : realtime;
        RawOutput acquisition = chooseAcquisition(candidates, processing);
        FpsChoice fps = chooseFps(acquisition, aeRanges, preferredFps);
        return new Plan(acquisition, fitWithin(processing, acquisition.size), fps.fps, fps.range);
    }

    public static RawOutput selectCaptureOutput(List<RawOutput> outputs) {
        return selectCaptureOutput(outputs, EnumSet.allOf(RawFormat.class));
    }

    public static RawOutput selectCaptureOutput(List<RawOutput> outputs,
                                                 Set<RawFormat> importableFormats) {
        Objects.requireNonNull(importableFormats);
        return outputs.stream().filter(output -> importableFormats.contains(output.format)).max(Comparator
                .comparingLong((RawOutput value) -> value.size.area())
                .thenComparingInt(value -> captureFormatRank(value.format))
                .thenComparing(value -> value.maximumResolutionMode))
                .orElseThrow(() -> new IllegalArgumentException("No RAW capture output"));
    }

    static Size2d scaledEven(Size2d source, float scale) {
        if (!(scale > 0f && scale <= 1f)) throw new IllegalArgumentException("Scale must be in (0, 1]");
        int width = Math.max(2, ((int) Math.floor(source.width * scale)) & ~1);
        int height = Math.max(2, ((int) Math.floor(source.height * scale)) & ~1);
        return new Size2d(width, height);
    }

    private static RawOutput chooseAcquisition(List<RawOutput> outputs, Size2d processing) {
        Comparator<RawOutput> order = Comparator
                .comparing((RawOutput value) -> value.size.area() < processing.area())
                .thenComparingLong(value -> value.size.area() >= processing.area()
                        ? value.size.area() : -value.size.area())
                .thenComparingInt(value -> previewFormatRank(value.format));
        return outputs.stream().min(order).orElseThrow(AssertionError::new);
    }

    private static Size2d fitWithin(Size2d requested, Size2d source) {
        if (requested.width <= source.width && requested.height <= source.height) return requested;
        float scale = Math.min((float) source.width / requested.width, (float) source.height / requested.height);
        return scaledEven(requested, Math.min(1f, scale));
    }

    private static FpsChoice chooseFps(RawOutput output, List<IntRange> ranges, int preferred) {
        int ceiling = output.minimumFrameDurationNs == 0 ? preferred
                : Math.max(1, (int) Math.floor(output.maximumFps() + FPS_TOLERANCE));
        int limit = Math.min(preferred, ceiling);
        IntRange bestRange = null;
        int bestFps = 0;
        for (IntRange range : ranges) {
            int fps = Math.min(limit, range.upper);
            if (fps >= range.lower && (fps > bestFps
                    || fps == bestFps && (bestRange == null || range.lower > bestRange.lower))) {
                bestFps = fps;
                bestRange = range;
            }
        }
        return bestRange == null ? new FpsChoice(limit, new IntRange(limit, limit))
                : new FpsChoice(bestFps, bestRange);
    }

    private static int previewFormatRank(RawFormat format) {
        switch (format) {
            case RAW10: return 0;
            case RAW12: return 1;
            default: return 2;
        }
    }

    private static int captureFormatRank(RawFormat format) {
        switch (format) {
            case RAW_SENSOR: return 2;
            case RAW12: return 1;
            default: return 0;
        }
    }

    private static final class FpsChoice {
        final int fps;
        final IntRange range;
        FpsChoice(int fps, IntRange range) { this.fps = fps; this.range = range; }
    }
}
