package com.unspektrawesome.preview

import com.unspektrawesome.camera.RawFormat

/** Camera RAW formats that native Vulkan probes against each delivered buffer. */
object VulkanRawImportPolicy {
    val candidateFormats: Set<RawFormat> = RawFormat.entries.toSet()
}
