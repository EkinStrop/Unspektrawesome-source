package com.unspektrawesome.camera;

import java.util.Objects;

public final class CameraRoute {
    public enum Kind { DIRECT, LOGICAL, PHYSICAL, HIDDEN_DIRECT, HIDDEN_LOGICAL }

    public final String routeId;
    public final String openCameraId;
    public final String characteristicsCameraId;
    public final String physicalCameraId;
    public final Kind kind;

    public CameraRoute(String routeId, String openCameraId, String characteristicsCameraId,
                       String physicalCameraId, Kind kind) {
        this.routeId = requireId(routeId);
        this.openCameraId = requireId(openCameraId);
        this.characteristicsCameraId = requireId(characteristicsCameraId);
        this.physicalCameraId = physicalCameraId;
        this.kind = Objects.requireNonNull(kind);
        if (kind == Kind.PHYSICAL && (physicalCameraId == null || physicalCameraId.isEmpty())) {
            throw new IllegalArgumentException("Physical route requires a physical camera ID");
        }
    }

    private static String requireId(String id) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Camera ID is blank");
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CameraRoute && routeId.equals(((CameraRoute) other).routeId);
    }

    @Override
    public int hashCode() { return routeId.hashCode(); }
}
