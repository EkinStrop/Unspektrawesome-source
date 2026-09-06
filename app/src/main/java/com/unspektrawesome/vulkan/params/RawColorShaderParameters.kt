package com.unspektrawesome.vulkan.params

import com.unspektrawesome.camera.SensorMetadata
import com.unspektrawesome.camera.session.RawCaptureMetadata
import com.unspektrawesome.spektra.SpektraCaptureSnapshot
import com.unspektrawesome.spektra.SpektraState
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WhiteBalanceGains(val red: Float, val green: Float, val blue: Float) {
    init {
        require(red.isFinite() && red > 0f) { "Red white-balance gain must be finite and positive" }
        require(green.isFinite() && green > 0f) { "Green white-balance gain must be finite and positive" }
        require(blue.isFinite() && blue > 0f) { "Blue white-balance gain must be finite and positive" }
    }

    companion object {
        fun fromRawCaptureMetadata(metadata: RawCaptureMetadata): WhiteBalanceGains {
            val neutral = requireNotNull(metadata.neutralColorPoint()) {
                "Capture metadata has no neutral color point"
            }
            require(neutral.all { it.isFinite() && it > 0f }) {
                "Neutral color point must contain three finite positive values"
            }
            return WhiteBalanceGains(
                red = neutral[1] / neutral[0],
                green = 1f,
                blue = neutral[1] / neutral[2],
            )
        }
    }
}

