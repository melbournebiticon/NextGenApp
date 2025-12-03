package com.example.nextgen;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

public class LogoIntroActivity extends AppCompatActivity {

    private ImageView logoImage;
    private TextView titleText;
    private TextView subtitleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo_intro);

        logoImage = findViewById(R.id.logoImage);
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);

        titleText.setTextColor(0xFFFFFFFF);
        subtitleText.setTextColor(0xFFFFFFFF);
        titleText.setShadowLayer(0f, 0f, 0f, 0);
        subtitleText.setShadowLayer(0f, 0f, 0f, 0);

        titleText.setVisibility(View.INVISIBLE);
        subtitleText.setVisibility(View.INVISIBLE);

        logoImage.setScaleX(0f);
        logoImage.setScaleY(0f);

        logoImage.post(() -> {
            logoImage.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(800)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .withEndAction(this::logoPulseEffect)
                    .start();
        });
    }

    private void logoPulseEffect() {
        logoImage.animate()
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> logoImage.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(this::startTextAnimations)
                        .start())
                .start();
    }

    private void startTextAnimations() {
        // Text slide IN from below for modern effect, white text
        final int fromY = dpToPx(50);
        final int toY = 0;

        titleText.setVisibility(View.VISIBLE);
        subtitleText.setVisibility(View.INVISIBLE); // Subtitle visible after title animation

        titleText.setAlpha(0f);
        titleText.setTranslationY(fromY);

        // Title fades & slides up
        titleText.animate()
                .translationY(toY)
                .alpha(1f)
                .setDuration(700)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> showSubtitle(fromY, toY))
                .start();
    }

    private void showSubtitle(int fromY, int toY) {
        subtitleText.setVisibility(View.VISIBLE);
        subtitleText.setAlpha(0f);
        subtitleText.setTranslationY(fromY);

        // Subtitle fades & slides up after title
        subtitleText.animate()
                .translationY(toY)
                .alpha(1f)
                .setDuration(600)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(this::goToMainActivity)
                .start();
    }

    private void goToMainActivity() {
        // SLIDE ANIMATION!
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}