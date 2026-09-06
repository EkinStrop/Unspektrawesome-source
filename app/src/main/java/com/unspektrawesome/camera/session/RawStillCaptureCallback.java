package com.unspektrawesome.camera.session;

public interface RawStillCaptureCallback {
    /** Called once with the full-resolution buffer still owned by its Image. */
    void onStillFrame(RawHardwareFrame frame);

    /** Called after resumePreview has restored the RAW repeating request. */
    void onPreviewResumed();

    /** Reports a recoverable still-capture failure. Preview can still be resumed. */
    void onStillCaptureError(Camera2RawSession.SessionError error);
}
