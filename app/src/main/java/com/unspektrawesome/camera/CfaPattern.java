package com.unspektrawesome.camera;

import android.hardware.camera2.CameraMetadata;

public enum CfaPattern {
    RGGB, GRBG, GBRG, BGGR, RGB, MONO, NIR, UNKNOWN;

    public static CfaPattern fromCamera2(Integer value) {
        if (value == null) return UNKNOWN;
        switch (value) {
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB: return RGGB;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG: return GRBG;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG: return GBRG;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR: return BGGR;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGB: return RGB;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO: return MONO;
            case CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR: return NIR;
            default: return UNKNOWN;
        }
    }

    public boolean isBayer() {
        return this == RGGB || this == GRBG || this == GBRG || this == BGGR;
    }
}
