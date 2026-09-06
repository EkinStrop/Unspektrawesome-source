package com.unspektrawesome.camera.session;

public interface RawFrameConsumer {
    /** Import or retain the native buffer handle before this method returns. */
    void onRawFrame(RawHardwareFrame frame);
}
