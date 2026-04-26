package com.example.Peerly;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private HubView hubView;
    private View loginContainer, chatContainer, mainRoot;
    private EditText usernameInput, messageInput;
    private Button joinButton;
    private ImageButton sendButton, backButton;
    private RecyclerView recyclerView;
    private MessageAdapter chatAdapter;
    
    private String username;
    private DatabaseReference presenceRef, chatRef;
    private DatabaseReference myPresenceRef;
    private ValueEventListener presenceListener;
    private ChildEventListener chatListener;

    private boolean isLoggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use fitSystemWindows to handle status bar and navigation bar properly
        setContentView(R.layout.activity_main);
        
        mainRoot = findViewById(R.id.mainRoot);
        
        // Comprehensive Window Insets Handling for modern displays
        ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, insets) -> {
            int imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int systemBarsTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            
            // Padding top to avoid status bar / slide down toolbar
            v.setPadding(0, systemBarsTop, 0, Math.max(imeHeight, systemBarsBottom));
            return insets;
        });

        // UI Initialization
        hubView = findViewById(R.id.hubView);
        loginContainer = findViewById(R.id.loginContainer);
        chatContainer = findViewById(R.id.chatContainer);
        usernameInput = findViewById(R.id.usernameInput);
        joinButton = findViewById(R.id.joinButton);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        recyclerView = findViewById(R.id.recyclerView);

        hubView.setZoomFactor(0.02f);
        joinButton.setOnClickListener(v -> attemptLogin());
        hubView.setOnEarthClickedListener(() -> showWorldChat());
        
        hubView.setOnPeerClickedListener(peerName -> {
            String roomId = getRoomId(username, peerName);
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("username", username);
            i.putExtra("peerName", peerName);
            i.putExtra("roomId", roomId);
            i.putExtra("isCaller", username.compareTo(peerName) < 0);
            startActivity(i);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        if (backButton != null) {
            backButton.setOnClickListener(v -> hideWorldChat());
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (chatContainer.getVisibility() == View.VISIBLE) {
                    hideWorldChat();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        setupChat();
    }

    private void attemptLogin() {
        String name = usernameInput.getText().toString().trim();
        if (name.isEmpty()) {
            usernameInput.setError("pick a callsign");
            return;
        }
        username = name;
        isLoggedIn = true;

        loginContainer.animate().alpha(0f).setDuration(500).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                loginContainer.setVisibility(View.GONE);
            }
        }).start();

        ValueAnimator zoom = ValueAnimator.ofFloat(0.02f, 1.0f);
        zoom.setDuration(1200);
        zoom.setInterpolator(new AccelerateDecelerateInterpolator());
        zoom.addUpdateListener(a -> hubView.setZoomFactor((float) a.getAnimatedValue()));
        zoom.start();

        startPresence();
    }

    private void startPresence() {
        presenceRef = FirebaseDatabase.getInstance().getReference("presence");
        myPresenceRef = presenceRef.child(username);
        myPresenceRef.child("online").setValue(true);
        myPresenceRef.child("online").onDisconnect().setValue(false);

        presenceListener = presenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<String> activePeers = new HashSet<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean online = child.child("online").getValue(Boolean.class);
                    String name = child.getKey();
                    if (Boolean.TRUE.equals(online) && name != null && !name.equals(username)) {
                        activePeers.add(name);
                    }
                }
                hubView.setPeers(activePeers);
                TextView onlineCountLabel = findViewById(R.id.onlineCountLabel);
                if (onlineCountLabel != null) onlineCountLabel.setText(activePeers.size() + " online");
            }
            @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void setupChat() {
        chatAdapter = new MessageAdapter(username != null ? username : "anon");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && chatAdapter.getItemCount() > 0) {
                recyclerView.postDelayed(() -> recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1), 100);
            }
        });

        chatRef = FirebaseDatabase.getInstance().getReference("nearby_chat");
        sendButton.setOnClickListener(v -> sendMessage());
        
        chatListener = chatRef.limitToLast(50).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String s) {
                String from = snapshot.child("from").getValue(String.class);
                String text = snapshot.child("text").getValue(String.class);
                String key = snapshot.getKey();
                if (from != null && text != null) {
                    chatAdapter.addMessage(new Message(key, from, text, from.equals(username)));
                    recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }
            @Override public void onChildChanged(DataSnapshot s, String p) {}
            @Override public void onChildRemoved(DataSnapshot s) {}
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
        chatRef.push().setValue(msg);
        messageInput.setText("");
    }

    private void showWorldChat() {
        if (chatContainer.getVisibility() == View.VISIBLE) return;
        chatContainer.setVisibility(View.VISIBLE);
        chatContainer.setAlpha(0f);
        chatContainer.animate().alpha(1f).setDuration(800).start();
        ValueAnimator zoom = ValueAnimator.ofFloat(1.0f, 10.0f);
        zoom.setDuration(1000);
        zoom.setInterpolator(new AccelerateDecelerateInterpolator());
        zoom.addUpdateListener(a -> hubView.setZoomFactor((float) a.getAnimatedValue()));
        zoom.start();
        hubView.animate().alpha(0f).setDuration(1000).start();
    }

    private void hideWorldChat() {
        chatContainer.animate().alpha(0f).setDuration(600).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                chatContainer.setVisibility(View.GONE);
            }
        }).start();
        ValueAnimator zoom = ValueAnimator.ofFloat(10.0f, 1.0f);
        zoom.setDuration(800);
        zoom.setInterpolator(new DecelerateInterpolator());
        zoom.addUpdateListener(a -> hubView.setZoomFactor((float) a.getAnimatedValue()));
        zoom.start();
        hubView.animate().alpha(1f).setDuration(800).start();
    }

    private String getRoomId(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "_" + u2 : u2 + "_" + u1;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isLoggedIn && myPresenceRef != null) myPresenceRef.child("online").setValue(true);
        hubView.resumeAnimations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hubView.pauseAnimations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenceListener != null) presenceRef.removeEventListener(presenceListener);
        if (chatListener != null) chatRef.removeEventListener(chatListener);
        if (myPresenceRef != null) myPresenceRef.child("online").setValue(false);
    }
}
