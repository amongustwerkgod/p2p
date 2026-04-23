package com.example.Peerly;

import android.content.Context;
import android.util.Log;
import com.google.firebase.database.*;
import org.webrtc.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebRtcManager {
    private static final String TAG = "WebRtcManager";
    public static Map<String, DataChannel> dataChannels = new HashMap<>();

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private DataChannel localDataChannel;
    private DatabaseReference roomRef;
    private String roomId;
    private boolean isCaller;

    public interface MessageListener {
        void onMessageReceived(String message);
        void onStatusChanged(String status);
    }

    private MessageListener listener;

    public WebRtcManager(Context context, String roomId, boolean isCaller, MessageListener listener) {
        this.roomId = roomId;
        this.isCaller = isCaller;
        this.listener = listener;
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        initializePeerConnectionFactory(context);
        this.peerConnection = createPeerConnection();

        if (isCaller) {
            setupDataChannel();
            createOffer();
        } else {
            listenForOffer();
        }
        listenForIceCandidates();
    }

    private void initializePeerConnectionFactory(Context context) {
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();
    }

    private PeerConnection createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        // Important for data channels
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        return factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Map<String, Object> candidate = new HashMap<>();
                candidate.put("sdpMid", iceCandidate.sdpMid);
                candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                candidate.put("sdp", iceCandidate.sdp);
                roomRef.child("candidates").child(isCaller ? "caller" : "callee").push().setValue(candidate);
            }

            @Override public void onDataChannel(DataChannel dc) {
                Log.d(TAG, "New DataChannel received: " + dc.label());
                localDataChannel = dc;
                setupDataChannelObserver();
            }

            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                Log.d(TAG, "ICE State: " + state.name());
                listener.onStatusChanged("P2P: " + state.name());
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onAddStream(MediaStream stream) {}
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}
        });
    }

    private void setupDataChannel() {
        DataChannel.Init init = new DataChannel.Init();
        localDataChannel = peerConnection.createDataChannel("chat", init);
        setupDataChannelObserver();
    }

    private void setupDataChannelObserver() {
        localDataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] bytes = new byte[buffer.data.remaining()];
                buffer.data.get(bytes);
                String msg = new String(bytes, StandardCharsets.UTF_8);
                listener.onMessageReceived(msg);
            }
            @Override public void onBufferedAmountChange(long l) {}
            @Override public void onStateChange() {
                Log.d(TAG, "DataChannel State: " + localDataChannel.state().name());
                listener.onStatusChanged("Channel: " + localDataChannel.state().name());
            }
        });
        dataChannels.put(roomId, localDataChannel);
    }

    public void sendMessage(String message) {
        if (localDataChannel != null && localDataChannel.state() == DataChannel.State.OPEN) {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
            localDataChannel.send(new DataChannel.Buffer(buffer, false));
        } else {
            Log.e(TAG, "Cannot send: DataChannel is not open");
        }
    }

    private void createOffer() {
        peerConnection.createOffer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SdpObserverAdapter(), sdp);
                roomRef.child("offer").setValue(sdp.description);
                listenForAnswer();
            }
        }, new MediaConstraints());
    }

    private void listenForOffer() {
        roomRef.child("offer").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String sdpText = snapshot.getValue(String.class);
                if (sdpText != null && peerConnection.getRemoteDescription() == null) {
                    SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, sdpText);
                    peerConnection.setRemoteDescription(new SdpObserverAdapter() {
                        @Override public void onSetSuccess() { createAnswer(); }
                    }, sdp);
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void createAnswer() {
        peerConnection.createAnswer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                peerConnection.setLocalDescription(new SdpObserverAdapter(), sdp);
                roomRef.child("answer").setValue(sdp.description);
            }
        }, new MediaConstraints());
    }

    private void listenForAnswer() {
        roomRef.child("answer").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String sdpText = snapshot.getValue(String.class);
                if (sdpText != null && peerConnection.getRemoteDescription() == null) {
                    SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER, sdpText);
                    peerConnection.setRemoteDescription(new SdpObserverAdapter(), sdp);
                }
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void listenForIceCandidates() {
        roomRef.child("candidates").child(isCaller ? "callee" : "caller").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String s) {
                String sdp = snapshot.child("sdp").getValue(String.class);
                String sdpMid = snapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Integer.class);
                if (sdp != null && sdpMid != null && sdpMLineIndex != null) {
                    peerConnection.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, sdp));
                }
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    public void onDestroy() {
        roomRef.removeValue(); // Clean up signaling data when leaving
        if (peerConnection != null) peerConnection.dispose();
        if (factory != null) factory.dispose();
        dataChannels.remove(roomId);
    }
}
