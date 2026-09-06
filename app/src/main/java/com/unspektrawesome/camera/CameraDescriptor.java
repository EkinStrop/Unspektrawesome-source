package com.unspektrawesome.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CameraDescriptor {
    public final CameraRoute route;
    public final LensFacing facing;
    public final LensRole lensRole;
    public final Float focalLengthMm;
    public final Float equivalentFocalLengthMm;
    public final Float relativeZoom;
    public final Float sensorWidthMm;
    public final Float sensorHeightMm;
    public final boolean rawCapability;
    public final boolean manualSensorCapability;
    public final SensorMetadata sensorMetadata;
    public final List<RawOutput> rawOutputs;
    public final List<IntRange> aeFpsRanges;
    public final Set<CapabilityIssue> issues;

    public CameraDescriptor(CameraRoute route, LensFacing facing, LensRole lensRole,
                            Float focalLengthMm, Float equivalentFocalLengthMm, Float relativeZoom,
                            Float sensorWidthMm, Float sensorHeightMm, boolean rawCapability,
                            boolean manualSensorCapability, SensorMetadata sensorMetadata,
                            List<RawOutput> rawOutputs, List<IntRange> aeFpsRanges,
                            Set<CapabilityIssue> issues) {
        this.route = Objects.requireNonNull(route);
        this.facing = Objects.requireNonNull(facing);
        this.lensRole = Objects.requireNonNull(lensRole);
        this.focalLengthMm = focalLengthMm;
        this.equivalentFocalLengthMm = equivalentFocalLengthMm;
        this.relativeZoom = relativeZoom;
        this.sensorWidthMm = sensorWidthMm;
        this.sensorHeightMm = sensorHeightMm;
        this.rawCapability = rawCapability;
        this.manualSensorCapability = manualSensorCapability;
        this.sensorMetadata = sensorMetadata;
        this.rawOutputs = Collections.unmodifiableList(new ArrayList<>(rawOutputs));
        this.aeFpsRanges = Collections.unmodifiableList(new ArrayList<>(aeFpsRanges));
        EnumSet<CapabilityIssue> issueCopy = issues.isEmpty()
                ? EnumSet.noneOf(CapabilityIssue.class) : EnumSet.copyOf(issues);
        this.issues = Collections.unmodifiableSet(issueCopy);
    }

    public boolean isUsable() {
        if (!rawCapability || sensorMetadata == null || rawOutputs.isEmpty()) return false;
        for (CapabilityIssue issue : issues) if (issue.blocking) return false;
        return true;
    }

    public String displayName() {
        StringBuilder value = new StringBuilder(lensRole.label);
        if (relativeZoom != null) value.append(String.format(java.util.Locale.ROOT, " %.1fx", relativeZoom));
        if (equivalentFocalLengthMm != null) value.append(String.format(java.util.Locale.ROOT, " %.0f mm", equivalentFocalLengthMm));
        return value.append("  ID ").append(route.routeId).toString();
    }

    public CameraDescriptor withLensIdentity(LensRole role, Float zoom) {
        return new CameraDescriptor(route, facing, role, focalLengthMm, equivalentFocalLengthMm,
                zoom, sensorWidthMm, sensorHeightMm, rawCapability, manualSensorCapability,
                sensorMetadata, rawOutputs, aeFpsRanges, issues);
    }
}
