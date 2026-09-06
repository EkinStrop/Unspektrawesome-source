package com.unspektrawesome.camera;

public enum CapabilityIssue {
    CHARACTERISTICS_UNAVAILABLE(true, "Camera characteristics are unavailable"),
    RAW_CAPABILITY_NOT_ADVERTISED(true, "Camera does not advertise RAW capability"),
    RAW_OUTPUT_NOT_ADVERTISED(true, "Camera advertises no RAW_SENSOR, RAW10, or RAW12 output"),
    BAYER_CFA_UNAVAILABLE(true, "A supported Bayer CFA arrangement is unavailable"),
    INVALID_RAW_LEVELS(true, "Black or white sensor levels are invalid"),
    ACTIVE_ARRAY_UNAVAILABLE(true, "Sensor active array is unavailable"),
    COLOR_CALIBRATION_UNAVAILABLE(true, "Camera color calibration metadata is unavailable"),
    MANUAL_SENSOR_UNAVAILABLE(false, "Manual sensor controls are unavailable"),
    TARGET_30_FPS_UNAVAILABLE(false, "No advertised RAW output reaches 30 fps"),
    PHYSICAL_CHARACTERISTICS_FALLBACK(true, "Physical metadata is unavailable; logical metadata is insufficient for RAW processing");

    public final boolean blocking;
    public final String description;

    CapabilityIssue(boolean blocking, String description) {
        this.blocking = blocking;
        this.description = description;
    }
}
