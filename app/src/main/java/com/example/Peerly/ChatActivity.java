package com.example.Peerly;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.webrtc.DataChannel;

import java.nio.charset.StandardCharsets;

public class ChatActivity extends AppCompatActivity {

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private String peerName;
    private String username;
    private String roomId;
    private boolean isCaller;
    private WebRtcManager webRtcManager;

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
        isCaller = getIntent().getBooleanExtra("isCaller", false);

        if (peerName == null) peerName = "peer";
        if (roomId == null) roomId = "default-room";

        // Header
        TextView peerNameLabel = findViewById(R.id.peerNameLabel);
        TextView peerAvatar    = findViewById(R.id.peerAvatar);
        peerNameLabel.setText(peerName);
        peerAvatar.setText(String.valueOf(peerName.charAt(0)).toUpperCase());

        // Back
        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        // RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        adapter = new MessageAdapter(username);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(adapter);

        // Input
        messageInput = findViewById(R.id.messageInput);
        sendButton   = findViewById(R.id.sendButton);

        // Enable send button only when there's text
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                sendButton.setEnabled(!s.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable s){}
        });

        // Enter key sends (Shift+Enter = new line)
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

        // Initialize WebRTC
        webRtcManager = new WebRtcManager(this, roomId, isCaller, new WebRtcManager.MessageListener() {
            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> {
                    adapter.addMessage(new Message(peerName, message, false));
                    scrollToBottom();
                });
            }

            @Override
            public void onStatusChanged(String status) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, status, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        
        adapter.addMessage(new Message(username, text, true));
        scrollToBottom();
        messageInput.setText("");
        
        // Send via WebRTC
        if (webRtcManager != null) {
            webRtcManager.sendMessage(text);
        }
    }

    private void scrollToBottom() {
        recyclerView.post(() ->
            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webRtcManager != null) {
            webRtcManager.onDestroy();
        }
    }
}
