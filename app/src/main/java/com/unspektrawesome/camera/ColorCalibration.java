package com.unspektrawesome.camera;

/** Camera2 color calibration metadata. Matrices are row-major 3x3 values. */
public final class ColorCalibration {
    private final float[] colorMatrix1;
    private final float[] colorMatrix2;
    private final float[] forwardMatrix1;
    private final float[] forwardMatrix2;
    private final float[] calibrationMatrix1;
    private final float[] calibrationMatrix2;
    public final Integer illuminant1;
    public final Integer illuminant2;

    public ColorCalibration(float[] colorMatrix1, float[] colorMatrix2,
                            float[] forwardMatrix1, float[] forwardMatrix2,
                            float[] calibrationMatrix1, float[] calibrationMatrix2,
                            Integer illuminant1, Integer illuminant2) {
        this.colorMatrix1 = matrix(colorMatrix1);
        this.colorMatrix2 = matrix(colorMatrix2);
        this.forwardMatrix1 = matrix(forwardMatrix1);
        this.forwardMatrix2 = matrix(forwardMatrix2);
        this.calibrationMatrix1 = matrix(calibrationMatrix1);
        this.calibrationMatrix2 = matrix(calibrationMatrix2);
        this.illuminant1 = illuminant1;
        this.illuminant2 = illuminant2;
    }

    private static float[] matrix(float[] value) {
        if (value == null) return null;
        if (value.length != 9) throw new IllegalArgumentException("Color matrix must contain 9 values");
        return value.clone();
    }

    public float[] colorMatrix1() { return copy(colorMatrix1); }
    public float[] colorMatrix2() { return copy(colorMatrix2); }
    public float[] forwardMatrix1() { return copy(forwardMatrix1); }
    public float[] forwardMatrix2() { return copy(forwardMatrix2); }
    public float[] calibrationMatrix1() { return copy(calibrationMatrix1); }
    public float[] calibrationMatrix2() { return copy(calibrationMatrix2); }

    public boolean hasUsableColorTransform() {
        return forwardMatrix1 != null || colorMatrix1 != null;
    }

    private static float[] copy(float[] value) { return value == null ? null : value.clone(); }
}
