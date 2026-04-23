package com.example.Peerly;

import android.util.Log;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * A helper class to avoid boilerplate when implementing SdpObserver.
 */
public class SdpObserverAdapter implements SdpObserver {
    private static final String TAG = "SdpObserver";

    @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
    @Override public void onSetSuccess() {}
    @Override public void onCreateFailure(String s) { Log.e(TAG, "SDP Create Failure: " + s); }
    @Override public void onSetFailure(String s) { Log.e(TAG, "SDP Set Failure: " + s); }
}
