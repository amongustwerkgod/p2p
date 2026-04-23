package com.example.Peerly;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class UsernameActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Full-screen immersive
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_username);

        EarthView earthView  = findViewById(R.id.earthView);
        View      loginCard  = findViewById(R.id.loginCard);
        EditText  input      = findViewById(R.id.usernameInput);
        Button    joinBtn    = findViewById(R.id.joinButton);

        // Earth starts below screen, rises with overshoot
        earthView.setTranslationY(900f);
        ObjectAnimator earthRise = ObjectAnimator.ofFloat(earthView, "translationY", 900f, 0f);
        earthRise.setDuration(900);
        earthRise.setInterpolator(new OvershootInterpolator(0.8f));

        // Card fades in after earth settles
        loginCard.setAlpha(0f);
        loginCard.setVisibility(View.VISIBLE);
        ObjectAnimator cardFade = ObjectAnimator.ofFloat(loginCard, "alpha", 0f, 1f);
        cardFade.setDuration(500);
        cardFade.setStartDelay(700);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(earthRise, cardFade);
        set.start();

        // Enter key on keyboard triggers join
        input.setOnEditorActionListener((v, actionId, event) -> {
            tryJoin(input);
            return true;
        });

        joinBtn.setOnClickListener(v -> tryJoin(input));
    }

    private void tryJoin(EditText input) {
        String name = input.getText().toString().trim();
        if (name.isEmpty()) {
            input.setError("pick a callsign");
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("username", name);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
