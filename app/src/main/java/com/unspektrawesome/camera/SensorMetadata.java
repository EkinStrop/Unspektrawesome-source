package com.unspektrawesome.camera;

import java.util.Arrays;
import java.util.Objects;

public final class SensorMetadata {
    public final CfaPattern cfaPattern;
    private final float[] blackLevel;
    public final int whiteLevel;
    public final Rect2d activeArray;
    public final Size2d pixelArray;
    public final Rect2d maximumResolutionActiveArray;
    public final Size2d maximumResolutionPixelArray;
    public final int orientationDegrees;
    public final ColorCalibration colorCalibration;

    public SensorMetadata(CfaPattern cfaPattern, float[] blackLevel, int whiteLevel,
                          Rect2d activeArray, Size2d pixelArray, int orientationDegrees,
                          ColorCalibration colorCalibration) {
        this(cfaPattern, blackLevel, whiteLevel, activeArray, pixelArray, null, null,
                orientationDegrees, colorCalibration);
    }

    public SensorMetadata(CfaPattern cfaPattern, float[] blackLevel, int whiteLevel,
                          Rect2d activeArray, Size2d pixelArray,
                          Rect2d maximumResolutionActiveArray, Size2d maximumResolutionPixelArray,
                          int orientationDegrees, ColorCalibration colorCalibration) {
        this.cfaPattern = Objects.requireNonNull(cfaPattern);
        if (blackLevel == null || blackLevel.length != 4) {
            throw new IllegalArgumentException("Black level must contain one value per 2x2 CFA site");
        }
        this.blackLevel = blackLevel.clone();
        this.whiteLevel = whiteLevel;
        this.activeArray = activeArray;
        this.pixelArray = pixelArray;
        this.maximumResolutionActiveArray = maximumResolutionActiveArray;
        this.maximumResolutionPixelArray = maximumResolutionPixelArray;
        this.orientationDegrees = orientationDegrees;
        this.colorCalibration = Objects.requireNonNull(colorCalibration);
    }

    public float[] blackLevel() { return blackLevel.clone(); }

    public Rect2d activeArray(boolean maximumResolutionMode) {
        return maximumResolutionMode && maximumResolutionActiveArray != null
                ? maximumResolutionActiveArray : activeArray;
    }

    public Size2d pixelArray(boolean maximumResolutionMode) {
        return maximumResolutionMode && maximumResolutionPixelArray != null
                ? maximumResolutionPixelArray : pixelArray;
    }

    public boolean hasValidLevels() {
        if (whiteLevel <= 0) return false;
        for (float black : blackLevel) {
            if (!Float.isFinite(black) || black < 0 || black >= whiteLevel) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return cfaPattern + " black=" + Arrays.toString(blackLevel) + " white=" + whiteLevel;
    }
}