/** RAW-to-linear working RGB calibration consumed by the demosaic shaders. */
class RawColorShaderParameters private constructor(
    val revisionId: Long,
    private val values: FloatArray,
) {
    fun toByteBuffer(): ByteBuffer = writeTo(
        ByteBuffer.allocateDirect(SIZE_BYTES).order(ByteOrder.nativeOrder()),
    )

    fun writeTo(target: ByteBuffer): ByteBuffer {
        require(target.isDirect && target.capacity() >= SIZE_BYTES) {
            "Spektra parameter target must be a direct buffer of at least $SIZE_BYTES bytes"
        }
        return target.order(ByteOrder.nativeOrder()).apply {
            values.forEachIndexed { index, value -> putFloat(index * Float.SIZE_BYTES, value) }
            position(0)
            limit(SIZE_BYTES)
        }
    }

    companion object {
        const val WHITE_BALANCE_EXPOSURE_OFFSET = 0
        const val COLOR_MATRIX_0_OFFSET = 16
        const val COLOR_MATRIX_1_OFFSET = 32
        const val COLOR_MATRIX_2_OFFSET = 48
        const val TONE_OFFSET = 64
        const val LEVELS_OFFSET = 80
        const val COLOR_CONTROLS_OFFSET = 96
        const val TEXTURE_CONTROLS_OFFSET = 112
        const val GEOMETRY_CONTROLS_OFFSET = 128
        const val SIZE_BYTES = 144
        const val STD140_ALIGNMENT_BYTES = 16
        const val VEC4_SIZE_BYTES = 16
        const val DEFAULT_CLIP_THRESHOLD = 0.99f
        const val LENS_SHADING_STRENGTH_OFFSET = LEVELS_OFFSET + 12
        const val LENS_SHADING_WIDTH_OFFSET = GEOMETRY_CONTROLS_OFFSET + 8
        const val LENS_SHADING_HEIGHT_OFFSET = GEOMETRY_CONTROLS_OFFSET + 12

        fun fromState(
            state: SpektraState,
            sensor: SensorMetadata,
            whiteBalance: WhiteBalanceGains,
            lensShadingMap: LensShadingMapParameters?,
            clipThreshold: Float = DEFAULT_CLIP_THRESHOLD,
        ): RawColorShaderParameters = create(
            state = state,
            sensor = sensor,
            whiteBalance = whiteBalance,
            lensShadingMap = lensShadingMap,
            clipThreshold = clipThreshold,
        )

        fun fromState(
            state: SpektraState,
            sensor: SensorMetadata,
            captureMetadata: RawCaptureMetadata,
            clipThreshold: Float = DEFAULT_CLIP_THRESHOLD,
        ): RawColorShaderParameters = fromState(
            state,
            sensor,
            WhiteBalanceGains.fromRawCaptureMetadata(captureMetadata),
            LensShadingMapParameters.fromRawCaptureMetadataOrNull(captureMetadata),
            clipThreshold,
        )

        fun fromCaptureSnapshot(
            snapshot: SpektraCaptureSnapshot,
            sensor: SensorMetadata,
            whiteBalance: WhiteBalanceGains,
            lensShadingMap: LensShadingMapParameters?,
            clipThreshold: Float = DEFAULT_CLIP_THRESHOLD,
        ): RawColorShaderParameters = create(
            state = snapshot.state,
            sensor = sensor,
            whiteBalance = whiteBalance,
            lensShadingMap = lensShadingMap,
            clipThreshold = clipThreshold,
        ).also {
            check(it.revisionId == snapshot.revisionId)
        }

        fun fromCaptureSnapshot(
            snapshot: VulkanCaptureSnapshot,
            sensor: SensorMetadata,
            whiteBalance: WhiteBalanceGains,
            lensShadingMap: LensShadingMapParameters?,
            clipThreshold: Float = DEFAULT_CLIP_THRESHOLD,
        ): RawColorShaderParameters = fromCaptureSnapshot(
            snapshot.state, sensor, whiteBalance, lensShadingMap, clipThreshold,
        )

        fun fromCaptureSnapshot(
            snapshot: VulkanCaptureSnapshot,
            sensor: SensorMetadata,
            captureMetadata: RawCaptureMetadata,
            clipThreshold: Float = DEFAULT_CLIP_THRESHOLD,
        ): RawColorShaderParameters = fromCaptureSnapshot(
            snapshot.state,
            sensor,
            WhiteBalanceGains.fromRawCaptureMetadata(captureMetadata),
            LensShadingMapParameters.fromRawCaptureMetadataOrNull(captureMetadata),
            clipThreshold,
        )

        private fun create(
            state: SpektraState,
            sensor: SensorMetadata,
            whiteBalance: WhiteBalanceGains,
            lensShadingMap: LensShadingMapParameters?,
            clipThreshold: Float,
        ): RawColorShaderParameters {
            require(clipThreshold.isFinite() && clipThreshold in 0.5f..1f) {
                "Clip threshold must be finite and in 0.5..1.0"
            }
            val matrix = cameraToWorkingMatrix(sensor)
            return RawColorShaderParameters(
                revisionId = state.revisionId,
                values = floatArrayOf(
                    whiteBalance.red, whiteBalance.green, whiteBalance.blue, 0f,
                    matrix[0], matrix[3], matrix[6], 0f,
                    matrix[1], matrix[4], matrix[7], 0f,
                    matrix[2], matrix[5], matrix[8], 0f,
                    0f, 0f, 0f, 0f,
                    0f, 0f, 1f, 1f,
                    0f, 0f, 0f, 0f,
                    0f, 0.5f, 0f, 0.5f,
                    0.5f, clipThreshold,
                    lensShadingMap?.width?.toFloat() ?: 0f,
                    lensShadingMap?.height?.toFloat() ?: 0f,
                ),
            )
        }

        private fun cameraToWorkingMatrix(sensor: SensorMetadata): FloatArray {
            val calibration = sensor.colorCalibration
            val cameraToXyz = calibration.forwardMatrix1()?.let {
                validateMatrix(it, "forwardMatrix1")
            } ?: calibration.colorMatrix1()?.let {
                invert3x3(validateMatrix(it, "colorMatrix1"))
            } ?: throw IllegalArgumentException("Sensor metadata has no usable color transform")
            return multiply3x3(LINEAR_SRGB_FROM_XYZ_D50, cameraToXyz)
        }

        private fun validateMatrix(matrix: FloatArray, name: String): FloatArray {
            require(matrix.size == 9 && matrix.all(Float::isFinite)) {
                "$name must contain nine finite values"
            }
            return matrix
        }

        private fun invert3x3(m: FloatArray): FloatArray {
            val determinant =
                m[0] * (m[4] * m[8] - m[5] * m[7]) -
                    m[1] * (m[3] * m[8] - m[5] * m[6]) +
                    m[2] * (m[3] * m[7] - m[4] * m[6])
            require(determinant.isFinite() && kotlin.math.abs(determinant) > 1e-8f) {
                "colorMatrix1 must be invertible"
            }
            val inverse = 1f / determinant
            return floatArrayOf(
                (m[4] * m[8] - m[5] * m[7]) * inverse,
                (m[2] * m[7] - m[1] * m[8]) * inverse,
                (m[1] * m[5] - m[2] * m[4]) * inverse,
                (m[5] * m[6] - m[3] * m[8]) * inverse,
                (m[0] * m[8] - m[2] * m[6]) * inverse,
                (m[2] * m[3] - m[0] * m[5]) * inverse,
                (m[3] * m[7] - m[4] * m[6]) * inverse,
                (m[1] * m[6] - m[0] * m[7]) * inverse,
                (m[0] * m[4] - m[1] * m[3]) * inverse,
            )
        }

        private fun multiply3x3(left: FloatArray, right: FloatArray): FloatArray =
            FloatArray(9) { index ->
                val row = index / 3
                val column = index % 3
                left[row * 3] * right[column] +
                    left[row * 3 + 1] * right[3 + column] +
                    left[row * 3 + 2] * right[6 + column]
            }

        /* D50 XYZ to D65-adapted linear sRGB. */
        private val LINEAR_SRGB_FROM_XYZ_D50 = floatArrayOf(
            3.1341359f, -1.6173863f, -0.4906619f,
            -0.9787955f, 1.9162546f, 0.0334427f,
            0.0719554f, -0.2289768f, 1.4053860f,
        )
    }
}

data class VulkanCaptureSnapshot(
    val state: SpektraCaptureSnapshot,
    val frameSeed: FrameSeedSnapshot,
) {
    val revisionId: Long get() = state.revisionId

    companion object {
        fun from(
            state: SpektraCaptureSnapshot,
            metadata: RawCaptureMetadata,
        ): VulkanCaptureSnapshot = VulkanCaptureSnapshot(
            state,
            FrameSeedSnapshot.fromFrameNumber(metadata.frameNumber),
        )
    }
}
