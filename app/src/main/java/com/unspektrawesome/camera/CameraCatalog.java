package com.unspektrawesome.camera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Camera2 adapter. Discovery results contain no live Camera2 objects and are safe to persist or test. */
public final class CameraCatalog {
    public static final int DEFAULT_HIDDEN_NUMERIC_PROBE_LIMIT = 128;

    private final CameraManager manager;
    private final int hiddenNumericProbeLimit;

    public CameraCatalog(Context context) {
        this(context.getSystemService(CameraManager.class), DEFAULT_HIDDEN_NUMERIC_PROBE_LIMIT);
    }

    public CameraCatalog(CameraManager manager, int hiddenNumericProbeLimit) {
        if (manager == null) throw new IllegalArgumentException("CameraManager is unavailable");
        if (hiddenNumericProbeLimit < 0) throw new IllegalArgumentException("Probe limit cannot be negative");
        this.manager = manager;
        this.hiddenNumericProbeLimit = hiddenNumericProbeLimit;
    }

    public List<CameraDescriptor> discover() throws CameraAccessException {
        Set<String> publicIds = new LinkedHashSet<>(Arrays.asList(manager.getCameraIdList()));
        Map<String, CameraCharacteristics> known = new LinkedHashMap<>();
        for (String id : publicIds) {
            CameraCharacteristics value = getCharacteristics(id);
            if (value != null) known.put(id, value);
        }

        Set<String> hiddenIds = new LinkedHashSet<>();
        for (String id : CameraRoutePlanner.numericProbeCandidates(publicIds, hiddenNumericProbeLimit)) {
            CameraCharacteristics value = getCharacteristics(id);
            if (value != null) {
                known.put(id, value);
                hiddenIds.add(id);
            }
        }

        Map<String, Set<String>> physicalByLogical = new LinkedHashMap<>();
        for (Map.Entry<String, CameraCharacteristics> entry : new ArrayList<>(known.entrySet())) {
            Set<String> physical = new LinkedHashSet<>(entry.getValue().getPhysicalCameraIds());
            physical.remove(entry.getKey());
            if (!physical.isEmpty()) physicalByLogical.put(entry.getKey(), physical);
            for (String physicalId : physical) {
                if (!known.containsKey(physicalId)) {
                    CameraCharacteristics value = getCharacteristics(physicalId);
                    if (value != null) known.put(physicalId, value);
                }
            }
        }

        List<CameraDescriptor> descriptors = new ArrayList<>();
        for (CameraRoute route : CameraRoutePlanner.plan(publicIds, physicalByLogical, hiddenIds)) {
            CameraCharacteristics characteristics = known.get(route.characteristicsCameraId);
            boolean physicalFallback = false;
            if (characteristics == null && route.physicalCameraId != null) {
                characteristics = known.get(route.openCameraId);
                physicalFallback = characteristics != null;
            }
            if (characteristics == null) {
                descriptors.add(unavailable(route));
                continue;
            }
            try {
                descriptors.add(describe(route, characteristics, physicalFallback));
            } catch (RuntimeException | AssertionError ignored) {
                descriptors.add(unavailable(route));
            }
        }
        return LensClassifier.classify(descriptors);
    }

    private CameraCharacteristics getCharacteristics(String id) {
        try {
            return manager.getCameraCharacteristics(id);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException |
                 AssertionError ignored) {
            return null;
        }
    }

