package com.example.p2p;

import android.animation.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

/**
 * Hub screen.
 *  - Full-screen custom HubView draws Earth + orbiting peer bubbles on Canvas.
 *  - Tap Earth → Earth sinks below screen → WorldChatActivity slides up.
 *  - Tap peer bubble → ChatActivity for that peer.
 *  - When returning from world/peer chat the Earth rises back up.
 */
public class MainActivity extends AppCompatActivity {

    private HubView hubView;
    private String  username;

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

        hubView.setOnEarthClickedListener(() -> {
            // Earth sinks, then open world chat
            hubView.sinkEarth(() -> {
                Intent i = new Intent(MainActivity.this, WorldChatActivity.class);
                i.putExtra("username", username);
                startActivityForResult(i, 1);
                overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
            });
        });

        hubView.setOnPeerClickedListener(peerName -> {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("username", username);
            i.putExtra("peerName", peerName);
            startActivityForResult(i, 2);
            overridePendingTransition(R.anim.slide_in_up, android.R.anim.fade_out);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Earth rises back when returning from any sub-screen
        hubView.riseEarth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hubView.resumeAnimations();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hubView.pauseAnimations();
    }
}
