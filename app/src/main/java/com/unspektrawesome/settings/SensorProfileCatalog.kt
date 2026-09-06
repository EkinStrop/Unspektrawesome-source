package com.unspektrawesome.settings

import com.unspektrawesome.camera.CameraDescriptor
import com.unspektrawesome.camera.CameraRoute
import com.unspektrawesome.camera.LensFacing
import com.unspektrawesome.camera.RawFormat
import com.unspektrawesome.camera.RawOutput
import kotlin.math.roundToInt

@JvmInline
value class SensorProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "Sensor profile ID is blank" }
    }
}

data class PhysicalSensorProfile(
    val id: SensorProfileId,
    val primary: CameraDescriptor,
    val paths: List<CameraDescriptor>,
    val stableSensorIds: Set<String>,
) {
    init {
        require(paths.isNotEmpty()) { "Sensor profile has no Camera2 paths" }
        require(primary in paths) { "Primary Camera2 path is not part of its sensor profile" }
        require(paths.all(CameraDescriptor::isUsable)) {
            "Sensor profiles may contain only usable Camera2 paths"
        }
    }

    val routeIds: List<String> = paths.map { it.route.routeId }
}

data class ConfiguredSensorProfile(
    val profile: PhysicalSensorProfile,
    val selectedPath: CameraDescriptor,
    val enabled: Boolean,
    val isDefault: Boolean,
    val selectedRegularRawOutput: RawOutput?,
)

object SensorProfileCatalog {
    fun group(cameras: List<CameraDescriptor>): List<PhysicalSensorProfile> {
        val buckets = mutableListOf<MutableProfile>()
        cameras.filter(CameraDescriptor::isUsable).forEach { camera ->
            val stableIdentity = StableIdentity(camera.facing, stableSensorId(camera))
            val fingerprint = calibratedFingerprint(camera)
            val bucket = buckets.firstOrNull { candidate ->
                stableIdentity in candidate.stableIdentities ||
                    candidate.fingerprints.any { it == fingerprint }
            } ?: MutableProfile().also(buckets::add)
            bucket.paths.putIfAbsent(camera.route.routeId, camera)
            bucket.stableIdentities += stableIdentity
            bucket.fingerprints += fingerprint
        }
        return buckets.map(::toProfile).sortedWith(
            compareBy<PhysicalSensorProfile>(
                { facingOrder(it.primary.facing) },
                { it.primary.relativeZoom ?: it.primary.equivalentFocalLengthMm ?: Float.MAX_VALUE },
                { it.id.value },
            ),
        )
    }

