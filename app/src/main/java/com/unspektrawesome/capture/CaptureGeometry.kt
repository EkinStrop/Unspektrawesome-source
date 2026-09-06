package com.unspektrawesome.capture

import com.unspektrawesome.camera.LensFacing
import com.unspektrawesome.camera.Size2d

data class CaptureTransform(
    val orientationDegrees: Int,
    val mirrorHorizontal: Boolean,
) {
    val swapsAxes: Boolean get() = orientationDegrees == 90 || orientationDegrees == 270

    init {
        require(orientationDegrees in VALID_ORIENTATIONS) {
            "Capture orientation must be 0, 90, 180, or 270 degrees"
        }
    }

    fun outputSize(source: Size2d): Size2d = if (swapsAxes) {
        Size2d(source.height, source.width)
    } else {
        source
    }

    companion object {
        private val VALID_ORIENTATIONS = setOf(0, 90, 180, 270)
    }
}

object CaptureGeometry {
    fun transform(
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        facing: LensFacing,
    ): CaptureTransform {
        require(sensorOrientationDegrees in VALID_ORIENTATIONS) {
            "Sensor orientation must be 0, 90, 180, or 270 degrees"
        }
        require(displayRotationDegrees in VALID_ORIENTATIONS) {
            "Display rotation must be 0, 90, 180, or 270 degrees"
        }
        val relativeRotation = if (facing == LensFacing.FRONT) {
            (sensorOrientationDegrees + displayRotationDegrees) % 360
        } else {
            (sensorOrientationDegrees - displayRotationDegrees + 360) % 360
        }
        return CaptureTransform(relativeRotation, facing == LensFacing.FRONT)
    }

    fun rgbaBufferSize(size: Size2d): Int {
        val bytes = size.width.toLong() * size.height.toLong() * ProcessedImage.BYTES_PER_PIXEL
        require(bytes in 1..Int.MAX_VALUE.toLong()) {
            "Oriented capture is too large for one direct RGBA buffer"
        }
        return bytes.toInt()
    }

    private val VALID_ORIENTATIONS = setOf(0, 90, 180, 270)
}
