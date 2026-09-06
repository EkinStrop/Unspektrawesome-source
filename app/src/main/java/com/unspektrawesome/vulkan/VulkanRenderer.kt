package com.unspektrawesome.vulkan

import android.hardware.HardwareBuffer
import android.view.Surface
import java.nio.ByteBuffer

/** Lifecycle-safe owner of one native Vulkan renderer instance. */
class VulkanRenderer : AutoCloseable {
    private var handle: Long = nativeCreate().also {
        check(it != 0L) { "Native Vulkan renderer creation failed" }
    }

    @Synchronized
    fun setSurface(surface: Surface?): Boolean = withHandle { nativeSetSurface(it, surface) }

    @Synchronized
    fun render(
        hardwareBuffer: HardwareBuffer,
        packedRawBytes: ByteBuffer?,
        rawParameters: ByteBuffer,
        rawColorParameters: ByteBuffer,
        geometryParameters: ByteBuffer,
        spektraFilmParameters: ByteBuffer,
        lensShadingMap: ByteBuffer?,
        processingWidth: Int,
        processingHeight: Int,
    ): Boolean = withHandle { current ->
        require(rawParameters.isDirect) { "RAW parameters must be direct" }
        require(packedRawBytes == null || packedRawBytes.isDirect) {
            "Packed RAW bytes must be direct"
        }
        require(rawColorParameters.isDirect) { "RAW color parameters must be direct" }
        require(geometryParameters.isDirect) { "Geometry parameters must be direct" }
        require(spektraFilmParameters.isDirect) { "SpektraFilm parameters must be direct" }
        require(lensShadingMap == null || lensShadingMap.isDirect) {
            "Lens-shading parameters must be direct"
        }
        nativeRender(
            current,
            hardwareBuffer,
            packedRawBytes,
            rawParameters,
            rawColorParameters,
            geometryParameters,
            spektraFilmParameters,
            lensShadingMap,
            processingWidth,
            processingHeight,
        )
    }

    @Synchronized
    fun captureRcd(
        hardwareBuffer: HardwareBuffer,
        packedRawBytes: ByteBuffer?,
        rawParameters: ByteBuffer,
        rawColorParameters: ByteBuffer,
        geometryParameters: ByteBuffer,
        spektraFilmParameters: ByteBuffer,
        lensShadingMap: ByteBuffer?,
        rgbaOutput: ByteBuffer,
        spektraTimeSeconds: Double,
    ): Boolean = withHandle { current ->
        require(rawParameters.isDirect) { "RAW parameters must be direct" }
        require(packedRawBytes == null || packedRawBytes.isDirect) {
            "Packed RAW bytes must be direct"
        }
        require(rawColorParameters.isDirect) { "RAW color parameters must be direct" }
        require(geometryParameters.isDirect) { "Geometry parameters must be direct" }
        require(spektraFilmParameters.isDirect) { "SpektraFilm parameters must be direct" }
        require(lensShadingMap == null || lensShadingMap.isDirect) {
            "Lens-shading parameters must be direct"
        }
        require(rgbaOutput.isDirect) { "RGBA capture output must be direct" }
        nativeCaptureRcd(
            current,
            hardwareBuffer,
            packedRawBytes,
            rawParameters,
            rawColorParameters,
            geometryParameters,
            spektraFilmParameters,
            lensShadingMap,
            rgbaOutput,
            spektraTimeSeconds,
        )
    }

    @Synchronized
    fun diagnostics(): String = if (handle == 0L) {
        "Renderer is released"
    } else {
        nativeGetDiagnosticString(handle)
    }

    @Synchronized
    override fun close() {
        val current = handle
        if (current == 0L) return
        handle = 0L
        nativeRelease(current)
    }

    private inline fun <T> withHandle(block: (Long) -> T): T {
        check(handle != 0L) { "Renderer is released" }
        return block(handle)
    }

    private external fun nativeCreate(): Long
    private external fun nativeSetSurface(handle: Long, surface: Surface?): Boolean
    private external fun nativeRender(
        handle: Long,
        hardwareBuffer: HardwareBuffer,
        packedRawBytes: ByteBuffer?,
        rawParameters: ByteBuffer,
        rawColorParameters: ByteBuffer,
        geometryParameters: ByteBuffer,
        spektraFilmParameters: ByteBuffer,
        lensShadingMap: ByteBuffer?,
        processingWidth: Int,
        processingHeight: Int,
    ): Boolean
    private external fun nativeGetDiagnosticString(handle: Long): String
    private external fun nativeCaptureRcd(
        handle: Long,
        hardwareBuffer: HardwareBuffer,
        packedRawBytes: ByteBuffer?,
        rawParameters: ByteBuffer,
        rawColorParameters: ByteBuffer,
        geometryParameters: ByteBuffer,
        spektraFilmParameters: ByteBuffer,
        lensShadingMap: ByteBuffer?,
        rgbaOutput: ByteBuffer,
        spektraTimeSeconds: Double,
    ): Boolean
    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("unspektrawesome_vulkan")
        }
    }
}
