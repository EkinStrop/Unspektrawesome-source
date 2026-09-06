package com.unspektrawesome.camera.session;

import android.hardware.HardwareBuffer;

import com.unspektrawesome.camera.RawFormat;
import com.unspektrawesome.camera.RawOutput;

import java.nio.ByteBuffer;
import java.util.Objects;

/** Borrowed frame view. Its buffers are valid only during the synchronous callback. */
public final class RawHardwareFrame {
    public final HardwareBuffer hardwareBuffer;
    public final ByteBuffer packedRawBytes;
    public final RawCaptureMetadata metadata;
    public final RawOutput output;
    public final int rowStrideBytes;

    RawHardwareFrame(HardwareBuffer hardwareBuffer, ByteBuffer packedRawBytes,
                     RawCaptureMetadata metadata, RawOutput output, int rowStrideBytes) {
        this.hardwareBuffer = Objects.requireNonNull(hardwareBuffer);
        this.packedRawBytes = packedRawBytes;
        this.metadata = Objects.requireNonNull(metadata);
        this.output = Objects.requireNonNull(output);
        if (rowStrideBytes <= 0) throw new IllegalArgumentException("RAW row stride must be positive");
        if (output.format == RawFormat.RAW_SENSOR && packedRawBytes != null) {
            throw new IllegalArgumentException("RAW_SENSOR must use its HardwareBuffer image");
        }
        if (output.format != RawFormat.RAW_SENSOR && packedRawBytes == null) {
            throw new IllegalArgumentException("Packed RAW requires plane bytes");
        }
        this.rowStrideBytes = rowStrideBytes;
    }
}
