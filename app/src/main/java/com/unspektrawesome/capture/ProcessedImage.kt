package com.unspektrawesome.capture

import java.nio.ByteBuffer

/** Final display-referred pixels produced by the Vulkan capture pipeline. */
data class ProcessedImage(
    val width: Int,
    val height: Int,
    val rgba8888: ByteBuffer,
) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        val required = width.toLong() * height.toLong() * BYTES_PER_PIXEL
        require(required <= Int.MAX_VALUE && rgba8888.isDirect && rgba8888.capacity() >= required) {
            "Image buffer must be direct and contain width * height * 4 bytes"
        }
    }

    companion object {
        const val BYTES_PER_PIXEL = 4
    }
}
