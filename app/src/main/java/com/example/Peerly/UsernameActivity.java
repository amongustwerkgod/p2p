package com.example.Peerly;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class UsernameActivity extends AppCompatActivity {

    private EarthView earthView;
    private View loginCard;
    private EditText input;
    private Button joinBtn;
    private boolean isJoining = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_username);

        earthView  = findViewById(R.id.earthView);
        loginCard  = findViewById(R.id.loginCard);
        input      = findViewById(R.id.usernameInput);
        joinBtn    = findViewById(R.id.joinButton);

        // Start as a distant star
        earthView.setZoomFactor(0.02f);
        
        // Initial fade in for the card
        loginCard.setAlpha(0f);
        loginCard.animate().alpha(1f).setDuration(1000).setStartDelay(500).start();

        input.setOnEditorActionListener((v, actionId, event) -> {
            tryJoin();
            return true;
        });

        joinBtn.setOnClickListener(v -> tryJoin());
    }

    private void tryJoin() {
        if (isJoining) return;
        
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            input.setError("pick a callsign");
            return;
        }
        
        isJoining = true;
        
        // Professional transition: Earth zooms in as card fades out
        loginCard.animate().alpha(0f).setDuration(400).start();
        
        ValueAnimator zoomAnim = ValueAnimator.ofFloat(0.02f, 1.0f);
        zoomAnim.setDuration(1200);
        zoomAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        zoomAnim.addUpdateListener(animation -> {
            earthView.setZoomFactor((float) animation.getAnimatedValue());
        });
        
        zoomAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Intent i = new Intent(UsernameActivity.this, MainActivity.class);
                i.putExtra("username", name);
                startActivity(i);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        
        zoomAnim.start();
    }
}
