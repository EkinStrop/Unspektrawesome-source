package com.unspektrawesome.camera;

import android.hardware.camera2.CameraCharacteristics;

public enum LensFacing {
    BACK, FRONT, EXTERNAL, UNKNOWN;

    public static LensFacing fromCamera2(Integer value) {
        if (value == null) return UNKNOWN;
        switch (value) {
            case CameraCharacteristics.LENS_FACING_BACK: return BACK;
            case CameraCharacteristics.LENS_FACING_FRONT: return FRONT;
            case CameraCharacteristics.LENS_FACING_EXTERNAL: return EXTERNAL;
            default: return UNKNOWN;
        }
    }
}
