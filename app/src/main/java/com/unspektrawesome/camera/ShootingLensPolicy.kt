package com.unspektrawesome.camera

import kotlin.math.roundToInt

/** Reduces duplicate logical, physical, and direct routes to one reliable shooting route per lens. */
object ShootingLensPolicy {
    fun visibleCameras(
        cameras: List<CameraDescriptor>,
        enabledRouteIds: Set<String>,
        selectionConfigured: Boolean,
    ): List<CameraDescriptor> {
        val usable = cameras.filter(CameraDescriptor::isUsable)
        val selected = if (selectionConfigured) {
            usable.filter { it.route.routeId in enabledRouteIds }
        } else {
            canonicalCameras(usable)
        }
        return selected.sortedWith(
            compareBy<CameraDescriptor>({ facingOrder(it.facing) }, { zoomOrder(it) }, { it.route.routeId }),
        )
    }

    fun canonicalRouteIds(cameras: List<CameraDescriptor>): Set<String> =
        canonicalCameras(cameras.filter(CameraDescriptor::isUsable))
            .mapTo(linkedSetOf()) { it.route.routeId }

    fun canonicalCameras(cameras: List<CameraDescriptor>): List<CameraDescriptor> =
        cameras.filter(CameraDescriptor::isUsable).groupBy(::lensIdentity).values.map { candidates ->
            candidates.minWithOrNull(
                compareBy<CameraDescriptor>(
                    { routePreference(it.route.kind) },
                    { if (it.manualSensorCapability) 0 else 1 },
                    { -it.rawOutputs.maxOfOrNull { output -> output.size.area() }.orEmpty() },
                    { it.route.routeId },
                ),
            ) ?: error("Lens group cannot be empty")
        }.sortedWith(
            compareBy<CameraDescriptor>({ facingOrder(it.facing) }, { zoomOrder(it) }, { it.route.routeId }),
        )

    private fun lensIdentity(camera: CameraDescriptor): String {
        val focalKey = camera.equivalentFocalLengthMm?.let { "eq${(it * 2f).roundToInt()}" }
            ?: "f${camera.focalLengthMm?.times(10f)?.roundToInt()}" +
                "s${camera.sensorWidthMm?.times(10f)?.roundToInt()}x" +
                "${camera.sensorHeightMm?.times(10f)?.roundToInt()}"
        return "${camera.facing}:$focalKey"
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

    private fun zoomOrder(camera: CameraDescriptor): Float =
        camera.relativeZoom ?: camera.equivalentFocalLengthMm ?: Float.MAX_VALUE

    private fun Long?.orEmpty(): Long = this ?: 0L
}
