package com.unspektrawesome.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LensClassifier {
    static final double FULL_FRAME_DIAGONAL_MM = 43.2666153;

    private LensClassifier() {}

    public static Float equivalentFocalLength(Float focalLengthMm, Float sensorWidthMm,
                                               Float sensorHeightMm) {
        if (!positive(focalLengthMm) || !positive(sensorWidthMm) || !positive(sensorHeightMm)) {
            return null;
        }
        double diagonal = Math.hypot(sensorWidthMm, sensorHeightMm);
        return (float) (focalLengthMm * FULL_FRAME_DIAGONAL_MM / diagonal);
    }

    public static List<CameraDescriptor> classify(List<CameraDescriptor> cameras) {
        Map<LensFacing, Float> reference = new EnumMap<>(LensFacing.class);
        for (CameraDescriptor camera : cameras) {
            if (isLogical(camera.route)) continue;
            Float equivalent = camera.equivalentFocalLengthMm;
            if (!positive(equivalent)) continue;
            Float current = reference.get(camera.facing);
            if (current == null || Math.abs(equivalent - target(camera.facing))
                    < Math.abs(current - target(camera.facing))) {
                reference.put(camera.facing, equivalent);
            }
        }

        List<CameraDescriptor> result = new ArrayList<>(cameras.size());
        for (CameraDescriptor camera : cameras) {
            if (isLogical(camera.route)) {
                result.add(camera.withLensIdentity(role(camera.facing, null), null));
                continue;
            }
            Float base = reference.get(camera.facing);
            Float zoom = positive(camera.equivalentFocalLengthMm) && positive(base)
                    ? roundTenth(camera.equivalentFocalLengthMm / base) : null;
            result.add(camera.withLensIdentity(role(camera.facing, zoom), zoom));
        }
        result.sort((left, right) -> {
            int supported = Boolean.compare(right.isUsable(), left.isUsable());
            if (supported != 0) return supported;
            int facing = Integer.compare(left.facing.ordinal(), right.facing.ordinal());
            if (facing != 0) return facing;
            float leftFocal = left.equivalentFocalLengthMm == null ? Float.MAX_VALUE : left.equivalentFocalLengthMm;
            float rightFocal = right.equivalentFocalLengthMm == null ? Float.MAX_VALUE : right.equivalentFocalLengthMm;
            int focal = Float.compare(leftFocal, rightFocal);
            return focal != 0 ? focal : left.route.routeId.compareTo(right.route.routeId);
        });
        return Collections.unmodifiableList(result);
    }

    public static LensRole role(LensFacing facing, Float relativeZoom) {
        switch (facing) {
            case FRONT: return LensRole.FRONT;
            case EXTERNAL: return LensRole.EXTERNAL;
            case BACK:
                if (relativeZoom == null) return LensRole.REAR;
                if (relativeZoom < 0.8f) return LensRole.ULTRA_WIDE;
                if (relativeZoom > 1.35f) return LensRole.TELEPHOTO;
                return LensRole.MAIN;
            default: return LensRole.UNKNOWN;
        }
    }

    private static float target(LensFacing facing) { return facing == LensFacing.BACK ? 26f : 24f; }
    private static boolean isLogical(CameraRoute route) {
        return route.kind == CameraRoute.Kind.LOGICAL || route.kind == CameraRoute.Kind.HIDDEN_LOGICAL;
    }
    private static boolean positive(Float value) { return value != null && Float.isFinite(value) && value > 0; }
    private static Float roundTenth(float value) { return Math.round(value * 10f) / 10f; }
}
