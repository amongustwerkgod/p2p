package com.example.Peerly;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Base64;

public class ChatActivity extends AppCompatActivity {

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private ImageButton attachButton;
    private String peerName;
    private String username;
    private String roomId;
    private boolean isCaller;
    private WebRtcManager webRtcManager;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    sendImage(uri);
                }
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
        isCaller = getIntent().getBooleanExtra("isCaller", false);

        if (peerName == null) peerName = "peer";
        if (roomId == null) roomId = "default-room";

        // Header
        TextView peerNameLabel = findViewById(R.id.peerNameLabel);
        TextView peerAvatar    = findViewById(R.id.peerAvatar);
        peerNameLabel.setText(peerName);
        peerAvatar.setText(String.valueOf(peerName.charAt(0)).toUpperCase());

        // Back
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

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
        attachButton = findViewById(R.id.attachButton);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            @Override public void onTextChanged(CharSequence s,int st,int b,int c){
                sendButton.setEnabled(!s.toString().trim().isEmpty());
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

        // Initialize WebRTC
        webRtcManager = new WebRtcManager(this, roomId, isCaller, new WebRtcManager.MessageListener() {
            @Override
            public void onMessageReceived(String message) {
                runOnUiThread(() -> {
                    if (message.startsWith("IMG:")) {
                        String data = message.substring(4);
                        adapter.addMessage(new Message(peerName, data, Message.Type.IMAGE, false));
                    } else {
                        adapter.addMessage(new Message(peerName, message, false));
                    }
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
        
        if (webRtcManager != null) {
            webRtcManager.sendMessage(text);
        }
    }

    private void sendImage(Uri uri) {
        // In a real P2P app, we'd send the file in chunks or via a separate stream.
        // For simplicity here, we'll convert to a local URI for display and send the path
        // (Note: This simple implementation assumes a shared environment or just UI demo)
        String uriString = uri.toString();
        adapter.addMessage(new Message(username, uriString, Message.Type.IMAGE, true));
        scrollToBottom();
        
        if (webRtcManager != null) {
            // Prefixing with IMG: to distinguish from text
            webRtcManager.sendMessage("IMG:" + uriString);
        }
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
        if (webRtcManager != null) {
            webRtcManager.onDestroy();
        }
    }
}
