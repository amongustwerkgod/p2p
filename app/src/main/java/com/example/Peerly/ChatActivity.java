package com.example.Peerly;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class ChatActivity extends AppCompatActivity {

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton, attachButton, muteButton, voiceNoteButton, callButton;
    private TextView typingIndicator, qualityIndicator;
    
    private String peerName, username, roomId;
    private WebRtcManager webRtcManager;

    private boolean isTyping = false;
    private Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable stopTypingRunnable = () -> sendTypingStatus(false);

    private Timer qualityTimer;
    private long lastPingTime = 0;

    private boolean isMuted = false;
    private MediaRecorder mediaRecorder;
    private String audioPath;
    private boolean isRecording = false;

    // Call UI & Audio
    private View callOverlay;
    private TextView callStatus, callPeerName, callPeerAvatar;
    private ImageButton acceptCallBtn, rejectCallBtn;
    private long callStartTime = 0;
    private Handler callTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable callTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (callStartTime > 0) {
                long seconds = (SystemClock.elapsedRealtime() - callStartTime) / 1000;
                callStatus.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                callTimerHandler.postDelayed(this, 1000);
            }
        }
    };

    private MediaPlayer ringtonePlayer;
    private Vibrator vibrator;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) sendImage(uri);
            });

    private final ActivityResultLauncher<String> requestAudioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startRecording();
            });

    private final ActivityResultLauncher<String[]> requestCallPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean audioGranted = result.containsKey(Manifest.permission.RECORD_AUDIO) && result.get(Manifest.permission.RECORD_AUDIO);
                if (audioGranted) {
                    performCall();
                } else {
                    Toast.makeText(this, "Audio permission required for calls", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestStoragePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickImageLauncher.launch("image/*");
                else Toast.makeText(this, "Storage permission denied.", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(0, top, 0, Math.max(bottom, ime));
            return insets;
        });

        username = getIntent().getStringExtra("username");
        peerName = getIntent().getStringExtra("peerName"); 
        roomId = getIntent().getStringExtra("roomId");

        if (username == null) username = "User_" + System.currentTimeMillis() % 1000;
        if (roomId == null) roomId = "default-room";

        TextView peerNameLabel = findViewById(R.id.peerNameLabel);
        TextView peerAvatar    = findViewById(R.id.peerAvatar);
        typingIndicator = findViewById(R.id.typingIndicator);
        qualityIndicator = findViewById(R.id.qualityIndicator);
        muteButton = findViewById(R.id.muteButton);
        voiceNoteButton = findViewById(R.id.voiceNoteButton);
        callButton = findViewById(R.id.callButton);
        
        peerNameLabel.setText(peerName != null ? peerName : "Waiting...");
        peerAvatar.setText(peerName != null ? String.valueOf(peerName.charAt(0)).toUpperCase() : "?");

        // Initialize Call UI
        callOverlay = findViewById(R.id.callOverlay);
        callStatus = findViewById(R.id.callStatus);
        callPeerName = findViewById(R.id.callPeerName);
        callPeerAvatar = findViewById(R.id.callPeerAvatar);
        acceptCallBtn = findViewById(R.id.acceptCallBtn);
        rejectCallBtn = findViewById(R.id.rejectCallBtn);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        adapter = new MessageAdapter(username);
        adapter.setOnMessageInteractionListener(new MessageAdapter.OnMessageInteractionListener() {
            @Override public void onMessageLongClick(Message message, int position) { showReactionDialog(message); }
            @Override public void onReactionClick(Message message, int position) { showReactionDialog(message); }
        });

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {
                recyclerView.postDelayed(() -> {
                    if (adapter.getItemCount() > 0) recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
                }, 100);
            }
        });

        messageInput = findViewById(R.id.messageInput);
        sendButton   = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                sendButton.setEnabled(!s.toString().trim().isEmpty());
                if (!isTyping) { isTyping = true; sendTypingStatus(true); }
                typingHandler.removeCallbacks(stopTypingRunnable);
                typingHandler.postDelayed(stopTypingRunnable, 2000);
            }
            @Override public void afterTextChanged(Editable s){}
        });

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN && !event.isShiftPressed())) {
                sendMessage(); return true;
            }
            return false;
        });

        sendButton.setOnClickListener(v -> sendMessage());
        attachButton.setOnClickListener(v -> checkStoragePermissionAndPickImage());
        muteButton.setOnClickListener(v -> toggleMute());
        
        voiceNoteButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) checkPermissionAndRecord();
            else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) stopRecordingAndSend();
            return true;
        });

        callButton.setOnClickListener(v -> checkCallPermissionsAndStart());

        webRtcManager = new WebRtcManager(this, roomId, username, new WebRtcManager.MessageListener() {
            @Override public void onMessageReceived(String sender, String message) { runOnUiThread(() -> handleIncomingData(sender, message)); }
            @Override public void onStatusChanged(String status) { runOnUiThread(() -> Log.d("WebRTC", status)); }
            @Override public void onPeersChanged(List<String> peers) {
                runOnUiThread(() -> {
                    if (peers.size() > 1) { peerNameLabel.setText(roomId); peerAvatar.setText("G"); }
                    else if (peers.size() == 1) {
                        peerName = peers.get(0);
                        peerNameLabel.setText(peerName);
                        peerAvatar.setText(String.valueOf(peerName.charAt(0)).toUpperCase());
                    } else { peerNameLabel.setText("Waiting..."); peerAvatar.setText("?"); }
                });
            }
            @Override public void onIncomingCall(String fromPeer) { runOnUiThread(() -> showIncomingCallOverlay(fromPeer)); }
            @Override public void onCallAccepted(String fromPeer) { runOnUiThread(() -> onCallConnected(fromPeer)); }
            @Override public void onCallRejected(String fromPeer) { runOnUiThread(() -> onCallEnded("Rejected by " + fromPeer)); }
        });

        startNetworkMonitoring();
        setupAudioManager();
    }

    private void setupAudioManager() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);
    }

    private void startRinging(boolean isIncoming) {
        stopRinging();
        try {
            Uri alert = isIncoming 
                ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE); // Could use a different sound for outgoing
            
            ringtonePlayer = new MediaPlayer();
            ringtonePlayer.setDataSource(this, alert);
            ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
            ringtonePlayer.setLooping(true);
            ringtonePlayer.prepare();
            ringtonePlayer.start();

            if (isIncoming && vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 1000}, 0));
                } else {
                    vibrator.vibrate(new long[]{0, 1000, 1000}, 0);
                }
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "Error playing ringtone", e);
        }
    }

    private void stopRinging() {
        if (ringtonePlayer != null) {
            try {
                ringtonePlayer.stop();
                ringtonePlayer.release();
            } catch (Exception ignored) {}
            ringtonePlayer = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private void checkCallPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestCallPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
        } else {
            performCall();
        }
    }

    private void performCall() {
        if (peerName == null || peerName.equals("Waiting...")) {
            Toast.makeText(this, "No peer to call", Toast.LENGTH_SHORT).show();
            return;
        }
        showCallOverlay(peerName, "Calling...");
        startRinging(false);
        acceptCallBtn.setVisibility(View.GONE);
        rejectCallBtn.setOnClickListener(v -> {
            webRtcManager.sendToPeer(peerName, "CALL_REJECT");
            onCallEnded("Call Cancelled");
        });
        webRtcManager.sendToPeer(peerName, "CALL_INVITE");
    }

    private void showIncomingCallOverlay(String fromPeer) {
        showCallOverlay(fromPeer, "Incoming Call...");
        startRinging(true);
        acceptCallBtn.setVisibility(View.VISIBLE);
        acceptCallBtn.setOnClickListener(v -> {
            webRtcManager.sendToPeer(fromPeer, "CALL_ACCEPT");
            webRtcManager.enableAudioForPeer(fromPeer);
            onCallConnected(fromPeer);
        });
        rejectCallBtn.setOnClickListener(v -> {
            webRtcManager.sendToPeer(fromPeer, "CALL_REJECT");
            onCallEnded("Call Rejected");
        });
    }

    private void showCallOverlay(String name, String status) {
        callOverlay.setVisibility(View.VISIBLE);
        callPeerName.setText(name);
        callPeerAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
        callStatus.setText(status);
    }

    private void onCallConnected(String peer) {
        stopRinging();
        webRtcManager.enableAudioForPeer(peer);
        callStartTime = SystemClock.elapsedRealtime();
        callStatus.setText("00:00");
        callTimerHandler.post(callTimerRunnable);
        acceptCallBtn.setVisibility(View.GONE);
        rejectCallBtn.setOnClickListener(v -> {
            webRtcManager.sendToPeer(peer, "CALL_REJECT");
            onCallEnded("Call Ended");
        });
    }

    private void onCallEnded(String reason) {
        stopRinging();
        callOverlay.setVisibility(View.GONE);
        callStartTime = 0;
        callTimerHandler.removeCallbacks(callTimerRunnable);
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    private void checkStoragePermissionAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                pickImageLauncher.launch("image/*");
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                pickImageLauncher.launch("image/*");
            }
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        webRtcManager.setMute(isMuted);
        muteButton.setColorFilter(isMuted ? 0xFFFF4444 : 0xFF7070FF);
        Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        else startRecording();
    }

    private void startRecording() {
        audioPath = getCacheDir().getAbsolutePath() + "/voice_note_" + System.currentTimeMillis() + ".3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(audioPath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try { mediaRecorder.prepare(); mediaRecorder.start(); isRecording = true; voiceNoteButton.setColorFilter(0xFFFF4444); }
        catch (IOException e) { Log.e("ChatActivity", "Recorder failed", e); }
    }

    private void stopRecordingAndSend() {
        if (!isRecording) return;
        try { mediaRecorder.stop(); mediaRecorder.release(); } catch (Exception e) {}
        mediaRecorder = null; isRecording = false;
        voiceNoteButton.setColorFilter(0xFF7070FF);
        
        File audioFile = new File(audioPath);
        Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", audioFile);
        sendVoiceNote(contentUri);
    }

    private void sendVoiceNote(Uri uri) {
        String msgId = UUID.randomUUID().toString();
        adapter.addMessage(new Message(msgId, username, null, uri, Message.Type.AUDIO, true, System.currentTimeMillis()));
        scrollToBottom();
        
        new Thread(() -> {
            String base64 = uriToBase64(uri, false);
            if (base64 != null && webRtcManager != null) {
                runOnUiThread(() -> webRtcManager.broadcastMessage("AUD:" + msgId + ":" + base64));
            }
        }).start();
    }

    private void handleIncomingData(String sender, String data) {
        if (data.startsWith("TYP:")) {
            boolean peerIsTyping = data.substring(4).equals("ON");
            typingIndicator.setText(sender + " is typing...");
            typingIndicator.setVisibility(peerIsTyping ? View.VISIBLE : View.GONE);
        } else if (data.startsWith("AUD:")) {
            String[] parts = data.split(":", 3);
            if (parts.length >= 3) {
                String msgId = parts[1];
                String base64 = parts[2];
                new Thread(() -> {
                    Uri localUri = base64ToUri(base64, "aud_" + msgId + ".3gp");
                    if (localUri != null) {
                        runOnUiThread(() -> {
                            adapter.addMessage(new Message(msgId, sender, null, localUri, Message.Type.AUDIO, false, System.currentTimeMillis()));
                            scrollToBottom();
                        });
                    }
                }).start();
            }
        } else if (data.startsWith("PNG:")) { if (webRtcManager != null) webRtcManager.broadcastMessage("POG:" + data.substring(4)); }
        else if (data.startsWith("POG:")) { updateQualityUI(System.currentTimeMillis() - Long.parseLong(data.substring(4))); }
        else if (data.startsWith("IMG:")) {
            String[] parts = data.split(":", 3);
            if (parts.length >= 3) {
                String msgId = parts[1];
                String base64 = parts[2];
                new Thread(() -> {
                    Uri localUri = base64ToUri(base64, "img_" + msgId + ".jpg");
                    if (localUri != null) {
                        runOnUiThread(() -> {
                            adapter.addMessage(new Message(msgId, sender, null, localUri, Message.Type.IMAGE, false, System.currentTimeMillis()));
                            scrollToBottom();
                        });
                    }
                }).start();
            }
        } else if (data.startsWith("REA:")) {
            String[] parts = data.split(":", 3);
            if (parts.length >= 3) {
                String emoji = parts[1];
                String msgId = parts[2];
                updateLocalReaction(msgId, sender, emoji);
            }
        } else if (data.equals("CALL_REJECT")) {
            runOnUiThread(() -> onCallEnded("Call Ended"));
        } else {
            String msgId = UUID.randomUUID().toString();
            adapter.addMessage(new Message(msgId, sender, data, null, Message.Type.TEXT, false, System.currentTimeMillis()));
            scrollToBottom();
        }
    }

    private void showReactionDialog(Message message) {
        String[] emojis = {"❤️", "😂", "😮", "🔥", "👍", "👎"};
        new AlertDialog.Builder(this).setTitle("React").setItems(emojis, (dialog, which) -> {
            String emoji = emojis[which];
            message.reactions.put(username, emoji);
            adapter.notifyDataSetChanged();
            if (webRtcManager != null) webRtcManager.broadcastMessage("REA:" + emoji + ":" + message.key);
        }).show();
    }

    private void updateLocalReaction(String msgId, String sender, String emoji) {
        int pos = adapter.getPositionByMsgId(msgId);
        if (pos != -1) {
            Message m = adapter.messages.get(pos);
            m.reactions.put(sender, emoji);
            adapter.notifyItemChanged(pos);
        }
    }

    private void sendTypingStatus(boolean typing) { if (webRtcManager != null) webRtcManager.broadcastMessage("TYP:" + (typing ? "ON" : "OFF")); }

    private void startNetworkMonitoring() {
        qualityTimer = new Timer();
        qualityTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() { if (webRtcManager != null) { lastPingTime = System.currentTimeMillis(); webRtcManager.broadcastMessage("PNG:" + lastPingTime); } }
        }, 1000, 5000);
    }

    private void updateQualityUI(long rtt) {
        String color = "#40D080"; String status = "Excellent";
        if (rtt > 150) { color = "#FFC107"; status = "Fair"; }
        if (rtt > 300) { color = "#F44336"; status = "Poor"; }
        qualityIndicator.setText(rtt + "ms (" + status + ")");
        qualityIndicator.setTextColor(android.graphics.Color.parseColor(color));
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        String msgId = UUID.randomUUID().toString();
        adapter.addMessage(new Message(msgId, username, text, null, Message.Type.TEXT, true, System.currentTimeMillis()));
        scrollToBottom();
        messageInput.setText("");
        if (webRtcManager != null) webRtcManager.broadcastMessage(text);
    }

    private void sendImage(Uri uri) {
        String msgId = UUID.randomUUID().toString();
        adapter.addMessage(new Message(msgId, username, null, uri, Message.Type.IMAGE, true, System.currentTimeMillis()));
        scrollToBottom();
        
        new Thread(() -> {
            String base64 = uriToBase64(uri, true);
            if (base64 != null && webRtcManager != null) {
                runOnUiThread(() -> webRtcManager.broadcastMessage("IMG:" + msgId + ":" + base64));
            }
        }).start();
    }

    private void scrollToBottom() { recyclerView.post(() -> { if (adapter.getItemCount() > 0) recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1); }); }

    private String uriToBase64(Uri uri, boolean isImage) {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            
            byte[] bytes;
            if (isImage) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2; // Pre-scale to save memory
                Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                if (bitmap == null) return null;
                
                int maxSide = 400; // Aggressive resize for DataChannel
                if (bitmap.getWidth() > maxSide || bitmap.getHeight() > maxSide) {
                    float scale = Math.min((float)maxSide / bitmap.getWidth(), (float)maxSide / bitmap.getHeight());
                    bitmap = Bitmap.createScaledBitmap(bitmap, (int)(bitmap.getWidth()*scale), (int)(bitmap.getHeight()*scale), true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                bytes = baos.toByteArray();
                
                if (bytes.length > 30000) { // Safety limit for SCTP DataChannel
                   baos.reset();
                   bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos);
                   bytes = baos.toByteArray();
                }
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                bytes = baos.toByteArray();
            }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("ChatActivity", "Error converting uri to base64", e);
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    private Uri base64ToUri(String base64, String fileName) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            File cacheDir = getCacheDir();
            if (cacheDir == null) return null;
            File file = new File(cacheDir, fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();
            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        } catch (Exception e) {
            Log.e("ChatActivity", "Error converting base64 to uri", e);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRinging();
        if (callStartTime > 0) {
            long durationSeconds = (SystemClock.elapsedRealtime() - callStartTime) / 1000;
            Log.d("ChatActivity", String.format(Locale.getDefault(), "Call ended. Duration: %d:%02d", durationSeconds / 60, durationSeconds % 60));
        }
        if (qualityTimer != null) qualityTimer.cancel();
        if (webRtcManager != null) webRtcManager.onDestroy();
    }
}
