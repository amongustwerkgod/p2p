package com.example.Peerly;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.*;
import java.util.*;

public class WorldChatActivity extends AppCompatActivity {
    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText messageInput;
    private String username;
    private DatabaseReference chatRef;
    private ChildEventListener chatListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_world_chat);

        username = getIntent().getStringExtra("username");
        if (username == null) username = "Anonymous";

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        adapter = new MessageAdapter(username);
        
        adapter.setOnMessageLongClickListener((message, position) -> {
            if (message.sender.equals(username)) {
                showDeleteDialog(message, position);
            }
        });

        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setStackFromEnd(true);
        recyclerView.setLayoutManager(llm);
        recyclerView.setAdapter(adapter);

        messageInput = findViewById(R.id.messageInput);
        ImageButton sendBtn = findViewById(R.id.sendButton);

        chatRef = FirebaseDatabase.getInstance().getReference("nearby_chat");

        setupFirebaseListener();

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN
                            && !event.isShiftPressed())) {
                sendMessage(); return true;
            }
            return false;
        });
        sendBtn.setOnClickListener(v -> sendMessage());
    }

    private void showDeleteDialog(Message message, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (message.key != null) {
                        chatRef.child(message.key).removeValue();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupFirebaseListener() {
        chatListener = chatRef.limitToLast(100).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String prevKey) {
                String from = snapshot.child("from").getValue(String.class);
                String text = snapshot.child("text").getValue(String.class);
                String key = snapshot.getKey();
                if (from != null && text != null) {
                    boolean isMe = from.equals(username);
                    runOnUiThread(() -> {
                        // Check if message already exists (e.g. added locally)
                        if (adapter.getPositionOfMessage(key) == -1) {
                            adapter.addMessage(new Message(key, from, text, isMe));
                            scrollToBottom();
                        }
                    });
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String key = snapshot.getKey();
                if (key != null) {
                    runOnUiThread(() -> {
                        int pos = adapter.getPositionOfMessage(key);
                        if (pos != -1) {
                            adapter.removeMessage(pos);
                        }
                    });
                }
            }

            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildMoved(DataSnapshot s, String p) {}
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        Map<String, Object> msg = new HashMap<>();
        msg.put("from", username);
        msg.put("text", text);
        msg.put("ts", ServerValue.TIMESTAMP);

        // We let the listener handle adding to adapter for consistency and getting the key
        chatRef.push().setValue(msg);
        
        messageInput.setText("");
    }

    private void scrollToBottom() {
        recyclerView.post(() -> {
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatRef.removeEventListener(chatListener);
    }
}
