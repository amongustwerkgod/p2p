package com.example.Peerly;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private HubView hubView;
    private String username;
    private DatabaseReference presenceRef;
    private DatabaseReference myPresenceRef;
    private ValueEventListener presenceListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        username = getIntent().getStringExtra("username");
        if (username == null) username = "anon";

        hubView = new HubView(this, username);
        setContentView(hubView);

        // Firebase Presence Setup
        presenceRef = FirebaseDatabase.getInstance().getReference("presence");
        myPresenceRef = presenceRef.child(username);

        // Set self as online and handle disconnect
        myPresenceRef.child("online").setValue(true);
        myPresenceRef.child("online").onDisconnect().setValue(false);

        setupPresenceListener();

        hubView.setOnEarthClickedListener(() -> {
            hubView.sinkEarth(() -> {
                Intent i = new Intent(MainActivity.this, WorldChatActivity.class);
                i.putExtra("username", username);
                startActivityForResult(i, 1);
                overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
            });
        });

        hubView.setOnPeerClickedListener(peerName -> {
            String roomId = getRoomId(username, peerName);
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("username", username);
            i.putExtra("peerName", peerName);
            i.putExtra("roomId", roomId);
            i.putExtra("isCaller", username.compareTo(peerName) < 0);
            startActivityForResult(i, 2);
            overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
        });
    }

    private void setupPresenceListener() {
        presenceListener = presenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Set<String> activePeers = new HashSet<>();
                Log.d("Peerly", "Presence snapshot changed: " + snapshot.getChildrenCount() + " total nodes");
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean online = child.child("online").getValue(Boolean.class);
                    String name = child.getKey();
                    Log.d("Peerly", "Peer check: " + name + " online=" + online);
                    if (Boolean.TRUE.equals(online) && name != null && !name.equals(username)) {
                        activePeers.add(name);
                    }
                }
                Log.d("Peerly", "Updating HubView with " + activePeers.size() + " active peers");
                hubView.setPeers(activePeers);
            }
            @Override public void onCancelled(DatabaseError error) {
                Log.e("Peerly", "Firebase presence error: " + error.getMessage());
            }
        });
    }

    private String getRoomId(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? u1 + "_" + u2 : u2 + "_" + u1;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        hubView.riseEarth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        myPresenceRef.child("online").setValue(true);
        hubView.resumeAnimations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // We don't set online=false here so the orbit persists while in a sub-activity
        hubView.pauseAnimations();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (presenceListener != null) presenceRef.removeEventListener(presenceListener);
        myPresenceRef.child("online").setValue(false);
    }
}
