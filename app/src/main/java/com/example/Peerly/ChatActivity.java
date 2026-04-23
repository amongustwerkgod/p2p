package com.example.Peerly;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ChatActivity extends AppCompatActivity {

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton attachButton;
    private ImageButton muteButton;
    private ImageButton voiceNoteButton;
    private TextView typingIndicator;
    private TextView qualityIndicator;
    
    private String peerName;
    private String username;
    private String roomId;
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

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) sendImage(uri);
            });

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) startRecording();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_chat);

        username = getIntent().getStringExtra("username");
        peerName = getIntent().getStringExtra("peerName"); 
        roomId = getIntent().getStringExtra("roomId");

        if (username == null) username = "User_" + System.currentTimeMillis() % 1000;
        if (roomId == null) roomId = "default-room";

        // UI Refs
        TextView peerNameLabel = findViewById(R.id.peerNameLabel);
        TextView peerAvatar    = findViewById(R.id.peerAvatar);
        typingIndicator = findViewById(R.id.typingIndicator);
        qualityIndicator = findViewById(R.id.qualityIndicator);
        muteButton = findViewById(R.id.muteButton);
        voiceNoteButton = findViewById(R.id.voiceNoteButton);
        
        peerNameLabel.setText("Waiting...");
        peerAvatar.setText("?");

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        adapter = new MessageAdapter(username);
        
        adapter.setOnMessageInteractionListener(new MessageAdapter.OnMessageInteractionListener() {
            @Override
            public void onMessageLongClick(Message message, int position) {
                showReactionDialog(message);
            }
            @Override
            public void onReactionClick(Message message, int position) {
                showReactionDialog(message);
            }
        });

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(adapter);

        // Input
        messageInput = findViewById(R.id.messageInput);
        sendButton   = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                sendButton.setEnabled(!s.toString().trim().isEmpty());
                if (!isTyping) {
                    isTyping = true;
                    sendTypingStatus(true);
                }
                typingHandler.removeCallbacks(stopTypingRunnable);
                typingHandler.postDelayed(stopTypingRunnable, 2000);
            }
            @Override public void afterTextChanged(Editable s){}
        });

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                 && event.getAction() == KeyEvent.ACTION_DOWN
                 && !event.isShiftPressed())) {
                sendMessage();
                return true;
            }
            return false;
        });

        sendButton.setOnClickListener(v -> sendMessage());
        attachButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        
        muteButton.setOnClickListener(v -> toggleMute());
        
        voiceNoteButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                checkPermissionAndRecord();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopRecordingAndSend();
            }
            return true;
        });

        // WebRTC Mesh Setup
        webRtcManager = new WebRtcManager(this, roomId, username, new WebRtcManager.MessageListener() {
            @Override
            public void onMessageReceived(String sender, String message) {
                runOnUiThread(() -> handleIncomingData(sender, message));
            }
            @Override
            public void onStatusChanged(String status) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, status, Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onPeersChanged(List<String> peers) {
                runOnUiThread(() -> {
                    if (peers.size() > 1) {
                        peerNameLabel.setText(roomId);
                        peerAvatar.setText("G");
                    } else if (peers.size() == 1) {
                        String name = peers.get(0);
                        peerNameLabel.setText(name);
                        peerAvatar.setText(String.valueOf(name.charAt(0)).toUpperCase());
                    } else {
                        peerNameLabel.setText("Waiting...");
                        peerAvatar.setText("?");
                    }
                });
            }
        });

        startNetworkMonitoring();
    }

    private void toggleMute() {
        isMuted = !isMuted;
        webRtcManager.setMute(isMuted);
        muteButton.setImageResource(isMuted ? R.drawable.ic_mic : R.drawable.ic_mic); // Add mic_off if you have it
        muteButton.setColorFilter(isMuted ? 0xFFFF4444 : 0xFF7070FF);
        Toast.makeText(this, isMuted ? "Muted" : "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        audioPath = getExternalCacheDir().getAbsolutePath() + "/voice_note.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(audioPath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            voiceNoteButton.setColorFilter(0xFFFF4444);
        } catch (IOException e) {
            Log.e("ChatActivity", "Recorder failed", e);
        }
    }

    private void stopRecordingAndSend() {
        if (!isRecording) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
        } catch (Exception e) {}
        mediaRecorder = null;
        isRecording = false;
        voiceNoteButton.setColorFilter(0xFF7070FF);
        
        Uri uri = Uri.fromFile(new File(audioPath));
        sendVoiceNote(uri);
    }

    private void sendVoiceNote(Uri uri) {
        String uriString = uri.toString();
        adapter.addMessage(new Message(username, uriString, Message.Type.AUDIO, true));
        scrollToBottom();
        if (webRtcManager != null) webRtcManager.broadcastMessage("AUD:" + uriString);
    }

    private void handleIncomingData(String sender, String data) {
        if (data.startsWith("TYP:")) {
            boolean peerIsTyping = data.substring(4).equals("ON");
            typingIndicator.setText(sender + " is typing...");
            typingIndicator.setVisibility(peerIsTyping ? View.VISIBLE : View.GONE);
        } else if (data.startsWith("AUD:")) {
            adapter.addMessage(new Message(sender, data.substring(4), Message.Type.AUDIO, false));
            scrollToBottom();
        } else if (data.startsWith("PNG:")) {
            webRtcManager.broadcastMessage("POG:" + data.substring(4));
        } else if (data.startsWith("POG:")) {
            long rtt = System.currentTimeMillis() - Long.parseLong(data.substring(4));
            updateQualityUI(rtt);
        } else if (data.startsWith("IMG:")) {
            adapter.addMessage(new Message(sender, data.substring(4), Message.Type.IMAGE, false));
            scrollToBottom();
        } else {
            adapter.addMessage(new Message(sender, data, false));
            scrollToBottom();
        }
    }

    private void showReactionDialog(Message message) {
        String[] emojis = {"❤️", "😂", "😮", "🔥", "👍", "👎"};
        new AlertDialog.Builder(this)
                .setTitle("React")
                .setItems(emojis, (dialog, which) -> {
                    String emoji = emojis[which];
                    message.reactions.put(username, emoji);
                    adapter.notifyDataSetChanged();
                    if (webRtcManager != null) {
                        webRtcManager.broadcastMessage("REA:" + emoji + ":" + message.timestamp);
                    }
                })
                .show();
    }

    private void sendTypingStatus(boolean typing) {
        isTyping = typing;
        if (webRtcManager != null) {
            webRtcManager.broadcastMessage("TYP:" + (typing ? "ON" : "OFF"));
        }
    }

    private void startNetworkMonitoring() {
        qualityTimer = new Timer();
        qualityTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (webRtcManager != null) {
                    lastPingTime = System.currentTimeMillis();
                    webRtcManager.broadcastMessage("PNG:" + lastPingTime);
                }
            }
        }, 1000, 5000);
    }

    private void updateQualityUI(long rtt) {
        String color = "#40D080";
        String status = "Excellent";
        if (rtt > 150) { color = "#FFC107"; status = "Fair"; }
        if (rtt > 300) { color = "#F44336"; status = "Poor"; }
        
        qualityIndicator.setText(rtt + "ms (" + status + ")");
        qualityIndicator.setTextColor(android.graphics.Color.parseColor(color));
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        adapter.addMessage(new Message(username, text, true));
        scrollToBottom();
        messageInput.setText("");
        if (webRtcManager != null) webRtcManager.broadcastMessage(text);
    }

    private void sendImage(Uri uri) {
        String uriString = uri.toString();
        adapter.addMessage(new Message(username, uriString, Message.Type.IMAGE, true));
        scrollToBottom();
        if (webRtcManager != null) webRtcManager.broadcastMessage("IMG:" + uriString);
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            if (adapter.getItemCount() > 0)
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (qualityTimer != null) qualityTimer.cancel();
        if (webRtcManager != null) webRtcManager.onDestroy();
    }
}
