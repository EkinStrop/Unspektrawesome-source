package com.unspektrawesome.camera.session;

public interface RawSessionListener {
    void onStateChanged(Camera2RawSession.State state);
    void onError(Camera2RawSession.SessionError error);
}
