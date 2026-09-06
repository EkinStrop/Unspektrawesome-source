package com.unspektrawesome.settings

import com.unspektrawesome.camera.CameraDescriptor
import com.unspektrawesome.camera.ShootingLensPolicy

object LensButtonMapping {
    fun enabledCameras(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<CameraDescriptor> {
        val usable = cameras.filter(CameraDescriptor::isUsable)
        if (!preferences.lensSelectionConfigured) {
            return ShootingLensPolicy.canonicalCameras(cameras)
        }
        return usable.filter { it.route.routeId in preferences.enabledLensIds }
    }

    fun selectableCameras(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<CameraDescriptor> = if (preferences.lensSelectionConfigured) {
        enabledCameras(cameras, preferences)
    } else {
        cameras.filter(CameraDescriptor::isUsable)
    }

    fun resolvedRouteIds(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<String> {
        val enabled = enabledCameras(cameras, preferences)
        val defaults = enabled.map { it.route.routeId }
        if (!preferences.lensButtonMappingConfigured) return defaults

        val usableById = selectableCameras(cameras, preferences)
            .associateBy { it.route.routeId }
        val targetCount = defaults.size
        if (targetCount == 0) return emptyList()
        val resolved = LinkedHashSet<String>(targetCount)
        preferences.lensButtonRouteIds.forEach { routeId ->
            if (routeId in usableById && resolved.size < targetCount) resolved += routeId
        }
        defaults.forEach { routeId ->
            if (resolved.size < targetCount) resolved += routeId
        }
        return resolved.take(targetCount)
    }

    fun resolvedCameras(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
    ): List<CameraDescriptor> {
        val byId = cameras.associateBy { it.route.routeId }
        return resolvedRouteIds(cameras, preferences).mapNotNull(byId::get)
    }

    fun assign(
        cameras: List<CameraDescriptor>,
        preferences: CameraPreferences,
        buttonIndex: Int,
        routeId: String,
    ): CameraPreferences {
        val usableIds = selectableCameras(cameras, preferences)
            .map { it.route.routeId }
            .toSet()
        require(routeId in usableIds) { "Lens button route is not a usable discovered camera" }
        val routes = resolvedRouteIds(cameras, preferences).toMutableList()
        require(buttonIndex in routes.indices) { "Lens button index is out of range" }
        val previousIndex = routes.indexOf(routeId)
        if (previousIndex >= 0 && previousIndex != buttonIndex) {
            routes[previousIndex] = routes[buttonIndex]
        }
        routes[buttonIndex] = routeId
        return preferences.copy(
            lensButtonRouteIds = routes,
            lensButtonMappingConfigured = true,
        )
    }
}
