package com.unspektrawesome.vulkan

import android.hardware.HardwareBuffer

object VulkanBridge {
    fun probe(hardwareBuffer: HardwareBuffer? = null): String = nativeProbeVulkan(hardwareBuffer)

    fun diagnostics(): String = nativeGetDiagnosticString()

    private external fun nativeProbeVulkan(hardwareBuffer: HardwareBuffer?): String
    private external fun nativeGetDiagnosticString(): String

    init {
        System.loadLibrary("unspektrawesome_vulkan")
    }
}
