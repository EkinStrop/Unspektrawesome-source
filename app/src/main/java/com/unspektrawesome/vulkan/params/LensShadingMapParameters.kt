package com.unspektrawesome.vulkan.params

import com.unspektrawesome.camera.session.RawCaptureMetadata
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Immutable row-major Camera2 lens-shading grid for Vulkan descriptor binding 4. */
class LensShadingMapParameters private constructor(
    val width: Int,
    val height: Int,
    private val gainFactors: FloatArray,
) {
    val texelCount: Int get() = width * height
    val sizeBytes: Int get() = gainFactors.size * Float.SIZE_BYTES

    fun gainFactor(x: Int, y: Int, channel: Int): Float {
        require(x in 0 until width && y in 0 until height) { "Lens-shading coordinate is out of range" }
        require(channel in 0 until CHANNEL_COUNT) { "Lens-shading channel is out of range" }
        return gainFactors[((y * width + x) * CHANNEL_COUNT) + channel]
    }

    fun toByteBuffer(): ByteBuffer = writeTo(
        ByteBuffer.allocateDirect(sizeBytes).order(ByteOrder.nativeOrder()),
    )

    fun writeTo(target: ByteBuffer): ByteBuffer {
        require(target.isDirect && target.capacity() >= sizeBytes) {
            "Lens-shading target must be a direct buffer of at least $sizeBytes bytes"
        }
        return target.order(ByteOrder.nativeOrder()).apply {
            gainFactors.forEachIndexed { index, value -> putFloat(index * Float.SIZE_BYTES, value) }
            position(0)
            limit(sizeBytes)
        }
    }

    companion object {
        const val DESCRIPTOR_SET = 0
        const val DESCRIPTOR_BINDING = 4
        const val CHANNEL_COUNT = 4
        const val TEXEL_SIZE_BYTES = CHANNEL_COUNT * Float.SIZE_BYTES
        const val RED_CHANNEL = 0
        const val GREEN_EVEN_CHANNEL = 1
        const val GREEN_ODD_CHANNEL = 2
        const val BLUE_CHANNEL = 3

        fun fromRawCaptureMetadata(metadata: RawCaptureMetadata): LensShadingMapParameters {
            require(metadata.hasLensShadingMap()) { "Capture metadata has no lens-shading map" }
            return create(
                metadata.lensShadingMapWidth,
                metadata.lensShadingMapHeight,
                requireNotNull(metadata.lensShadingGainFactors()),
            )
        }

        fun fromRawCaptureMetadataOrNull(metadata: RawCaptureMetadata): LensShadingMapParameters? =
            if (metadata.hasLensShadingMap()) fromRawCaptureMetadata(metadata) else null

        fun preferCaptureMetadata(
            metadata: RawCaptureMetadata,
            previewSnapshot: LensShadingMapParameters,
        ): LensShadingMapParameters =
            runCatching { fromRawCaptureMetadataOrNull(metadata) }.getOrNull() ?: previewSnapshot

        fun create(width: Int, height: Int, gainFactors: FloatArray): LensShadingMapParameters {
            require(width > 0 && height > 0) { "Lens-shading dimensions must be positive" }
            val expectedCount = width.toLong() * height.toLong() * CHANNEL_COUNT
            require(expectedCount <= Int.MAX_VALUE && gainFactors.size == expectedCount.toInt()) {
                "Lens-shading map must contain width * height * 4 gains"
            }
            require(gainFactors.all { it.isFinite() && it >= 1f }) {
                "Lens-shading gains must be finite and at least 1.0"
            }
            return LensShadingMapParameters(width, height, gainFactors.clone())
        }
    }
}
