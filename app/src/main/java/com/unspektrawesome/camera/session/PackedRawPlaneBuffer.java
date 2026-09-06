package com.unspektrawesome.camera.session;

import java.nio.ByteBuffer;
import java.util.Objects;

final class PackedRawPlaneBuffer {
    private PackedRawPlaneBuffer() {}

    static ByteBuffer borrow(ByteBuffer plane, int rowStrideBytes, int height) {
        Objects.requireNonNull(plane, "Packed RAW plane is null");
        if (!plane.isDirect()) {
            throw new IllegalArgumentException("Packed RAW plane must be a direct ByteBuffer");
        }
        if (rowStrideBytes <= 0 || height <= 0) {
            throw new IllegalArgumentException("Packed RAW plane dimensions must be positive");
        }
        long required = (long) rowStrideBytes * height;
        if (required > Integer.MAX_VALUE || plane.remaining() < required) {
            throw new IllegalArgumentException(
                    "Packed RAW plane is smaller than row stride times height");
        }
        ByteBuffer view = plane.slice();
        view.limit((int) required);
        return view.slice().order(plane.order());
    }
}
