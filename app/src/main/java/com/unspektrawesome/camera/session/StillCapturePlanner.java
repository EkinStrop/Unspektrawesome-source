package com.unspektrawesome.camera.session;

import com.unspektrawesome.camera.RawFormat;
import com.unspektrawesome.camera.RawOutput;
import com.unspektrawesome.camera.RawPreviewPolicy;

import java.util.List;
import java.util.Set;

/** Pure full-resolution output selection and capture-state validation. */
final class StillCapturePlanner {
    private StillCapturePlanner() {}

    static RawOutput select(Camera2RawSession.State state, List<RawOutput> outputs,
                            Set<RawFormat> importableFormats) {
        if (state != Camera2RawSession.State.STREAMING) {
            throw new IllegalStateException("Still capture requires an active RAW preview");
        }
        return RawPreviewPolicy.selectCaptureOutput(outputs, importableFormats);
    }

    static boolean canResume(Camera2RawSession.State state) {
        return state == Camera2RawSession.State.STILL_CAPTURED
                || state == Camera2RawSession.State.STILL_FAILED;
    }
}