    private static CameraDescriptor describe(CameraRoute route, CameraCharacteristics value,
                                             boolean physicalFallback) {
        Set<Integer> capabilities = integers(value.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES));
        boolean hasRaw = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW);
        boolean hasManual = capabilities.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR);
        List<RawOutput> outputs = rawOutputs(value);

        CfaPattern cfa = CfaPattern.fromCamera2(value.get(
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT));
        float[] black = blackLevel(value.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Integer whiteValue = value.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        int white = whiteValue == null ? 0 : whiteValue;
        Rect2d activeArray = rect(value.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        Size2d pixelArray = size(value.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE));
        Rect2d maximumActiveArray = null;
        Size2d maximumPixelArray = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maximumActiveArray = rect(value.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE_MAXIMUM_RESOLUTION));
            maximumPixelArray = size(value.get(
                    CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION));
        }
        Integer orientationValue = value.get(CameraCharacteristics.SENSOR_ORIENTATION);
        ColorCalibration color = colorCalibration(value);
        SensorMetadata sensor = new SensorMetadata(cfa, black, white, activeArray, pixelArray,
                maximumActiveArray, maximumPixelArray,
                orientationValue == null ? 0 : orientationValue, color);

        SizeF physicalSize = value.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Float sensorWidth = positive(physicalSize == null ? null : physicalSize.getWidth());
        Float sensorHeight = positive(physicalSize == null ? null : physicalSize.getHeight());
        Float focal = firstPositive(value.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS));
        Float equivalent = LensClassifier.equivalentFocalLength(focal, sensorWidth, sensorHeight);
        List<IntRange> fpsRanges = fpsRanges(value.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));

        EnumSet<CapabilityIssue> issues = EnumSet.noneOf(CapabilityIssue.class);
        if (physicalFallback) issues.add(CapabilityIssue.PHYSICAL_CHARACTERISTICS_FALLBACK);
        if (!hasRaw) issues.add(CapabilityIssue.RAW_CAPABILITY_NOT_ADVERTISED);
        if (outputs.isEmpty()) issues.add(CapabilityIssue.RAW_OUTPUT_NOT_ADVERTISED);
        if (!cfa.isBayer()) issues.add(CapabilityIssue.BAYER_CFA_UNAVAILABLE);
        if (!sensor.hasValidLevels()) issues.add(CapabilityIssue.INVALID_RAW_LEVELS);
        if (activeArray == null) issues.add(CapabilityIssue.ACTIVE_ARRAY_UNAVAILABLE);
        if (!color.hasUsableColorTransform()) issues.add(CapabilityIssue.COLOR_CALIBRATION_UNAVAILABLE);
        if (!hasManual) issues.add(CapabilityIssue.MANUAL_SENSOR_UNAVAILABLE);
        if (outputs.stream().noneMatch(output -> !output.maximumResolutionMode
                && output.minimumFrameDurationNs > 0
                && output.maximumFps() >= 29.75)) {
            issues.add(CapabilityIssue.TARGET_30_FPS_UNAVAILABLE);
        }

        LensFacing facing = LensFacing.fromCamera2(value.get(CameraCharacteristics.LENS_FACING));
        return new CameraDescriptor(route, facing, LensClassifier.role(facing, null), focal,
                equivalent, null, sensorWidth, sensorHeight, hasRaw, hasManual, sensor,
                outputs, fpsRanges, issues);
    }

    private static CameraDescriptor unavailable(CameraRoute route) {
        return new CameraDescriptor(route, LensFacing.UNKNOWN, LensRole.UNKNOWN, null, null,
                null, null, null, false, false, null, Collections.emptyList(),
                Collections.emptyList(), EnumSet.of(CapabilityIssue.CHARACTERISTICS_UNAVAILABLE));
    }

    private static List<RawOutput> rawOutputs(CameraCharacteristics characteristics) {
        LinkedHashSet<RawOutput> result = new LinkedHashSet<>();
        appendOutputs(result, characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appendOutputs(result, characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION), true);
        }
        List<RawOutput> sorted = new ArrayList<>(result);
        sorted.sort((left, right) -> {
            int maximumMode = Boolean.compare(left.maximumResolutionMode, right.maximumResolutionMode);
            if (maximumMode != 0) return maximumMode;
            int area = Long.compare(right.size.area(), left.size.area());
            return area != 0 ? area : left.format.compareTo(right.format);
        });
        return sorted;
    }

    private static void appendOutputs(Set<RawOutput> result, StreamConfigurationMap map,
                                      boolean maximumResolutionMode) {
        if (map == null) return;
        Set<Integer> formats = integers(map.getOutputFormats());
        for (RawFormat format : RawFormat.values()) {
            if (!formats.contains(format.imageFormat)) continue;
            Size[] sizes;
            try { sizes = map.getOutputSizes(format.imageFormat); }
            catch (RuntimeException ignored) { continue; }
            if (sizes == null) continue;
            for (Size size : sizes) {
                long minimum = duration(() -> map.getOutputMinFrameDuration(format.imageFormat, size));
                long stall = duration(() -> map.getOutputStallDuration(format.imageFormat, size));
                result.add(new RawOutput(format, new Size2d(size.getWidth(), size.getHeight()),
                        minimum, stall, maximumResolutionMode));
            }
        }
    }

    private static long duration(DurationReader reader) {
        try { return Math.max(0, reader.read()); }
        catch (RuntimeException | AssertionError ignored) { return 0; }
    }

    private static Set<Integer> integers(int[] values) {
        Set<Integer> result = new LinkedHashSet<>();
        if (values != null) for (int value : values) result.add(value);
        return result;
    }

    private static float[] blackLevel(BlackLevelPattern pattern) {
        if (pattern == null) return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        return new float[]{pattern.getOffsetForIndex(0, 0), pattern.getOffsetForIndex(1, 0),
                pattern.getOffsetForIndex(0, 1), pattern.getOffsetForIndex(1, 1)};
    }

    private static ColorCalibration colorCalibration(CameraCharacteristics value) {
        Integer illuminant1 = value.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1);
        Byte illuminant2 = value.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);
        return new ColorCalibration(
                matrix(value.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)),
                matrix(value.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)),
                matrix(value.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)),
                matrix(value.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)),
                matrix(value.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)),
                matrix(value.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)),
                illuminant1, illuminant2 == null ? null : illuminant2.intValue());
    }

    private static float[] matrix(ColorSpaceTransform transform) {
        if (transform == null) return null;
        float[] result = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                Rational rational = transform.getElement(column, row);
                result[row * 3 + column] = rational.getDenominator() == 0 ? 0f
                        : rational.getNumerator() / (float) rational.getDenominator();
            }
        }
        return result;
    }

    private static List<IntRange> fpsRanges(Range<Integer>[] values) {
        List<IntRange> result = new ArrayList<>();
        if (values != null) {
            for (Range<Integer> value : values) result.add(new IntRange(value.getLower(), value.getUpper()));
        }
        result.sort((left, right) -> left.upper != right.upper
                ? Integer.compare(left.upper, right.upper) : Integer.compare(left.lower, right.lower));
        return result;
    }

    private static Float firstPositive(float[] values) {
        Float result = null;
        if (values != null) for (float value : values) {
            if (Float.isFinite(value) && value > 0 && (result == null || value < result)) result = value;
        }
        return result;
    }

    private static Float positive(Float value) {
        return value != null && Float.isFinite(value) && value > 0 ? value : null;
    }

    private static Rect2d rect(Rect value) {
        return value == null || value.width() <= 0 || value.height() <= 0 ? null
                : new Rect2d(value.left, value.top, value.right, value.bottom);
    }

    private static Size2d size(Size value) {
        return value == null || value.getWidth() <= 0 || value.getHeight() <= 0 ? null
                : new Size2d(value.getWidth(), value.getHeight());
    }

    private interface DurationReader { long read(); }
}
