package com.unspektrawesome.capture

data class CaptureMetadata(
    val sensorTimestampNs: Long,
    val exposureTimeNs: Long?,
    val sensitivityIso: Int?,
    val focalLengthMm: Float?,
    val lensModel: String,
)
