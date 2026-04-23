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
import java.util.concurrent.ConcurrentHashMap;

public class WebRtcManager {
    private static final String TAG = "WebRtcManager";
    
    private PeerConnectionFactory factory;
    private final Map<String, PeerConnection> peerConnections = new ConcurrentHashMap<>();
    private final Map<String, DataChannel> dataChannels = new ConcurrentHashMap<>();
    private final List<String> activePeers = new ArrayList<>();
    
    private DatabaseReference roomRef;
    private String roomId;
    private String myId;

    public interface MessageListener {
        void onMessageReceived(String sender, String message);
        void onStatusChanged(String status);
        void onPeersChanged(List<String> peers);
    }

    private MessageListener listener;

    public WebRtcManager(Context context, String roomId, String myId, MessageListener listener) {
        this.roomId = roomId;
        this.myId = myId;
        this.listener = listener;
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);

        initializePeerConnectionFactory(context);
        
        // Register myself in the room
        roomRef.child("members").child(myId).setValue(true);
        roomRef.child("members").child(myId).onDisconnect().removeValue();

        // Listen for other members
        roomRef.child("members").addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String s) {
                String peerId = snapshot.getKey();
                if (peerId != null && !peerId.equals(myId)) {
                    if (!activePeers.contains(peerId)) {
                        activePeers.add(peerId);
                        listener.onPeersChanged(new ArrayList<>(activePeers));
                    }
                    // Partial Mesh Logic: Connect if myId < peerId (lexicographical)
                    if (myId.compareTo(peerId) < 0) {
                        initiateConnection(peerId);
                    }
                }
            }
            @Override public void onChildRemoved(DataSnapshot s) {
                String peerId = s.getKey();
                if (peerId != null) {
                    activePeers.remove(peerId);
                    listener.onPeersChanged(new ArrayList<>(activePeers));
                    closeConnection(peerId);
                }
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });

        listenForIncomingSignals();
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

    private void initiateConnection(String peerId) {
        PeerConnection pc = createPeerConnection(peerId);
        peerConnections.put(peerId, pc);
        
        DataChannel.Init init = new DataChannel.Init();
        DataChannel dc = pc.createDataChannel("chat", init);
        setupDataChannel(peerId, dc);
        
        pc.createOffer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SdpObserverAdapter(), sdp);
                sendSignal(peerId, "offer", sdp.description);
            }
        }, new MediaConstraints());
    }

    private PeerConnection createPeerConnection(final String peerId) {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        return factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Map<String, Object> candidate = new HashMap<>();
                candidate.put("sdpMid", iceCandidate.sdpMid);
                candidate.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                candidate.put("sdp", iceCandidate.sdp);
                roomRef.child("signals").child(peerId).child(myId).child("candidates").push().setValue(candidate);
            }

            @Override public void onDataChannel(DataChannel dc) {
                setupDataChannel(peerId, dc);
            }

            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
                listener.onStatusChanged("Peer " + peerId + ": " + state.name());
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onAddStream(MediaStream stream) {}
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver r, MediaStream[] s) {}
        });
    }

    private void setupDataChannel(String peerId, DataChannel dc) {
        dc.registerObserver(new DataChannel.Observer() {
            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                byte[] bytes = new byte[buffer.data.remaining()];
                buffer.data.get(bytes);
                String msg = new String(bytes, StandardCharsets.UTF_8);
                listener.onMessageReceived(peerId, msg);
            }
            @Override public void onBufferedAmountChange(long l) {}
            @Override public void onStateChange() {
                Log.d(TAG, "DC State with " + peerId + ": " + dc.state().name());
            }
        });
        dataChannels.put(peerId, dc);
    }

    private void listenForIncomingSignals() {
        roomRef.child("signals").child(myId).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String s) {
                String fromPeerId = snapshot.getKey();
                if (fromPeerId == null) return;
                
                // Handle Offer
                String offer = snapshot.child("offer").getValue(String.class);
                if (offer != null) {
                    handleOffer(fromPeerId, offer);
                }
                
                // Handle Candidates
                snapshot.child("candidates").getRef().addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot snap, String s) {
                        PeerConnection pc = peerConnections.get(fromPeerId);
                        if (pc != null) {
                            String sdp = snap.child("sdp").getValue(String.class);
                            String mid = snap.child("sdpMid").getValue(String.class);
                            Integer idx = snap.child("sdpMLineIndex").getValue(Integer.class);
                            if (sdp != null) pc.addIceCandidate(new IceCandidate(mid, idx, sdp));
                        }
                    }
                    @Override public void onChildChanged(DataSnapshot s, String p) {}
                    @Override public void onChildRemoved(DataSnapshot s) {}
                    @Override public void onChildMoved(DataSnapshot s, String p) {}
                    @Override public void onCancelled(DatabaseError e) {}
                });
            }
            @Override public void onChildChanged(DataSnapshot snapshot, String s) {
                // Handle Answer
                String answer = snapshot.child("answer").getValue(String.class);
                if (answer != null) {
                    PeerConnection pc = peerConnections.get(snapshot.getKey());
                    if (pc != null) pc.setRemoteDescription(new SdpObserverAdapter(), new SessionDescription(SessionDescription.Type.ANSWER, answer));
                }
            }
            @Override public void onChildRemoved(DataSnapshot s) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void handleOffer(String peerId, String sdpText) {
        PeerConnection pc = createPeerConnection(peerId);
        peerConnections.put(peerId, pc);
        pc.setRemoteDescription(new SdpObserverAdapter(), new SessionDescription(SessionDescription.Type.OFFER, sdpText));
        pc.createAnswer(new SdpObserverAdapter() {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SdpObserverAdapter(), sdp);
                sendSignal(peerId, "answer", sdp.description);
            }
        }, new MediaConstraints());
    }

    private void sendSignal(String toPeerId, String type, String data) {
        roomRef.child("signals").child(toPeerId).child(myId).child(type).setValue(data);
    }

    public void broadcastMessage(String message) {
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8));
        DataChannel.Buffer dcBuffer = new DataChannel.Buffer(buffer, false);
        for (DataChannel dc : dataChannels.values()) {
            if (dc.state() == DataChannel.State.OPEN) {
                dc.send(dcBuffer);
                buffer.rewind();
            }
        }
    }

    private void closeConnection(String peerId) {
        if (peerId == null) return;
        PeerConnection pc = peerConnections.remove(peerId);
        if (pc != null) pc.dispose();
        dataChannels.remove(peerId);
    }

    public void onDestroy() {
        roomRef.child("members").child(myId).removeValue();
        roomRef.child("signals").child(myId).removeValue();
        for (PeerConnection pc : peerConnections.values()) pc.dispose();
        peerConnections.clear();
        dataChannels.clear();
        if (factory != null) factory.dispose();
    }
}