    fun resolve(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<ConfiguredSensorProfile> = group(cameras).map { profile ->
        val selectedPath = preferences.lensButtonRouteIds
            .firstNotNullOfOrNull { configuredId ->
                profile.paths.firstOrNull { it.route.routeId == configuredId }
            }
            ?: profile.primary
        val regularOutputs = selectedPath.rawOutputs.filterNot { it.maximumResolutionMode }
        val savedRaw = preferences.rawPreviewStreams[selectedPath.route.routeId]
        val selectedRaw = regularOutputs.firstOrNull { savedRaw?.matches(it) == true }
            ?: defaultRegularRawOutput(regularOutputs)
        ConfiguredSensorProfile(
            profile = profile,
            selectedPath = selectedPath,
            enabled = !preferences.lensSelectionConfigured ||
                profile.routeIds.any { it in preferences.enabledLensIds },
            isDefault = preferences.defaultLensId in profile.routeIds,
            selectedRegularRawOutput = selectedRaw,
        )
    }

    fun selectPath(
        preferences: CameraPreferences,
        profile: PhysicalSensorProfile,
        routeId: String,
    ): CameraPreferences {
        require(routeId in profile.routeIds) { "Camera2 path does not belong to this sensor profile" }
        val routes = preferences.lensButtonRouteIds.toMutableList()
        val profileIndex = routes.indexOfFirst { it in profile.routeIds }
        if (profileIndex >= 0) routes[profileIndex] = routeId else routes += routeId
        val wasEnabled = !preferences.lensSelectionConfigured ||
            profile.routeIds.any { it in preferences.enabledLensIds }
        val enabledRoutes = preferences.enabledLensIds - profile.routeIds + if (
            preferences.lensSelectionConfigured && wasEnabled
        ) {
            setOf(routeId)
        } else {
            emptySet()
        }
        return preferences.copy(
            lensButtonRouteIds = routes.distinct(),
            lensButtonMappingConfigured = true,
            enabledLensIds = enabledRoutes,
            defaultLensId = if (preferences.defaultLensId in profile.routeIds) {
                routeId
            } else {
                preferences.defaultLensId
            },
        )
    }

    fun setEnabled(
        preferences: CameraPreferences,
        profile: PhysicalSensorProfile,
        enabled: Boolean,
    ): CameraPreferences {
        val selectedRoute = selectedPath(profile, preferences).route.routeId
        return preferences.copy(
            enabledLensIds = preferences.enabledLensIds - profile.routeIds +
                if (enabled) setOf(selectedRoute) else emptySet(),
            lensSelectionConfigured = true,
        )
    }

    fun setDefault(
        preferences: CameraPreferences,
        profile: PhysicalSensorProfile,
    ): CameraPreferences = preferences.copy(
        defaultLensId = selectedPath(profile, preferences).route.routeId,
    )

    fun selectRawOutput(
        preferences: CameraPreferences,
        profile: PhysicalSensorProfile,
        output: RawOutput,
    ): CameraPreferences {
        val path = selectedPath(profile, preferences)
        require(!output.maximumResolutionMode && output in path.rawOutputs) {
            "RAW output is not a regular raster advertised by the selected Camera2 path"
        }
        return preferences.copy(
            rawPreviewStreams = preferences.rawPreviewStreams +
                (path.route.routeId to RawStreamSelection.from(output)),
        )
    }

    private fun selectedPath(
        profile: PhysicalSensorProfile,
        preferences: CameraPreferences,
    ): CameraDescriptor = preferences.lensButtonRouteIds.firstNotNullOfOrNull { routeId ->
        profile.paths.firstOrNull { it.route.routeId == routeId }
    } ?: profile.primary

    private fun toProfile(bucket: MutableProfile): PhysicalSensorProfile {
        val paths = bucket.paths.values.sortedWith(
            compareBy<CameraDescriptor>(
                { routePreference(it.route.kind) },
                { if (it.manualSensorCapability) 0 else 1 },
                { it.route.routeId },
            ),
        )
        val primary = paths.first()
        val stableIds = bucket.stableIdentities.mapTo(linkedSetOf()) { it.sensorId }
        return PhysicalSensorProfile(
            id = SensorProfileId("${primary.facing.name.lowercase()}:${stableSensorId(primary)}"),
            primary = primary,
            paths = paths,
            stableSensorIds = stableIds,
        )
    }

    private fun stableSensorId(camera: CameraDescriptor): String =
        camera.route.physicalCameraId?.takeIf(String::isNotBlank)
            ?: camera.route.characteristicsCameraId

    private fun calibratedFingerprint(camera: CameraDescriptor): CalibratedFingerprint {
        val sensor = requireNotNull(camera.sensorMetadata)
        val pixelArray = sensor.pixelArray
            ?: camera.rawOutputs.maxByOrNull { it.size.area() }?.size
        val calibration = sensor.colorCalibration
        return CalibratedFingerprint(
            facing = camera.facing,
            cfa = sensor.cfaPattern.name,
            focalLength = quantize(camera.focalLengthMm, 100f),
            sensorWidth = quantize(camera.sensorWidthMm, 100f),
            sensorHeight = quantize(camera.sensorHeightMm, 100f),
            pixelWidth = pixelArray?.width,
            pixelHeight = pixelArray?.height,
            whiteLevel = sensor.whiteLevel,
            blackLevel = sensor.blackLevel().map { quantize(it, 100f) },
            calibration = quantizeMatrix(
                calibration.colorMatrix1()
                    ?: calibration.forwardMatrix1()
                    ?: calibration.calibrationMatrix1(),
            ),
        )
    }

    private fun defaultRegularRawOutput(outputs: List<RawOutput>): RawOutput? {
        val preferred = outputs.filter { it.format == RawFormat.RAW_SENSOR }.ifEmpty { outputs }
        return preferred.filter { it.maximumFps() >= 29.75 }
            .maxByOrNull { it.size.area() }
            ?: preferred.maxByOrNull { it.size.area() }
    }

    private fun quantize(value: Float?, scale: Float): Int? =
        value?.takeIf(Float::isFinite)?.times(scale)?.roundToInt()

    private fun quantizeMatrix(value: FloatArray?): List<Int>? = value?.map {
        if (it.isFinite()) (it * 10_000f).roundToInt() else Int.MIN_VALUE
    }

    private fun routePreference(kind: CameraRoute.Kind): Int = when (kind) {
        CameraRoute.Kind.PHYSICAL -> 0
        CameraRoute.Kind.DIRECT -> 1
        CameraRoute.Kind.HIDDEN_DIRECT -> 2
        CameraRoute.Kind.LOGICAL -> 3
        CameraRoute.Kind.HIDDEN_LOGICAL -> 4
    }

    private fun facingOrder(facing: LensFacing): Int = when (facing) {
        LensFacing.BACK -> 0
        LensFacing.FRONT -> 1
        LensFacing.EXTERNAL -> 2
        LensFacing.UNKNOWN -> 3
    }

    private data class StableIdentity(
        val facing: LensFacing,
        val sensorId: String,
    )

    private data class CalibratedFingerprint(
        val facing: LensFacing,
        val cfa: String,
        val focalLength: Int?,
        val sensorWidth: Int?,
        val sensorHeight: Int?,
        val pixelWidth: Int?,
        val pixelHeight: Int?,
        val whiteLevel: Int,
        val blackLevel: List<Int?>,
        val calibration: List<Int>?,
    )

    private class MutableProfile {
        val paths = linkedMapOf<String, CameraDescriptor>()
        val stableIdentities = linkedSetOf<StableIdentity>()
        val fingerprints = linkedSetOf<CalibratedFingerprint>()
    }
}
