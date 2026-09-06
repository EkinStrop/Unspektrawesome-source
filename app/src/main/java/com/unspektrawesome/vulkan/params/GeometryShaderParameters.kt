package com.unspektrawesome.vulkan.params

import com.unspektrawesome.capture.FrameGeometrySnapshot
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeometryShaderParameters private constructor(
    private val snapshot: FrameGeometrySnapshot,
) {
    fun toByteBuffer(): ByteBuffer = writeTo(
        ByteBuffer.allocateDirect(SIZE_BYTES).order(ByteOrder.nativeOrder()),
    )

    fun writeTo(target: ByteBuffer): ByteBuffer {
        require(target.isDirect && target.capacity() >= SIZE_BYTES) {
            "Geometry parameter target must be a direct buffer of at least $SIZE_BYTES bytes"
        }
        val crop = snapshot.sourceCrop
        val active = snapshot.activeArray
        return target.order(ByteOrder.nativeOrder()).apply {
            putFloat(SOURCE_CROP_OFFSET, crop.left)
            putFloat(SOURCE_CROP_OFFSET + 4, crop.top)
            putFloat(SOURCE_CROP_OFFSET + 8, crop.width)
            putFloat(SOURCE_CROP_OFFSET + 12, crop.height)
            putFloat(ACTIVE_ARRAY_OFFSET, active.left)
            putFloat(ACTIVE_ARRAY_OFFSET + 4, active.top)
            putFloat(ACTIVE_ARRAY_OFFSET + 8, active.width)
            putFloat(ACTIVE_ARRAY_OFFSET + 12, active.height)
            putInt(TRANSFORM_OFFSET, snapshot.transform.orientationDegrees / 90)
            putInt(TRANSFORM_OFFSET + 4, if (snapshot.transform.mirrorHorizontal) 1 else 0)
            putInt(TRANSFORM_OFFSET + 8, snapshot.outputSize.width)
            putInt(TRANSFORM_OFFSET + 12, snapshot.outputSize.height)
            putFloat(TARGET_OFFSET, snapshot.targetViewport.width.toFloat())
            putFloat(TARGET_OFFSET + 4, snapshot.targetViewport.height.toFloat())
            putFloat(TARGET_OFFSET + 8, snapshot.targetAspect)
            putFloat(TARGET_OFFSET + 12, 1f / snapshot.targetAspect)
            position(0)
            limit(SIZE_BYTES)
        }
    }

    companion object {
        const val SOURCE_CROP_OFFSET = 0
        const val ACTIVE_ARRAY_OFFSET = 16
        const val TRANSFORM_OFFSET = 32
        const val TARGET_OFFSET = 48
        const val SIZE_BYTES = 64

        fun from(snapshot: FrameGeometrySnapshot) = GeometryShaderParameters(snapshot)
    }
}
