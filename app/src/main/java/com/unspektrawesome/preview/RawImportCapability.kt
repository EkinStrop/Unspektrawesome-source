package com.unspektrawesome.preview

import com.unspektrawesome.camera.RawFormat
import com.unspektrawesome.camera.RawOutput

data class RawImportStreamKey(
    val routeId: String,
    val format: RawFormat,
    val width: Int,
    val height: Int,
    val maximumResolutionMode: Boolean,
) {
    companion object {
        fun from(routeId: String, output: RawOutput) = RawImportStreamKey(
            routeId = routeId,
            format = output.format,
            width = output.size.width,
            height = output.size.height,
            maximumResolutionMode = output.maximumResolutionMode,
        )
    }
}

enum class RawImportCapabilityState {
    COMPATIBLE,
    INCOMPATIBLE,
}

data class RawImportCapability(
    val state: RawImportCapabilityState,
    val reason: String,
)

object RawImportDiagnosticClassifier {
    private val importFailureMarkers = listOf(
        "Unable to access RAW HardwareBuffer",
        "vkGetAndroidHardwareBufferPropertiesANDROID",
        "RAW HardwareBuffer image dimensions do not match",
        "Packed RAW10/RAW12 2D HardwareBuffer is not importable",
        "Packed RAW plane must be a direct ByteBuffer",
        "Packed RAW plane exceeds the Vulkan storage buffer range",
        "vkFlushMappedMemoryRanges for packed RAW upload",
        "RAW_SENSOR HardwareBuffer does not expose",
        "RAW_SENSOR R16_UINT HardwareBuffer lacks",
        "RAW_SENSOR HardwareBuffer image has no compatible",
        "vkCreateImage for RAW_SENSOR HardwareBuffer",
        "vkAllocateMemory for RAW_SENSOR HardwareBuffer",
        "vkBindImageMemory for RAW_SENSOR HardwareBuffer",
        "RAW HardwareBuffer is not a Vulkan buffer",
        "RAW HardwareBuffer lacks GPU_DATA_BUFFER usage",
        "RAW HardwareBuffer byte size must be aligned",
        "RAW HardwareBuffer exceeds the device storage buffer range",
        "RAW HardwareBuffer is smaller than rowStride times height",
        "Device cannot import Android HardwareBuffer storage buffers",
        "vkCreateBuffer for RAW HardwareBuffer",
        "RAW HardwareBuffer has no compatible Vulkan memory type",
        "vkAllocateMemory for RAW HardwareBuffer",
        "vkBindBufferMemory for RAW HardwareBuffer",
    )

    fun importRejectionReason(diagnostic: String): String? {
        val normalized = diagnostic.trim()
        if (normalized.isEmpty()) return null
        return normalized.takeIf { message ->
            importFailureMarkers.any { marker -> message.contains(marker, ignoreCase = true) }
        }
    }
}
