package com.unspektrawesome.camera.session;

public final class RawCaptureMetadata {
    public final long sensorTimestampNs;
    public final long frameNumber;
    public final Long exposureTimeNs;
    public final Long frameDurationNs;
    public final Integer sensitivityIso;
    public final Float focusDistanceDiopters;
    public final Integer dynamicWhiteLevel;
    public final int lensShadingMapWidth;
    public final int lensShadingMapHeight;
    private final float[] dynamicBlackLevel;
    private final float[] neutralColorPoint;
    private final float[] lensShadingGainFactors;

    public RawCaptureMetadata(long sensorTimestampNs, long frameNumber, Long exposureTimeNs,
                              Long frameDurationNs, Integer sensitivityIso,
                              Float focusDistanceDiopters, float[] dynamicBlackLevel,
                              Integer dynamicWhiteLevel, float[] neutralColorPoint) {
        this(sensorTimestampNs, frameNumber, exposureTimeNs, frameDurationNs, sensitivityIso,
                focusDistanceDiopters, dynamicBlackLevel, dynamicWhiteLevel, neutralColorPoint,
                0, 0, null);
    }

    public RawCaptureMetadata(long sensorTimestampNs, long frameNumber, Long exposureTimeNs,
                              Long frameDurationNs, Integer sensitivityIso,
                              Float focusDistanceDiopters, float[] dynamicBlackLevel,
                              Integer dynamicWhiteLevel, float[] neutralColorPoint,
                              int lensShadingMapWidth, int lensShadingMapHeight,
                              float[] lensShadingGainFactors) {
        if (sensorTimestampNs <= 0) throw new IllegalArgumentException("Timestamp must be positive");
        this.sensorTimestampNs = sensorTimestampNs;
        this.frameNumber = frameNumber;
        this.exposureTimeNs = exposureTimeNs;
        this.frameDurationNs = frameDurationNs;
        this.sensitivityIso = sensitivityIso;
        this.focusDistanceDiopters = focusDistanceDiopters;
        this.dynamicBlackLevel = copyLength(dynamicBlackLevel, 4, "Dynamic black level");
        this.dynamicWhiteLevel = dynamicWhiteLevel;
        this.neutralColorPoint = copyLength(neutralColorPoint, 3, "Neutral color point");
        this.lensShadingGainFactors = validateLensShadingMap(
                lensShadingMapWidth, lensShadingMapHeight, lensShadingGainFactors);
        this.lensShadingMapWidth = lensShadingMapWidth;
        this.lensShadingMapHeight = lensShadingMapHeight;
    }

    public float[] dynamicBlackLevel() {
        return dynamicBlackLevel == null ? null : dynamicBlackLevel.clone();
    }

    public float[] neutralColorPoint() {
        return neutralColorPoint == null ? null : neutralColorPoint.clone();
    }

    public boolean hasLensShadingMap() { return lensShadingGainFactors != null; }

    /** Row-major grid containing interleaved R, G-even, G-odd, and B gains. */
    public float[] lensShadingGainFactors() {
        return lensShadingGainFactors == null ? null : lensShadingGainFactors.clone();
    }

    private static float[] copyLength(float[] value, int length, String name) {
        if (value == null) return null;
        if (value.length != length) throw new IllegalArgumentException(name + " has invalid length");
        return value.clone();
    }

    private static float[] validateLensShadingMap(int width, int height, float[] gains) {
        if (gains == null) {
            if (width != 0 || height != 0) {
                throw new IllegalArgumentException("Lens shading dimensions require gain factors");
            }
            return null;
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Lens shading map dimensions must be positive");
        }
        long required = (long) width * height * 4;
        if (required != gains.length) {
            throw new IllegalArgumentException("Lens shading map gain count does not match dimensions");
        }
        float[] copy = gains.clone();
        for (float gain : copy) {
            if (!Float.isFinite(gain) || gain <= 0f) {
                throw new IllegalArgumentException("Lens shading gains must be finite and positive");
            }
        }
        return copy;
    }
}
